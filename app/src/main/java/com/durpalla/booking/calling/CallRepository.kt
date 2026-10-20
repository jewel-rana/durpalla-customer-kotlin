package com.durpalla.booking.calling

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-app driver calling (audio WebRTC + Firestore signaling).
 *
 * HTTP (exact):
 * - POST /calls {tripId} → {callId}
 * - POST /calls/{id}/accept
 * - POST /calls/{id}/decline {reason}
 * - POST /calls/{id}/end
 * - GET /calls/ice-servers → [{urls, username, credential}] + ttl (fallback stun:stun.l.google.com:19302)
 * - POST /devices/voip {voipToken, platform:'ios', userId} — iOS only (N/A on Android)
 *
 * Ring: FCM high-priority data `type=call.incoming {callId, tripId, callerName}`; Pusher secondary.
 * Missed after 30s unanswered.
 *
 * TODO(calling): Durpalla/durpalla-driver docs/calling-contract.md
 */
interface CallRepository {
    val session: StateFlow<CallSession?>
    val callLog: StateFlow<List<CallLogEntry>>

    fun logForTrip(tripId: Int): Flow<List<CallLogEntry>>

    fun canCallDriver(tripId: Int, bidAccepted: Boolean = false, tripActive: Boolean = false): Boolean

    suspend fun startOutgoing(tripId: Int, driverName: String, callerName: String = "You"): CallSession
    suspend fun handleIncoming(callId: String, tripId: Int, callerName: String)
    suspend fun accept()
    suspend fun decline(reason: String = "declined")
    suspend fun end()
    suspend fun setMuted(muted: Boolean)
    suspend fun setSpeaker(on: Boolean)

    /** GET /calls/ice-servers with STUN fallback. */
    suspend fun fetchIceServers(): IceServersResponse
}

object CallTimeouts {
    const val RING_MISS_MS = 30_000L
    const val FALLBACK_STUN = "stun:stun.l.google.com:19302"
}
