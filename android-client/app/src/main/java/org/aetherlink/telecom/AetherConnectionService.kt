package org.aetherlink.telecom

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.util.Log

class AetherConnectionService : ConnectionService() {

    companion object {
        private const val TAG = "AetherConnectionService"
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.i(TAG, "Incoming Telecom connection created.")
        return super.onCreateIncomingConnection(connectionManagerPhoneAccount, request)
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.i(TAG, "Outgoing Telecom connection requested from Mac dialer.")
        return super.onCreateOutgoingConnection(connectionManagerPhoneAccount, request)
    }
}
