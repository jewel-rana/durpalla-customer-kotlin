package com.durpalla.booking.calling

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.durpalla.booking.data.net.ApiClient
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Agora Voice SDK transport. Initial credentials from create/accept `agora` block
 * (no separate token fetch at call start). Renewal: `POST /calls/{id}/token`.
 *
 * Caller joins right after POST /calls; callee joins after accept.
 * COMMUNICATION + SPEECH_STANDARD + CHATROOM;
 * weak hint from network quality (≥ BAD) and rtc stats (loss ≥ 5% or lastmile RTT ≥ 400ms),
 * cleared after 3 good samples.
 */
class AgoraTransport(
    private val appContext: Context,
    private val api: ApiClient?,
    private val scope: CoroutineScope,
    private val initialCredentials: AgoraCredentials?,
) : CallTransport {
    override val provider = CallProvider.AGORA

    private var engine: RtcEngine? = null
    private var callbacks: CallTransportCallbacks = CallTransportCallbacks()
    private var activeCallId: String? = null
    private var joined = false
    private var weakHint = false
    private var goodStreak = 0
    private var audioModeBefore = AudioManager.MODE_NORMAL
    private var speakerBefore = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val handler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            Log.d(TAG, "joined $channel uid=$uid")
            joined = true
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            mainHandler.post { callbacks.onConnected() }
        }

        override fun onTokenPrivilegeWillExpire(token: String?) {
            val callId = activeCallId ?: return
            val client = api ?: return
            scope.launch {
                runCatching {
                    val renewed = AgoraTokenClient.renew(client, callId)
                    engine?.renewToken(renewed.token)
                }.onFailure { Log.w(TAG, "token renew failed", it) }
            }
        }

        override fun onRequestToken() {
            onTokenPrivilegeWillExpire(null)
        }

        override fun onNetworkQuality(uid: Int, txQuality: Int, rxQuality: Int) {
            // Local uid 0; tx/rx ≥ QUALITY_BAD (4) → weak (driver contract §0).
            if (uid != 0) return
            val worst = maxOf(txQuality, rxQuality)
            applyWeakSample(bad = worst >= Constants.QUALITY_BAD)
        }

        override fun onRtcStats(stats: RtcStats?) {
            if (stats == null) return
            val loss = maxOf(stats.txPacketLossRate, stats.rxPacketLossRate)
            val rtt = stats.lastmileDelay
            applyWeakSample(bad = loss >= LOSS_BAD_PCT || rtt >= RTT_BAD_MS)
        }

        override fun onError(err: Int) {
            Log.w(TAG, "agora error $err")
        }
    }

    private fun applyWeakSample(bad: Boolean) {
        if (bad) {
            goodStreak = 0
            setWeak(true)
        } else {
            goodStreak++
            if (goodStreak >= GOOD_STREAK_TO_CLEAR) {
                goodStreak = 0
                setWeak(false)
            }
        }
    }

    private fun setWeak(weak: Boolean) {
        if (weak == weakHint) return
        weakHint = weak
        mainHandler.post { callbacks.onWeakConnection(weak) }
    }

    override suspend fun prepare(asCaller: Boolean, callbacks: CallTransportCallbacks) {
        this.callbacks = callbacks
        enterCommunicationMode()
    }

    override suspend fun startOutgoing(callId: String): SdpPayload? {
        join(callId)
        return null
    }

    override suspend fun startIncoming(callId: String): SdpPayload? {
        join(callId)
        return null
    }

    private suspend fun join(callId: String) {
        activeCallId = callId
        val creds = initialCredentials
            ?: error("agora credentials missing from create/accept response")
        if (creds.appId.isBlank()) error("agora block missing appId")
        mainHandler.post {
            ensureEngine(creds.appId)
            val eng = engine ?: return@post
            eng.setAudioProfile(
                Constants.AUDIO_PROFILE_SPEECH_STANDARD,
                Constants.AUDIO_SCENARIO_CHATROOM,
            )
            val options = ChannelMediaOptions().apply {
                channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                autoSubscribeAudio = true
                publishMicrophoneTrack = true
                publishCameraTrack = false
            }
            val channel = AgoraCredentials.resolveChannel(creds, callId)
            val code = eng.joinChannel(creds.token, channel, creds.uid, options)
            if (code != 0) Log.w(TAG, "joinChannel returned $code")
        }
    }

    private fun ensureEngine(appId: String) {
        if (engine != null) return
        val config = RtcEngineConfig().apply {
            mContext = appContext
            mAppId = appId
            mEventHandler = handler
            mChannelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            mAudioScenario = Constants.AUDIO_SCENARIO_CHATROOM
        }
        engine = RtcEngine.create(config)
        engine?.enableAudio()
        engine?.setDefaultAudioRoutetoSpeakerphone(false)
    }

    override fun setMuted(muted: Boolean) {
        engine?.muteLocalAudioStream(muted)
    }

    override fun setSpeaker(on: Boolean) {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        am.isSpeakerphoneOn = on
        engine?.setEnableSpeakerphone(on)
    }

    override fun dispose() {
        runCatching {
            if (joined) engine?.leaveChannel()
            RtcEngine.destroy()
        }
        engine = null
        joined = false
        activeCallId = null
        goodStreak = 0
        if (weakHint) callbacks.onWeakConnection(false)
        weakHint = false
        restoreAudioMode()
    }

    private fun enterCommunicationMode() {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioModeBefore = am.mode
        speakerBefore = am.isSpeakerphoneOn
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        am.isSpeakerphoneOn = false
    }

    private fun restoreAudioMode() {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.isSpeakerphoneOn = speakerBefore
        am.mode = AudioManager.MODE_NORMAL
    }

    companion object {
        private const val TAG = "AgoraTransport"
        private const val LOSS_BAD_PCT = 5
        private const val RTT_BAD_MS = 400
        private const val GOOD_STREAK_TO_CLEAR = 3
    }
}
