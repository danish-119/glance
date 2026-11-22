# Google Play compliance

Apps that use `AccessibilityService` without being accessibility tools get extra review. This is what Glance does and what you must submit.

## In the app (implemented)

- **Prominent disclosure before the permission.** Onboarding step "Before you turn it on" and the Today-screen dialog (`ACCESSIBILITY_DISCLOSURE`) describe what is read in Instagram, YouTube, Facebook and Snapchat, that only the name of any other app in front is used (TikTok, X and Reddit included), how it's used and what is never collected. They require an affirmative **I agree**. Declining skips the Accessibility step; nothing opens Accessibility settings without consent. The disclosure is versioned (`CURRENT_DISCLOSURE_VERSION = 2`): users who agreed to the earlier, Instagram-only wording are asked again. (`OnboardingFlow.Disclosure`, `TodayScreen`, `Disclosures.kt`)
- **Separate disclosure for notification access.** The onboarding page "Quiet when it's closed" and the "Allow notification access" row in Rules explain that only the posting app is checked and content is never read, before Android's settings are opened. (`OnboardingFlow`, `RulesScreen`)
- **`android:isAccessibilityTool="false"`** declared in `accessibility_service_config.xml`.
- **Service description** (`accessibility_service_description`) explains the purpose in plain language and is shown by Android on the toggle screen.
- **Package visibility is narrow.** `<queries>` lists the watched packages and intents only (launcher, home, dialer, SMS, messaging, contacts, maps, camera, clock). `QUERY_ALL_PACKAGES` is not requested.
- **No data leaves the device:** no `INTERNET` permission, backup disabled, no SDKs.
- **Easy off-switch:** users can disable the service in system settings at any time (except during a strict session they started themselves, and the disclosure says so).

## Play Console

1. **Permissions declaration → Accessibility API**: purpose "Digital wellbeing: closes user-selected distracting sections of Instagram, YouTube, Facebook and Snapchat (Reels, Shorts, Spotlight, Explore) while leaving messaging available, and applies the user's own daily app limits, quiet hours, focus sessions and a pause before opening apps. For apps other than those four, only the package name of the app in the foreground is used." The core functionality depends on the API.
2. **Demo video**: show the disclosure, enabling the service, the Instagram Reels tab moving to the profile, a shared Reel playing, YouTube Shorts moving to Subscriptions, an app closing at its daily limit, quiet hours closing an app while Messages stays open, DMs working.
3. **Data safety**: no data collected or shared. All processing on device.
4. **Notification access** (`BIND_NOTIFICATION_LISTENER_SERVICE`): requested from the user; when granted it is used only to snooze notifications during the user's quiet hours and focus sessions, and an app's own after its daily limit. Essential apps (calls, messages) always notify, and so does any app's incoming or ongoing call (recognised by the notification's category alone). No notification content is read or stored.
5a. **POST_NOTIFICATIONS**: only for the optional Monday weekly-summary notification; asked for when it is switched on.
5. **Usage access** (`PACKAGE_USAGE_STATS`): optional, explained in onboarding; used for screen time per app on Today, app history and Insights (a daily summary per app, per hour and unlocks, stored on the device for 90 days).
6. **Target API**: targetSdk 36.
7. **Store listing**: don't use Instagram's, YouTube's, Facebook's, Snapchat's, TikTok's, X's or Reddit's logos, or imply affiliation. Use "works with" wording; the names are trademarks of their owners.

## Privacy policy (publish at a public URL and link it in the listing)

> **Glance privacy policy**
>
> Glance does not collect, transmit or share any personal data. The app has no internet access.
>
> **Accessibility service.** Glance uses Android's Accessibility Service to recognise which screen is open in Instagram, YouTube, Facebook and Snapchat, so it can close the sections you chose (such as Reels, Shorts, Spotlight and Explore). In those apps it reads the screen's structure and short on-screen labels while the app is open. For every other app, it only uses the name of the app that comes to the front, to apply your daily limits, quiet hours, focus sessions and the pause before opening. This information is processed instantly on your device and is never stored or sent anywhere. Glance never reads or stores your messages, posts, comments, passwords or login details.
>
> **Stored on your device.** Your settings and aggregate statistics per day (phone time, minutes per watched app and per app you gave a limit, minutes per Instagram section, and with usage access minutes and times opened per app, minutes per hour and the unlock count) are stored locally. Statistics older than 90 days are deleted automatically, and you can delete them any time in Settings. This data is excluded from backups.
>
> **Notification access.** If you allow notification access, Glance checks which app each new notification comes from. During quiet hours and focus sessions, notifications are snoozed until they end, and after an app's daily limit, that app's are snoozed until midnight. Calls (from any app) and messages always come through. Notification content is never read, stored or sent.
>
> **Usage access (optional).** If you grant it, Glance reads apps' screen time from Android to show accurate totals.
>
> **Contact.** [your support email]
