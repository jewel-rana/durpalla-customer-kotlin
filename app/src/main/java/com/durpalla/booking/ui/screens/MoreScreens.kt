package com.durpalla.booking.ui.screens

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.durpalla.booking.R
import com.durpalla.booking.core.*
import com.durpalla.booking.l10n.t
import com.durpalla.booking.ui.components.*
import com.durpalla.booking.ui.nav.LocalContainer
import com.durpalla.booking.ui.nav.LocalNav
import com.durpalla.booking.ui.nav.Routes
import com.durpalla.booking.ui.theme.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import kotlin.math.max

@Composable
fun OffersScreen() {
    val c = LocalContainer.current
    val vm = rememberLoad("offers") { c.home.offers() }
    val state by vm.state.collectAsState()
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxWidth().background(Brand.HeroGradient).statusBarsPadding().padding(20.dp)) {
            Text(t(R.string.offers), style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }
        when (val s = state) {
            Load.Loading -> LoadingList()
            is Load.Err -> ErrorState(s.message) { vm.reload() }
            is Load.Ok -> if (s.value.isEmpty()) EmptyState(Icons.Default.LocalOffer, t(R.string.no_offers), t(R.string.offers_check_back))
            else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(s.value) { o ->
                    SoftCard {
                        PhotoBox(o.image, R.drawable.photo_river, Modifier.fillMaxWidth().height(140.dp))
                        Column(Modifier.padding(16.dp)) {
                            Text(o.title, style = MaterialTheme.typography.titleMedium); Text(o.subtitle, color = Brand.Muted)
                            o.code?.let { code -> Spacer(Modifier.height(8.dp)); AssistChip({ clip.setText(androidx.compose.ui.text.AnnotatedString(code)) }, { Text(t(R.string.copy_code, code)) }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccountScreen() {
    val c = LocalContainer.current; val nav = LocalNav.current
    val loggedIn by c.session.loggedIn.collectAsState(); val user by c.session.user.collectAsState()
    val scope = rememberCoroutineScope()
    var confirmLogout by remember { mutableStateOf(false) }
    LaunchedEffect(loggedIn) { if (loggedIn) runCatching { c.profile.profile() } }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().background(Brand.HeroGradient).statusBarsPadding().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(.2f)), contentAlignment = Alignment.Center) {
                    val avatar = MediaUrl.resolve(user.s("avatar") ?: user.s("photo"))
                    if (loggedIn && avatar != null) PhotoBox(avatar, R.drawable.logo_mark_white, Modifier.matchParentSize())
                    else Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (loggedIn) user.str("name", default = t(R.string.traveller_cap)) else t(R.string.guest), style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(if (loggedIn) user.str("mobile", "phone", "email") else t(R.string.guest_banner), color = Color.White.copy(.8f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (!loggedIn) GradientButton(t(R.string.sign_in), Modifier.padding(16.dp).fillMaxWidth()) { nav.go(Routes.login(Routes.ACCOUNT)) }
        SoftCard(Modifier.padding(16.dp)) {
            MenuRow(Icons.Default.Edit, t(R.string.edit_profile)) { nav.go(Routes.PROFILE) }
            if (loggedIn) MenuRow(Icons.Default.LockReset, t(R.string.change_password)) { nav.go(Routes.CHANGE_PASSWORD) }
            MenuRow(Icons.Default.ConfirmationNumber, t(R.string.my_tickets)) { nav.tab(Routes.TICKETS) }
            MenuRow(Icons.Default.DirectionsBoat, t(R.string.my_trip_requests)) { nav.go(Routes.BOAT_MY_REQUESTS) }
            MenuRow(Icons.Default.Favorite, t(R.string.saved)) { nav.go(Routes.SAVED) }
            MenuRow(Icons.Default.Notifications, t(R.string.notifications)) { nav.go(Routes.NOTIFICATIONS) }
        }
        SoftCard(Modifier.padding(horizontal = 16.dp)) {
            MenuRow(Icons.Default.Settings, t(R.string.settings)) { nav.go(Routes.SETTINGS) }
            MenuRow(Icons.Default.SupportAgent, t(R.string.support)) { nav.go(Routes.SUPPORT) }
            MenuRow(Icons.Default.HelpOutline, t(R.string.faq)) { nav.go(Routes.FAQ) }
            MenuRow(Icons.Default.Info, t(R.string.about)) { nav.go(Routes.ABOUT) }
            MenuRow(Icons.Default.Policy, t(R.string.terms)) { nav.go(Routes.TERMS) }
        }
        if (loggedIn) SoftCard(Modifier.padding(16.dp)) { MenuRow(Icons.AutoMirrored.Filled.Logout, t(R.string.sign_out), Brand.Error) { confirmLogout = true } }
        Spacer(Modifier.height(24.dp))
    }
    if (confirmLogout) AlertDialog({ confirmLogout = false }, title = { Text(t(R.string.sign_out) + "?") },
        confirmButton = { Button({ confirmLogout = false; scope.launch { c.auth.logout() } }) { Text(t(R.string.sign_out)) } },
        dismissButton = { TextButton({ confirmLogout = false }) { Text(t(R.string.not_now)) } })
}

@Composable
fun MenuRow(icon: ImageVector, label: String, tint: Color = Brand.Primary, trailing: @Composable () -> Unit = { Icon(Icons.Default.ChevronRight, null, tint = Brand.Muted) }, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(.1f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
        Text(label, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
        trailing()
    }
}

@Composable
fun ProfileScreen() {
    val c = LocalContainer.current; val nav = LocalNav.current
    val ctx = LocalContext.current
    val user by c.session.user.collectAsState()
    var name by remember(user) { mutableStateOf(user.str("name")) }
    var email by remember(user) { mutableStateOf(user.str("email")) }
    var mobile by remember(user) { mutableStateOf(user.str("mobile", "phone")) }
    var croppedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var croppedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var sourceForCrop by remember { mutableStateOf<Bitmap?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    runCatching {
                        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    }.getOrNull()
                }
                if (bmp != null) sourceForCrop = bmp
            }
        }
    }

    Scaffold(
        topBar = { DTopBar(t(R.string.edit_profile), onBack = nav::back) },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth().imePadding(),
            ) {
                Box(Modifier.navigationBarsPadding().padding(16.dp)) {
                    GradientButton(t(R.string.save), Modifier.fillMaxWidth(), loading = loading) {
                        scope.launch {
                            loading = true
                            val part = croppedBytes?.let { bytes ->
                                MultipartBody.Part.createFormData(
                                    "avatar",
                                    "avatar.jpg",
                                    bytes.toRequestBody("image/jpeg".toMediaType()),
                                )
                            }
                            msg = runCatching {
                                c.profile.update(name.trim(), email.trim(), mobile.trim().ifBlank { null }, part).message
                                    ?: AppCtx.str(R.string.saved)
                            }.getOrElse { it.message }
                            loading = false
                        }
                    }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .dismissKeyboardOnTap()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(92.dp).clip(CircleShape).background(Brand.P100).clickable { pickImage.launch("image/*") },
                contentAlignment = Alignment.Center,
            ) {
                val avatarUrl = MediaUrl.resolve(user.s("avatar") ?: user.s("photo"))
                when {
                    croppedBitmap != null -> Image(croppedBitmap!!.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    avatarUrl != null -> PhotoBox(avatarUrl, R.drawable.logo_mark, Modifier.matchParentSize())
                    else -> Icon(Icons.Default.CameraAlt, null, tint = Brand.Primary, modifier = Modifier.size(34.dp))
                }
            }
            TextButton(onClick = { pickImage.launch("image/*") }) {
                Text(t(R.string.change_photo))
            }
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(t(R.string.full_name)) })
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text(t(R.string.email)) })
            OutlinedTextField(mobile, { mobile = it }, Modifier.fillMaxWidth(), label = { Text(t(R.string.mobile_number)) })
            msg?.let { Text(it, color = Brand.Primary) }
            Spacer(Modifier.height(16.dp))
        }
    }

    sourceForCrop?.let { src ->
        AvatarCropDialog(
            source = src,
            onDismiss = { sourceForCrop = null },
            onCropped = { bmp, jpegBytes ->
                croppedBitmap = bmp
                croppedBytes = jpegBytes
                sourceForCrop = null
            },
        )
    }
}

/**
 * Circular drag + zoom crop dialog exporting a 100x100 JPEG (mirrors Flutter `avatar_crop_page.dart` & Web crop UX).
 */
@Composable
fun AvatarCropDialog(
    source: Bitmap,
    onDismiss: () -> Unit,
    onCropped: (Bitmap, ByteArray) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t(R.string.crop_photo)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t(R.string.crop_drag_hint), style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
                Box(
                    Modifier
                        .size(220.dp)
                        .clip(CircleShape)
                        .background(Brand.Navy)
                        .border(2.dp, Brand.Amber, CircleShape)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 4f)
                                offset += pan
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = source.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y,
                            ),
                    )
                }
                Text(t(R.string.crop_zoom_hint), style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
                Slider(value = scale, onValueChange = { scale = it }, valueRange = 1f..4f)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val outSize = 100
                    val outBmp = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(outBmp)
                    val baseScale = max(outSize.toFloat() / source.width, outSize.toFloat() / source.height) * scale
                    val dx = (outSize - source.width * baseScale) / 2f + (offset.x / 220f) * outSize
                    val dy = (outSize - source.height * baseScale) / 2f + (offset.y / 220f) * outSize
                    val matrix = Matrix().apply {
                        postScale(baseScale, baseScale)
                        postTranslate(dx, dy)
                    }
                    canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
                    val baos = ByteArrayOutputStream()
                    outBmp.compress(Bitmap.CompressFormat.JPEG, 88, baos)
                    onCropped(outBmp, baos.toByteArray())
                },
            ) {
                Text(t(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t(R.string.not_now)) }
        },
    )
}

@Composable
fun ChangePasswordScreen() {
    val c = LocalContainer.current
    val nav = LocalNav.current
    var currentPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var showCurrentPass by remember { mutableStateOf(false) }
    var showNewPass by remember { mutableStateOf(false) }
    var showConfirmPass by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val mismatchMsg = t(R.string.passwords_do_not_match)
    val successMsg = t(R.string.password_changed)
    val minLenMsg = t(R.string.password_min_8)

    Scaffold(
        topBar = { DTopBar(t(R.string.change_password), onBack = nav::back) },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth().imePadding(),
            ) {
                Box(Modifier.navigationBarsPadding().padding(16.dp)) {
                    GradientButton(
                        t(R.string.save),
                        Modifier.fillMaxWidth(),
                        enabled = currentPass.isNotBlank() && newPass.length >= 6 && confirmPass.isNotBlank(),
                        loading = loading,
                    ) {
                        if (newPass != confirmPass) {
                            isError = true
                            msg = mismatchMsg
                            return@GradientButton
                        }
                        if (newPass.length < 6) {
                            isError = true
                            msg = minLenMsg
                            return@GradientButton
                        }
                        scope.launch {
                            loading = true
                            runCatching {
                                c.profile.changePassword(currentPass, newPass, confirmPass)
                            }.onSuccess { r ->
                                isError = false
                                msg = r.message ?: successMsg
                                currentPass = ""; newPass = ""; confirmPass = ""
                            }.onFailure {
                                isError = true
                                msg = it.message
                            }
                            loading = false
                        }
                    }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .dismissKeyboardOnTap()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                currentPass,
                { currentPass = it },
                Modifier.fillMaxWidth(),
                label = { Text(t(R.string.current_password)) },
                visualTransformation = if (showCurrentPass) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton({ showCurrentPass = !showCurrentPass }) {
                        Icon(
                            if (showCurrentPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showCurrentPass) "Hide password" else "Show password",
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(
                newPass,
                { newPass = it },
                Modifier.fillMaxWidth(),
                label = { Text(t(R.string.new_password)) },
                supportingText = { Text(t(R.string.password_min_8)) },
                visualTransformation = if (showNewPass) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton({ showNewPass = !showNewPass }) {
                        Icon(
                            if (showNewPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showNewPass) "Hide password" else "Show password",
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(
                confirmPass,
                { confirmPass = it },
                Modifier.fillMaxWidth(),
                label = { Text(t(R.string.confirm_password)) },
                visualTransformation = if (showConfirmPass) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton({ showConfirmPass = !showConfirmPass }) {
                        Icon(
                            if (showConfirmPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showConfirmPass) "Hide password" else "Show password",
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            msg?.let { Text(it, color = if (isError) Brand.Error else Brand.Success) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun SavedScreen() {
    val c = LocalContainer.current; val nav = LocalNav.current
    val vm = rememberLoad("saved") { c.hotels.favourites() }
    val state by vm.state.collectAsState()
    Scaffold(topBar = { DTopBar(t(R.string.saved), onBack = nav::back) }) { pad ->
        Box(Modifier.padding(pad)) {
            when (val s = state) {
                Load.Loading -> LoadingList()
                is Load.Err -> ErrorState(s.message) { vm.reload() }
                is Load.Ok -> if (s.value.isEmpty()) EmptyState(Icons.Default.FavoriteBorder, t(R.string.nothing_saved), t(R.string.nothing_saved_body))
                else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.value) { h -> HotelCard(h, Modifier.fillMaxWidth()) { nav.go(Routes.hotel(h.id, todayIso(1), todayIso(2))) } }
                }
            }
        }
    }
}

private fun openMessengerSupport(context: Context, url: String, unavailable: String, unlockRequired: String) {
    fun openLink() {
        if (!launchMessengerSupport(context, url)) {
            Toast.makeText(context, unavailable, Toast.LENGTH_LONG).show()
        }
    }

    val keyguard = context.getSystemService(KeyguardManager::class.java)
    if (keyguard?.isKeyguardLocked != true) {
        openLink()
        return
    }

    val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>()
        .firstOrNull()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
        keyguard.requestDismissKeyguard(activity, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = openLink()

            override fun onDismissError() {
                Toast.makeText(context, unlockRequired, Toast.LENGTH_LONG).show()
            }
        })
    } else {
        Toast.makeText(context, unlockRequired, Toast.LENGTH_LONG).show()
    }
}

private fun launchMessengerSupport(context: Context, url: String): Boolean {
    val link = Uri.parse(url.trim())
    if (!link.scheme.equals("https", ignoreCase = true) || link.host.isNullOrBlank()) return false

    val pageId = link.pathSegments.singleOrNull()?.takeIf { it.all { ch -> ch in '0'..'9' } }
    val browserLink = if (link.host.equals("m.me", ignoreCase = true) && pageId != null) {
        Uri.parse("https://www.messenger.com/t/$pageId").buildUpon()
            .encodedQuery(link.encodedQuery)
            .build()
    } else link

    val browserProbe = Intent(Intent.ACTION_VIEW, Uri.parse("https://browser-check.invalid"))
        .addCategory(Intent.CATEGORY_BROWSABLE)
    val packageManager = context.packageManager
    val browsers = packageManager.queryIntentActivities(browserProbe, PackageManager.MATCH_DEFAULT_ONLY)
    val defaultBrowser = packageManager.resolveActivity(browserProbe, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    val browserPackages = browsers.map { it.activityInfo.packageName }.distinct()
    val browser = defaultBrowser?.takeIf { it in browserPackages } ?: browserPackages.firstOrNull()
    if (browser != null) {
        val intent = Intent(Intent.ACTION_VIEW, browserLink)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(browser)
        if (runCatching { context.startActivity(intent) }.isSuccess) return true
    }

    return runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, link).addCategory(Intent.CATEGORY_BROWSABLE))
    }.isSuccess
}

enum class InfoKind { SUPPORT, FAQ, ABOUT, TERMS, NOTIFICATIONS }

@Composable
fun InfoScreen(kind: InfoKind) {
    val c = LocalContainer.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val title = when (kind) {
        InfoKind.SUPPORT -> t(R.string.support)
        InfoKind.FAQ -> t(R.string.faq)
        InfoKind.ABOUT -> t(R.string.about)
        InfoKind.TERMS -> t(R.string.terms)
        InfoKind.NOTIFICATIONS -> t(R.string.notifications)
    }
    Scaffold(topBar = { DTopBar(title, onBack = nav::back) }) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (kind) {
                InfoKind.SUPPORT -> {
                    val vm = rememberLoad("support-contact") { c.home.siteInit() to c.home.appConfig() }
                    val state by vm.state.collectAsState()
                    val (siteInit, appCfg) = (state as? Load.Ok)?.value ?: (kotlinx.serialization.json.JsonObject(emptyMap()) to kotlinx.serialization.json.JsonObject(emptyMap()))
                    val contact = siteInit.o("contact") ?: appCfg.o("contact")
                    val options = siteInit.o("options")
                    val hotline = contact?.str("hotline", "phone")?.takeIf { it.isNotBlank() }
                        ?: options?.str("company_hotline")?.takeIf { it.isNotBlank() }
                        ?: "09644666777"
                    val tel = contact?.str("tel", "digits")?.takeIf { it.isNotBlank() } ?: hotline
                    val email = contact?.str("email")?.takeIf { it.isNotBlank() }
                        ?: options?.str("company_email")?.takeIf { it.isNotBlank() }
                        ?: "support@durpalla.com"
                    val whatsappUrl = contact?.o("chat")?.o("whatsapp")?.str("url")
                    val messengerUrl = contact?.o("chat")?.o("messenger")?.str("url")
                    val corpAddr = contact?.str("address")?.takeIf { it.isNotBlank() }
                        ?: options?.str("company_address")?.takeIf { it.isNotBlank() }
                    val regAddr = contact?.str("registered_address")?.takeIf { it.isNotBlank() }
                        ?: options?.str("company_registered_address")?.takeIf { it.isNotBlank() }

                    SoftCard {
                        MenuRow(Icons.Default.Call, hotline) {
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$tel"))) }
                        }
                        MenuRow(Icons.Default.Email, email) {
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))) }
                        }
                        if (!whatsappUrl.isNullOrBlank()) {
                            MenuRow(Icons.Default.Chat, t(R.string.whatsapp_support)) {
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(whatsappUrl))) }
                            }
                        }
                        if (!messengerUrl.isNullOrBlank()) {
                            val unavailable = t(R.string.messenger_support_unavailable)
                            val unlockRequired = t(R.string.messenger_support_unlock_required)
                            MenuRow(Icons.Default.Forum, t(R.string.messenger_support)) {
                                openMessengerSupport(ctx, messengerUrl, unavailable, unlockRequired)
                            }
                        }
                        MenuRow(Icons.Default.HelpOutline, t(R.string.browse_faq)) { nav.go(Routes.FAQ) }
                    }
                    if (!corpAddr.isNullOrBlank() || !regAddr.isNullOrBlank()) {
                        SoftCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!corpAddr.isNullOrBlank()) {
                                    Text(t(R.string.corporate_address), fontWeight = FontWeight.SemiBold)
                                    Text(corpAddr, color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
                                }
                                if (!regAddr.isNullOrBlank()) {
                                    Text(t(R.string.registered_address), fontWeight = FontWeight.SemiBold)
                                    Text(regAddr, color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                InfoKind.FAQ -> {
                    val vm = rememberLoad("api-faq") { c.home.faq() }
                    val state by vm.state.collectAsState()
                    val fallbackFaqs = listOf(
                        t(R.string.faq_q_ticket) to t(R.string.faq_a_ticket),
                        t(R.string.faq_q_cancel) to t(R.string.faq_a_cancel),
                        t(R.string.faq_q_payment) to t(R.string.faq_a_payment),
                        t(R.string.faq_q_hold) to t(R.string.faq_a_hold),
                    )
                    val items = (state as? Load.Ok)?.value?.takeIf { it.isNotEmpty() } ?: fallbackFaqs
                    items.forEach { (q, a) ->
                        var open by remember { mutableStateOf(false) }
                        SoftCard(onClick = { open = !open }) {
                            Column(Modifier.padding(16.dp).animateContentSizeCompat()) {
                                Text(q, style = MaterialTheme.typography.titleSmall)
                                if (open) Text(a, color = Brand.Muted, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
                InfoKind.ABOUT -> {
                    val vm = rememberLoad("cms-about") { c.home.cmsPage("about-us") }
                    val state by vm.state.collectAsState()
                    val cms = (state as? Load.Ok)?.value
                    Image(painterResource(R.drawable.logo_stacked), null, Modifier.fillMaxWidth().height(140.dp))
                    Text(
                        cms?.second?.takeIf { it.isNotBlank() } ?: t(R.string.about_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(t(R.string.version_label, com.durpalla.booking.BuildConfig.VERSION_NAME), color = Brand.Muted)
                }
                InfoKind.TERMS -> {
                    var selectedSlug by remember { mutableStateOf("terms-and-conditions") }
                    val slugs = listOf(
                        "terms-and-conditions" to R.string.terms_conditions,
                        "privacy-policy" to R.string.privacy_policy,
                        "cancellation-and-refund-policy" to R.string.cancellation_refund_policy,
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        slugs.forEach { (slug, labelRes) ->
                            FilterChip(selectedSlug == slug, { selectedSlug = slug }, { Text(t(labelRes)) })
                        }
                    }
                    val vm = rememberLoad("cms-$selectedSlug") { c.home.cmsPage(selectedSlug) }
                    val state by vm.state.collectAsState()
                    when (val s = state) {
                        Load.Loading -> LoadingList(2)
                        else -> {
                            val cms = (s as? Load.Ok)?.value
                            SoftCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (!cms?.first.isNullOrBlank()) {
                                        Text(cms!!.first, style = MaterialTheme.typography.titleMedium)
                                    }
                                    Text(
                                        cms?.second?.takeIf { it.isNotBlank() } ?: t(R.string.terms_placeholder),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Brand.Ink,
                                    )
                                }
                            }
                        }
                    }
                }
                InfoKind.NOTIFICATIONS -> EmptyState(Icons.Default.NotificationsNone, t(R.string.notifications_caught_up), t(R.string.notifications_empty_body))
            }
        }
    }
}

fun Modifier.animateContentSizeCompat() = this.then(Modifier.animateContentSize())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val c = LocalContainer.current; val nav = LocalNav.current
    val dark by c.session.darkMode.collectAsState(false)
    val lang by c.session.language.collectAsState("en")
    val scope = rememberCoroutineScope()
    var showLangSheet by remember { mutableStateOf(false) }
    val localeLabel = com.durpalla.booking.l10n.SupportedLocales.find(lang).nativeName
    Scaffold(topBar = { DTopBar(t(R.string.settings), onBack = nav::back) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            SoftCard {
                MenuRow(Icons.Default.DarkMode, t(R.string.dark_mode), trailing = { Switch(dark, { v -> scope.launch { c.session.setDark(v) } }) }) { scope.launch { c.session.setDark(!dark) } }
                MenuRow(
                    Icons.Default.Translate,
                    t(R.string.language),
                    trailing = { Text(localeLabel, color = Brand.Primary) },
                ) { showLangSheet = true }
            }
        }
    }
    if (showLangSheet) {
        ModalBottomSheet(onDismissRequest = { showLangSheet = false }) {
            LanguagePickerSheet(onDismiss = { showLangSheet = false })
        }
    }
}

/** @deprecated Prefer [com.durpalla.booking.l10n.LocaleHelper.apply]. */
fun applyLocale(ctx: android.content.Context, lang: String) {
    com.durpalla.booking.l10n.LocaleHelper.apply(lang)
    if (android.os.Build.VERSION.SDK_INT < 33) (ctx as? Activity)?.recreate()
}
