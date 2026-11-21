# Testing

## Automated

`./gradlew testDebugUnitTest` covers the pure engine and phone-time logic:

- `ScreenClassifierTest`: Instagram Reels tab vs viewer, small or Stories ids not mistaken for Reels, shared-Reel markers, DMs, profiles opened from the feed, Explore vs search, feed end with curly apostrophes, search typing without the tab bar, fail-open.
- `ShortVideoClassifierTest`: full-screen YouTube, Facebook and Snapchat players, a small Shorts shelf not counted, selected-tab labels, ids not crossing between apps.
- `TrackersTest`: single Reel from DMs until swipe, snapping back re-arms the swipe, positioning window, origin rules (Reels tab, Explore and search are not intentional).
- `PolicyEngineTest`: quiet hours and focus sessions lock only with strict mode, switching strict mode off counts as loosening; Instagram rules, single Reels always play, suggested accounts allowed, feed stops at "caught up" (and past it), Home feed open by default and, when closed, redirected to the profile; per-app daily limits for watched and any other app; the earlier single Instagram limit still applies; short-video sections move to a safe tab; focus sessions and quiet hours close only the chosen apps (the social apps by default, or all with exceptions; an unchosen app still honours its own limit); Instagram Lite follows the limits; the pause for chosen or all apps; schedules (incl. overnight and next start).
- `PhoneUseTest`: phone time today across unlocks, a new day starting at zero.

## On a real device (required before every release)

Use a phone with current versions of the apps from the Play Store, signed in to test accounts. Note each app's version.

### Setup

| # | Scenario | Expected |
|---|---|---|
| 1 | Fresh install → onboarding → decline disclosure | Accessibility step skipped; usage and notification access pages still shown (both skippable); Today shows "Protection is off" |
| 2 | Accept disclosure, enable service ("Glance" in Accessibility settings) | Today shows "Protection on" within a few seconds |

### Instagram

| # | Scenario | Expected |
|---|---|---|
| 3 | Tap the Reels tab | A blank cover for a moment (no sound), then your profile; no lock screen |
| 4 | Friend sends a Reel in DM → open it | Reel plays; swipe up → a blank cover, then back on the same Reel (still playing) |
| 5 | Tap the Search tab | A blank cover, then the search box, focused; no Explore. Back from search → Home (or your profile when Home is closed) |
| 6 | Rules → Instagram Home feed on; tap Home | A blank cover, then your profile |
| 7 | Scroll the feed to "You've completely caught up"; then "View older posts" and scroll on | Each time: a blank cover, then the top of the feed with stories |
| 8 | DMs, a profile, followers, notifications, stories | Never blocked |

### YouTube, Facebook, Snapchat

| # | Scenario | Expected |
|---|---|---|
| 9 | YouTube: tap Shorts; open a Short from the home shelf or search | A blank cover (no sound), then Subscriptions (or Home); normal videos play |
| 10 | Facebook: tap Reels or Video; open a Reel from the feed | A blank cover, then Home; feed, groups and profiles open |
| 11 | Snapchat: swipe to Spotlight | A blank cover, then Chat (or Camera); chats, camera and stories open |
| 9b | YouTube on a slow connection: tap Shorts while the feed is still loading | Still moved away within about 3 s; the Short never keeps playing |
| 12 | Switch "YouTube Shorts" off in Rules | Shorts plays normally |

### Every app

| # | Scenario | Expected |
|---|---|---|
| 13 | Daily limit 5 min on YouTube (ruler) | After 5 min YouTube closes to the home screen with a lock card until midnight; other apps unaffected |
| 14 | Limits tab → a game or browser → 5 min | Its row shows "5 min"; it appears in Today's screen time with a progress line; after 5 min it closes until midnight. Every app is listed alphabetically with its icon; Phone, Messages and Maps aren't listed |
| 15 | Quiet hours now–+30 min (default apps) | Instagram, YouTube, TikTok etc. close with the "Quiet hours" card; Chrome, a game, WhatsApp still open. Phone, Messages, Maps, Camera, Clock, Settings and the keyboard keep working |
| 16 | Rules → Apps that close → switch on Chrome; then All apps with WhatsApp off | Chrome closes too; with All apps, everything but WhatsApp and the essentials closes |
| 17 | Focus session 25 min | Same as 15; any setting can be changed (looser too) and **End now** on Today ends it. With strict mode on for focus: loosening shows "Strict mode is on…", no End now, Glance's Accessibility entry bounces back |
| 17b | Lock card → Emergency access → Open for 5 minutes | The app reopens; everything stays open 5 min; Today shows the countdown; the 4th time in a day says none left |
| 17d | Daily goal 1 h | Today shows time left / over; next day "Yesterday was within your goal" if it was |
| 18 | Quiet hours overnight 22:00–07:00 | Apps can't be opened across midnight; open again at 07:00 |
| 19 | Notification access allowed; quiet hours or a focus session; then an app's limit used up | During quiet hours / focus no app's notifications show (calls and SMS do) and they appear when it ends; after a limit, only that app's wait until midnight |
| 20 | Pause tab → on → All apps | Every app (not the home screen, keyboard, dialer or Glance) shows the 10-second pause; "Open" lets it through and skips it for a minute; "Not now" goes home. Pressing Home, Back or Recents during the pause removes it. No pause on permission dialogs or share sheets |
| 21 | Pause tab → Chosen apps → only Instagram | Only Instagram is paused |
| 22 | Today with usage access | "Screen time today" lists every app used today, most used first, with icons; the Focus session card at the bottom scrolls fully above the tab bar |
| 23 | Add the "Screen time today" widget | Shows today's total and the top three apps (no limits), same numbers as Today; updates after unlocking; without usage access, the total and a note to allow it. Tap opens Glance |
| 23b | Leave the widget overnight; first unlock after midnight | Shows the new day (near 0), never yesterday's total |
| 23c | Add the "Daily goal" widget, no goal → set a 2 h goal | "No goal yet" → a full ring at 100% and "… left / of 2 h" right after setting it; the ring empties as the phone is used; a full ember ring, "over" and "… over" past the goal; tap opens Today |
| 23d | Today → ‹ arrow, tap a bar, tap an app | Earlier week's bars; that day's total and apps; the app's page with its week, total, daily average and limit |
| 23e | Insights tab with usage access → Week | Daily average vs the week before, day-by-day bars against the goal, at-a-glance grid, hour chart and parts of the day, habits, most used apps (tap opens the app page) |
| 23e2 | Insights → Day; tap a bar in Week; switch Day ↔ Week | One day's total vs your usual day, hour chart, pickups, apps. Each tap switches once, with no flicker back to the other view |
| 23g | Today during a focus session or quiet hours | The active panel spans the full page width, like the cards around it |
| 23f | Weekly summary on; Monday after 9:00 unlock | One notification with last week's average; tap opens Insights. Off in Settings → nothing |
| 24 | Limits, Pause, Apps that close: type in the search box | The list narrows to matching apps; Phone, Messages, Maps etc. show an (i) explaining they can't be limited or closed; Snapchat and WhatsApp can be limited and paused |
| 25 | Reminder 5 min | "5 min on YouTube" after 5 min in YouTube, again at 10; switching to another app, going home or locking starts it over |
| 26 | Instagram Lite, TikTok, X, Reddit after their limit or in quiet hours | Opening goes home + lock card; otherwise they open |

### System

| # | Scenario | Expected |
|---|---|---|
| 27 | Reboot during a focus session | Session still active after unlock; closing resumes when an app opens |
| 28 | Force-stop Glance | Service restarts or shows as off; Today reflects it |
| 29 | Dark mode, large font (200%), TalkBack | All screens readable; toggles announce state |
| 30 | Battery: 1 h of normal use with quiet hours set and the pause on | Glance < 3% in Battery usage |
| 31 | Android 8, 10, 12, 14, 15/16 (emulator + one real device each where possible) | 1–30 pass |

### Demo data for screenshots

Debug builds accept sample usage, so Today, Insights and limits look realistic on an emulator. See [Demo data](ARCHITECTURE.md#demo-data-debug-builds-only). Turn it off (`--ez off true`) before testing real usage. Release builds ignore it.

When a case fails inside an app, dump its screen with `adb shell uiautomator dump /sdcard/dump.xml && adb pull /sdcard/dump.xml` and attach it (and the app version) to the bug.
