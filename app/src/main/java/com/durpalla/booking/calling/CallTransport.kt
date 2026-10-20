package com.durpalla.booking.calling

/**
 * Audio media transport selected by the server (`provider` on POST /calls, FCM, Firestore).
 * Ringing, call log, status, and Firestore doc stay outside this interface.
 */
interface CallTransport {
    val provider: CallProvider

    suspend fun prepare(asCaller: Boolean, callbacks: CallTransportCallbacks)

    /** WebRTC: createOffer. Agora: join channel with server token. */
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
)

object CallTransportFactory {
    fun create(
        provider: CallProvider,
        context: android.content.Context,
        api: com.durpalla.booking.data.net.ApiClient?,
        scope: kotlinx.coroutines.CoroutineScope,
        isCaller: Boolean,
    ): CallTransport = when (provider) {
        CallProvider.AGORA -> AgoraTransport(context.applicationContext, api, scope)
        CallProvider.WEBRTC -> WebRtcTransport(context.applicationContext, api, isCaller)
    }
}
