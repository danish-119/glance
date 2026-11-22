package app.screenbalance.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import app.screenbalance.engine.WatchedApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-process channel between the accessibility service and the UI. Nothing here is persisted. */
object ServiceBridge {
    private val _connected = MutableStateFlow(false)
    /** True while the system has the service bound (it is enabled and running). */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** Live time Glance measured today in each watched app (UsageStats may report more). */
    private val _usedTodayMs = MutableStateFlow<Map<WatchedApp, Long>>(emptyMap())
    val usedTodayMs: StateFlow<Map<WatchedApp, Long>> = _usedTodayMs.asStateFlow()

    /** Live time today in each other app that has a daily limit, by package. */
    private val _packageUsedTodayMs = MutableStateFlow<Map<String, Long>>(emptyMap())
    val packageUsedTodayMs: StateFlow<Map<String, Long>> = _packageUsedTodayMs.asStateFlow()

    /** A call is ringing or under way in any app, going by its call notification (needs notification access). */
    private val _callNotified = MutableStateFlow(false)
    val callNotified: StateFlow<Boolean> = _callNotified.asStateFlow()
    internal fun setCallNotified(value: Boolean) { _callNotified.value = value }

    internal fun setConnected(value: Boolean) { _connected.value = value }
    internal fun setUsedToday(used: Map<WatchedApp, Long>, packages: Map<String, Long>) {
        _usedTodayMs.value = used
        _packageUsedTodayMs.value = packages
    }

    /** Live phone time today (screen on and unlocked, any app). */
    private val _phoneTodayMs = MutableStateFlow(0L)
    val phoneTodayMs: StateFlow<Long> = _phoneTodayMs.asStateFlow()
    private var phoneDay: java.time.LocalDate? = null
    internal fun setPhoneToday(ms: Long) {
        phoneDay = java.time.LocalDate.now()
        _phoneTodayMs.value = ms
    }
    /** [phoneTodayMs] if it was measured today; 0 when it is still yesterday's (just after midnight). */
    fun phoneMsToday(): Long = if (phoneDay == java.time.LocalDate.now()) _phoneTodayMs.value else 0L

    /** Whether the user has the service switched on in system settings (it may not be bound yet). */
    fun isEnabledInSettings(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val me = ComponentName(context, FocusAccessibilityService::class.java)
        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
        return splitter.any { ComponentName.unflattenFromString(it) == me }
    }
}
