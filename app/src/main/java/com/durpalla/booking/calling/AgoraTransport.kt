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
 * Agora Voice SDK transport. App ID / channel / token / uid come from
 * `GET /calls/{id}/agora-token` (never hard-coded).
 *
 * Profile: CHANNEL_PROFILE_COMMUNICATION + AUDIO_PROFILE_SPEECH_STANDARD.
 * Renews token on [IRtcEngineEventHandler.onTokenPrivilegeWillExpire].
 * Network quality ≥ poor → same "Weak connection" hint as WebRTC.
 */
class AgoraTransport(
    private val appContext: Context,
    private val api: ApiClient?,
    private val scope: CoroutineScope,
) : CallTransport {
    override val provider = CallProvider.AGORA

    private var engine: RtcEngine? = null
    private var callbacks: CallTransportCallbacks = CallTransportCallbacks()
    private var activeCallId: String? = null
    private var joined = false
    private var weakHint = false
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
                    val creds = AgoraTokenClient.fetch(client, callId)
                    engine?.renewToken(creds.token)
                }.onFailure { Log.w(TAG, "token renew failed", it) }
            }
        }

        override fun onRequestToken() {
            onTokenPrivilegeWillExpire(null)
        }

        override fun onNetworkQuality(uid: Int, txQuality: Int, rxQuality: Int) {
            // Local user reports as uid 0. Quality: 0 unknown, 1 excellent … 3 poor … 6 down.
            if (uid != 0) return
            val worst = maxOf(txQuality, rxQuality)
            val weak = worst >= Constants.QUALITY_POOR
            if (weak != weakHint) {
                weakHint = weak
                mainHandler.post { callbacks.onWeakConnection(weak) }
            }
        }

        override fun onError(err: Int) {
            Log.w(TAG, "agora error $err")
        }
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
        val client = api ?: error("Agora requires ApiClient")
        activeCallId = callId
        val creds = AgoraTokenClient.fetch(client, callId)
        if (creds.appId.isBlank()) error("agora-token missing appId")
        mainHandler.post {
            ensureEngine(creds.appId)
            val eng = engine ?: return@post
            eng.setAudioProfile(
                Constants.AUDIO_PROFILE_SPEECH_STANDARD,
                Constants.AUDIO_SCENARIO_DEFAULT,
            )
            val options = ChannelMediaOptions().apply {
                channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                autoSubscribeAudio = true
                publishMicrophoneTrack = true
                publishCameraTrack = false
            }
            val code = eng.joinChannel(creds.token, creds.channel, creds.uid, options)
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
    }
}
