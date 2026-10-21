package com.durpalla.booking.core

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.StringRes

/** Application context for resolving strings outside Compose (repos, FCM, services). */
@SuppressLint("StaticFieldLeak")
object AppCtx {
    lateinit var app: Context
        private set

    fun init(context: Context) {
        app = context.applicationContext
    }

    fun str(@StringRes id: Int): String = app.getString(id)
    fun str(@StringRes id: Int, vararg args: Any): String = app.getString(id, *args)
}
