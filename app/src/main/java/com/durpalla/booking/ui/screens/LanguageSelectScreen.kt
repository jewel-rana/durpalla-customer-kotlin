package com.durpalla.booking.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.durpalla.booking.R
import com.durpalla.booking.l10n.LocaleHelper
import com.durpalla.booking.l10n.SupportedLocales
import com.durpalla.booking.l10n.t
import com.durpalla.booking.ui.components.GradientButton
import com.durpalla.booking.ui.nav.LocalContainer
import com.durpalla.booking.ui.theme.Brand
import kotlinx.coroutines.launch

/**
 * Flutter `language_select_screen.dart` — first-launch onboarding and reusable picker.
 * Prefs: `app_locale`, `language_onboarding_done`.
 */
@Composable
fun LanguageSelectScreen(
    onFinished: () -> Unit,
    showContinue: Boolean = true,
    title: String? = null,
) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val current by c.session.language.collectAsState(initial = "en")
    var selected by remember(current) { mutableStateOf(current) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brand.HeroGradient)
                .padding(24.dp, 28.dp, 24.dp, 36.dp),
        ) {
            Column {
                Text(
                    title ?: t(R.string.choose_language),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    t(R.string.choose_language_body),
                    color = Color.White.copy(0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(SupportedLocales.all, key = { it.code }) { loc ->
                val sel = selected == loc.code
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (sel) Brand.P50 else MaterialTheme.colorScheme.surface)
                        .clickable { selected = loc.code }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(loc.nativeName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            buildString {
                                append(loc.englishName)
                                if (loc.bundled) append(" · ").append(stringResource(R.string.lang_bundled))
                                else append(" · ").append(stringResource(R.string.lang_remote))
                                if (loc.rtl) append(" · RTL")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Brand.Muted,
                        )
                    }
                    if (sel) Icon(Icons.Default.Check, null, tint = Brand.Primary)
                }
            }
        }
        if (showContinue) {
            GradientButton(
                t(R.string.continue_label),
                Modifier.fillMaxWidth().padding(16.dp),
            ) {
                scope.launch {
                    c.session.setLanguage(selected)
                    c.session.setLanguageOnboardingDone(true)
                    LocaleHelper.apply(selected)
                    c.l10n.ensureLoaded(selected)
                    onFinished()
                }
            }
        }
    }
}

/** Settings / account: pick a language and dismiss. */
@Composable
fun LanguagePickerSheet(onDismiss: () -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val current by c.session.language.collectAsState(initial = "en")
    Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
        Text(
            t(R.string.language),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(24.dp, 16.dp, 24.dp, 8.dp),
        )
        SupportedLocales.all.forEach { loc ->
            val sel = current == loc.code
            ListItem(
                headlineContent = { Text(loc.nativeName) },
                supportingContent = { Text(loc.englishName) },
                trailingContent = { if (sel) Icon(Icons.Default.Check, null, tint = Brand.Primary) },
                modifier = Modifier.clickable {
                    scope.launch {
                        c.session.setLanguage(loc.code)
                        c.session.setLanguageOnboardingDone(true)
                        LocaleHelper.apply(loc.code)
                        c.l10n.ensureLoaded(loc.code)
                        onDismiss()
                    }
                },
            )
        }
    }
}
