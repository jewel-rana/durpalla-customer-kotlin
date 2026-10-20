package com.durpalla.booking.calling

import android.content.Context
import com.durpalla.booking.core.*
import com.durpalla.booking.data.net.ApiClient
import com.durpalla.booking.data.net.requireSuccess
import com.durpalla.booking.data.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Production: POST /calls → Firestore signaling → WebRTC audio.
 * TODO(calling): Durpalla/durpalla-driver docs/calling-contract.md
 */
class ApiCallRepository(
    private val context: Context,
    private val api: ApiClient,
    private val session: SessionStore,
    private val signaling: SignalingRepository,
    private val scope: CoroutineScope,
) : CallRepository {

    private val _session = MutableStateFlow<CallSession?>(null)
    override val session: StateFlow<CallSession?> = _session.asStateFlow()

    private val _log = MutableStateFlow<List<CallLogEntry>>(emptyList())
    override val callLog: StateFlow<List<CallLogEntry>> = _log.asStateFlow()

    private var engine: WebRtcEngine? = null
    private var iceJob: Job? = null
    private var watchJob: Job? = null
    private var missJob: Job? = null
    private var startedAt = 0L

    private fun userId(): String =
        session.user.value.str("id", "_id", default = session.token?.take(12) ?: "customer")

    override fun logForTrip(tripId: Int): Flow<List<CallLogEntry>> =
        callLog.map { it.filter { e -> e.tripId == tripId } }

    override fun canCallDriver(tripId: Int, bidAccepted: Boolean, tripActive: Boolean) =
        bidAccepted || tripActive

    override suspend fun startOutgoing(tripId: Int, driverName: String, callerName: String): CallSession {
        disposeMedia()
        // POST /calls {tripId} → {callId}
        val res = api.postJson("calls", jsonOf("tripId" to tripId)).requireSuccess("Could not start call")
        val callId = res.o("data").s("callId") ?: res.s("callId")
            ?: throw IllegalStateException("callId missing")
        val callerId = userId()
        signaling.createCallDoc(callId, tripId, callerId, CallRole.CUSTOMER, calleeRole = CallRole.DRIVER)
        startedAt = System.currentTimeMillis()
        val s = CallSession(
            callId = callId,
            tripId = tripId,
            peerName = driverName,
            isOutgoing = true,
            status = CallStatus.RINGING,
            callerId = callerId,
        )
        _session.value = s
        CallForegroundService.start(context, driverName, outgoing = true)
        awaitEngine(asCaller = true)
        val offer = engine!!.createOffer()
        signaling.setOffer(callId, offer)
        watchRemote(callId, asCaller = true)
        armMissTimer(callId, driverName, tripId)
        return s
    }

    override suspend fun handleIncoming(callId: String, tripId: Int, callerName: String) {
        disposeMedia()
        startedAt = System.currentTimeMillis()
        _session.value = CallSession(
            callId = callId,
            tripId = tripId,
            peerName = callerName,
            isOutgoing = false,
            status = CallStatus.RINGING,
            calleeId = userId(),
        )
        CallNotifications.showIncoming(context, callId, tripId, callerName)
        CallForegroundService.start(context, callerName, outgoing = false)
        armMissTimer(callId, callerName, tripId)
        watchRemote(callId, asCaller = false)
    }

    override suspend fun accept() {
        val s = _session.value ?: return
        // POST /calls/{id}/accept
        runCatching { api.postJson("calls/${s.callId}/accept", jsonOf("ok" to true)) }
        CallNotifications.cancelIncoming(context)
        awaitEngine(asCaller = false)
        val snap = signaling.observeCall(s.callId).first { it.offer != null }
        snap.offer?.let { engine!!.setRemote(it) }
        val answer = engine!!.createAnswer()
        signaling.setAnswer(s.callId, answer)
        update(CallStatus.ACCEPTED, answeredAt = System.currentTimeMillis())
        iceJob = scope.launch {
            signaling.observeRemoteCandidates(s.callId, asCaller = false).collect { engine?.addIce(it) }
        }
    }

    override suspend fun decline(reason: String) {
        val s = _session.value ?: return
        // POST /calls/{id}/decline {reason}
        runCatching { api.postJson("calls/${s.callId}/decline", jsonOf("reason" to reason)) }
        signaling.setStatus(s.callId, CallStatus.DECLINED, endedBy = userId(), reason = reason)
        finish(CallStatus.DECLINED, endedBy = userId(), reason = reason)
    }

    override suspend fun end() {
        val s = _session.value ?: return
        // POST /calls/{id}/end
        runCatching { api.postJson("calls/${s.callId}/end", jsonOf("ok" to true)) }
        signaling.setStatus(s.callId, CallStatus.ENDED, endedBy = userId())
        finish(CallStatus.ENDED, endedBy = userId())
    }

    override suspend fun setMuted(muted: Boolean) {
        engine?.setMuted(muted)
        _session.value = _session.value?.copy(muted = muted)
    }

    override suspend fun setSpeaker(on: Boolean) {
        engine?.setSpeaker(on)
        _session.value = _session.value?.copy(speakerOn = on)
    }

    override suspend fun fetchIceServers(): IceServersResponse = IceServersClient.fetch(api)

    private suspend fun awaitEngine(asCaller: Boolean) {
        val e = WebRtcEngine(context.applicationContext) { IceServersClient.fetch(api) }
        engine = e
        e.init(
            onIceCandidate = { cand ->
                scope.launch {
                    val id = _session.value?.callId ?: return@launch
                    if (asCaller) signaling.addCallerCandidate(id, cand)
                    else signaling.addCalleeCandidate(id, cand)
                }
            },
            onConnected = {
                scope.launch {
                    if (_session.value?.status != CallStatus.ACCEPTED) {
                        update(CallStatus.ACCEPTED, answeredAt = System.currentTimeMillis())
                    }
                }
            },
        )
        if (asCaller) {
            iceJob = scope.launch {
                val id = _session.value?.callId ?: return@launch
                signaling.observeRemoteCandidates(id, asCaller = true).collect { e.addIce(it) }
            }
        }
    }

    private fun watchRemote(callId: String, asCaller: Boolean) {
        watchJob?.cancel()
        watchJob = scope.launch {
            signaling.observeCall(callId).collect { snap ->
                when (snap.status) {
                    CallStatus.DECLINED, CallStatus.ENDED, CallStatus.MISSED ->
                        finish(snap.status, endedBy = snap.endedBy, reason = snap.reason)
                    CallStatus.ACCEPTED -> if (_session.value?.status != CallStatus.ACCEPTED) {
                        update(CallStatus.ACCEPTED, answeredAt = System.currentTimeMillis())
                    }
                    else -> {}
                }
                if (asCaller && snap.answer != null && _session.value?.status != CallStatus.ACCEPTED) {
                    runCatching { engine?.setRemote(snap.answer) }
                }
            }
        }
    }

    private fun armMissTimer(callId: String, peer: String, tripId: Int) {
        missJob?.cancel()
        missJob = scope.launch {
            delay(CallTimeouts.RING_MISS_MS)
            val cur = _session.value
            if (cur?.callId == callId && cur.status == CallStatus.RINGING) {
                signaling.setStatus(callId, CallStatus.MISSED, endedBy = "system", reason = "timeout")
                finish(CallStatus.MISSED, endedBy = "system", reason = "timeout")
                CallNotifications.showMissed(context, peer, tripId)
            }
        }
    }

    private fun update(status: CallStatus, answeredAt: Long? = null) {
        _session.value = _session.value?.copy(
            status = status,
            answeredAtMs = answeredAt ?: _session.value?.answeredAtMs,
        )
    }

    private suspend fun finish(status: CallStatus, endedBy: String? = null, reason: String? = null) {
        missJob?.cancel()
        watchJob?.cancel()
        iceJob?.cancel()
        val cur = _session.value ?: return
        val duration = cur.answeredAtMs?.let { (System.currentTimeMillis() - it) / 1000 } ?: 0L
        _log.value = listOf(
            CallLogEntry(cur.callId, cur.tripId, cur.peerName, status, startedAt, duration, cur.isOutgoing),
        ) + _log.value
        _session.value = cur.copy(
            status = status,
            endedAtMs = System.currentTimeMillis(),
            endedBy = endedBy,
            reason = reason,
        )
        disposeMedia()
        CallForegroundService.stop(context)
        CallNotifications.cancelIncoming(context)
        delay(400)
        if (_session.value?.callId == cur.callId) _session.value = null
    }

    private fun disposeMedia() {
        engine?.dispose()
        engine = null
    }
}
