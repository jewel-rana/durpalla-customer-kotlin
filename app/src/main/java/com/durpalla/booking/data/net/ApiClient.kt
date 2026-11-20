package com.durpalla.booking.data.net

import com.durpalla.booking.BuildConfig
import com.durpalla.booking.core.*
import com.durpalla.booking.data.session.SessionStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Master Flutter `api_service.dart`:
 * - Headers: Accept, no-cache, Authorization, **X-Guest-Id**, **Accept-Language**, **Content-Language**
 * - Capture `x-guest-id` response header
 * - Dev assets URL rewrite
 * - 401 → forced logout when authenticated (nav → Home)
 */
class ApiClient(private val session: SessionStore) {

    private val authInterceptor = Interceptor { chain ->
        val lang = session.contentLanguage.ifBlank { "en" }
        val b = chain.request().newBuilder()
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache, no-store, must-revalidate")
            .header("Pragma", "no-cache")
            .header("Expires", "0")
            .header("Accept-Language", lang)
            .header("Content-Language", lang)
        session.token?.let { b.header("Authorization", "Bearer $it") }
        session.guestId?.takeIf { it.isNotBlank() }?.let { b.header("X-Guest-Id", it) }
        val resp = chain.proceed(b.build())
        resp.header("x-guest-id")?.takeIf { it.isNotBlank() }?.let { gid ->
            runBlocking { session.saveGuestId(gid) }
        }
        resp
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        redactHeader("Authorization")
    }

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(logging)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: DurpallaApi = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(okHttp)
        .build()
        .create(DurpallaApi::class.java)

    private val jsonType = "application/json".toMediaType()

    private fun normalizePath(path: String): String = path.trimStart('/')

    private fun decode(resp: Response<okhttp3.ResponseBody>): JsonObject {
        var raw = (resp.body() ?: resp.errorBody())?.string().orEmpty().ifEmpty { "{}" }
        raw = MediaUrl.rewriteJsonText(raw)
        val parsed = runCatching { LooseJson.parseToJsonElement(raw) }.getOrNull()
        val map: MutableMap<String, JsonElement> = when (parsed) {
            is JsonObject -> parsed.toMutableMap()
            is JsonArray -> mutableMapOf("success" to JsonPrimitive(resp.isSuccessful), "data" to parsed)
            else -> mutableMapOf(
                "success" to JsonPrimitive(false),
                "message" to JsonPrimitive(ApiUserMessage.BAD_RESPONSE),
                "raw" to JsonPrimitive(raw),
            )
        }
        if (resp.code() == 401) {
            map["success"] = JsonPrimitive(false)
            map["unauthorized"] = JsonPrimitive(true)
            if (session.isAuthenticated) session.forceLogout()
        }
        if (!map.containsKey("success")) {
            map["success"] = JsonPrimitive(map["status"]?.jsonPrimitive?.booleanOrNull == true)
        }
        map["http_status"] = JsonPrimitive(resp.code())
        if (resp.code() !in 200..299 && map["message"] == null) {
            map["message"] = JsonPrimitive(ApiUserMessage.fromHttp(resp.code(), null))
        }
        return JsonObject(map)
    }

    private suspend fun call(block: suspend () -> Response<okhttp3.ResponseBody>): JsonObject =
        try {
            decode(block())
        } catch (e: IOException) {
            throw ApiException(ApiUserMessage.fromThrowable(e))
        }

    suspend fun get(path: String, query: Map<String, Any?> = emptyMap()) =
        call { api.get(normalizePath(path), query.filterValues { it != null }.mapValues { it.value.toString() }) }

    suspend fun getJson(path: String, query: Map<String, Any?> = emptyMap()): JsonObject {
        val r = get(path, query)
        val code = r.i("http_status") ?: 200
        if (code !in 200..299) throw ApiException(r.message ?: ApiUserMessage.fromHttp(code, null), code, code == 401)
        return r
    }

    suspend fun postForm(path: String, fields: Map<String, String>, headers: Map<String, String> = emptyMap()) =
        call { api.postForm(normalizePath(path), fields, headers) }

    suspend fun postJson(path: String, body: JsonObject, headers: Map<String, String> = emptyMap()) =
        call { api.postJson(normalizePath(path), body.toString().toRequestBody(jsonType), headers) }

    suspend fun put(path: String, body: JsonObject) =
        call { api.putJson(normalizePath(path), body.toString().toRequestBody(jsonType)) }

    suspend fun delete(path: String): JsonObject = call { api.delete(normalizePath(path)) }

    suspend fun putMultipart(path: String, fields: Map<String, String>, file: MultipartBody.Part?, method: String = "PUT") =
        call {
            val parts = (fields + ("_method" to method)).mapValues { it.value.toRequestBody("text/plain".toMediaType()) }
            api.multipart(normalizePath(path), parts, file)
        }

}

fun JsonObject.requireSuccess(fallback: String = ApiUserMessage.GENERIC): JsonObject {
    if (!success) throw ApiException(message ?: fallback, i("http_status") ?: 0, b("unauthorized"))
    return this
}
