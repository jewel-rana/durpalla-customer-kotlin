package com.durpalla.booking.calling

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

/**
 * In-app driver calling (audio WebRTC + Firestore signaling).
 * Contract: docs/calling-contract.md (Durpalla/durpalla-driver).
 */
interface CallRepository {
    val session: StateFlow<CallSession?>
    val callLog: StateFlow<List<CallLogEntry>>

    fun logForTrip(tripId: Int): Flow<List<CallLogEntry>>

    /** Merge server trip `callLog[]` into the local log for UI. */
    fun ingestTripCallLog(tripId: Int, entries: List<JsonObject>)

    fun canCallDriver(tripId: Int, bidAccepted: Boolean = false, tripActive: Boolean = false): Boolean

    suspend fun startOutgoing(tripId: Int, driverName: String, callerName: String = ""): CallSession
    suspend fun handleIncoming(
        callId: String,
        tripId: Int,
        callerName: String,
        provider: CallProvider = CallProvider.AGORA,
    )
    /** FCM / Pusher `call.ended|declined|missed` when Firestore listener is not yet attached. */
    suspend fun onRemoteEnded(callId: String, status: CallStatus)
    suspend fun accept()
    suspend fun decline(reason: String = "declined")
    suspend fun end(reason: CallEndReason = CallEndReason.HANGUP)
    suspend fun setMuted(muted: Boolean)
    suspend fun setSpeaker(on: Boolean)

    /** GET /calls/ice-servers with STUN fallback + ttl/2 cache. */
    suspend fun fetchIceServers(): IceServersResponse
}

object CallTimeouts {
    const val RING_MISS_MS = 30_000L
    const val FALLBACK_STUN = "stun:stun.l.google.com:19302"
}
