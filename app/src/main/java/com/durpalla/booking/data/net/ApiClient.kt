package com.durpalla.booking.data.net

import com.durpalla.booking.BuildConfig
import com.durpalla.booking.core.*
import com.durpalla.booking.data.session.SessionStore
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
 * Kotlin port of `lib/services/api_service.dart`.
 * - Headers: Accept: application/json, no-cache headers, `Authorization: Bearer <token>` when signed in.
 * - 401 → `{success:false, unauthorized:true}` and forced logout when we had a token.
 * - Non-JSON → `{success:false, message:'Invalid server response'}`.
 * - `success` defaults to `status == true`.
 */
class ApiClient(private val session: SessionStore) {

    private val authInterceptor = Interceptor { chain ->
        val b = chain.request().newBuilder()
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache, no-store, must-revalidate")
            .header("Pragma", "no-cache")
            .header("Expires", "0")
        session.token?.let { b.header("Authorization", "Bearer $it") }
        chain.proceed(b.build())
    }

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        })
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: DurpallaApi = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(okHttp)
        .build()
        .create(DurpallaApi::class.java)

    private val jsonType = "application/json".toMediaType()

    private fun decode(resp: Response<okhttp3.ResponseBody>): JsonObject {
        val raw = (resp.body() ?: resp.errorBody())?.string().orEmpty().ifEmpty { "{}" }
        val parsed = runCatching { LooseJson.parseToJsonElement(raw) }.getOrNull()
        val map = when (parsed) {
            is JsonObject -> parsed.toMutableMap()
            is JsonArray -> mutableMapOf<String, JsonElement>("success" to JsonPrimitive(resp.isSuccessful), "data" to parsed)
            else -> mutableMapOf<String, JsonElement>("success" to JsonPrimitive(false), "message" to JsonPrimitive("Invalid server response"), "raw" to JsonPrimitive(raw))
        }
        if (resp.code() == 401) {
            map["success"] = JsonPrimitive(false)
            map["unauthorized"] = JsonPrimitive(true)
            // Failed password login also returns 401 — only wipe the session when we had a token.
            if (session.isAuthenticated) session.forceLogout()
        }
        if (!map.containsKey("success")) map["success"] = JsonPrimitive(map["status"]?.jsonPrimitive?.booleanOrNull == true)
        map["http_status"] = JsonPrimitive(resp.code())
        return JsonObject(map)
    }

    private suspend fun call(block: suspend () -> Response<okhttp3.ResponseBody>): JsonObject =
        try { decode(block()) } catch (e: IOException) { throw ApiException("offline") }

    suspend fun get(path: String, query: Map<String, Any?> = emptyMap()) =
        call { api.get(path, query.filterValues { it != null }.mapValues { it.value.toString() }) }

    /** Flutter `getJson`: throws on non-2xx. */
    suspend fun getJson(path: String, query: Map<String, Any?> = emptyMap()): JsonObject {
        val r = get(path, query)
        val code = r.i("http_status") ?: 200
        if (code !in 200..299) throw ApiException(r.message ?: "HTTP $code", code, code == 401)
        return r
    }

    suspend fun postForm(path: String, fields: Map<String, String>, headers: Map<String, String> = emptyMap()) =
        call { api.postForm(path, fields, headers) }

    suspend fun postJson(path: String, body: JsonObject, headers: Map<String, String> = emptyMap()) =
        call { api.postJson(path, body.toString().toRequestBody(jsonType), headers) }

    suspend fun put(path: String, body: JsonObject) = call { api.putJson(path, body.toString().toRequestBody(jsonType)) }

    suspend fun delete(path: String): JsonObject = call { api.delete(path) }

    /** Flutter `putMultipart`: POST with `_method` override. */
    suspend fun putMultipart(path: String, fields: Map<String, String>, file: MultipartBody.Part?, method: String = "PUT") =
        call {
            val parts = (fields + ("_method" to method)).mapValues { it.value.toRequestBody("text/plain".toMediaType()) }
            api.multipart(path, parts, file)
        }
}

/** Throws ApiException with the server message when `success` is false. */
fun JsonObject.requireSuccess(fallback: String = "Request could not be completed."): JsonObject {
    if (!success) throw ApiException(message ?: fallback, i("http_status") ?: 0, b("unauthorized"))
    return this
}
