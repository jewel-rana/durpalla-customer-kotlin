package com.durpalla.booking.core

import java.util.Locale

/** Port of Flutter `lib/helpers/form_validators.dart` (dev/master email+mobile). */
object AuthValidators {
    private val EMAIL_RE = Regex("^[^@]+@[^@]+\\.[^@]+")
    private val INTL_MOBILE_RE = Regex("^\\+?[1-9]\\d{7,14}$")

    fun isEmail(raw: String): Boolean = EMAIL_RE.matches(raw.trim())

    fun normalizeEmail(raw: String): String = raw.trim().lowercase(Locale.US)

    /** Flutter `normalizeBdMobile`. */
    fun normalizeBdMobile(raw: String): String {
        var d = raw.filter { it.isDigit() }
        if (d.startsWith("8801") && d.length >= 13) d = d.drop(2) // drop "88"
        if (d.length == 10 && d.startsWith("1")) d = "0$d"
        return d
    }

    fun isBdMobile(raw: String): Boolean {
        val n = normalizeBdMobile(raw)
        return n.length == 11 && n.startsWith("01")
    }

    /** Flutter `isIntlOrBdMobile`: BD local form **or** `^\+?[1-9]\d{7,14}$`. */
    fun isIntlOrBdMobile(raw: String): Boolean {
        val t = raw.trim()
        if (t.isEmpty()) return false
        if (isBdMobile(t)) return true
        return INTL_MOBILE_RE.matches(t)
    }

    /**
     * Compose the `mobile` value for the API from the country picker + national input.
     * BD → `01XXXXXXXXX`; otherwise as typed / `+{dial}{national}` matching intl shape.
     * Does **not** send country_code / dial_code.
     */
    fun composeMobileForApi(regionIso: String, nationalInput: String): String? {
        val trimmed = nationalInput.trim()
        if (trimmed.startsWith("+")) {
            return if (isIntlOrBdMobile(trimmed)) {
                if (isBdMobile(trimmed)) normalizeBdMobile(trimmed) else trimmed
            } else null
        }
        if (regionIso.equals("BD", ignoreCase = true)) {
            val n = normalizeBdMobile(trimmed)
            return n.takeIf { isBdMobile(it) }
        }
        val dial = PhoneNumbers.country(regionIso).dialCode
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        val composed = "+$dial$digits"
        return composed.takeIf { isIntlOrBdMobile(it) }
    }

    fun isOtpComplete(code: String, length: Int = 6): Boolean =
        code.length == length && code.all { it.isDigit() }

    fun passwordOk(pw: String): Boolean = pw.length >= 8
}
