package org.aetherlink.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.aetherlink.telecom.CallActionHelper
import org.aetherlink.telecom.CallStateMachine
import org.aetherlink.telecom.ContactResolver

class CallStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallStateReceiver"
        private var lastState = TelephonyManager.EXTRA_STATE_IDLE
        private var lastOutgoingNumber: String? = null
        private var wasAnsweredFromMac = false

        fun markAnsweredFromMac() {
            wasAnsweredFromMac = true
        }

        fun isRinging(): Boolean = CallStateMachine.isRinging()
        fun isOutgoing(): Boolean = CallStateMachine.isDialing()
        fun isCallActive(): Boolean = CallStateMachine.isActive()

        fun notifyCallAnswered(context: Context) {
            CallStateMachine.onCallAnswered(context, wasAnsweredFromMac)
        }

        fun updateCallInfoFromNotification(context: Context, contactName: String, phoneNumber: String) {
            CallStateMachine.updateContactInfo(contactName, phoneNumber)
        }

        fun onCallLogChanged(context: Context) {
            if (CallStateMachine.isDialing()) {
                val latest = ContactResolver.getLatestOutgoingCall(context)
                if (latest != null && latest.contactName != ContactResolver.UNKNOWN_NUMBER) {
                    Log.i(TAG, "CallLog observer resolved outgoing call: ${latest.contactName} (${latest.phoneNumber})")
                    CallStateMachine.updateContactInfo(latest.contactName, latest.phoneNumber)
                }
            }
        }

        fun handleStateChange(context: Context, stateStr: String, rawIncomingNumber: String?) {
            Log.i(TAG, "Phone state transition: $stateStr (previous: $lastState, incoming: $rawIncomingNumber)")
            if (stateStr == lastState) return
            val previousState = lastState
            lastState = stateStr

            when (stateStr) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    // Incoming Call Ringing
                    CallStateMachine.onIncomingRinging(context, rawIncomingNumber)
                }

                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    if (previousState == TelephonyManager.EXTRA_STATE_RINGING) {
                        // Incoming call was answered
                        CallStateMachine.onCallAnswered(context, wasAnsweredFromMac)
                    } else if (previousState == TelephonyManager.EXTRA_STATE_IDLE) {
                        // Outgoing call was initiated
                        val rawTarget = rawIncomingNumber ?: lastOutgoingNumber
                        CallStateMachine.onOutgoingDialing(context, rawTarget)
                        scheduleOutgoingCallLookup(context)
                    }
                }

                TelephonyManager.EXTRA_STATE_IDLE -> {
                    // Call terminated
                    CallStateMachine.onCallTerminated(context)
                    wasAnsweredFromMac = false
                    lastOutgoingNumber = null
                }
            }
        }

        private fun scheduleOutgoingCallLookup(context: Context) {
            val appContext = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                val delays = listOf(350L, 800L, 1600L)
                for (delayMs in delays) {
                    delay(delayMs)
                    if (!CallStateMachine.isDialing() && !CallStateMachine.isActive()) break
                    val latest = ContactResolver.getLatestOutgoingCall(appContext)
                    if (latest != null && latest.contactName != ContactResolver.UNKNOWN_NUMBER) {
                        Log.i(TAG, "Async outgoing lookup succeeded: ${latest.contactName} (${latest.phoneNumber})")
                        CallStateMachine.updateContactInfo(latest.contactName, latest.phoneNumber)
                        break
                    }
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Handle Outgoing Call broadcast (Dialing number capture)
        if (intent.action == Intent.ACTION_NEW_OUTGOING_CALL) {
            val dialedNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
            if (!dialedNumber.isNullOrBlank()) {
                lastOutgoingNumber = dialedNumber
                Log.i(TAG, "Captured outgoing dialed number: $dialedNumber")
            }
            return
        }

        val isTestPhoneState = (intent.action == "org.aetherlink.test.PHONE_STATE")
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED && !isTestPhoneState) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val rawIncomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            ?: intent.getStringExtra("extra_outgoing_number")

        handleStateChange(context, stateStr, rawIncomingNumber)
    }
}
