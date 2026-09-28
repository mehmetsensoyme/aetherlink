package org.aetherlink.telecom

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.aetherlink.service.AetherCoreService
import org.aetherlink.service.AetherNotificationListener

/**
 * Deterministic Call State Machine for AetherLink.
 * Single source of truth for all cellular and VoIP calls.
 * Isolates Samsung One UI and OEM InCallUI conflicting notifications.
 */
object CallStateMachine {
    private const val TAG = "CallStateMachine"

    enum class State {
        IDLE,
        RINGING_INCOMING,
        DIALING_OUTGOING,
        ACTIVE_TALKING,
        TERMINATED
    }

    data class Session(
        val callId: String,
        val state: State,
        val isOutgoing: Boolean,
        val callerName: String,
        val phoneNumber: String,
        val appType: String = "cellular",
        val startTime: Long = System.currentTimeMillis(),
        var answeredTime: Long? = null
    )

    @Volatile
    private var currentSession: Session? = null

    @Synchronized
    fun getState(): State = currentSession?.state ?: State.IDLE

    @Synchronized
    fun getSession(): Session? = currentSession

    fun isRinging(): Boolean = (getState() == State.RINGING_INCOMING)
    fun isDialing(): Boolean = (getState() == State.DIALING_OUTGOING)
    fun isActive(): Boolean = (getState() == State.ACTIVE_TALKING)

    /**
     * Triggered when an incoming ringing call is detected from TelephonyManager or Dialer Notification.
     */
    @Synchronized
    fun onIncomingRinging(
        context: Context,
        rawNumber: String?,
        callId: String = System.currentTimeMillis().toString(),
        appType: String = "cellular"
    ) {
        val existing = currentSession
        if (existing != null && existing.state == State.RINGING_INCOMING) {
            // Already ringing, attempt to refine contact info if better
            refineContactInfo(context, rawNumber)
            return
        }

        if (existing != null && existing.state == State.ACTIVE_TALKING) {
            Log.w(TAG, "Ignoring incoming ringing: call already active: ${existing.callId}")
            return
        }

        val resolved = ContactResolver.resolve(context, rawNumber)
        val session = Session(
            callId = callId,
            state = State.RINGING_INCOMING,
            isOutgoing = false,
            callerName = resolved.contactName,
            phoneNumber = resolved.phoneNumber,
            appType = appType
        )
        currentSession = session
        Log.i(TAG, "State transition -> RINGING_INCOMING: ${session.callerName} (${session.phoneNumber})")

        broadcastStatus(session)
    }

    /**
     * Triggered when an outgoing call is dialed from the device.
     */
    @Synchronized
    fun onOutgoingDialing(
        context: Context,
        rawNumber: String?,
        callId: String = System.currentTimeMillis().toString(),
        appType: String = "cellular"
    ) {
        val existing = currentSession
        if (existing != null && (existing.state == State.DIALING_OUTGOING || existing.state == State.ACTIVE_TALKING)) {
            Log.d(TAG, "Already in outgoing/active state, ignoring redundant dialing event.")
            return
        }

        val resolved = if (!rawNumber.isNullOrBlank()) {
            ContactResolver.resolve(context, rawNumber)
        } else {
            ContactResolver.getLatestOutgoingCall(context) ?: ResolvedContact(
                contactName = "Giden Arama",
                phoneNumber = ContactResolver.UNKNOWN_NUMBER,
                isKnown = false
            )
        }

        val session = Session(
            callId = callId,
            state = State.DIALING_OUTGOING,
            isOutgoing = true,
            callerName = resolved.contactName,
            phoneNumber = resolved.phoneNumber,
            appType = appType
        )
        currentSession = session
        Log.i(TAG, "State transition -> DIALING_OUTGOING: ${session.callerName} (${session.phoneNumber})")

        broadcastStatus(session)
    }

    /**
     * Triggered when a call transitions to active talking:
     * - Incoming call answered locally or from Mac
     * - Outgoing call answered by remote party (chronometer started)
     */
    @Synchronized
    fun onCallAnswered(context: Context, answeredFromMac: Boolean = false) {
        val existing = currentSession ?: run {
            Log.w(TAG, "onCallAnswered called with no active session. Creating fallback session.")
            Session(
                callId = System.currentTimeMillis().toString(),
                state = State.ACTIVE_TALKING,
                isOutgoing = false,
                callerName = "Görüşme",
                phoneNumber = ""
            )
        }

        if (existing.state == State.ACTIVE_TALKING) {
            Log.d(TAG, "Call is already in ACTIVE_TALKING state. No-op.")
            return
        }

        val updated = existing.copy(
            state = State.ACTIVE_TALKING,
            answeredTime = System.currentTimeMillis()
        )
        currentSession = updated
        Log.i(TAG, "State transition -> ACTIVE_TALKING: ${updated.callerName} (answeredFromMac=$answeredFromMac)")

        if (answeredFromMac) {
            CallActionHelper.routeAudioForRemoteAnswer(context)
        }

        broadcastStatus(updated)

        // Dispatch backwards-compatible CALL_ACTION "answered"
        val answeredPayload = JsonObject().apply {
            addProperty("callId", updated.callId)
            addProperty("action", "answered")
            addProperty("timestamp", System.currentTimeMillis().toDouble())
        }
        AetherCoreService.instance?.sendMessage("CALL_ACTION", answeredPayload)
    }

    /**
     * Seamlessly update contact info without resetting call state or triggering unwanted ringtones.
     */
    @Synchronized
    fun updateContactInfo(callerName: String, phoneNumber: String) {
        val session = currentSession ?: return
        val isBetterName = callerName.isNotBlank() &&
                callerName != ContactResolver.UNKNOWN_NUMBER &&
                callerName != "Gelen Arama" &&
                callerName != "Giden Arama" &&
                callerName != session.callerName

        val isBetterNumber = phoneNumber.isNotBlank() &&
                phoneNumber != ContactResolver.UNKNOWN_NUMBER &&
                phoneNumber != session.phoneNumber

        if (isBetterName || isBetterNumber) {
            val newName = if (isBetterName) callerName else session.callerName
            val newNumber = if (isBetterNumber) phoneNumber else session.phoneNumber
            val updated = session.copy(callerName = newName, phoneNumber = newNumber)
            currentSession = updated
            Log.i(TAG, "Updated contact info for active call: '$newName' ($newNumber)")
            broadcastStatus(updated)
        }
    }

    private fun refineContactInfo(context: Context, rawNumber: String?) {
        val session = currentSession ?: return
        if (rawNumber.isNullOrBlank()) return
        val resolved = ContactResolver.resolve(context, rawNumber)
        updateContactInfo(resolved.contactName, resolved.phoneNumber)
    }

    /**
     * Triggered when call ends (Telephony IDLE, notification removed, or explicit hangup).
     */
    @Synchronized
    fun onCallTerminated(context: Context) {
        val session = currentSession
        if (session == null || session.state == State.IDLE) {
            Log.d(TAG, "onCallTerminated called but state is already IDLE.")
            return
        }

        Log.i(TAG, "State transition -> TERMINATED (previous: ${session.state}): ${session.callId}")
        val terminatedSession = session.copy(state = State.TERMINATED)
        currentSession = null

        broadcastStatus(terminatedSession)

        // Backwards-compatible CALL_ACTION "hangup"
        val dropPayload = JsonObject().apply {
            addProperty("callId", terminatedSession.callId)
            addProperty("action", "hangup")
            addProperty("timestamp", System.currentTimeMillis().toDouble())
        }
        AetherCoreService.instance?.sendMessage("CALL_ACTION", dropPayload)

        // Teardown audio and intents
        CallActionHelper.setSpeakerphone(context, false)
        AetherNotificationListener.clearActiveCallIntents()
        org.aetherlink.audio.CallAudioRelayManager.stop(context)
    }

    private fun broadcastStatus(session: Session) {
        val payload = JsonObject().apply {
            addProperty("callId", session.callId)
            addProperty("state", session.state.name)
            addProperty("direction", if (session.isOutgoing) "outgoing" else "incoming")
            addProperty("callerName", session.callerName)
            addProperty("contact_name", session.callerName)
            addProperty("phoneNumber", session.phoneNumber)
            addProperty("appType", session.appType)
            addProperty("timestamp", session.startTime.toDouble())
            addProperty("answeredTime", session.answeredTime?.toDouble() ?: 0.0)
            addProperty("hasVideo", false)
        }

        // 1. Send dedicated deterministic CALL_STATUS event
        AetherCoreService.instance?.sendMessage("CALL_STATUS", payload)

        // 2. Backward compatibility: CALL_INCOMING for ring / dial
        if (session.state == State.RINGING_INCOMING || session.state == State.DIALING_OUTGOING) {
            AetherCoreService.instance?.sendMessage("CALL_INCOMING", payload)
        }
    }
}
