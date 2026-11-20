package com.durpalla.booking.core

import java.util.Locale

/** Bangla digits for display only (timers, call log). Western digits stay in APIs. */
object BanglaDigits {
    private val map = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

    fun format(raw: String, locale: Locale = Locale.getDefault()): String {
        if (!locale.language.equals("bn", ignoreCase = true)) return raw
        return buildString {
            raw.forEach { ch ->
                append(if (ch in '0'..'9') map[ch - '0'] else ch)
            }
        }
    }

    fun formatDuration(seconds: Long, locale: Locale = Locale.getDefault()): String {
        val m = seconds / 60
        val s = seconds % 60
        val western = "%d:%02d".format(m, s)
        return format(western, locale)
    }
}
