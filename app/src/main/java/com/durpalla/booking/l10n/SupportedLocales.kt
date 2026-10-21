package com.durpalla.booking.l10n

/**
 * Flutter `constants/supported_locales.dart` (master).
 * Bundled: en, bn. Remote overlay: hi, ar, zh, ur, fa, tr, es, it (ar/ur/fa RTL).
 */
data class AppLocale(
    val code: String,
    val englishName: String,
    val nativeName: String,
    val bundled: Boolean = false,
    val rtl: Boolean = false,
)

object SupportedLocales {
    val all: List<AppLocale> = listOf(
        AppLocale("en", "English", "English", bundled = true),
        AppLocale("bn", "Bangla", "বাংলা", bundled = true),
        AppLocale("hi", "Hindi", "हिन्दी"),
        AppLocale("ar", "Arabic", "العربية", rtl = true),
        AppLocale("zh", "Chinese", "中文"),
        AppLocale("ur", "Urdu", "اردو", rtl = true),
        AppLocale("fa", "Persian", "فارسی", rtl = true),
        AppLocale("tr", "Turkish", "Türkçe"),
        AppLocale("es", "Spanish", "Español"),
        AppLocale("it", "Italian", "Italiano"),
    )

    val codes: List<String> = all.map { it.code }
    val rtlCodes: Set<String> = all.filter { it.rtl }.map { it.code }.toSet()

    fun find(code: String): AppLocale =
        all.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: all.first()

    fun isRtl(code: String): Boolean = code.lowercase() in rtlCodes

    fun isRemote(code: String): Boolean = find(code).let { !it.bundled }
}
