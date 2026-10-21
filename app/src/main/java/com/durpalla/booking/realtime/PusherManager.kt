package com.durpalla.booking.realtime

import android.util.Log
import com.durpalla.booking.BuildConfig
import com.durpalla.booking.core.LooseJson
import com.pusher.client.Pusher
import com.pusher.client.PusherOptions
import com.pusher.client.channel.Channel
import com.pusher.client.channel.PrivateChannelEventListener
import com.pusher.client.channel.PusherEvent
import com.pusher.client.connection.ConnectionEventListener
import com.pusher.client.connection.ConnectionState
import com.pusher.client.connection.ConnectionStateChange
import com.pusher.client.util.HttpChannelAuthorizer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.JsonObject

/**
 * Pusher Channels client.
 * - Public `trip.{id}` for cart lock events (Flutter parity).
 * - Private `private-user-{userId}` for in-app calling (contract secondary ring path);
 *   authorised via `POST {BASE_URL}realtime/auth`.
 */
class PusherManager {
    data class Event(val channel: String, val name: String, val data: JsonObject?)

    private var tokenProvider: () -> String? = { null }
    private var authorizer: HttpChannelAuthorizer? = null
    private var pusher: Pusher? = null
    private val channels = mutableMapOf<String, Channel>()
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 32)
    val events: SharedFlow<Event> = _events

    @Volatile var socketId: String? = null
        private set

    val tripEvents = listOf("cart.item.locked", "cart.item.booked", "cart.item.released")
    val callEvents = listOf("call.incoming", "call.ended", "call.declined", "call.missed")

    fun connect(authToken: () -> String? = tokenProvider) {
        tokenProvider = authToken
        if (pusher != null) {
            refreshAuthHeaders()
            return
        }
        val authUrl = BuildConfig.BASE_URL.trimEnd('/') + "/realtime/auth"
        val auth = HttpChannelAuthorizer(authUrl)
        authorizer = auth
        refreshAuthHeaders()
        val options = PusherOptions()
            .setCluster(BuildConfig.PUSHER_CLUSTER)
            .setUseTLS(true)
            .setChannelAuthorizer(auth)
        val p = runCatching { Pusher(BuildConfig.PUSHER_KEY, options) }.getOrNull() ?: return
        p.connect(object : ConnectionEventListener {
            override fun onConnectionStateChange(change: ConnectionStateChange) {
                Log.d("Pusher", "Connection: ${change.currentState}")
                if (change.currentState == ConnectionState.CONNECTED) socketId = p.connection.socketId
            }
            override fun onError(message: String?, code: String?, e: Exception?) {
                Log.w("Pusher", "Error $message $code", e)
            }
        }, ConnectionState.ALL)
        pusher = p
    }

    private fun refreshAuthHeaders() {
        authorizer?.setHeaders(
            mapOf("Authorization" to "Bearer ${tokenProvider().orEmpty()}"),
        )
    }

    fun subscribe(channelName: String, events: List<String> = tripEvents) {
        val p = pusher ?: return
        if (channels.containsKey(channelName)) return
        val ch = p.subscribe(channelName)
        events.forEach { ev ->
            ch.bind(ev) { e ->
                val payload = runCatching { LooseJson.parseToJsonElement(e.data) as? JsonObject }.getOrNull()
                _events.tryEmit(Event(e.channelName, e.eventName, payload))
            }
        }
        channels[channelName] = ch
    }

    /** Secondary in-app calling path: `private-user-{userId}` (contract §2). */
    fun subscribeUser(userId: String) {
        if (userId.isBlank()) return
        val name = "private-user-$userId"
        val p = pusher ?: return
        if (channels.containsKey(name)) return
        refreshAuthHeaders()
        val listener = object : PrivateChannelEventListener {
            override fun onEvent(event: PusherEvent) {
                val payload = runCatching { LooseJson.parseToJsonElement(event.data) as? JsonObject }.getOrNull()
                _events.tryEmit(Event(event.channelName, event.eventName, payload))
            }
            override fun onSubscriptionSucceeded(channelName: String) {
                Log.d("Pusher", "Subscribed $channelName")
            }
            override fun onAuthenticationFailure(message: String?, e: Exception?) {
                Log.w("Pusher", "Auth failed for $name: $message", e)
            }
        }
        runCatching {
            val ch = p.subscribePrivate(name, listener, *callEvents.toTypedArray())
            channels[name] = ch
        }.onFailure { Log.w("Pusher", "subscribePrivate $name failed", it) }
    }

    fun unsubscribeUser(userId: String) {
        if (userId.isBlank()) return
        unsubscribe("private-user-$userId")
    }

    fun unsubscribe(channelName: String) {
        if (channels.remove(channelName) != null) pusher?.unsubscribe(channelName)
    }

    fun disconnect() {
        pusher?.disconnect()
        pusher = null
        authorizer = null
        channels.clear()
        socketId = null
    }
}
