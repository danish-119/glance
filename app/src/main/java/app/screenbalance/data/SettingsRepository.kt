package app.screenbalance.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.screenbalance.engine.PolicyEngine
import app.screenbalance.model.AppSettings
import app.screenbalance.model.FocusSession
import app.screenbalance.model.RuntimeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import java.time.ZoneId

private val Context.store: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Thrown when a settings change is attempted during a focus session or quiet hours. */
class SettingsLockedException(val until: Long?) : IllegalStateException("Settings are locked until $until")

/**
 * Settings and runtime state, stored as JSON in app-private DataStore (sandboxed, excluded
 * from backups). No secrets are stored, so platform sandboxing is the right protection level;
 * the focus-session / quiet-hours lock is enforced here as well as in the UI.
 */
class SettingsRepository(
    context: Context,
    scope: CoroutineScope,
    private val policy: PolicyEngine,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val store = context.applicationContext.store
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }

    /** null until the first read from disk completes, so consumers never act on defaults. */
    val settings: StateFlow<AppSettings?> = store.data
        .map { decode(it[KEY_SETTINGS], AppSettings.serializer()) ?: AppSettings() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val runtime: StateFlow<RuntimeState?> = store.data
        .map { decode(it[KEY_RUNTIME], RuntimeState.serializer()) ?: RuntimeState() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun current(): AppSettings = settings.value ?: settings.first { it != null }!!
    suspend fun currentRuntime(): RuntimeState = runtime.value ?: runtime.first { it != null }!!

    fun isLocked(s: AppSettings, r: RuntimeState): Boolean =
        policy.effective(s, r, clock(), ZoneId.systemDefault()).locked

    /**
     * Updates settings. While strict mode applies (the policy is locked), a change that loosens anything
     * ([AppSettings.loosens]) throws [SettingsLockedException] instead, unless [allowWhenLocked];
     * changes that keep or tighten the rules always go through.
     */
    suspend fun updateSettings(allowWhenLocked: Boolean = false, transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val s = decode(prefs[KEY_SETTINGS], AppSettings.serializer()) ?: AppSettings()
            val r = decode(prefs[KEY_RUNTIME], RuntimeState.serializer()) ?: RuntimeState()
            val next = transform(s)
            if (!allowWhenLocked && isLocked(s, r) && s.loosens(next)) {
                throw SettingsLockedException(policy.effective(s, r, clock(), ZoneId.systemDefault()).until)
            }
            prefs[KEY_SETTINGS] = json.encodeToString(AppSettings.serializer(), next)
        }
    }

    suspend fun updateRuntime(transform: (RuntimeState) -> RuntimeState) {
        store.edit { prefs ->
            val r = decode(prefs[KEY_RUNTIME], RuntimeState.serializer()) ?: RuntimeState()
            prefs[KEY_RUNTIME] = json.encodeToString(RuntimeState.serializer(), prune(transform(r)))
        }
    }

    /**
     * Emergency access: every app opens for [RuntimeState.EMERGENCY_MINUTES] minutes, whatever is on,
     * up to [RuntimeState.EMERGENCY_PER_DAY] times a day. False when none are left today.
     */
    suspend fun startEmergency(): Boolean {
        val now = clock()
        val today = java.time.LocalDate.now().toString()
        var ok = false
        updateRuntime { r ->
            val used = if (r.emergencyDay == today) r.emergencyUsed else 0
            if (used >= RuntimeState.EMERGENCY_PER_DAY) r
            else {
                ok = true
                r.copy(emergencyUntilMs = now + RuntimeState.EMERGENCY_MINUTES * 60_000L, emergencyDay = today, emergencyUsed = used + 1)
            }
        }
        return ok
    }

    suspend fun startFocusSession(minutes: Int) {
        val now = clock()
        updateRuntime { it.copy(session = FocusSession(now, now + minutes.coerceIn(1, 12 * 60) * 60_000L)) }
    }

    /** Ending early is only possible when the session is not locked. */
    suspend fun endFocusSession() {
        val s = current()
        val r = currentRuntime()
        if (isLocked(s, r) && r.session != null) throw SettingsLockedException(r.session.endsAtMs)
        updateRuntime { it.copy(session = null) }
    }


    private fun prune(r: RuntimeState): RuntimeState {
        val now = clock()
        return r.copy(
            session = r.session?.takeIf { it.endsAtMs > now },
        )
    }

    private fun <T> decode(raw: String?, serializer: kotlinx.serialization.KSerializer<T>): T? {
        if (raw.isNullOrEmpty()) return null
        return try {
            json.decodeFromString(serializer, raw)
        } catch (e: Exception) {
            // Corrupt or incompatible data: fall back to defaults rather than crash the service.
            Log.w(TAG, "Could not decode stored state; using defaults", e)
            null
        }
    }

    private companion object {
        const val TAG = "SettingsRepository"
        val KEY_SETTINGS = stringPreferencesKey("settings_v1")
        val KEY_RUNTIME = stringPreferencesKey("runtime_v1")
    }
}
