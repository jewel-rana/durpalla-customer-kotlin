package com.durpalla.booking.realtime

import android.util.Log
import com.durpalla.booking.BuildConfig
import com.durpalla.booking.core.LooseJson
import com.pusher.client.Pusher
import com.pusher.client.PusherOptions
import com.pusher.client.channel.Channel
import com.pusher.client.connection.ConnectionEventListener
import com.pusher.client.connection.ConnectionState
import com.pusher.client.connection.ConnectionStateChange
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.JsonObject

/**
 * Port of lib/services/pusher_service.dart (pusher_channels_flutter → pusher-java-client).
 * Key/cluster from the Flutter .env (PUSHER_KEY / PUSHER_CLUSTER). Public channels only (`trip.{id}`),
 * no auth endpoint is used by the Flutter app. Socket id is sent as `X-Socket-Id` on `cart/add`.
 * Events: `cart.item.locked`, `cart.item.booked`, `cart.item.released` with payload `{item_id, data:{cabin_no, fare}}`.
 */
class PusherManager {
    data class Event(val channel: String, val name: String, val data: JsonObject?)

    private val pusher: Pusher? = runCatching {
        Pusher(BuildConfig.PUSHER_KEY, PusherOptions().setCluster(BuildConfig.PUSHER_CLUSTER))
    }.getOrNull()
    private val channels = mutableMapOf<String, Channel>()
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 32)
    val events: SharedFlow<Event> = _events

    @Volatile var socketId: String? = null
        private set

    val tripEvents = listOf("cart.item.locked", "cart.item.booked", "cart.item.released")

    fun connect() {
        pusher?.connect(object : ConnectionEventListener {
            override fun onConnectionStateChange(change: ConnectionStateChange) {
                Log.d("Pusher", "Connection: ${change.currentState}")
                if (change.currentState == ConnectionState.CONNECTED) socketId = pusher.connection.socketId
            }
            override fun onError(message: String?, code: String?, e: Exception?) { Log.w("Pusher", "Error $message $code", e) }
        }, ConnectionState.ALL)
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

    fun unsubscribe(channelName: String) {
        if (channels.remove(channelName) != null) pusher?.unsubscribe(channelName)
    }
}
