plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}
// google-services is applied only when app/google-services.json exists (copied from the Flutter repo).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

fun parseKeyValueText(content: String?): Map<String, String> =
    content?.lineSequence()?.mapNotNull { line ->
        val s = line.trim()
        if (s.isEmpty() || s.startsWith("#") || !s.contains("=")) null
        else {
            val k = s.substringBefore("=").trim()
            val v = s.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
            k to v
        }
    }?.toMap().orEmpty()

val envFileName = (project.findProperty("ENV_FILE") as? String)?.takeIf { it.isNotBlank() } ?: ".env"
val localPropsText = providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText.orNull
val envFileText = providers.fileContents(rootProject.layout.projectDirectory.file(envFileName)).asText.orNull
val dotEnv: Map<String, String> = parseKeyValueText(localPropsText) + parseKeyValueText(envFileText)

fun envVal(vararg keys: String, default: String = ""): String {
    for (k in keys) {
        val v = dotEnv[k] ?: System.getenv(k)
        if (!v.isNullOrBlank()) return v.trim()
    }
    return default
}

fun ensureTrailingSlash(url: String): String =
    if (url.isBlank()) "" else if (url.endsWith("/")) url else "$url/"

val apiBaseUrl = ensureTrailingSlash(envVal("API_BASE_URL", "BASE_URL", default = "https://apigw.durpalla.com/api/v1"))
val apiDnsOverride = envVal("API_DNS_OVERRIDE", default = "")
val apiAuthBaseUrl = ensureTrailingSlash(envVal("API_AUTH_BASE_URL", "AUTH_BASE_URL", default = ""))
val assetsBaseUrl = envVal("ASSETS_BASE_URL", default = "https://assets.durpalla.com").trimEnd('/')
val pusherKey = envVal("PUSHER_KEY", default = "")
val pusherCluster = envVal("PUSHER_CLUSTER", default = "ap2")
val recaptchaSiteKey = envVal("RECAPTCHA_SITE_KEY", default = "")
val recaptchaOrigin = envVal("RECAPTCHA_ORIGIN", default = "https://durpalla.com")
val ghatSuggestPath = envVal("GHAT_SUGGEST_API_PATH", default = "trip/ghat-suggestions")
val useMockCalls = envVal("USE_MOCK_CALLS", default = "false").toBooleanStrictOrNull() ?: false
val apiDebug = envVal("API_DEBUG", default = "false").toBooleanStrictOrNull() ?: false
val hotelApiDebug = envVal("HOTEL_API_DEBUG", default = "false").toBooleanStrictOrNull() ?: false
val webrtcStunUrls = envVal("WEBRTC_STUN_URLS", default = "stun:stun.l.google.com:19302,stun:stun1.l.google.com:19302")
val webrtcTurnUrls = envVal("WEBRTC_TURN_URLS", default = "")
val webrtcTurnUsername = envVal("WEBRTC_TURN_USERNAME", default = "")
val webrtcTurnCredential = envVal("WEBRTC_TURN_CREDENTIAL", default = "")

android {
    namespace = "com.durpalla.booking"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.durpalla.booking"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "2.0.0"
        vectorDrawables { useSupportLibrary = true }

        // All base URLs and credentials are loaded from .env at build time (copied from durpalla-flutter-app/.env).
        buildConfigField("String", "BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "API_DNS_OVERRIDE", "\"$apiDnsOverride\"")
        buildConfigField("String", "AUTH_BASE_URL", "\"$apiAuthBaseUrl\"")
        buildConfigField("String", "ASSETS_BASE_URL", "\"$assetsBaseUrl\"")
        buildConfigField("String", "PUSHER_KEY", "\"$pusherKey\"")
        buildConfigField("String", "PUSHER_CLUSTER", "\"$pusherCluster\"")
        buildConfigField("String", "RECAPTCHA_SITE_KEY", "\"$recaptchaSiteKey\"")
        buildConfigField("String", "RECAPTCHA_ORIGIN", "\"$recaptchaOrigin\"")
        buildConfigField("String", "GHAT_SUGGEST_API_PATH", "\"$ghatSuggestPath\"")
        buildConfigField("boolean", "USE_MOCK_CALLS", useMockCalls.toString())
        buildConfigField("boolean", "API_DEBUG", apiDebug.toString())
        buildConfigField("boolean", "HOTEL_API_DEBUG", hotelApiDebug.toString())
        buildConfigField("String", "WEBRTC_STUN_URLS", "\"$webrtcStunUrls\"")
        buildConfigField("String", "WEBRTC_TURN_URLS", "\"$webrtcTurnUrls\"")
        buildConfigField("String", "WEBRTC_TURN_USERNAME", "\"$webrtcTurnUsername\"")
        buildConfigField("String", "WEBRTC_TURN_CREDENTIAL", "\"$webrtcTurnCredential\"")
    }

    buildTypes {
        debug {
            // Single ABI keeps debug APK under artifact size limits (Agora+WebRTC .so bloat).
            ndk { abiFilters += listOf("arm64-v8a") }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.coil.compose)
    implementation(libs.zxing.core)
    implementation(libs.pusher)
    implementation(libs.libphonenumber)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.config)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.stream.webrtc)
    implementation(libs.agora.voice)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
}

tasks.register("installOnConnectedDevices") {
    group = "install"
    description = "Installs and launches the debug APK on all connected authorized adb devices."
    doLast {
        val apk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile
        if (!apk.exists()) {
            logger.lifecycle("installOnConnectedDevices: APK not found at ${apk.absolutePath}")
            return@doLast
        }
        val sdkDir = dotEnv["sdk.dir"] ?: System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
            ?: "${System.getProperty("user.home")}/Android/Sdk"
        val adbCandidate = File(sdkDir, "platform-tools/adb")
        val adbBin = if (adbCandidate.canExecute()) adbCandidate.absolutePath else "adb"
        val devicesOut = runCatching {
            val p = ProcessBuilder(adbBin, "devices").redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            out
        }.getOrNull().orEmpty()
        val serials = devicesOut.lineSequence().drop(1).mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size >= 2 && parts[1] == "device") parts[0] else null
        }.toList()
        if (serials.isEmpty()) {
            logger.lifecycle("==> No connected authorized adb devices found.")
            return@doLast
        }
        val pkg = "com.durpalla.booking"
        val act = "$pkg/.MainActivity"
        for (serial in serials) {
            logger.lifecycle("==> Installing ${apk.name} on $serial...")
            val code = ProcessBuilder(adbBin, "-s", serial, "install", "-r", "-d", apk.absolutePath)
                .inheritIO().start().waitFor()
            if (code != 0) {
                ProcessBuilder(adbBin, "-s", serial, "uninstall", pkg).start().waitFor()
                ProcessBuilder(adbBin, "-s", serial, "install", "-r", "-d", apk.absolutePath)
                    .inheritIO().start().waitFor()
            }
            ProcessBuilder(adbBin, "-s", serial, "shell", "am", "force-stop", pkg).start().waitFor()
            ProcessBuilder(adbBin, "-s", serial, "shell", "am", "start", "-n", act).start().waitFor()
            logger.lifecycle("==> Installed & launched $act on $serial")
        }
    }
}

afterEvaluate {
    val skipAuto = (project.findProperty("skipGradleAutoInstall") as? String)?.toBoolean() == true
    if (!skipAuto) {
        tasks.findByName("assembleDebug")?.finalizedBy("installOnConnectedDevices")
    }
}
