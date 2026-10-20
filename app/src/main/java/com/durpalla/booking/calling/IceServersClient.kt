package com.durpalla.booking.calling

import com.durpalla.booking.core.*
import com.durpalla.booking.data.net.ApiClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * GET /calls/ice-servers → `[{urls, username, credential}]` + optional ttl.
 * On failure: fall back to [CallTimeouts.FALLBACK_STUN].
 */
object IceServersClient {
    suspend fun fetch(api: ApiClient?): IceServersResponse {
        if (api == null) return fallback()
        return runCatching {
            val r = api.get("calls/ice-servers")
            val code = r.i("http_status") ?: 200
            if (code !in 200..299) return@runCatching fallback()
            val data = r["data"] ?: r["servers"] ?: r["iceServers"] ?: r
            val ttl = r.i("ttl")?.toLong() ?: (data as? JsonObject)?.i("ttl")?.toLong()
            val arr: JsonArray? = when (data) {
                is JsonArray -> data
                is JsonObject -> data.a("servers") ?: data.a("iceServers") ?: data.a("data")
                else -> null
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
            if (servers.isEmpty()) fallback() else IceServersResponse(servers, ttl)
        }.getOrElse { fallback() }
    }

    fun fallback() = IceServersResponse(
        listOf(IceServerEntry(listOf(CallTimeouts.FALLBACK_STUN))),
        ttlSeconds = null,
    )
}
