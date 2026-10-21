plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}
// google-services is applied only when app/google-services.json exists (it is copied from the Flutter repo).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

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
        // Master Flutter uses --dart-define / .env.example (no secrets in repo long-term).
        buildConfigField("String", "BASE_URL", "\"https://dev-apigw.durpalla.com/api/v1/\"")
        buildConfigField("String", "AUTH_BASE_URL", "\"\"") // empty → same as BASE_URL
        buildConfigField("String", "ASSETS_BASE_URL", "\"https://dev-assets.durpalla.com\"")
        buildConfigField("String", "PUSHER_KEY", "\"aed7edc1e0e3afd30d65\"") // TODO: move to local.properties
        buildConfigField("String", "PUSHER_CLUSTER", "\"ap2\"")
        buildConfigField("String", "RECAPTCHA_SITE_KEY", "\"6Le3VUYsAAAAADJfnKuZ6aWKhvkf4KJJ1dsnHOKT\"")
        buildConfigField("String", "RECAPTCHA_ORIGIN", "\"https://durpalla.com\"")
        buildConfigField("String", "GHAT_SUGGEST_API_PATH", "\"trip/ghat-suggestions\"")
        // In-app driver calling (WebRTC). Mock until backend + Firestore contract is live.
        // TODO(calling): see Durpalla/durpalla-driver docs/calling-contract.md
        buildConfigField("boolean", "USE_MOCK_CALLS", "true")
        buildConfigField("String", "WEBRTC_STUN_URLS", "\"stun:stun.l.google.com:19302,stun:stun1.l.google.com:19302\"")
        buildConfigField("String", "WEBRTC_TURN_URLS", "\"\"") // TURN creds from backend later
        buildConfigField("String", "WEBRTC_TURN_USERNAME", "\"\"")
        buildConfigField("String", "WEBRTC_TURN_CREDENTIAL", "\"\"")
    }

    buildTypes {
        debug {
            // Single ABI keeps debug APK under artifact size limits (Agora+WebRTC .so bloat).
            ndk { abiFilters += listOf("arm64-v8a") }
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "BASE_URL", "\"https://apigw.durpalla.com/api/v1/\"")
            buildConfigField("boolean", "USE_MOCK_CALLS", "false")
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
