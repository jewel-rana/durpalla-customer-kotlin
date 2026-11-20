package com.durpalla.booking.calling

/**
 * Audio media transport selected by the server (`provider` on POST /calls + accept).
 * Media bootstrap (agora / iceServers) comes from those responses — no separate start token fetch.
 */
interface CallTransport {
    val provider: CallProvider

    suspend fun prepare(asCaller: Boolean, callbacks: CallTransportCallbacks)

    /** WebRTC: createOffer. Agora: join with create/accept `agora` credentials. */
    suspend fun startOutgoing(callId: String): SdpPayload?

    /** WebRTC: wait is done by repo; Agora: join on accept. Returns local answer SDP for WebRTC after [setRemote]. */
    suspend fun startIncoming(callId: String): SdpPayload? = null

    suspend fun setRemote(sdp: SdpPayload) {}
    suspend fun createAnswer(): SdpPayload? = null
    fun addIce(candidate: IceCandidatePayload) {}

    fun setMuted(muted: Boolean)
    fun setSpeaker(on: Boolean)
    fun dispose()
}

data class CallTransportCallbacks(
    val onIceCandidate: (IceCandidatePayload) -> Unit = {},
    val onConnected: () -> Unit = {},
    val onWeakConnection: (Boolean) -> Unit = {},
    /** Caller-only WebRTC ICE restart publishes a new offer. */
    val onRenegotiateOffer: (SdpPayload) -> Unit = {},
    /** Fatal media error (e.g. Agora renew 409) — end call locally; [message] is localized. */
    val onFatalError: (message: String) -> Unit = {},
)

object CallTransportFactory {
    fun create(
        provider: CallProvider,
        context: android.content.Context,
        api: com.durpalla.booking.data.net.ApiClient?,
        scope: kotlinx.coroutines.CoroutineScope,
        isCaller: Boolean,
        media: CallMediaBootstrap = CallMediaBootstrap(),
    ): CallTransport = when (provider) {
        CallProvider.AGORA -> AgoraTransport(context.applicationContext, api, scope, media.agora)
        CallProvider.WEBRTC -> WebRtcTransport(context.applicationContext, api, isCaller, media.iceServers)
    }
}
