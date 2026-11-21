package app.screenbalance.data

import android.content.Context
import app.screenbalance.BuildConfig

/**
 * Sample usage for recording the store video and screenshots on an emulator. Debug builds only:
 * BuildConfig.DEBUG is a constant false in release, so R8 removes every use of it. Switched on by
 * the debug-only DemoReceiver (`adb shell am broadcast -a app.screenbalance.DEMO ...`).
 */
object DemoMode {
    private const val PREFS = "demo"

    fun active(context: Context): Boolean =
        BuildConfig.DEBUG && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", false)

    /** Today's sample time per app, or null when demo mode is off. */
    fun today(context: Context): Map<String, Long>? {
        if (!active(context)) return null
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("today", null) ?: return null
        return raw.split(';').mapNotNull { e ->
            val (pkg, ms) = e.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            ms.toLongOrNull()?.let { pkg to it }
        }.toMap()
    }

    fun set(context: Context, on: Boolean, today: Map<String, Long>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("on", on)
            .putString("today", today.entries.joinToString(";") { "${it.key}=${it.value}" })
            .apply()
    }
}
