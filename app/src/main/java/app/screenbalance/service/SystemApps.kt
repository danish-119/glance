package app.screenbalance.service

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Telephony
import android.view.inputmethod.InputMethodManager

/**
 * The apps a phone can't do without. They are never closed by quiet hours or focus sessions and
 * never paused, and their notifications are never held back. Resolved from the user's own default
 * apps, so it works whichever home screen, dialer or messages app they use.
 */
class SystemApps(private val context: Context) {

    private val pm = context.packageManager

    /** Home screens: back on one means the app in front was left. */
    val homes: Set<String> = resolve(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))

    /** Never paused: home screens, keyboards, the dialer, the system shade and this app. */
    val neverPause: Set<String> = homes +
        essential(Intent(Intent.ACTION_DIAL)) +
        context.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList.orEmpty().map { it.packageName } +
        setOf(context.packageName, "com.android.systemui")

    /** Never closed: [neverPause] plus messages, contacts, maps, camera, clock and Settings. */
    val alwaysOpen: Set<String> = neverPause +
        listOfNotNull(Telephony.Sms.getDefaultSmsPackage(context)) +
        essential(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))) +
        essential(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)) +
        essential(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CONTACTS)) +
        essential(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MAPS)) +
        essential(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) +
        essential(Intent(AlarmClock.ACTION_SHOW_ALARMS)) +
        SETTINGS_PACKAGES

    private val launchable = HashMap<String, Boolean>()

    /** Can quiet hours, focus sessions or a daily limit close [pkg]? Any app with a launcher icon but the essentials. */
    fun isCloseable(pkg: String): Boolean = pkg !in alwaysOpen && isLaunchable(pkg)

    /** A real app with a launcher icon, not a system dialog, share sheet or permission screen. */
    fun isLaunchable(pkg: String): Boolean = launchable.getOrPut(pkg) { pm.getLaunchIntentForPackage(pkg) != null }

    private fun resolve(intent: Intent): Set<String> =
        pm.queryIntentActivities(intent, PackageManager.MATCH_ALL).mapTo(HashSet()) { it.activityInfo.packageName }

    /**
     * The phone's own apps for [intent] (preinstalled), and the app the user picked as its default.
     * Not every app that merely can handle it: Snapchat registers as a camera and WhatsApp as an SMS
     * and dial target, and they must stay limitable and pausable like any other app.
     */
    private fun essential(intent: Intent): Set<String> {
        val chosen = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL).mapNotNullTo(HashSet()) { r ->
            val info = r.activityInfo
            val system = info.applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            info.packageName.takeIf { system || it == chosen }
        }
    }

    companion object {
        /** System Settings (and MIUI's security centre, which hosts its accessibility pages). */
        val SETTINGS_PACKAGES = setOf("com.android.settings", "com.miui.securitycenter")
    }
}
