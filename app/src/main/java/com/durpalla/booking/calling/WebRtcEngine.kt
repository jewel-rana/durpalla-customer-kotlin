package com.durpalla.booking.calling

import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RTCStatsReport
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.max

/**
 * Audio-only WebRTC tuned for weak networks (driver parity):
 * Opus mono, fmtp FEC/DTX, adaptive sender bitrate 24→12→8 kbps, ICE restart on network change.
 * ICE from GET /calls/ice-servers (fallback STUN).
 */
class WebRtcEngine(
    private val appContext: Context,
    private val iceProvider: suspend () -> IceServersResponse,
) {
    private var factory: PeerConnectionFactory? = null
    private var audioDeviceModule: JavaAudioDeviceModule? = null
    private var pc: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudio: AudioTrack? = null
    private var audioSender: RtpSender? = null
    private var onIce: ((IceCandidatePayload) -> Unit)? = null
    private var onConnected: (() -> Unit)? = null
    private var onWeakConnection: ((Boolean) -> Unit)? = null
    private var onRenegotiateOffer: ((SdpPayload) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var statsRunnable: Runnable? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastNetwork: Network? = null
    private var lastIceRestartAt = 0L
    private var prevPacketsLost = 0L
    private var prevPacketsRecv = 0L
    private var targetBitrateBps = BITRATE_HIGH
    private var weakHint = false
    private var audioModeBefore: Int = AudioManager.MODE_NORMAL
    private var speakerBefore = false

    suspend fun init(
        onIceCandidate: (IceCandidatePayload) -> Unit,
        onConnected: () -> Unit,
        onWeakConnection: (Boolean) -> Unit = {},
        onRenegotiateOffer: (SdpPayload) -> Unit = {},
    ) {
        onIce = onIceCandidate
        this.onConnected = onConnected
        this.onWeakConnection = onWeakConnection
        this.onRenegotiateOffer = onRenegotiateOffer
        enterCommunicationMode()
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions(),
        )
        val adm = JavaAudioDeviceModule.builder(appContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        audioDeviceModule = adm
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .createPeerConnectionFactory()
        val audioConstraints = MediaConstraints().apply {
            // Keep software AEC / NS / AGC on (in addition to hardware where available).
            optional.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            optional.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            optional.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            optional.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }
        audioSource = factory!!.createAudioSource(audioConstraints)
        localAudio = factory!!.createAudioTrack("AUDIO", audioSource)
        localAudio?.setEnabled(true)
        val ice = iceProvider()
        pc = factory!!.createPeerConnection(rtcConfig(ice), observer)
        audioSender = pc?.addTrack(localAudio)
        applySenderBitrate(BITRATE_HIGH)
        registerNetworkCallback()
    }

    private fun enterCommunicationMode() {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioModeBefore = am.mode
        speakerBefore = am.isSpeakerphoneOn
        am.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    private fun restoreAudioMode() {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.isSpeakerphoneOn = speakerBefore
        am.mode = audioModeBefore
    }

    private fun rtcConfig(ice: IceServersResponse): PeerConnection.RTCConfiguration {
        val servers = ice.servers.flatMap { entry ->
            entry.urls.map { url ->
                val b = PeerConnection.IceServer.builder(url)
                if (!entry.username.isNullOrBlank()) {
                    b.setUsername(entry.username).setPassword(entry.credential.orEmpty())
                }
                b.createIceServer()
            }
        }.ifEmpty {
            listOf(PeerConnection.IceServer.builder(CallTimeouts.FALLBACK_STUN).createIceServer())
        }
        return PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
    }

    private val observer = object : PeerConnection.Observer {
        override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
            if (newState == PeerConnection.IceConnectionState.CONNECTED ||
                newState == PeerConnection.IceConnectionState.COMPLETED
            ) {
                onConnected?.invoke()
                startStatsPolling()
            }
        }
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate ?: return
            onIce?.invoke(
                IceCandidatePayload(
                    candidate = candidate.sdp,
                    sdpMid = candidate.sdpMid,
                    sdpMLineIndex = candidate.sdpMLineIndex,
                ),
            )
        }
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onAddStream(stream: MediaStream?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onDataChannel(dc: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {}
    }

    suspend fun createOffer(): SdpPayload = createLocalSdp(offer = true)

    suspend fun createAnswer(): SdpPayload = createLocalSdp(offer = false)

    private suspend fun createLocalSdp(offer: Boolean): SdpPayload = suspendCoroutine { cont ->
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        val observer = object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) {
                    cont.resumeWithException(IllegalStateException("empty sdp")); return
                }
                val munged = SessionDescription(desc.type, OpusSdpMunger.munge(desc.description))
                pc?.setLocalDescription(object : SdpObserverAdapter() {
                    override fun onSetSuccess() {
                        applySenderBitrate(targetBitrateBps)
                        cont.resume(SdpPayload(munged.type.canonicalForm(), munged.description))
                    }
                    override fun onSetFailure(error: String?) {
                        cont.resumeWithException(IllegalStateException(error ?: "setLocal failed"))
                    }
                }, munged)
            }
            override fun onCreateFailure(error: String?) {
                cont.resumeWithException(IllegalStateException(error ?: if (offer) "createOffer failed" else "createAnswer failed"))
            }
        }
        if (offer) {
            pc?.createOffer(observer, constraints)
                ?: cont.resumeWithException(IllegalStateException("no peer"))
        } else {
            pc?.createAnswer(observer, constraints)
                ?: cont.resumeWithException(IllegalStateException("no peer"))
        }
    }

    suspend fun setRemote(sdp: SdpPayload) = suspendCoroutine { cont ->
        val type = if (sdp.type.equals("answer", true)) SessionDescription.Type.ANSWER
        else SessionDescription.Type.OFFER
        val munged = SessionDescription(type, OpusSdpMunger.munge(sdp.sdp))
        pc?.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                applySenderBitrate(targetBitrateBps)
                cont.resume(Unit)
            }
            override fun onSetFailure(error: String?) {
                cont.resumeWithException(IllegalStateException(error ?: "setRemote failed"))
            }
        }, munged) ?: cont.resumeWithException(IllegalStateException("no peer"))
    }

    fun addIce(candidate: IceCandidatePayload) {
        pc?.addIceCandidate(
            IceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.candidate),
        )
    }

    fun setMuted(muted: Boolean) { localAudio?.setEnabled(!muted) }

    fun setSpeaker(on: Boolean) {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        am.isSpeakerphoneOn = on
    }

    /** ConnectivityManager-driven ICE restart (debounced). */
    fun restartIce() {
        val now = System.currentTimeMillis()
        if (now - lastIceRestartAt < ICE_RESTART_MIN_MS) return
        lastIceRestartAt = now
        val peer = pc ?: return
        peer.restartIce()
        // Renegotiate with ice-restart so both sides refresh ufrag/pwd (driver parity).
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
        }
        peer.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                val munged = SessionDescription(desc.type, OpusSdpMunger.munge(desc.description))
                peer.setLocalDescription(object : SdpObserverAdapter() {
                    override fun onSetSuccess() {
                        onRenegotiateOffer?.invoke(
                            SdpPayload(munged.type.canonicalForm(), munged.description),
                        )
                    }
                }, munged)
            }
        }, constraints)
    }

    private fun applySenderBitrate(bps: Int) {
        targetBitrateBps = bps
        val sender = audioSender ?: pc?.senders?.firstOrNull { it.track()?.kind() == "audio" } ?: return
        audioSender = sender
        val params = sender.parameters
        var changed = false
        for (enc in params.encodings) {
            if (enc.maxBitrateBps != bps) {
                enc.maxBitrateBps = bps
                changed = true
            }
        }
        if (changed) sender.parameters = params
        val weak = bps < BITRATE_HIGH
        if (weak != weakHint) {
            weakHint = weak
            onWeakConnection?.invoke(weak)
        }
    }

    private fun startStatsPolling() {
        stopStatsPolling()
        prevPacketsLost = 0L
        prevPacketsRecv = 0L
        val r = object : Runnable {
            override fun run() {
                val peer = pc
                if (peer == null) return
                peer.getStats { report -> onStats(report) }
                mainHandler.postDelayed(this, STATS_INTERVAL_MS)
            }
        }
        statsRunnable = r
        mainHandler.postDelayed(r, STATS_INTERVAL_MS)
    }

    private fun stopStatsPolling() {
        statsRunnable?.let { mainHandler.removeCallbacks(it) }
        statsRunnable = null
    }

    private fun onStats(report: RTCStatsReport) {
        var packetsLost = 0L
        var packetsRecv = 0L
        var rttMs: Double? = null
        for (stats in report.statsMap.values) {
            when (stats.type) {
                "inbound-rtp" -> {
                    val kind = stats.members["kind"] as? String
                        ?: stats.members["mediaType"] as? String
                    if (kind == null || kind == "audio") {
                        packetsLost += (stats.members["packetsLost"] as? Number)?.toLong() ?: 0L
                        packetsRecv += (stats.members["packetsReceived"] as? Number)?.toLong() ?: 0L
                    }
                }
                "candidate-pair" -> {
                    val selected = stats.members["selected"] as? Boolean
                        ?: ((stats.members["nominated"] as? Boolean) == true)
                    if (selected) {
                        val rttSec = (stats.members["currentRoundTripTime"] as? Number)?.toDouble()
                        if (rttSec != null) rttMs = rttSec * 1000.0
                    }
                }
            }
        }
        val dLost = max(0L, packetsLost - prevPacketsLost)
        val dRecv = max(0L, packetsRecv - prevPacketsRecv)
        prevPacketsLost = packetsLost
        prevPacketsRecv = packetsRecv
        val total = dLost + dRecv
        val lossPct = if (total > 0) (dLost.toDouble() / total.toDouble()) * 100.0 else 0.0
        val bad = lossPct > LOSS_THRESHOLD_PCT || (rttMs != null && rttMs > RTT_THRESHOLD_MS)
        val next = when {
            bad && targetBitrateBps > BITRATE_MID -> BITRATE_MID
            bad && targetBitrateBps > BITRATE_LOW -> BITRATE_LOW
            !bad && targetBitrateBps < BITRATE_MID -> BITRATE_MID
            !bad && targetBitrateBps < BITRATE_HIGH -> BITRATE_HIGH
            else -> targetBitrateBps
        }
        if (next != targetBitrateBps) applySenderBitrate(next)
    }

    private fun registerNetworkCallback() {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val prev = lastNetwork
                lastNetwork = network
                if (prev != null && prev != network) restartIce()
            }
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val prev = lastNetwork
                lastNetwork = network
                // Restart only when the default network instance changes (Wi‑Fi ↔ cellular).
                if (prev != null && prev != network) restartIce()
            }
            override fun onLost(network: Network) {
                if (lastNetwork == network) lastNetwork = null
            }
        }
        networkCallback = cb
        cm.registerDefaultNetworkCallback(cb)
    }

    private fun unregisterNetworkCallback() {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        networkCallback = null
        lastNetwork = null
    }

    fun dispose() {
        stopStatsPolling()
        unregisterNetworkCallback()
        runCatching {
            localAudio?.dispose(); audioSource?.dispose()
            pc?.close(); pc?.dispose(); factory?.dispose()
            audioDeviceModule?.release()
        }
        restoreAudioMode()
        localAudio = null; audioSource = null; pc = null; factory = null
        audioDeviceModule = null; audioSender = null
        onWeakConnection?.invoke(false)
        weakHint = false
        targetBitrateBps = BITRATE_HIGH
    }

    private open class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }

    companion object {
        const val BITRATE_HIGH = 24_000
        const val BITRATE_MID = 12_000
        const val BITRATE_LOW = 8_000
        const val STATS_INTERVAL_MS = 2_000L
        const val LOSS_THRESHOLD_PCT = 5.0
        const val RTT_THRESHOLD_MS = 400.0
        const val ICE_RESTART_MIN_MS = 3_000L
    }
}
