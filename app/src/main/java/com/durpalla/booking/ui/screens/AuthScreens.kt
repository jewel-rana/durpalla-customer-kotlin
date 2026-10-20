package com.durpalla.booking.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.durpalla.booking.R
import com.durpalla.booking.core.AuthCheckResult
import com.durpalla.booking.core.AuthIdentifier
import com.durpalla.booking.core.AuthValidators
import com.durpalla.booking.core.PhoneNumbers
import com.durpalla.booking.core.message
import com.durpalla.booking.core.success
import com.durpalla.booking.ui.components.GradientButton
import com.durpalla.booking.ui.components.PhoneInputField
import com.durpalla.booking.ui.components.SoftCard
import com.durpalla.booking.ui.nav.LocalContainer
import com.durpalla.booking.ui.nav.LocalNav
import com.durpalla.booking.ui.nav.Routes
import com.durpalla.booking.ui.theme.Brand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import com.durpalla.booking.l10n.t

@Composable
private fun AuthScaffold(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    val nav = LocalNav.current
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brand.HeroGradient)
                .statusBarsPadding()
                .padding(20.dp, 8.dp, 20.dp, 56.dp),
        ) {
            Column {
                IconButton(onClick = nav::back) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                }
                Image(painterResource(R.drawable.logo_horizontal_white), null, Modifier.height(34.dp))
                Spacer(Modifier.height(16.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text(subtitle, color = Color.White.copy(.8f))
            }
        }
        SoftCard(Modifier.padding(horizontal = 16.dp).offset(y = (-32).dp)) {
            Column(Modifier.padding(20.dp), content = content)
        }
    }
}

private enum class AuthTab { Mobile, Email }

/** E1 — Mobile|Email tabs (explicit; no auto-detect). Switching tabs clears the field. */
@Composable
fun LoginScreen(redirect: String) {
    val c = LocalContainer.current
    val nav = LocalNav.current
    var tab by rememberSaveable { mutableStateOf(AuthTab.Mobile.name) }
    val authTab = AuthTab.valueOf(tab)
    var region by rememberSaveable { mutableStateOf(PhoneNumbers.DEFAULT_REGION) }
    var national by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var warn by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun selectTab(t: AuthTab) {
        tab = t.name
        err = null
        warn = null
        national = ""
        email = ""
        region = PhoneNumbers.DEFAULT_REGION
    }

    val mobileApi = AuthValidators.composeMobileForApi(region, national)
    val emailOk = AuthValidators.isEmail(email)
    val mobileOk = mobileApi != null
    val canContinue = if (authTab == AuthTab.Email) emailOk else mobileOk
    val unableContinue = t(R.string.unable_to_continue)

    AuthScaffold(t(R.string.sign_in), t(R.string.enter_identifier_hint)) {
        IdentifierTabs(authTab, ::selectTab)
        Spacer(Modifier.height(12.dp))
        when (authTab) {
            AuthTab.Mobile -> {
                PhoneInputField(national, { national = it }, region, { region = it })
                Text(
                    t(R.string.auth_mobile_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand.Muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            AuthTab.Email -> {
                OutlinedTextField(
                    email,
                    { email = it },
                    Modifier.fillMaxWidth(),
                    label = { Text(t(R.string.email)) },
                    placeholder = { Text(t(R.string.email_placeholder_example)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                Text(
                    t(R.string.auth_email_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand.Muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        warn?.let {
            Text(it, color = Brand.Amber, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
        err?.let {
            Text(it, color = Brand.Error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(16.dp))
        GradientButton(
            t(R.string.continue_),
            Modifier.fillMaxWidth(),
            enabled = canContinue,
            loading = loading,
        ) {
            val id = when (authTab) {
                AuthTab.Mobile -> AuthIdentifier.mobile(mobileApi!!)
                AuthTab.Email -> AuthIdentifier.email(AuthValidators.normalizeEmail(email))
            }
            scope.launch {
                loading = true
                err = null
                warn = null
                runCatching { c.auth.checkStep(id) }.onSuccess { result ->
                    when (result) {
                        is AuthCheckResult.UseEmail -> {
                            warn = result.message
                            selectTab(AuthTab.Email)
                        }
                        is AuthCheckResult.Step -> when (result.step) {
                            "login" -> nav.nav.navigate(Routes.password(id, redirect))
                            "otp" -> nav.nav.navigate(Routes.otp(id, redirect))
                            // E1: register goes straight to Register (empty otp_code)
                            "register" -> nav.nav.navigate(Routes.register(id, "", redirect))
                            else -> err = unableContinue
                        }
                    }
                }.onFailure { err = it.message }
                loading = false
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            t(R.string.auth_terms_note),
            style = MaterialTheme.typography.bodySmall,
            color = Brand.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun IdentifierTabs(selected: AuthTab, onSelect: (AuthTab) -> Unit) {
    TabRow(selectedTabIndex = if (selected == AuthTab.Mobile) 0 else 1, containerColor = Color.Transparent) {
        Tab(selected = selected == AuthTab.Mobile, onClick = { onSelect(AuthTab.Mobile) }, text = { Text(t(R.string.tab_mobile)) })
        Tab(selected = selected == AuthTab.Email, onClick = { onSelect(AuthTab.Email) }, text = { Text(t(R.string.tab_email)) })
    }
}

/** E2 — password login. */
@Composable
fun PasswordScreen(idRaw: String, redirect: String) {
    val id = AuthIdentifier.decode(idRaw) ?: return
    val c = LocalContainer.current
    val nav = LocalNav.current
    var pw by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var warn by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val canSubmit = id.display.isNotBlank() && pw.isNotBlank()
    val useEmailMsg = t(R.string.use_email_intl_sms)
    val emailRequired = t(R.string.email_required)
    val mobileMissing = t(R.string.mobile_missing)

    AuthScaffold(t(R.string.sign_in), id.display) {
        OutlinedTextField(
            id.display,
            {},
            Modifier.fillMaxWidth(),
            enabled = false,
            label = {
                Text(if (id.isEmail) t(R.string.email) else t(R.string.mobile_number))
            },
            shape = RoundedCornerShape(14.dp),
        )
        OutlinedTextField(
            pw,
            { pw = it },
            Modifier.fillMaxWidth(),
            label = { Text(t(R.string.password)) },
            singleLine = true,
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton({ show = !show }) {
                    Icon(if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                }
            },
            shape = RoundedCornerShape(14.dp),
        )
        warn?.let { Text(it, color = Brand.Amber, style = MaterialTheme.typography.bodySmall) }
        err?.let { Text(it, color = Brand.Error, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(16.dp))
        GradientButton(t(R.string.sign_in), Modifier.fillMaxWidth(), enabled = canSubmit, loading = loading) {
            if (id.isEmail && id.email.isNullOrBlank()) {
                err = emailRequired
                return@GradientButton
            }
            if (id.isMobile && id.mobile.isNullOrBlank()) {
                err = mobileMissing
                return@GradientButton
            }
            scope.launch {
                loading = true
                err = null
                warn = null
                runCatching { c.auth.login(id, pw) }.onSuccess { r ->
                    if (c.auth.isUseEmail(r)) {
                        warn = r.message?.takeIf { it.isNotBlank() } ?: useEmailMsg
                    } else {
                        c.auth.bindPushToken()
                        nav.finishAuth(redirect)
                    }
                }.onFailure { err = it.message }
                loading = false
            }
        }
        TextButton({ nav.nav.navigate(Routes.otp(id, redirect)) }, Modifier.fillMaxWidth()) {
            Text(t(R.string.use_otp_instead))
        }
        // E5: forgot is BD-mobile only — never pass an email into it
        if (id.isMobile && AuthValidators.isBdMobile(id.mobile.orEmpty())) {
            TextButton({ nav.nav.navigate(Routes.forgot(id.mobile!!)) }, Modifier.fillMaxWidth()) {
                Text(t(R.string.forgot_password))
            }
        }
    }
}

/** E3 — OTP not auto-sent; exactly 6 digits; 60s resend (master: Send enabled when identifier present). */
@Composable
fun OtpScreen(idRaw: String, redirect: String) {
    val id = AuthIdentifier.decode(idRaw) ?: return
    val c = LocalContainer.current
    val nav = LocalNav.current
    var code by remember { mutableStateOf("") }
    var seconds by remember { mutableIntStateOf(0) }
    var sending by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val otpSentDefault = t(R.string.otp_sent)
    val verificationComplete = t(R.string.verification_complete)
    val subtitle = if (id.isMobile) {
        t(R.string.otp_subtitle_mobile)
    } else {
        t(R.string.otp_subtitle_email, id.display)
    }

    LaunchedEffect(seconds) {
        if (seconds > 0) {
            delay(1000)
            seconds--
        }
    }

    fun sendOtp() {
        if (sending || seconds > 0 || id.display.isBlank()) return
        scope.launch {
            sending = true
            err = null
            runCatching { c.auth.sendOtp(id) }
                .onSuccess {
                    info = it.message ?: otpSentDefault
                    seconds = 60
                }
                .onFailure { err = it.message }
            sending = false
        }
    }

    AuthScaffold(t(R.string.otp_title), subtitle) {
        BasicTextField(
            code,
            { v -> code = v.filter(Char::isDigit).take(6) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            decorationBox = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    repeat(6) { i ->
                        val ch = code.getOrNull(i)?.toString() ?: ""
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(.85f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (i == code.length) Brand.P100 else Brand.P50)
                                .border(1.5.dp, if (i == code.length) Brand.Primary else Brand.Line, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(ch, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
        )
        info?.let { Text(it, color = Brand.Primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
        err?.let { Text(it, color = Brand.Error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(16.dp))
        GradientButton(
            t(R.string.send_otp),
            Modifier.fillMaxWidth(),
            enabled = seconds == 0 && !sending && id.display.isNotBlank(),
            loading = sending,
        ) { sendOtp() }
        Spacer(Modifier.height(8.dp))
        GradientButton(
            t(R.string.verify),
            Modifier.fillMaxWidth(),
            enabled = AuthValidators.isOtpComplete(code),
            loading = loading,
        ) {
            scope.launch {
                loading = true
                err = null
                runCatching { c.auth.verifyOtp(id, code, "register") }.onSuccess { r ->
                    val step = stepOf(r)
                    when {
                        c.session.isAuthenticated -> {
                            c.auth.bindPushToken()
                            nav.finishAuth(redirect)
                        }
                        step in listOf("register", "signup", "sign_up") ->
                            nav.nav.navigate(Routes.register(id, code, redirect))
                        step in listOf("login", "signin", "sign_in") ->
                            nav.nav.navigate(Routes.password(id, redirect))
                        step == "reset" && id.mobile != null ->
                            nav.nav.navigate(Routes.forgotReset(id.mobile!!))
                        else -> err = r.message ?: verificationComplete
                    }
                }.onFailure { err = it.message }
                loading = false
            }
        }
        TextButton(onClick = { sendOtp() }, Modifier.fillMaxWidth(), enabled = seconds == 0 && !sending && id.display.isNotBlank()) {
            Text(if (seconds > 0) t(R.string.resend_in, seconds) else t(R.string.resend))
        }
    }
}

private fun stepOf(r: JsonObject): String? =
    (r["step"] ?: (r["data"] as? JsonObject)?.get("step"))?.toString()?.trim('"')?.lowercase()

/** E4 — registration requirements per channel. */
@Composable
fun RegisterScreen(idRaw: String, otp: String, redirect: String) {
    val id = AuthIdentifier.decode(idRaw) ?: return
    val c = LocalContainer.current
    val nav = LocalNav.current
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(id.email.orEmpty()) }
    var region by rememberSaveable { mutableStateOf(PhoneNumbers.DEFAULT_REGION) }
    var national by rememberSaveable {
        mutableStateOf(
            id.mobile?.let { m ->
                if (AuthValidators.isBdMobile(m)) {
                    PhoneNumbers.formatAsYouType(AuthValidators.normalizeBdMobile(m), "BD")
                } else {
                    m.removePrefix("+").let { d ->
                        // Strip leading dial if we can match a region; otherwise keep digits
                        d.filter { it.isDigit() }
                    }
                }
            }.orEmpty(),
        )
    }
    var pw by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val lockedEmail = id.isEmail
    val lockedMobile = id.isMobile && !id.mobile.isNullOrBlank()
    val mobileApi = if (lockedMobile) id.mobile else AuthValidators.composeMobileForApi(region, national)
    val emailTrim = email.trim()
    val emailValid = if (lockedEmail) AuthValidators.isEmail(emailTrim) else (emailTrim.isEmpty() || AuthValidators.isEmail(emailTrim))
    val emailRequiredOk = if (lockedEmail) AuthValidators.isEmail(emailTrim) else true
    val mobileOk = mobileApi != null && AuthValidators.isIntlOrBdMobile(mobileApi)
    val nameOk = name.trim().isNotEmpty()
    val pwOk = AuthValidators.passwordOk(pw) && pw == confirm && confirm.isNotEmpty()
    val canSubmit = nameOk && mobileOk && emailRequiredOk && emailValid && pwOk
    val mobileMissing = t(R.string.mobile_missing)
    val emailRequired = t(R.string.email_required)
    val enterPwConfirm = t(R.string.enter_password_confirm)
    val pwMismatch = t(R.string.passwords_do_not_match)
    val pwMin8 = t(R.string.password_min_8)

    AuthScaffold(t(R.string.register), t(R.string.otp_verified_chip)) {
        OutlinedTextField(
            name,
            { name = it },
            Modifier.fillMaxWidth(),
            label = { Text(t(R.string.full_name)) },
            placeholder = { Text(t(R.string.name_hint_id)) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(8.dp))
        PhoneInputField(
            nationalNumber = national,
            onNationalNumberChange = { national = it },
            regionIso = region,
            onRegionChange = { region = it },
            enabled = !lockedMobile,
            hint = if (id.isEmail) "+8801XXXXXXXXX" else null,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            email,
            { if (!lockedEmail) email = it },
            Modifier.fillMaxWidth(),
            enabled = !lockedEmail,
            label = {
                Text(
                    if (lockedEmail) t(R.string.email)
                    else t(R.string.email_optional),
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            pw,
            { pw = it },
            Modifier.fillMaxWidth(),
            label = { Text(t(R.string.password)) },
            supportingText = { Text(t(R.string.password_min_8)) },
            singleLine = true,
            visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton({ showPw = !showPw }) {
                    Icon(if (showPw) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                }
            },
            shape = RoundedCornerShape(14.dp),
        )
        OutlinedTextField(
            confirm,
            { confirm = it },
            Modifier.fillMaxWidth(),
            label = { Text(t(R.string.confirm_password)) },
            singleLine = true,
            visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
            shape = RoundedCornerShape(14.dp),
        )
        err?.let { Text(it, color = Brand.Error, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(16.dp))
        GradientButton(t(R.string.register), Modifier.fillMaxWidth(), enabled = canSubmit, loading = loading) {
            if (mobileApi.isNullOrBlank()) {
                err = mobileMissing
                return@GradientButton
            }
            if (lockedEmail && !AuthValidators.isEmail(emailTrim)) {
                err = emailRequired
                return@GradientButton
            }
            if (pw.isEmpty() || confirm.isEmpty()) {
                err = enterPwConfirm
                return@GradientButton
            }
            if (pw != confirm) {
                err = pwMismatch
                return@GradientButton
            }
            if (!AuthValidators.passwordOk(pw)) {
                err = pwMin8
                return@GradientButton
            }
            scope.launch {
                loading = true
                err = null
                runCatching {
                    c.auth.register(
                        id = id,
                        name = name,
                        email = emailTrim.takeIf { it.isNotEmpty() }?.let(AuthValidators::normalizeEmail),
                        mobile = if (AuthValidators.isBdMobile(mobileApi)) AuthValidators.normalizeBdMobile(mobileApi) else mobileApi,
                        password = pw,
                        confirmPassword = confirm,
                        otp = otp,
                    )
                }.onSuccess {
                    c.auth.bindPushToken()
                    nav.finishAuth(redirect)
                }.onFailure { err = it.message }
                loading = false
            }
        }
    }
}

/** E5 — forgot start: BD mobile only. */
@Composable
fun ForgotStartScreen(mobilePrefill: String) {
    val c = LocalContainer.current
    val nav = LocalNav.current
    var national by rememberSaveable {
        mutableStateOf(
            mobilePrefill.takeIf { AuthValidators.isBdMobile(it) }?.let {
                PhoneNumbers.formatAsYouType(AuthValidators.normalizeBdMobile(it), "BD")
            }.orEmpty(),
        )
    }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val mobile = AuthValidators.composeMobileForApi("BD", national)
    val canSend = mobile != null && AuthValidators.isBdMobile(mobile)

    AuthScaffold(t(R.string.forgot_password), t(R.string.forgot_password_hint)) {
        // Force BD for forgot (E5)
        PhoneInputField(
            national,
            { national = it },
            "BD",
            { /* stay BD */ },
            hint = "01XXXXXXXXX",
        )
        err?.let { Text(it, color = Brand.Error, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(16.dp))
        GradientButton(t(R.string.send_otp), Modifier.fillMaxWidth(), enabled = canSend, loading = loading) {
            val m = AuthValidators.normalizeBdMobile(mobile!!)
            scope.launch {
                loading = true
                err = null
                runCatching { c.auth.forgotStart(m) }
                    .onSuccess { nav.nav.navigate(Routes.forgotOtp(m)) }
                    .onFailure { err = it.message }
                loading = false
            }
        }
    }
}

@Composable
fun ForgotOtpScreen(mobile: String) {
    val c = LocalContainer.current
    val nav = LocalNav.current
    var code by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val otpVerifiedMsg = t(R.string.otp_verified_chip)

    AuthScaffold(t(R.string.otp_title), t(R.string.otp_forgot_subtitle, mobile)) {
        BasicTextField(
            code,
            { v -> code = v.filter(Char::isDigit).take(6) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            decorationBox = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    repeat(6) { i ->
                        val ch = code.getOrNull(i)?.toString() ?: ""
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(.85f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (i == code.length) Brand.P100 else Brand.P50)
                                .border(1.5.dp, if (i == code.length) Brand.Primary else Brand.Line, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(ch, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
        )
        err?.let { Text(it, color = Brand.Error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(16.dp))
        GradientButton(
            t(R.string.verify),
            Modifier.fillMaxWidth(),
            enabled = AuthValidators.isOtpComplete(code),
            loading = loading,
        ) {
            scope.launch {
                loading = true
                err = null
                c.session.clear()
                runCatching { c.auth.forgotVerify(mobile, code) }
                    .onSuccess { r ->
                        if (r.success) nav.nav.navigate(Routes.forgotReset(mobile))
                        else err = r.message ?: otpVerifiedMsg
                    }
                    .onFailure { err = it.message }
                loading = false
            }
        }
    }
}

@Composable
fun ForgotResetScreen(mobile: String) {
    val c = LocalContainer.current
    val nav = LocalNav.current
    var pw by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val canSubmit = AuthValidators.passwordOk(pw) && pw == confirm
    val enterNew = t(R.string.enter_new_password)
    val pwMismatch = t(R.string.passwords_do_not_match)

    AuthScaffold(t(R.string.reset_password), mobile) {
        OutlinedTextField(
            pw,
            { pw = it },
            Modifier.fillMaxWidth(),
            label = { Text(t(R.string.new_password)) },
            supportingText = { Text(t(R.string.password_min_8)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(14.dp),
        )
        OutlinedTextField(
            confirm,
            { confirm = it },
            Modifier.fillMaxWidth(),
            label = { Text(t(R.string.confirm_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(14.dp),
        )
        err?.let { Text(it, color = Brand.Error, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(16.dp))
        GradientButton(t(R.string.reset_password), Modifier.fillMaxWidth(), enabled = canSubmit, loading = loading) {
            if (pw.isEmpty() || confirm.isEmpty()) {
                err = enterNew
                return@GradientButton
            }
            if (pw != confirm) {
                err = pwMismatch
                return@GradientButton
            }
            scope.launch {
                loading = true
                err = null
                runCatching { c.auth.forgotReset(mobile, pw, confirm) }
                    .onSuccess {
                        val id = AuthIdentifier.mobile(mobile)
                        nav.nav.navigate(Routes.password(id, "")) {
                            popUpTo(0) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                    .onFailure { err = it.message }
                loading = false
            }
        }
    }
}
