package com.durpalla.booking.calling

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Offline media stub used by [MockCallRepository]. */
class MockCallTransport(
    private val scope: CoroutineScope,
    private val connectAfterMs: Long = 2_200L,
) : CallTransport {
    override val provider = CallProvider.WEBRTC
    private var callbacks: CallTransportCallbacks = CallTransportCallbacks()
    private var job: Job? = null
    private var muted = false

    override suspend fun prepare(asCaller: Boolean, callbacks: CallTransportCallbacks) {
        this.callbacks = callbacks
    }

    override suspend fun startOutgoing(callId: String): SdpPayload? {
        job?.cancel()
        job = scope.launch {
            delay(connectAfterMs)
            callbacks.onConnected()
        }
        return SdpPayload("offer", "mock-sdp-$callId")
    }

    override suspend fun startIncoming(callId: String): SdpPayload? {
        callbacks.onConnected()
        return null
    }

    override suspend fun setRemote(sdp: SdpPayload) {}
    override suspend fun createAnswer(): SdpPayload? = SdpPayload("answer", "mock-answer")
    override fun addIce(candidate: IceCandidatePayload) {}
    override fun setMuted(muted: Boolean) {
        this.muted = muted
    }
    override fun setSpeaker(on: Boolean) {}
    override fun dispose() {
        job?.cancel()
        job = null
        callbacks.onWeakConnection(false)
    }
}
