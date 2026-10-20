package com.durpalla.booking.calling

import android.content.Context
import android.media.AudioManager
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Audio-only WebRTC. ICE from GET /calls/ice-servers (fallback STUN).
 */
class WebRtcEngine(
    private val appContext: Context,
    private val iceProvider: suspend () -> IceServersResponse,
) {
    private var factory: PeerConnectionFactory? = null
    private var pc: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudio: AudioTrack? = null
    private var onIce: ((IceCandidatePayload) -> Unit)? = null
    private var onConnected: (() -> Unit)? = null

    suspend fun init(onIceCandidate: (IceCandidatePayload) -> Unit, onConnected: () -> Unit) {
        onIce = onIceCandidate
        this.onConnected = onConnected
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions(),
        )
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        audioSource = factory!!.createAudioSource(MediaConstraints())
        localAudio = factory!!.createAudioTrack("AUDIO", audioSource)
        localAudio?.setEnabled(true)
        val ice = iceProvider()
        pc = factory!!.createPeerConnection(rtcConfig(ice), observer)
        pc?.addTrack(localAudio)
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
            ) onConnected?.invoke()
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

    suspend fun createOffer(): SdpPayload = suspendCoroutine { cont ->
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        pc?.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) {
                    cont.resumeWithException(IllegalStateException("empty offer")); return
                }
                pc?.setLocalDescription(object : SdpObserverAdapter() {
                    override fun onSetSuccess() {
                        cont.resume(SdpPayload(desc.type.canonicalForm(), desc.description))
                    }
                    override fun onSetFailure(error: String?) {
                        cont.resumeWithException(IllegalStateException(error ?: "setLocal failed"))
                    }
                }, desc)
            }
            override fun onCreateFailure(error: String?) {
                cont.resumeWithException(IllegalStateException(error ?: "createOffer failed"))
            }
        }, constraints) ?: cont.resumeWithException(IllegalStateException("no peer"))
    }

    suspend fun createAnswer(): SdpPayload = suspendCoroutine { cont ->
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        pc?.createAnswer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) {
                    cont.resumeWithException(IllegalStateException("empty answer")); return
                }
                pc?.setLocalDescription(object : SdpObserverAdapter() {
                    override fun onSetSuccess() {
                        cont.resume(SdpPayload(desc.type.canonicalForm(), desc.description))
                    }
                    override fun onSetFailure(error: String?) {
                        cont.resumeWithException(IllegalStateException(error ?: "setLocal failed"))
                    }
                }, desc)
            }
            override fun onCreateFailure(error: String?) {
                cont.resumeWithException(IllegalStateException(error ?: "createAnswer failed"))
            }
        }, constraints) ?: cont.resumeWithException(IllegalStateException("no peer"))
    }

    suspend fun setRemote(sdp: SdpPayload) = suspendCoroutine { cont ->
        val type = if (sdp.type.equals("answer", true)) SessionDescription.Type.ANSWER
        else SessionDescription.Type.OFFER
        pc?.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() { cont.resume(Unit) }
            override fun onSetFailure(error: String?) {
                cont.resumeWithException(IllegalStateException(error ?: "setRemote failed"))
            }
        }, SessionDescription(type, sdp.sdp))
            ?: cont.resumeWithException(IllegalStateException("no peer"))
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

    fun dispose() {
        runCatching {
            localAudio?.dispose(); audioSource?.dispose()
            pc?.close(); pc?.dispose(); factory?.dispose()
        }
        localAudio = null; audioSource = null; pc = null; factory = null
    }

    private open class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }
}
