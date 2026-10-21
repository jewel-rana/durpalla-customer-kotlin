package com.durpalla.booking.data.net

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Raw Retrofit surface. Bodies are decoded by [ApiClient] exactly like Flutter's `ApiService._decode`.
 * Paths are relative to BuildConfig.BASE_URL (…/api/v1/).
 */
interface DurpallaApi {
    @GET suspend fun get(@Url path: String, @QueryMap query: Map<String, String> = emptyMap()): Response<ResponseBody>

    @FormUrlEncoded @POST
    suspend fun postForm(@Url path: String, @FieldMap fields: Map<String, String>, @HeaderMap headers: Map<String, String> = emptyMap()): Response<ResponseBody>

    @POST suspend fun postJson(@Url path: String, @Body body: RequestBody, @HeaderMap headers: Map<String, String> = emptyMap()): Response<ResponseBody>

    @PUT suspend fun putJson(@Url path: String, @Body body: RequestBody): Response<ResponseBody>

    @DELETE suspend fun delete(@Url path: String): Response<ResponseBody>

    @Multipart @POST
    suspend fun multipart(@Url path: String, @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>, @Part file: MultipartBody.Part?): Response<ResponseBody>
}
