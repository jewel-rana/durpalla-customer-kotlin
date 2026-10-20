package com.durpalla.booking.calling

import com.durpalla.booking.R
import com.durpalla.booking.core.AppCtx

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import java.util.UUID

/**
 * Fake callId + simulated events. Statuses: ringing → accepted | declined | ended | missed (30s).
 */
class MockCallRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val signaling: MockSignalingRepository = MockSignalingRepository(),
) : CallRepository {

    private val _session = MutableStateFlow<CallSession?>(null)
    override val session: StateFlow<CallSession?> = _session.asStateFlow()

    private val _log = MutableStateFlow<List<CallLogEntry>>(emptyList())
    override val callLog: StateFlow<List<CallLogEntry>> = _log.asStateFlow()

    private var ringJob: Job? = null
    private var transport: CallTransport? = null
    private var startedAt = 0L
    private val mockCallerId = "mock_customer"
    private val mockCalleeId = "mock_driver"

    override fun logForTrip(tripId: Int): Flow<List<CallLogEntry>> =
        callLog.map { list -> list.filter { it.tripId == tripId } }

    override fun ingestTripCallLog(tripId: Int, entries: List<JsonObject>) {
        val parsed = entries.mapNotNull { CallLogEntry.fromTripJson(it, tripId) }
        if (parsed.isEmpty()) return
        val others = _log.value.filter { it.tripId != tripId }
        _log.value = parsed + others
    }

    override fun canCallDriver(tripId: Int, bidAccepted: Boolean, tripActive: Boolean): Boolean =
        bidAccepted || tripActive

    override suspend fun startOutgoing(tripId: Int, driverName: String, callerName: String): CallSession {
        val callerName = callerName.ifBlank { AppCtx.str(R.string.you) }
        endInternal(CallStatus.ENDED, appendLog = false)
        val callId = "mock_${UUID.randomUUID().toString().take(8)}"
        // Prod: POST /calls → {data:{callId, provider, agora|null, iceServers|null, ttl?}}
        signaling.createCallDoc(callId, tripId, mockCallerId, CallRole.CUSTOMER, mockCalleeId, CallRole.DRIVER)
        startedAt = System.currentTimeMillis()
        val s = CallSession(
            callId = callId,
            tripId = tripId,
            peerName = driverName,
            isOutgoing = true,
            status = CallStatus.RINGING,
            provider = CallProvider.WEBRTC,
            callerId = mockCallerId,
            calleeId = mockCalleeId,
        )
        _session.value = s
        CallForegroundService.start(context, driverName, outgoing = true)
        val media = MockCallTransport(scope)
        transport = media
        media.prepare(
            asCaller = true,
            callbacks = CallTransportCallbacks(
                onConnected = {
                    scope.launch {
                        if (_session.value?.callId == callId && _session.value?.status == CallStatus.RINGING) {
                            updateStatus(CallStatus.ACCEPTED, answeredAt = System.currentTimeMillis())
                            signaling.setStatus(callId, CallStatus.ACCEPTED)
                        }
                    }
                },
            ),
        )
        media.startOutgoing(callId)
        ringJob?.cancel()
        scope.launch {
            delay(CallTimeouts.RING_MISS_MS)
            val cur = _session.value
            if (cur?.callId == callId && cur.status == CallStatus.RINGING) {
                endInternal(CallStatus.MISSED, endedBy = "system", reason = "timeout")
                CallNotifications.showMissed(context, driverName, tripId)
            }
        }
        return s
    }

    override suspend fun handleIncoming(
        callId: String,
        tripId: Int,
        callerName: String,
        provider: CallProvider,
    ) {
        endInternal(CallStatus.ENDED, appendLog = false)
        signaling.createCallDoc(callId, tripId, "driver_or_peer", CallRole.DRIVER, mockCallerId, CallRole.CUSTOMER)
        startedAt = System.currentTimeMillis()
        _session.value = CallSession(
            callId = callId,
            tripId = tripId,
            peerName = callerName,
            isOutgoing = false,
            status = CallStatus.RINGING,
            provider = provider,
            callerId = "driver_or_peer",
            calleeId = mockCallerId,
        )
        CallForegroundService.start(context, callerName, outgoing = false)
        CallNotifications.showIncoming(context, callId, tripId, callerName)
        scope.launch {
            delay(CallTimeouts.RING_MISS_MS)
            val cur = _session.value
            if (cur?.callId == callId && cur.status == CallStatus.RINGING) {
                endInternal(CallStatus.MISSED, endedBy = "system", reason = "timeout")
                CallNotifications.showMissed(context, callerName, tripId)
            }
        }
    }

    override suspend fun onRemoteEnded(callId: String, status: CallStatus) {
        val cur = _session.value ?: return
        if (cur.callId != callId || !status.isTerminal) return
        endInternal(status, endedBy = "remote", reason = status.wire)
    }

    override suspend fun accept() {
        val s = _session.value ?: return
        ringJob?.cancel()
        // Prod: POST /calls/{id}/accept → {data:{callId, provider, agora|null, iceServers|null, ttl?}}
        val media = transport ?: MockCallTransport(scope).also { transport = it }
        media.prepare(asCaller = false, callbacks = CallTransportCallbacks())
        media.startIncoming(s.callId)
        updateStatus(CallStatus.ACCEPTED, answeredAt = System.currentTimeMillis())
        signaling.setStatus(s.callId, CallStatus.ACCEPTED)
        CallNotifications.cancelIncoming(context)
        CallForegroundService.start(context, s.peerName, outgoing = false)
    }

    override suspend fun decline(reason: String) {
        // Prod: POST /calls/{id}/decline → may echo provider
        endInternal(CallStatus.DECLINED, endedBy = mockCallerId, reason = reason)
        CallNotifications.cancelIncoming(context)
    }

    override suspend fun end(reason: CallEndReason) {
        val status = if (reason == CallEndReason.MISSED) CallStatus.MISSED else CallStatus.ENDED
        endInternal(status, endedBy = mockCallerId, reason = reason.wire)
    }

    override suspend fun setMuted(muted: Boolean) {
        transport?.setMuted(muted)
        _session.value = _session.value?.copy(muted = muted)
    }

    override suspend fun setSpeaker(on: Boolean) {
        transport?.setSpeaker(on)
        _session.value = _session.value?.copy(speakerOn = on)
        val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        am.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
        am.isSpeakerphoneOn = on
    }

    override suspend fun fetchIceServers(): IceServersResponse = IceServersClient.fallback()

    private fun updateStatus(status: CallStatus, answeredAt: Long? = null) {
        _session.value = _session.value?.copy(
            status = status,
            answeredAtMs = answeredAt ?: _session.value?.answeredAtMs,
        )
    }

    private suspend fun endInternal(
        status: CallStatus,
        appendLog: Boolean = true,
        endedBy: String? = null,
        reason: String? = null,
    ) {
        ringJob?.cancel()
        transport?.dispose()
        transport = null
        val cur = _session.value ?: return
        signaling.setStatus(cur.callId, status, endedBy = endedBy, reason = reason)
        val answered = cur.answeredAtMs
        val duration = if (answered != null) (System.currentTimeMillis() - answered) / 1000 else 0L
        if (appendLog) {
            _log.value = listOf(
                CallLogEntry(
                    callId = cur.callId,
                    tripId = cur.tripId,
                    peerName = cur.peerName,
                    callerRole = CallRole.CUSTOMER.wire,
                    status = status,
                    provider = cur.provider,
                    createdAtMs = startedAt,
                    answeredAtMs = answered,
                    endedAtMs = System.currentTimeMillis(),
                    durationSec = duration,
                    endedBy = endedBy,
                    reason = reason,
                    isOutgoing = cur.isOutgoing,
                ),
            ) + _log.value
        }
        _session.value = cur.copy(
            status = status,
            endedAtMs = System.currentTimeMillis(),
            endedBy = endedBy,
            reason = reason,
        )
        CallForegroundService.stop(context)
        CallNotifications.cancelIncoming(context)
        delay(400)
        if (_session.value?.callId == cur.callId && _session.value?.status?.isTerminal == true) {
            _session.value = null
        }
    }
}
