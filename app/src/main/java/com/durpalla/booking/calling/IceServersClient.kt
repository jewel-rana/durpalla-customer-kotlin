package com.durpalla.booking.calling

import com.durpalla.booking.core.a
import com.durpalla.booking.core.i
import com.durpalla.booking.core.o
import com.durpalla.booking.core.s
import com.durpalla.booking.data.net.ApiClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * GET /calls/ice-servers — array shape **or** `{ iceServers, ttl }`.
 * Cache for at most `ttl/2` seconds (driver contract).
 */
object IceServersClient {
    @Volatile private var cached: IceServersResponse? = null
    @Volatile private var cachedUntilMs: Long = 0L

    suspend fun fetch(api: ApiClient?): IceServersResponse {
        if (api == null) return fallback()
        val now = System.currentTimeMillis()
        cached?.let { if (now < cachedUntilMs) return it }
        return runCatching {
            val r = api.get("calls/ice-servers")
            val code = r.i("http_status") ?: 200
            if (code !in 200..299) return@runCatching fallback()

            // Shapes: [ {...} ] (wrapped as data), { iceServers, ttl }, { servers, ttl }, { data: [...] }
            val ttl = r.i("ttl")?.toLong()
                ?: r.o("data").i("ttl")?.toLong()
            val arr: JsonArray? = when {
                r.a("iceServers") != null -> r.a("iceServers")
                r.a("servers") != null -> r.a("servers")
                r.a("data") != null -> r.a("data")
                else -> {
                    val nested = r.o("data")
                    nested.a("iceServers") ?: nested.a("servers") ?: nested.a("data")
                }
            }
            val servers = arr?.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val urlsRaw = o["urls"]
                val urls = when (urlsRaw) {
                    is JsonArray -> urlsRaw.mapNotNull { (it as? JsonPrimitive)?.content }
                    is JsonPrimitive -> listOf(urlsRaw.content)
                    else -> o.s("url")?.let { listOf(it) } ?: emptyList()
                }.filter { it.isNotBlank() }
                if (urls.isEmpty()) return@mapNotNull null
                IceServerEntry(urls, o.s("username"), o.s("credential"))
            }.orEmpty()

            val result = if (servers.isEmpty()) fallback() else IceServersResponse(servers, ttl)
            val ttlSec = (result.ttlSeconds ?: ttl ?: 3_600L).coerceAtLeast(60L)
            cached = result
            cachedUntilMs = now + ttlSec * 1000L / 2L
            result
        }.getOrElse { fallback() }
    }

    fun fallback() = IceServersResponse(
        listOf(IceServerEntry(listOf(CallTimeouts.FALLBACK_STUN))),
        ttlSeconds = null,
    )

    fun clearCache() {
        cached = null
        cachedUntilMs = 0L
    }
}
