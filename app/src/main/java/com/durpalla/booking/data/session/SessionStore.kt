package com.durpalla.booking.data.session

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.durpalla.booking.core.LooseJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

private val Context.dataStore by preferencesDataStore(name = "durpalla_session")

/**
 * Port of Flutter Session + LocaleProvider prefs:
 * - `app_locale` (migrated from legacy `lang` / `locale_chosen`)
 * - `language_onboarding_done`
 * - token (secure), auth_user, device_id, dark, x_guest_id
 */
class SessionStore(private val context: Context, private val scope: CoroutineScope) {
    private val kToken = stringPreferencesKey("token")
    private val kUser = stringPreferencesKey("auth_user")
    private val kDeviceId = stringPreferencesKey("device_id")
    /** Legacy key — migrated to [kAppLocale]. */
    private val kLangLegacy = stringPreferencesKey("lang")
    private val kAppLocale = stringPreferencesKey("app_locale")
    private val kOnboardingDone = booleanPreferencesKey("language_onboarding_done")
    private val kLocaleChosenLegacy = booleanPreferencesKey("locale_chosen")
    private val kDark = booleanPreferencesKey("dark")
    private val kGuestId = stringPreferencesKey("x_guest_id")

    private val securePrefs: SharedPreferences? = runCatching {
        val master = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            "durpalla_secure",
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrNull()

    @Volatile var token: String? = null
        private set

    @Volatile var guestId: String? = null
        private set

    @Volatile var contentLanguage: String = "en"
        private set

    private val _user = MutableStateFlow<JsonObject?>(null)
    val user: StateFlow<JsonObject?> = _user
    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn

    val forcedLogout = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val language: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[kAppLocale] ?: prefs[kLangLegacy] ?: "en"
    }
    val languageOnboardingDone: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[kOnboardingDone] == true || prefs[kLocaleChosenLegacy] == true
    }
    val darkMode: Flow<Boolean> = context.dataStore.data.map { it[kDark] ?: false }

    init {
        runBlocking { restore() }
        scope.launch { language.collect { contentLanguage = it } }
    }

    suspend fun restore() {
        val p = context.dataStore.data.first()
        val secureToken = securePrefs?.getString("token", null)
        val dsToken = p[kToken]
        val t = secureToken ?: dsToken
        setToken(t)
        if (secureToken == null && !dsToken.isNullOrEmpty()) {
            securePrefs?.edit()?.putString("token", dsToken)?.apply()
            context.dataStore.edit { it.remove(kToken) }
        }
        guestId = p[kGuestId] ?: securePrefs?.getString("x_guest_id", null)
        // Migrate lang → app_locale
        val locale = p[kAppLocale] ?: p[kLangLegacy] ?: "en"
        if (p[kAppLocale] == null && p[kLangLegacy] != null) {
            context.dataStore.edit {
                it[kAppLocale] = locale
                it.remove(kLangLegacy)
            }
        }
        contentLanguage = locale
        _user.value = p[kUser]?.let { runCatching { LooseJson.parseToJsonElement(it) as JsonObject }.getOrNull() }
    }

    private fun setToken(t: String?) {
        token = t?.takeIf { it.isNotEmpty() }
        _loggedIn.value = token != null
    }

    val isAuthenticated get() = token != null

    suspend fun saveLogin(token: String, user: JsonObject?) {
        securePrefs?.edit()?.putString("token", token)?.apply()
        context.dataStore.edit { p ->
            p.remove(kToken)
            if (user != null) p[kUser] = user.toString()
        }
        setToken(token)
        if (user != null) _user.value = user
    }

    suspend fun saveUser(user: JsonObject) {
        context.dataStore.edit { it[kUser] = user.toString() }
        _user.value = user
    }

    suspend fun saveGuestId(id: String) {
        if (id.isBlank() || id == guestId) return
        guestId = id
        securePrefs?.edit()?.putString("x_guest_id", id)?.apply()
        context.dataStore.edit { it[kGuestId] = id }
    }

    suspend fun clear() {
        securePrefs?.edit()?.remove("token")?.apply()
        context.dataStore.edit { it.remove(kToken); it.remove(kUser) }
        setToken(null)
        _user.value = null
    }

    fun forceLogout() {
        if (!isAuthenticated) return
        scope.launch { clear(); forcedLogout.emit(Unit) }
    }

    suspend fun deviceId(): String? = context.dataStore.data.first()[kDeviceId]
    suspend fun saveDeviceId(id: String) { context.dataStore.edit { it[kDeviceId] = id } }

    /** Persist `app_locale` and keep Content-Language in sync. */
    suspend fun setLanguage(l: String) {
        contentLanguage = l
        context.dataStore.edit {
            it[kAppLocale] = l
            it.remove(kLangLegacy)
        }
    }

    suspend fun setLanguageOnboardingDone(done: Boolean = true) {
        context.dataStore.edit {
            it[kOnboardingDone] = done
            it[kLocaleChosenLegacy] = done
        }
    }

    suspend fun setDark(d: Boolean) { context.dataStore.edit { it[kDark] = d } }
}
