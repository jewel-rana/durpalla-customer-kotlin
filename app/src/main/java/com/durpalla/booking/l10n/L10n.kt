package com.durpalla.booking.l10n

import android.content.Context
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.os.LocaleListCompat
import java.util.Locale

val LocalL10nOverlay = staticCompositionLocalOf<Map<String, String>> { emptyMap() }
val LocalAppLocaleCode = staticCompositionLocalOf { "en" }

/**
 * String lookup with remote overlay (key = resource entry name, e.g. `call_driver`).
 * Falls back to bundled `values` / `values-bn` via [stringResource].
 */
@Composable
fun t(@StringRes id: Int, vararg formatArgs: Any): String {
    val ctx = LocalContext.current
    val name = runCatching { ctx.resources.getResourceEntryName(id) }.getOrNull()
    val overlay = LocalL10nOverlay.current
    val remote = name?.let { overlay[it] }
    return when {
        remote != null && formatArgs.isNotEmpty() ->
            runCatching { String.format(Locale.US, remote, *formatArgs) }.getOrElse { remote }
        remote != null -> remote
        formatArgs.isNotEmpty() -> stringResource(id, *formatArgs)
        else -> stringResource(id)
    }
}

/** Apply per-app locales (Android 13+ / AppCompat backport) + update default Locale. */
object LocaleHelper {
    fun apply(code: String) {
        val tags = code.ifBlank { "en" }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags))
        val locale = Locale.forLanguageTag(tags.replace('_', '-'))
        Locale.setDefault(locale)
    }

    fun layoutDirection(code: String): LayoutDirection =
        if (SupportedLocales.isRtl(code)) LayoutDirection.Rtl else LayoutDirection.Ltr
}

@Composable
fun ProvideAppLocale(
    code: String,
    overlay: Map<String, String>,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAppLocaleCode provides code,
        LocalL10nOverlay provides overlay,
        LocalLayoutDirection provides LocaleHelper.layoutDirection(code),
    ) {
        content()
    }
}

fun Context.resourceEntryName(@StringRes id: Int): String? =
    runCatching { resources.getResourceEntryName(id) }.getOrNull()
