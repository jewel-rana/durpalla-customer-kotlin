package com.durpalla.booking.l10n

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.os.LocaleListCompat
import java.util.Locale

val LocalL10nOverlay = staticCompositionLocalOf<Map<String, String>> { emptyMap() }
val LocalAppLocaleCode = staticCompositionLocalOf { "en" }
private val LocalAppResources = staticCompositionLocalOf<Resources?> { null }

/**
 * String lookup with remote overlay (key = resource entry name, e.g. `call_driver`).
 * Falls back to resources for the selected app locale.
 */
@Composable
fun t(@StringRes id: Int, vararg formatArgs: Any): String {
    val resources = LocalAppResources.current ?: return stringResource(id, *formatArgs)
    val name = runCatching { resources.getResourceEntryName(id) }.getOrNull()
    val overlay = LocalL10nOverlay.current
    val remote = name?.let { overlay[it] }
    return when {
        remote != null && formatArgs.isNotEmpty() ->
            runCatching { String.format(Locale.US, remote, *formatArgs) }.getOrElse { remote }
        remote != null -> remote
        formatArgs.isNotEmpty() -> resources.getString(id, *formatArgs)
        else -> resources.getString(id)
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
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val resources = remember(context, configuration, code) {
        val localized = Configuration(configuration)
        localized.setLocale(Locale.forLanguageTag(code.replace('_', '-')))
        context.createConfigurationContext(localized).resources
    }
    CompositionLocalProvider(
        LocalAppLocaleCode provides code,
        LocalAppResources provides resources,
        LocalL10nOverlay provides if (SupportedLocales.isRemote(code)) overlay else emptyMap(),
        LocalLayoutDirection provides LocaleHelper.layoutDirection(code),
    ) {
        content()
    }
}

fun Context.resourceEntryName(@StringRes id: Int): String? =
    runCatching { resources.getResourceEntryName(id) }.getOrNull()
