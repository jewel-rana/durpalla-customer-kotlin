package com.durpalla.booking.core

import com.google.i18n.phonenumbers.AsYouTypeFormatter
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import com.google.i18n.phonenumbers.Phonenumber
import java.util.Locale

data class CountryInfo(
    val iso: String,
    val dialCode: String,
    val nameEn: String,
    val nameBn: String,
    val flag: String,
) {
    fun displayName(bn: Boolean) = if (bn) nameBn.ifBlank { nameEn } else nameEn
}

object PhoneNumbers {
    val util: PhoneNumberUtil = PhoneNumberUtil.getInstance()
    const val DEFAULT_REGION = "BD"

    /** Pinned near the top of the picker (spec §10). */
    val pinnedIsos = listOf("BD", "IN", "SA", "AE", "QA", "KW", "OM", "MY", "SG", "GB", "US")

    val allCountries: List<CountryInfo> by lazy {
        util.supportedRegions.map { iso ->
            val cc = util.getCountryCodeForRegion(iso)
            CountryInfo(
                iso = iso,
                dialCode = cc.toString(),
                nameEn = Locale("", iso).getDisplayCountry(Locale.ENGLISH),
                nameBn = Locale("", iso).getDisplayCountry(Locale("bn")),
                flag = flagEmoji(iso),
            )
        }.sortedBy { it.nameEn.lowercase(Locale.US) }
    }

    fun country(iso: String): CountryInfo =
        allCountries.firstOrNull { it.iso.equals(iso, true) }
            ?: CountryInfo(iso.uppercase(Locale.US), "0", iso, iso, flagEmoji(iso))

    fun flagEmoji(iso: String): String {
        val c = iso.uppercase(Locale.US)
        if (c.length != 2 || !c.all { it in 'A'..'Z' }) return "🏳️"
        val a = Character.codePointAt(c, 0) - 0x41 + 0x1F1E6
        val b = Character.codePointAt(c, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(a)) + String(Character.toChars(b))
    }

    fun newFormatter(region: String): AsYouTypeFormatter = util.getAsYouTypeFormatter(region)

    /** Format national digits as-you-type for [region]. */
    fun formatAsYouType(nationalInput: String, region: String): String {
        val formatter = newFormatter(region)
        var last = ""
        nationalInput.filter { it.isDigit() }.forEach { last = formatter.inputDigit(it) }
        return last
    }

    /**
     * If [raw] starts with `+`, parse and return region + national significant number
     * (for paste → auto-switch country).
     */
    fun detectFromPlus(raw: String): Pair<String, String>? {
        val t = raw.trim()
        if (!t.startsWith("+")) return null
        return try {
            val n = util.parse(t, null)
            val region = util.getRegionCodeForNumber(n) ?: return null
            val national = util.getNationalSignificantNumber(n)
            region to national
        } catch (_: NumberParseException) {
            null
        }
    }

    data class ParsedPhone(
        val region: String,
        val dialCode: String,
        /** Value for API `mobile`: BD → `01XXXXXXXXX`, else E.164. */
        val apiMobile: String,
        val e164: String,
        val displayInternational: String,
    )

    /**
     * Validate SMS-capable mobile for [region]. Accepts BD local forms
     * (`01…`, `1…`, `+8801…`, `8801…`).
     */
    fun parseAndValidate(raw: String, region: String): ParsedPhone? {
        val regionIso = region.uppercase(Locale.US)
        val input = prepareInput(raw.trim(), regionIso) ?: return null
        val number = try {
            util.parse(input, regionIso)
        } catch (_: NumberParseException) {
            return null
        }
        if (!util.isValidNumber(number)) return null
        val type = util.getNumberType(number)
        if (type != PhoneNumberType.MOBILE && type != PhoneNumberType.FIXED_LINE_OR_MOBILE) return null
        val e164 = util.format(number, PhoneNumberUtil.PhoneNumberFormat.E164)
        val intl = util.format(number, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
        val dial = number.countryCode.toString()
        val api = if (regionIso == "BD" || number.countryCode == 880) {
            toBdLegacy(number)
        } else {
            e164
        }
        val resolvedRegion = util.getRegionCodeForNumber(number) ?: regionIso
        return ParsedPhone(resolvedRegion, dial, api, e164, intl)
    }

    private fun prepareInput(raw: String, region: String): String? {
        if (raw.isBlank()) return null
        if (raw.startsWith("+")) return raw
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        if (region == "BD") {
            // 8801XXXXXXXXX → keep full for parse with region null, or normalize for BD
            when {
                digits.startsWith("880") && digits.length >= 13 -> return "+$digits"
                digits.length == 11 && digits.startsWith("01") -> return digits
                digits.length == 10 && digits.startsWith("1") -> return "0$digits"
                else -> return digits
            }
        }
        return digits
    }

    private fun toBdLegacy(n: Phonenumber.PhoneNumber): String {
        val national = util.getNationalSignificantNumber(n)
        // BD mobiles are 10 digits starting with 1 → prefix 0
        return if (national.length == 10 && national.startsWith("1")) "0$national" else national
    }

    fun filterCountries(query: String, bn: Boolean): List<CountryInfo> {
        val q = query.trim().lowercase(Locale.US)
        val pinned = pinnedIsos.map { country(it) }
        val rest = allCountries.filter { it.iso !in pinnedIsos }
        fun match(c: CountryInfo): Boolean {
            if (q.isEmpty()) return true
            return c.iso.lowercase(Locale.US).contains(q) ||
                c.dialCode.contains(q.removePrefix("+")) ||
                c.nameEn.lowercase(Locale.US).contains(q) ||
                c.nameBn.lowercase(Locale.US).contains(q) ||
                "+${c.dialCode}".contains(q)
        }
        return (pinned.filter(::match) + rest.filter(::match))
    }
}
