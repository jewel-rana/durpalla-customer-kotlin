package com.durpalla.booking.calling

import com.durpalla.booking.R
import com.durpalla.booking.core.AppCtx
import com.durpalla.booking.core.ApiException
import com.durpalla.booking.core.i
import com.durpalla.booking.core.o
import com.durpalla.booking.core.s
import com.durpalla.booking.data.net.ApiClient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/**
 * Sign in to Firebase with backend custom token before any Firestore signaling.
 * `GET /auth/firebase-token` → `{ "token": "…" }`; auth uid = Durpalla userId.
 */
object FirebaseCallAuth {
    suspend fun ensureSignedIn(api: ApiClient, durpallaUserId: String) {
        if (durpallaUserId.isBlank()) {
            throw ApiException(AppCtx.str(R.string.err_call_unauthorized), 401, true)
        }
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser?.uid == durpallaUserId) return
        val r = api.get("auth/firebase-token")
        CallApiErrors.requireCallSuccess(r, AppCtx.str(R.string.err_call_unauthorized))
        val token = r.o("data").s("token") ?: r.s("token")
            ?: throw ApiException(AppCtx.str(R.string.err_call_unauthorized), r.i("http_status") ?: 401)
        auth.signInWithCustomToken(token).await()
    }
}
