package com.durpalla.booking.data.session

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.durpalla.booking.core.LooseJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

private val Context.dataStore by preferencesDataStore(name = "durpalla_session")

/**
 * Port of Flutter `Session` + `ApiService._token`: keys `token` and `auth_user`.
 * The token is kept in memory for the OkHttp interceptor and persisted in DataStore.
 */
class SessionStore(private val context: Context, private val scope: CoroutineScope) {
    private val kToken = stringPreferencesKey("token")
    private val kUser = stringPreferencesKey("auth_user")
    private val kDeviceId = stringPreferencesKey("device_id")
    private val kLang = stringPreferencesKey("lang")
    private val kDark = booleanPreferencesKey("dark")

    @Volatile var token: String? = null
        private set

    private val _user = MutableStateFlow<JsonObject?>(null)
    val user: StateFlow<JsonObject?> = _user
    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn

    /** Emits when the server returned 401 for an authenticated call (Session.forceLogout). */
    val forcedLogout = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val language: Flow<String> = context.dataStore.data.map { it[kLang] ?: "en" }
    val darkMode: Flow<Boolean> = context.dataStore.data.map { it[kDark] ?: false }

    init {
        runBlocking { restore() }
    }

    suspend fun restore() {
        val p = context.dataStore.data.first()
        setToken(p[kToken])
        _user.value = p[kUser]?.let { runCatching { LooseJson.parseToJsonElement(it) as JsonObject }.getOrNull() }
    }

    private fun setToken(t: String?) {
        token = t?.takeIf { it.isNotEmpty() }
        _loggedIn.value = token != null
    }

    val isAuthenticated get() = token != null

    suspend fun saveLogin(token: String, user: JsonObject?) {
        context.dataStore.edit { p ->
            p[kToken] = token
            if (user != null) p[kUser] = user.toString()
        }
        setToken(token)
        if (user != null) _user.value = user
    }

    suspend fun saveUser(user: JsonObject) {
        context.dataStore.edit { it[kUser] = user.toString() }
        _user.value = user
    }

    suspend fun clear() {
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
    suspend fun setLanguage(l: String) { context.dataStore.edit { it[kLang] = l } }
    suspend fun setDark(d: Boolean) { context.dataStore.edit { it[kDark] = d } }
}
