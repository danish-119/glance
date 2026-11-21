package app.screenbalance.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.screenbalance.ui.components.HairlineRule
import app.screenbalance.ui.components.SectionHeader

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Page("Privacy and", "limitations", onBack = onBack) {
        item(key = "privacy") {
            SectionHeader("Privacy")
            Para("No internet", "Glance does not have Android's internet permission, so it cannot send anything anywhere. There are no accounts, analytics, ads or crash reporters.")
            Para("What is read", "In Instagram, YouTube, Facebook and Snapchat: the layout of the screen while the app is open, short on-screen labels and which tab you tap. For every other app, including TikTok, X and Reddit: only its name when it comes to the front. If you allow notification access, only which app each new notification comes from (never what it says). All of it is examined in memory and discarded immediately.")
            Para("What is stored", "Your settings, plus totals per day: phone time, minutes per watched app and per app you gave a limit, and (with usage access) a daily summary for history and Insights: minutes and times opened per app, minutes per hour of the day, and unlock count. Nothing about what was closed is recorded. History older than 90 days is deleted automatically. You can clear it any time in Settings.")
            Para("What is never touched", "Your passwords or logins, your messages, posts, comments, contacts or photos, and the screens of any app other than the four listed above.")
            Para("Backups", "Glance's data is excluded from cloud backups and device transfers. It stays on this phone.")
        }
        item(key = "limits") {
            SectionHeader("Limitations")
            Para("App updates", "Glance recognises Reels, Shorts, Spotlight and Explore from the structure of each app. When an app redesigns a screen, recognition can stop working until Glance is updated. When unsure, Glance lets the screen through rather than block something you need. Daily limits, quiet hours and focus sessions don't depend on this.")
            Para("Redirects, not edits", "Glance can't change other apps' screens, only move you away from them. When it does, a plain cover shows for a moment while the app is moved underneath (for example from Reels to your profile, or from Shorts to Subscriptions). Small rows of suggestions inside a feed stay.")
            Para("Languages", "Instagram's \"You're all caught up\" and the tab names used for YouTube, Facebook and Snapchat are matched in English and a few other languages. Instagram's Reels and Explore detection doesn't depend on language.")
            Para("Instagram Lite", "Instagram Lite draws its own screens, so it can only be closed as a whole app, like TikTok, X and Reddit.")
            Para("Notifications", "Notifications of closed apps are held back only if you allow notification access. During quiet hours and focus sessions every app's notifications wait; calls and messages always come through. A very short sound or banner can occasionally slip through before Android hands the notification to Glance.")
            Para("Android rules", "Android always allows uninstalling apps and switching accessibility services off in Safe Mode. Some phone makers stop background services aggressively; if protection keeps stopping, exclude Glance from battery optimisation.")
            Para("iPhone", "Apple doesn't let apps see inside other apps, so this kind of section blocking isn't possible on iOS.")
        }
        item(key = "credits") {
            SectionHeader("Credits")
            Para("Open source", "Screen recognition builds on view names documented by open-source blockers, including Scrollhalt and ReelGuard for Instagram.")
        }
    }
}

@Composable
private fun Para(title: String, body: String) {
    Column(Modifier.padding(vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HairlineRule()
}
