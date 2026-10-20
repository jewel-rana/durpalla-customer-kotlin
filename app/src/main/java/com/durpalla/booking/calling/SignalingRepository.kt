package com.durpalla.booking.calling

import kotlinx.coroutines.flow.Flow

/**
 * Firestore `calls/{callId}` signaling.
 *
 * Doc fields (exact): callId, tripId, callerId, callerRole, calleeId, calleeRole,
 * offer{type,sdp}, answer{type,sdp}, status (ringing|accepted|declined|ended|missed),
 * createdAt, answeredAt, endedAt, endedBy, reason.
 * Subcollections: callerCandidates / calleeCandidates → {candidate, sdpMid, sdpMLineIndex}.
 * Only caller/callee may read/write the doc.
 *
 * TODO(calling): Durpalla/durpalla-driver docs/calling-contract.md
 */
interface SignalingRepository {
    fun observeCall(callId: String): Flow<SignalingSnapshot>

    suspend fun createCallDoc(
        callId: String,
        tripId: Int,
        callerId: String,
        callerRole: CallRole = CallRole.CUSTOMER,
        calleeId: String = "",
        calleeRole: CallRole = CallRole.DRIVER,
    )

    suspend fun setOffer(callId: String, offer: SdpPayload)
    suspend fun setAnswer(callId: String, answer: SdpPayload)
    suspend fun setStatus(
        callId: String,
        status: CallStatus,
        endedBy: String? = null,
        reason: String? = null,
    )

    suspend fun addCallerCandidate(callId: String, candidate: IceCandidatePayload)
    suspend fun addCalleeCandidate(callId: String, candidate: IceCandidatePayload)

    fun observeRemoteCandidates(callId: String, asCaller: Boolean): Flow<IceCandidatePayload>
}

data class SignalingSnapshot(
    val callId: String,
    val tripId: Int,
    val status: CallStatus,
    val callerId: String,
    val callerRole: String,
    val calleeId: String,
    val calleeRole: String,
    val offer: SdpPayload? = null,
    val answer: SdpPayload? = null,
    val endedBy: String? = null,
    val reason: String? = null,
)
