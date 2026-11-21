package app.screenbalance.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import app.screenbalance.service.FocusAccessibilityService

/** Deep links into system settings, each with a safe fallback because OEMs differ. */
object SystemIntents {

    fun openAccessibilitySettings(context: Context) {
        val component = ComponentName(context, FocusAccessibilityService::class.java).flattenToString()
        val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            .putExtra(Intent.EXTRA_COMPONENT_NAME, component)
            .putExtra(":settings:fragment_args_key", component)
        if (!tryStart(context, details)) tryStart(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun openUsageAccessSettings(context: Context) {
        val specific = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}"))
        if (!tryStart(context, specific)) tryStart(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    fun openAppDetails(context: Context) {
        tryStart(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }

    fun openNotificationAccessSettings(context: Context) {
        val component = ComponentName(context, app.screenbalance.service.NotificationGate::class.java).flattenToString()
        val details = Intent("android.settings.NOTIFICATION_LISTENER_DETAIL_SETTINGS")
            .putExtra("android.provider.extra.NOTIFICATION_LISTENER_COMPONENT_NAME", component)
        if (!tryStart(context, details)) tryStart(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
