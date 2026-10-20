package com.durpalla.booking.calling

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap

/** In-memory Firestore shape for mock mode. */
class MockSignalingRepository : SignalingRepository {
    private class Doc(
        val callId: String,
        val tripId: Int,
        var callerId: String,
        var callerRole: String,
        var calleeId: String,
        var calleeRole: String,
        var status: CallStatus = CallStatus.RINGING,
        var offer: SdpPayload? = null,
        var answer: SdpPayload? = null,
        var endedBy: String? = null,
        var reason: String? = null,
    )

    private val docs = ConcurrentHashMap<String, MutableStateFlow<Doc?>>()
    private val callerCand = ConcurrentHashMap<String, MutableSharedFlow<IceCandidatePayload>>()
    private val calleeCand = ConcurrentHashMap<String, MutableSharedFlow<IceCandidatePayload>>()

    private fun docFlow(callId: String) = docs.getOrPut(callId) { MutableStateFlow(null) }

    private fun publish(callId: String) {
        val f = docFlow(callId)
        val cur = f.value ?: return
        f.value = Doc(
            cur.callId, cur.tripId, cur.callerId, cur.callerRole, cur.calleeId, cur.calleeRole,
            cur.status, cur.offer, cur.answer, cur.endedBy, cur.reason,
        )
    }

    override fun observeCall(callId: String): Flow<SignalingSnapshot> =
        docFlow(callId).filterNotNull().map {
            SignalingSnapshot(
                callId = it.callId,
                tripId = it.tripId,
                status = it.status,
                callerId = it.callerId,
                callerRole = it.callerRole,
                calleeId = it.calleeId,
                calleeRole = it.calleeRole,
                offer = it.offer,
                answer = it.answer,
                endedBy = it.endedBy,
                reason = it.reason,
            )
        }

    override suspend fun createCallDoc(
        callId: String,
        tripId: Int,
        callerId: String,
        callerRole: CallRole,
        calleeId: String,
        calleeRole: CallRole,
    ) {
        docFlow(callId).value = Doc(
            callId = callId,
            tripId = tripId,
            callerId = callerId,
            callerRole = callerRole.wire,
            calleeId = calleeId,
            calleeRole = calleeRole.wire,
            status = CallStatus.RINGING,
        )
        callerCand[callId] = MutableSharedFlow(extraBufferCapacity = 64)
        calleeCand[callId] = MutableSharedFlow(extraBufferCapacity = 64)
    }

    override suspend fun setOffer(callId: String, offer: SdpPayload) {
        docFlow(callId).value?.let {
            it.offer = offer
            it.status = CallStatus.RINGING
            publish(callId)
        }
    }

    override suspend fun setAnswer(callId: String, answer: SdpPayload) {
        docFlow(callId).value?.let {
            it.answer = answer
            it.status = CallStatus.ACCEPTED
            publish(callId)
        }
    }

    override suspend fun setStatus(callId: String, status: CallStatus, endedBy: String?, reason: String?) {
        docFlow(callId).value?.let {
            it.status = status
            if (endedBy != null) it.endedBy = endedBy
            if (reason != null) it.reason = reason
            publish(callId)
        }
    }

    override suspend fun addCallerCandidate(callId: String, candidate: IceCandidatePayload) {
        callerCand.getOrPut(callId) { MutableSharedFlow(extraBufferCapacity = 64) }.emit(candidate)
    }

    override suspend fun addCalleeCandidate(callId: String, candidate: IceCandidatePayload) {
        calleeCand.getOrPut(callId) { MutableSharedFlow(extraBufferCapacity = 64) }.emit(candidate)
    }

    override fun observeRemoteCandidates(callId: String, asCaller: Boolean): Flow<IceCandidatePayload> =
        if (asCaller) calleeCand.getOrPut(callId) { MutableSharedFlow(extraBufferCapacity = 64) }
        else callerCand.getOrPut(callId) { MutableSharedFlow(extraBufferCapacity = 64) }
}
