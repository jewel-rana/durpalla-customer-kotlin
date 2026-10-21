package com.durpalla.booking.calling

import android.content.Context
import com.durpalla.booking.data.net.ApiClient

/**
 * WebRTC audio transport. ICE from create/accept `iceServers` when non-null;
 * otherwise [IceServersClient] GET /calls/ice-servers / STUN.
 */
class WebRtcTransport(
    private val appContext: Context,
    private val api: ApiClient?,
    private val isCaller: Boolean,
    private val iceBootstrap: IceServersResponse? = null,
) : CallTransport {
    override val provider = CallProvider.WEBRTC
    private var engine: WebRtcEngine? = null

    override suspend fun prepare(asCaller: Boolean, callbacks: CallTransportCallbacks) {
        val e = WebRtcEngine(
            appContext = appContext,
            iceProvider = { IceServersClient.resolve(api, iceBootstrap) },
            isCaller = asCaller || isCaller,
        )
        engine = e
        e.init(
            onIceCandidate = callbacks.onIceCandidate,
            onConnected = callbacks.onConnected,
            onWeakConnection = callbacks.onWeakConnection,
            onRenegotiateOffer = callbacks.onRenegotiateOffer,
        )
    }

    override suspend fun startOutgoing(callId: String): SdpPayload? =
        engine?.createOffer()

    override suspend fun setRemote(sdp: SdpPayload) {
        engine?.setRemote(sdp)
    }

    override suspend fun createAnswer(): SdpPayload? = engine?.createAnswer()

    override fun addIce(candidate: IceCandidatePayload) {
        engine?.addIce(candidate)
    }

    override fun setMuted(muted: Boolean) {
        engine?.setMuted(muted)
    }

    override fun setSpeaker(on: Boolean) {
        engine?.setSpeaker(on)
    }

    override fun dispose() {
        engine?.dispose()
        engine = null
    }
}
