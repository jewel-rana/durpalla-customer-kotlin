package com.durpalla.booking.calling

import android.content.Context
import com.durpalla.booking.R
import com.durpalla.booking.core.AppCtx
import com.durpalla.booking.core.jsonOf
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
 * Production calling: Firebase custom token → POST /calls (media bootstrap in response) →
 * Firestore status + [CallTransport] (WebRTC or Agora per `provider`).
 */
class ApiCallRepository(
    private val context: Context,
    private val api: ApiClient,
    private val sessionStore: SessionStore,
    private val signaling: SignalingRepository,
    private val scope: CoroutineScope,
) : CallRepository {

    private val _session = MutableStateFlow<CallSession?>(null)
    override val session: StateFlow<CallSession?> = _session.asStateFlow()

    private val _log = MutableStateFlow<List<CallLogEntry>>(emptyList())
    override val callLog: StateFlow<List<CallLogEntry>> = _log.asStateFlow()

    private var transport: CallTransport? = null
    private var iceJob: Job? = null
    private var watchJob: Job? = null
    private var missJob: Job? = null
    private var startedAt = 0L
    private var lastSeenOfferSdp: String? = null
    private var lastLocalOfferSdp: String? = null
    private var lastSeenAnswerSdp: String? = null
    private var asCallerFlag = false

    private fun userId(): String =
        sessionStore.user.value.str("id", "_id", default = sessionStore.token?.take(12) ?: "customer")

    override fun logForTrip(tripId: Int): Flow<List<CallLogEntry>> =
        callLog.map { it.filter { e -> e.tripId == tripId } }

    override fun ingestTripCallLog(tripId: Int, entries: List<JsonObject>) {
        val parsed = entries.mapNotNull { CallLogEntry.fromTripJson(it, tripId) }
        if (parsed.isEmpty()) return
        val others = _log.value.filter { it.tripId != tripId }
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
        val res = api.postJson("calls", jsonOf("tripId" to tripId.toString()))
        CallApiErrors.requireCallSuccess(res)
        val media = CallMediaPayload.parse(res)
        val callId = media.callId
        val provider = media.provider
        val callerId = userId()
        startedAt = System.currentTimeMillis()
        val s = CallSession(
            callId = callId,
            tripId = tripId,
            peerName = driverName,
            isOutgoing = true,
            status = CallStatus.RINGING,
            provider = provider,
            callerId = callerId,
        )
        _session.value = s
        CallForegroundService.start(context, driverName, outgoing = true)
        awaitTransport(provider, asCaller = true, bootstrap = CallMediaBootstrap.from(media))
        when (provider) {
            CallProvider.WEBRTC -> {
                val offer = transport!!.startOutgoing(callId)
                    ?: throw IllegalStateException("missing offer")
                lastLocalOfferSdp = offer.sdp
                lastSeenOfferSdp = offer.sdp
                signaling.setOffer(callId, offer)
            }
            // Caller joins Agora with create-response credentials (no separate token fetch).
            CallProvider.AGORA -> transport!!.startOutgoing(callId)
        }
        watchRemote(callId, asCaller = true, provider = provider)
        armMissTimer(callId, driverName, tripId, asCaller = true)
        return s
    }

    override suspend fun handleIncoming(
        callId: String,
        tripId: Int,
        callerName: String,
        provider: CallProvider,
    ) {
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
            provider = provider,
            calleeId = userId(),
        )
        CallNotifications.showIncoming(context, callId, tripId, callerName)
        CallForegroundService.start(context, callerName, outgoing = false)
        armMissTimer(callId, callerName, tripId, asCaller = false)
        watchRemote(callId, asCaller = false, provider = provider)
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
        val media = runCatching { CallMediaPayload.parse(res, fallbackCallId = s.callId) }.getOrNull()
        if (media != null) {
            if (_session.value?.provider != media.provider) {
                _session.value = _session.value?.copy(provider = media.provider)
            }
        } else {
            applyProviderFromResponse(res)
        }
        CallNotifications.cancelIncoming(context)
        val provider = media?.provider ?: _session.value?.provider ?: s.provider
        val bootstrap = media?.let { CallMediaBootstrap.from(it) } ?: CallMediaBootstrap()
        awaitTransport(provider, asCaller = false, bootstrap = bootstrap)
        when (provider) {
            CallProvider.WEBRTC -> {
                val snap = signaling.observeCall(s.callId).first { it.offer != null }
                snap.offer?.let {
                    lastSeenOfferSdp = it.sdp
                    transport!!.setRemote(it)
                }
                val answer = transport!!.createAnswer()
                if (answer != null) signaling.setAnswer(s.callId, answer)
                iceJob = scope.launch {
                    signaling.observeRemoteCandidates(s.callId, asCaller = false)
                        .collect { transport?.addIce(it) }
                }
            }
            // Callee joins Agora with accept-response credentials.
            CallProvider.AGORA -> transport!!.startIncoming(s.callId)
        }
        update(CallStatus.ACCEPTED, answeredAt = System.currentTimeMillis())
    }

    override suspend fun decline(reason: String) {
        val s = _session.value ?: return
        val res = runCatching { api.postJson("calls/${s.callId}/decline", jsonOf()) }.getOrNull()
        res?.let { applyProviderFromResponse(it) }
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
        val res = runCatching {
            api.postJson("calls/${s.callId}/end", jsonOf("reason" to wireReason))
        }.getOrNull()
        res?.let { applyProviderFromResponse(it) }
        runCatching {
            signaling.setStatus(s.callId, status, endedBy = userId(), reason = wireReason)
        }
        finish(status, endedBy = userId(), reason = wireReason)
    }

    /** accept/decline/end (and POST /calls) may echo `provider` — always prefer the wire value. */
    private fun applyProviderFromResponse(r: JsonObject) {
        val p = CallApiErrors.providerOf(r) ?: return
        if (_session.value?.provider != p) {
            _session.value = _session.value?.copy(provider = p)
        }
    }

    override suspend fun setMuted(muted: Boolean) {
        transport?.setMuted(muted)
        _session.value = _session.value?.copy(muted = muted)
    }

    override suspend fun setSpeaker(on: Boolean) {
        transport?.setSpeaker(on)
        _session.value = _session.value?.copy(speakerOn = on)
    }

    override suspend fun fetchIceServers(): IceServersResponse = IceServersClient.fetch(api)

    private suspend fun awaitTransport(
        provider: CallProvider,
        asCaller: Boolean,
        bootstrap: CallMediaBootstrap = CallMediaBootstrap(),
    ) {
        asCallerFlag = asCaller
        FirebaseCallAuth.ensureSignedIn(api, userId())
        val t = CallTransportFactory.create(provider, context, api, scope, asCaller, bootstrap)
        transport = t
        t.prepare(
            asCaller = asCaller,
            callbacks = CallTransportCallbacks(
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
                            // Agora: do not mark accepted until Firestore/accept; only fill if already accepted path.
                            if (provider == CallProvider.WEBRTC) {
                                update(CallStatus.ACCEPTED, answeredAt = System.currentTimeMillis())
                            }
                        }
                    }
                },
                onWeakConnection = { weak ->
                    _session.value = _session.value?.copy(weakConnection = weak)
                },
                onRenegotiateOffer = { offer ->
                    if (!asCaller || provider != CallProvider.WEBRTC) return@CallTransportCallbacks
                    scope.launch {
                        val id = _session.value?.callId ?: return@launch
                        lastLocalOfferSdp = offer.sdp
                        lastSeenOfferSdp = offer.sdp
                        runCatching { signaling.setOffer(id, offer) }
                    }
                },
                onFatalError = { message ->
                    scope.launch {
                        val cur = _session.value ?: return@launch
                        if (cur.status.isTerminal) return@launch
                        // Local end only (CALL_ALREADY_ENDED / provider mismatch — do not POST /end).
                        finish(
                            CallStatus.ENDED,
                            endedBy = userId(),
                            reason = CallEndReason.FAILED.wire,
                            userMessage = message,
                        )
                    }
                },
            ),
        )
        if (asCaller && provider == CallProvider.WEBRTC) {
            iceJob = scope.launch {
                val id = _session.value?.callId ?: return@launch
                signaling.observeRemoteCandidates(id, asCaller = true).collect { t.addIce(it) }
            }
        }
    }

    private fun watchRemote(callId: String, asCaller: Boolean, provider: CallProvider) {
        watchJob?.cancel()
        watchJob = scope.launch {
            signaling.observeCall(callId).collect { snap ->
                val resolvedProvider = snap.provider
                if (_session.value?.provider != resolvedProvider) {
                    _session.value = _session.value?.copy(provider = resolvedProvider)
                }
                when (snap.status) {
                    CallStatus.DECLINED, CallStatus.ENDED, CallStatus.MISSED ->
                        finish(snap.status, endedBy = snap.endedBy, reason = snap.reason)
                    CallStatus.ACCEPTED -> if (_session.value?.status != CallStatus.ACCEPTED) {
                        update(CallStatus.ACCEPTED, answeredAt = System.currentTimeMillis())
                    }
                    else -> {}
                }
                if (provider != CallProvider.WEBRTC &&
                    resolvedProvider != CallProvider.WEBRTC &&
                    _session.value?.provider != CallProvider.WEBRTC
                ) {
                    // Agora: status-only; no SDP/ICE.
                    return@collect
                }
                val useWebrtc = (_session.value?.provider ?: provider) == CallProvider.WEBRTC
                if (!useWebrtc) return@collect

                val answer = snap.answer
                if (asCaller && answer != null && answer.sdp != lastSeenAnswerSdp) {
                    lastSeenAnswerSdp = answer.sdp
                    runCatching { transport?.setRemote(answer) }
                    if (_session.value?.status != CallStatus.ACCEPTED) {
                        update(CallStatus.ACCEPTED, answeredAt = System.currentTimeMillis())
                    }
                }
                val offer = snap.offer
                if (!asCaller && offer != null && offer.sdp != lastSeenOfferSdp) {
                    val hadOffer = lastSeenOfferSdp != null
                    lastSeenOfferSdp = offer.sdp
                    if (hadOffer || _session.value?.status == CallStatus.ACCEPTED) {
                        runCatching {
                            if (transport == null) awaitTransport(CallProvider.WEBRTC, asCaller = false)
                            transport?.setRemote(offer)
                            val ans = transport?.createAnswer()
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

    private suspend fun finish(
        status: CallStatus,
        endedBy: String? = null,
        reason: String? = null,
        userMessage: String? = null,
    ) {
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
            provider = cur.provider,
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
            userMessage = userMessage ?: cur.userMessage,
        )
        disposeMedia()
        CallForegroundService.stop(context)
        CallNotifications.cancelIncoming(context)
        delay(400)
        if (_session.value?.callId == cur.callId) _session.value = null
    }

    private fun disposeMedia() {
        transport?.dispose()
        transport = null
        lastSeenOfferSdp = null
        lastLocalOfferSdp = null
        lastSeenAnswerSdp = null
        asCallerFlag = false
    }
}
