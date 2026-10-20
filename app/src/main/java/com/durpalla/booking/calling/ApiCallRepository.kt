package com.durpalla.booking.calling

import android.content.Context
import com.durpalla.booking.R
import com.durpalla.booking.core.AppCtx
import com.durpalla.booking.core.jsonOf
import com.durpalla.booking.core.o
import com.durpalla.booking.core.s
import com.durpalla.booking.core.str
import com.durpalla.booking.data.net.ApiClient
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
import kotlinx.serialization.json.JsonObject

/**
 * Production calling: Firebase custom token → POST /calls → Firestore signaling → WebRTC.
 * Matches Durpalla/durpalla-driver docs/calling-contract.md.
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
    private var lastSeenOfferSdp: String? = null
    private var lastLocalOfferSdp: String? = null
    private var lastSeenAnswerSdp: String? = null
    private var asCallerFlag = false

    private fun userId(): String =
        session.user.value.str("id", "_id", default = session.token?.take(12) ?: "customer")

    override fun logForTrip(tripId: Int): Flow<List<CallLogEntry>> =
        callLog.map { it.filter { e -> e.tripId == tripId } }

    override fun ingestTripCallLog(tripId: Int, entries: List<JsonObject>) {
        val parsed = entries.mapNotNull { CallLogEntry.fromTripJson(it, tripId) }
        if (parsed.isEmpty()) return
        val others = _log.value.filter { it.tripId != tripId }
        // Server list is oldest-first; keep newest local appends for same callId
        val byId = linkedMapOf<String, CallLogEntry>()
        parsed.forEach { byId[it.callId] = it }
        _log.value.filter { it.tripId == tripId }.forEach { byId[it.callId] = it }
        _log.value = byId.values.toList() + others.filter { it.callId !in byId }
    }

    override fun canCallDriver(tripId: Int, bidAccepted: Boolean, tripActive: Boolean) =
        bidAccepted || tripActive

    override suspend fun startOutgoing(tripId: Int, driverName: String, callerName: String): CallSession {
        val callerName = callerName.ifBlank { AppCtx.str(R.string.you) }
        disposeMedia()
        FirebaseCallAuth.ensureSignedIn(api, userId())
        // POST /calls { tripId: string } → { callId } — backend creates Firestore doc.
        val res = api.postJson("calls", jsonOf("tripId" to tripId.toString()))
        CallApiErrors.requireCallSuccess(res)
        val callId = res.o("data").s("callId") ?: res.s("callId")
            ?: throw CallApiErrors.toException(res)
        val callerId = userId()
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
        lastLocalOfferSdp = offer.sdp
        lastSeenOfferSdp = offer.sdp
        signaling.setOffer(callId, offer)
        watchRemote(callId, asCaller = true)
        armMissTimer(callId, driverName, tripId, asCaller = true)
        return s
    }

    override suspend fun handleIncoming(callId: String, tripId: Int, callerName: String) {
        // Busy: already in a call → auto-decline (contract §3.7).
        val cur = _session.value
        if (cur != null && cur.status.isActive && cur.callId != callId) {
            runCatching { api.postJson("calls/$callId/decline", jsonOf()) }
            return
        }
        disposeMedia()
        FirebaseCallAuth.ensureSignedIn(api, userId())
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
        armMissTimer(callId, callerName, tripId, asCaller = false)
        watchRemote(callId, asCaller = false)
    }

    override suspend fun onRemoteEnded(callId: String, status: CallStatus) {
        val cur = _session.value ?: return
        if (cur.callId != callId) return
        if (!status.isTerminal) return
        finish(status, endedBy = cur.endedBy, reason = cur.reason)
    }

    override suspend fun accept() {
        val s = _session.value ?: return
        FirebaseCallAuth.ensureSignedIn(api, userId())
        val res = api.postJson("calls/${s.callId}/accept", jsonOf())
        runCatching { CallApiErrors.requireCallSuccess(res) }
        CallNotifications.cancelIncoming(context)
        awaitEngine(asCaller = false)
        val snap = signaling.observeCall(s.callId).first { it.offer != null }
        snap.offer?.let {
            lastSeenOfferSdp = it.sdp
            engine!!.setRemote(it)
        }
        val answer = engine!!.createAnswer()
        signaling.setAnswer(s.callId, answer)
        update(CallStatus.ACCEPTED, answeredAt = System.currentTimeMillis())
        iceJob = scope.launch {
            signaling.observeRemoteCandidates(s.callId, asCaller = false).collect { engine?.addIce(it) }
        }
    }

    override suspend fun decline(reason: String) {
        val s = _session.value ?: return
        runCatching { api.postJson("calls/${s.callId}/decline", jsonOf()) }
        // Server sets declined + reason; client may also patch ended fields.
        runCatching {
            signaling.setStatus(s.callId, CallStatus.DECLINED, endedBy = userId(), reason = "declined")
        }
        finish(CallStatus.DECLINED, endedBy = userId(), reason = "declined")
    }

    override suspend fun end(reason: CallEndReason) {
        val s = _session.value ?: return
        val wireReason = when {
            reason != CallEndReason.HANGUP -> reason.wire
            s.status == CallStatus.RINGING && s.isOutgoing -> CallEndReason.CANCELLED.wire
            else -> CallEndReason.HANGUP.wire
        }
        val status = if (wireReason == CallEndReason.MISSED.wire) CallStatus.MISSED else CallStatus.ENDED
        runCatching {
            api.postJson("calls/${s.callId}/end", jsonOf("reason" to wireReason))
        }
        runCatching {
            signaling.setStatus(s.callId, status, endedBy = userId(), reason = wireReason)
        }
        finish(status, endedBy = userId(), reason = wireReason)
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
        asCallerFlag = asCaller
        FirebaseCallAuth.ensureSignedIn(api, userId())
        val e = WebRtcEngine(
            appContext = context.applicationContext,
            iceProvider = { IceServersClient.fetch(api) },
            isCaller = asCaller,
        )
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
            onWeakConnection = { weak ->
                _session.value = _session.value?.copy(weakConnection = weak)
            },
            onRenegotiateOffer = { offer ->
                // Caller-only ICE restart publishes a new offer.
                if (!asCaller) return@init
                scope.launch {
                    val id = _session.value?.callId ?: return@launch
                    lastLocalOfferSdp = offer.sdp
                    lastSeenOfferSdp = offer.sdp
                    runCatching { signaling.setOffer(id, offer) }
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
                val answer = snap.answer
                if (asCaller && answer != null && answer.sdp != lastSeenAnswerSdp) {
                    lastSeenAnswerSdp = answer.sdp
                    runCatching { engine?.setRemote(answer) }
                    if (_session.value?.status != CallStatus.ACCEPTED) {
                        update(CallStatus.ACCEPTED, answeredAt = System.currentTimeMillis())
                    }
                }
                val offer = snap.offer
                // Callee answers every new offer (initial + ICE restart). Caller never answers.
                if (!asCaller && offer != null && offer.sdp != lastSeenOfferSdp) {
                    val hadOffer = lastSeenOfferSdp != null
                    lastSeenOfferSdp = offer.sdp
                    if (hadOffer || _session.value?.status == CallStatus.ACCEPTED) {
                        runCatching {
                            if (engine == null) awaitEngine(asCaller = false)
                            engine?.setRemote(offer)
                            val ans = engine?.createAnswer()
                            if (ans != null) signaling.setAnswer(callId, ans)
                        }
                    }
                } else if (offer != null) {
                    lastSeenOfferSdp = offer.sdp
                }
            }
        }
    }

    private fun armMissTimer(callId: String, peer: String, tripId: Int, asCaller: Boolean) {
        missJob?.cancel()
        missJob = scope.launch {
            delay(CallTimeouts.RING_MISS_MS)
            val cur = _session.value
            if (cur?.callId == callId && cur.status == CallStatus.RINGING) {
                // Caller sends end {reason:missed}; server timeout is authoritative.
                if (asCaller) {
                    runCatching {
                        api.postJson("calls/$callId/end", jsonOf("reason" to CallEndReason.MISSED.wire))
                    }
                }
                finish(CallStatus.MISSED, endedBy = "system", reason = "no_answer")
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
        if (cur.status.isTerminal && cur.callId == _session.value?.callId) {
            disposeMedia()
            return
        }
        val answered = cur.answeredAtMs
        val duration = answered?.let { (System.currentTimeMillis() - it) / 1000 } ?: 0L
        val entry = CallLogEntry(
            callId = cur.callId,
            tripId = cur.tripId,
            peerName = cur.peerName,
            callerRole = if (cur.isOutgoing) CallRole.CUSTOMER.wire else CallRole.DRIVER.wire,
            status = status,
            createdAtMs = startedAt,
            answeredAtMs = answered,
            endedAtMs = System.currentTimeMillis(),
            durationSec = duration,
            endedBy = endedBy,
            reason = reason,
            isOutgoing = cur.isOutgoing,
        )
        _log.value = listOf(entry) + _log.value.filter { it.callId != entry.callId }
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
        lastSeenOfferSdp = null
        lastLocalOfferSdp = null
        lastSeenAnswerSdp = null
        asCallerFlag = false
    }
}
