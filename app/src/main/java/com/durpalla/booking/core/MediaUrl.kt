package com.durpalla.booking.core

import com.durpalla.booking.BuildConfig

/**
 * Flutter `lib/helpers/media_url.dart`: on debug / when API host is `dev-*`,
 * rewrite `https://assets.durpalla.com` → `https://dev-assets.durpalla.com` (or [BuildConfig.ASSETS_BASE_URL]).
 */
object MediaUrl {
    private const val PROD_ASSETS = "https://assets.durpalla.com"
    private val DEV_ASSETS: String
        get() = BuildConfig.ASSETS_BASE_URL.ifBlank { "https://dev-assets.durpalla.com" }

    fun shouldRewrite(): Boolean {
        if (BuildConfig.DEBUG) return true
        return BuildConfig.BASE_URL.contains("dev-", ignoreCase = true)
    }

    fun resolve(url: String?): String? {
        if (url.isNullOrBlank()) return url
        if (!shouldRewrite()) return url
        return if (url.startsWith(PROD_ASSETS)) url.replaceFirst(PROD_ASSETS, DEV_ASSETS.trimEnd('/')) else url
    }

    /** Walk a JSON string and rewrite asset URLs (cheap post-process of API bodies). */
    fun rewriteJsonText(raw: String): String {
        if (!shouldRewrite() || !raw.contains(PROD_ASSETS)) return raw
        return raw.replace(PROD_ASSETS, DEV_ASSETS.trimEnd('/'))
    }
}
