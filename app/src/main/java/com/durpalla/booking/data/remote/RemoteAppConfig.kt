package com.durpalla.booking.data.remote

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.tasks.await

/**
 * Master Firebase Remote Config keys (UI only — never for auth).
 * Defaults match `models/remote_app_config.dart`.
 */
object RemoteAppConfig {
    private val defaults = mapOf(
        "maintenance_mode" to false,
        "maintenance_message" to "",
        "booking_enabled" to true,
        "agent_booking_enabled" to true,
        "merchant_booking_enabled" to true,
        "minimum_app_version" to "1.0.0",
        "recommended_app_version" to "1.0.0",
        "payment_bkash_enabled" to true,
        "payment_nagad_enabled" to true,
        "payment_sslcommerz_enabled" to true,
        "show_new_checkout" to true,
        "show_new_home_banner" to true,
        "booking_hold_minutes" to 15L,
        "support_phone" to "",
        "support_whatsapp" to "",
    )

    suspend fun fetch(): FirebaseRemoteConfig? = runCatching {
        val rc = FirebaseRemoteConfig.getInstance()
        rc.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(3600).build(),
        ).await()
        rc.setDefaultsAsync(defaults).await()
        rc.fetchAndActivate().await()
        rc
    }.getOrNull()

    fun paymentEnabled(rc: FirebaseRemoteConfig?, gatewayName: String): Boolean {
        if (rc == null) return true
        val n = gatewayName.lowercase()
        return when {
            n.contains("bkash") -> rc.getBoolean("payment_bkash_enabled")
            n.contains("nagad") -> rc.getBoolean("payment_nagad_enabled")
            n.contains("ssl") || n.contains("commerz") -> rc.getBoolean("payment_sslcommerz_enabled")
            else -> true // unknown gateways stay visible
        }
    }

    fun maintenanceMode(rc: FirebaseRemoteConfig?) = rc?.getBoolean("maintenance_mode") == true
    fun maintenanceMessage(rc: FirebaseRemoteConfig?) = rc?.getString("maintenance_message").orEmpty()
    fun holdMinutes(rc: FirebaseRemoteConfig?) = rc?.getLong("booking_hold_minutes")?.takeIf { it > 0 } ?: 15L
}
