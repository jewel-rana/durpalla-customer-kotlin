package com.durpalla.booking.l10n

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.durpalla.booking.core.*
import com.durpalla.booking.data.net.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

private val Context.l10nStore by preferencesDataStore(name = "durpalla_remote_l10n")

/**
 * Flutter `remote_localization_service.dart`:
 * `GET localizations/customer-app/{code}` → `data.translations` + `data.version`, cached, overlay.
 */
class RemoteLocalizationService(
    private val context: Context,
    private val api: ApiClient,
    private val scope: CoroutineScope,
) {
    private val _overlay = MutableStateFlow<Map<String, String>>(emptyMap())
    val overlay: StateFlow<Map<String, String>> = _overlay.asStateFlow()

    private val _version = MutableStateFlow<String?>(null)
    val version: StateFlow<String?> = _version.asStateFlow()

    private fun versionKey(code: String) = stringPreferencesKey("ver_$code")
    private fun bodyKey(code: String) = stringPreferencesKey("body_$code")

    fun ensureLoaded(code: String) {
        scope.launch { load(code) }
    }

    suspend fun load(code: String) {
        val normalized = code.lowercase()
        // Bundled en/bn: clear remote overlay (resources handle them).
        if (!SupportedLocales.isRemote(normalized)) {
            _overlay.value = emptyMap()
            _version.value = null
            return
        }
        // Serve cache immediately, then refresh.
        val cached = readCache(normalized)
        if (cached != null) {
            _overlay.value = cached.first
            _version.value = cached.second
        }
        runCatching {
            val r = api.get("localizations/customer-app/$normalized")
            val data = r.o("data") ?: r
            val translations = data.o("translations") ?: data.o("data")
            val version = data.s("version") ?: data.s("updated_at") ?: ""
            val map = parseTranslations(translations)
            if (map.isNotEmpty()) {
                writeCache(normalized, version, map)
                _overlay.value = map
                _version.value = version
            }
        }
    }

    private fun parseTranslations(obj: JsonObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        return obj.mapNotNull { (k, v) ->
            val s = (v as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            k to s
        }.toMap()
    }

    private suspend fun readCache(code: String): Pair<Map<String, String>, String>? {
        val p = context.l10nStore.data.first()
        val body = p[bodyKey(code)] ?: return null
        val ver = p[versionKey(code)] ?: ""
        val parsed = runCatching { LooseJson.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: return null
        return parseTranslations(parsed) to ver
    }

    private suspend fun writeCache(code: String, version: String, map: Map<String, String>) {
        val json = buildJsonObject { map.forEach { (k, v) -> put(k, v) } }.toString()
        context.l10nStore.edit {
            it[bodyKey(code)] = json
            it[versionKey(code)] = version
        }
    }

    /** Resolve a string resource entry name against the remote overlay. */
    fun resolve(entryName: String, fallback: String): String =
        _overlay.value[entryName] ?: fallback
}
