package com.durpalla.booking.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.durpalla.booking.R
import com.durpalla.booking.core.message
import com.durpalla.booking.core.success
import com.durpalla.booking.ui.components.*
import com.durpalla.booking.ui.nav.LocalContainer
import com.durpalla.booking.ui.nav.LocalNav
import com.durpalla.booking.ui.nav.Routes
import com.durpalla.booking.ui.theme.Brand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun AuthScaffold(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    val nav = LocalNav.current
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().background(Brand.HeroGradient).statusBarsPadding().padding(20.dp, 8.dp, 20.dp, 56.dp)) {
            Column {
                IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
                Image(painterResource(R.drawable.logo_horizontal_white), null, Modifier.height(34.dp))
                Spacer(Modifier.height(16.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text(subtitle, color = Color.White.copy(.8f))
            }
        }
        SoftCard(Modifier.padding(horizontal = 16.dp).offset(y = (-32).dp)) { Column(Modifier.padding(20.dp), content = content) }
    }
}

/** Phone-first sign in (+880). Mirrors check_mobile_screen.dart → auth/check → login | otp | register. */
@Composable
fun LoginScreen(redirect: String) {
    val c = LocalContainer.current; val nav = LocalNav.current
    var phone by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }; var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val invalid = stringResource(R.string.invalid_mobile)
    AuthScaffold(stringResource(R.string.sign_in), stringResource(R.string.enter_mobile_hint)) {
        OutlinedTextField(phone, { v -> phone = v.filter(Char::isDigit).take(11) }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.mobile_number)) },
            prefix = { Text("+880 ", fontWeight = FontWeight.SemiBold) }, placeholder = { Text("01XXXXXXXXX") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, shape = RoundedCornerShape(14.dp))
        err?.let { Text(it, color = Brand.Error, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(16.dp))
        GradientButton(stringResource(R.string.continue_), Modifier.fillMaxWidth(), loading = loading) {
            val mobile = normalizeMobile(phone)
            if (mobile.length != 11 || !mobile.startsWith("01")) { err = invalid; return@GradientButton }
            scope.launch {
                loading = true; err = null
                runCatching { c.auth.checkStep(mobile) }.onSuccess { step ->
                    when (step) {
                        "login" -> nav.nav.navigate(Routes.password(mobile, redirect))
                        else -> nav.nav.navigate(Routes.otp(mobile, redirect))
                    }
                }.onFailure { err = it.message }
                loading = false
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("By continuing you agree to Durpalla's Terms & Privacy Policy.", style = MaterialTheme.typography.bodySmall, color = Brand.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

fun normalizeMobile(s: String): String { var d = s.filter(Char::isDigit); if (d.startsWith("880")) d = d.drop(2); if (d.length == 10 && d.startsWith("1")) d = "0$d"; return d }

@Composable
fun PasswordScreen(mobile: String, redirect: String) {
    val c = LocalContainer.current; val nav = LocalNav.current
    var pw by remember { mutableStateOf("") }; var show by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }; var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AuthScaffold(stringResource(R.string.sign_in), "+88 $mobile") {
        OutlinedTextField(pw, { pw = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.password)) }, singleLine = true,
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton({ show = !show }) { Icon(if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
            shape = RoundedCornerShape(14.dp))
        err?.let { Text(it, color = Brand.Error, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(16.dp))
        GradientButton(stringResource(R.string.sign_in), Modifier.fillMaxWidth(), enabled = pw.isNotBlank(), loading = loading) {
            scope.launch {
                loading = true; err = null
                runCatching { c.auth.login(mobile, pw) }.onSuccess { c.auth.bindPushToken(); nav.finishAuth(redirect) }.onFailure { err = it.message }
                loading = false
            }
        }
        TextButton({ nav.nav.navigate(Routes.otp(mobile, redirect)) }, Modifier.fillMaxWidth()) { Text("Sign in with OTP instead") }
    }
}

/** 6-box OTP with resend timer. auth/otp/send → auth/verify (type=register); step=register → registration. */
@Composable
fun OtpScreen(mobile: String, redirect: String) {
    val c = LocalContainer.current; val nav = LocalNav.current
    var code by remember { mutableStateOf("") }
    var seconds by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }; var err by remember { mutableStateOf<String?>(null) }
    var sendKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(sendKey) {
        val r = runCatching { c.auth.sendOtp(mobile) }
        r.onSuccess { if (!it.success) err = it.message }.onFailure { err = it.message }
        seconds = 60; while (seconds > 0) { delay(1000); seconds-- }
    }
    AuthScaffold(stringResource(R.string.otp_title), stringResource(R.string.otp_sent_to, "+88 $mobile")) {
        BasicTextField(code, { v -> code = v.filter(Char::isDigit).take(6) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            decorationBox = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    repeat(6) { i ->
                        val ch = code.getOrNull(i)?.toString() ?: ""
                        Box(Modifier.weight(1f).aspectRatio(.85f).clip(RoundedCornerShape(12.dp))
                            .background(if (i == code.length) Brand.P100 else Brand.P50)
                            .border(1.5.dp, if (i == code.length) Brand.Primary else Brand.Line, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Text(ch, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            })
        err?.let { Text(it, color = Brand.Error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(16.dp))
        GradientButton(stringResource(R.string.verify), Modifier.fillMaxWidth(), enabled = code.length >= 4, loading = loading) {
            scope.launch {
                loading = true; err = null
                runCatching { c.auth.verifyOtp(mobile, code, "register") }.onSuccess { r ->
                    val step = (r["step"] ?: (r["data"] as? kotlinx.serialization.json.JsonObject)?.get("step"))?.toString()?.trim('"')?.lowercase()
                    when {
                        c.session.isAuthenticated -> { c.auth.bindPushToken(); nav.finishAuth(redirect) }
                        step in listOf("register", "signup", "sign_up") || r.success -> nav.nav.navigate(Routes.register(mobile, code, redirect))
                        else -> err = r.message ?: "Invalid code"
                    }
                }.onFailure { err = it.message }
                loading = false
            }
        }
        TextButton({ sendKey++ }, Modifier.fillMaxWidth(), enabled = seconds == 0) {
            Text(if (seconds > 0) stringResource(R.string.resend_in, seconds) else stringResource(R.string.resend))
        }
    }
}

@Composable
fun RegisterScreen(mobile: String, otp: String, redirect: String) {
    val c = LocalContainer.current; val nav = LocalNav.current
    var name by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }; var pw by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }; var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AuthScaffold(stringResource(R.string.register), "+88 $mobile") {
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.full_name)) }, singleLine = true)
        OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.email)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(pw, { pw = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        err?.let { Text(it, color = Brand.Error, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(16.dp))
        GradientButton(stringResource(R.string.register), Modifier.fillMaxWidth(), enabled = name.isNotBlank() && pw.length >= 6, loading = loading) {
            scope.launch {
                loading = true; err = null
                runCatching { c.auth.register(name, email, mobile, pw, otp) }.onSuccess { c.auth.bindPushToken(); nav.finishAuth(redirect) }.onFailure { err = it.message }
                loading = false
            }
        }
    }
}
