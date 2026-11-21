# Detection: how screens are recognised

Only four apps are looked inside: Instagram, YouTube, Facebook and Snapchat. Every other app (TikTok, X, Reddit, Instagram Lite and anything else on the phone) is handled by its package name alone, which needs no detection.

| App | Signatures | Classifier |
|---|---|---|
| Instagram | [`InstagramSignatures.kt`](../app/src/main/java/app/screenbalance/engine/InstagramSignatures.kt) | [`ScreenClassifier.kt`](../app/src/main/java/app/screenbalance/engine/ScreenClassifier.kt) |
| YouTube, Facebook, Snapchat | `ShortVideoSignatures` in [`Apps.kt`](../app/src/main/java/app/screenbalance/engine/Apps.kt) | `ShortVideoClassifier` in the same file |

## Instagram

| Surface | Signal | Confidence |
|---|---|---|
| Reels tab | `clips_tab` selected + Reels player visible | High: confirmed by several open-source projects (Scrollhalt confirmed on Instagram 451.0.0.53.80) |
| Reels player | `clips_viewer_view_pager` / `clips_view_pager` / `clips_viewer_fragment_container` / `clips_viewer_container`, covering ≥80% × ≥55% of the screen | High |
| Reel shared in a DM | `clips_sent_by`, `reel_viewer_message_composer`, `reply_bar_container_scroll_view`, `sender_username_or_fullname` | Medium |
| Messages | `direct_tab` selected or `direct_thread_header`, `row_thread_composer_edittext`, `direct_inbox_recycler_view`, … | Medium–high |
| Profile | `profile_viewpager`, `profile_tab_layout`, `row_profile_header_imageview_frame_layout` | Medium–high |
| Explore | `search_tab` selected, search box not focused or empty, ≥3 `image_preview` cells | Medium |
| Search (allowed) | search box focused or has text (checked even with the tab bar hidden, as Instagram hides it while typing), or `row_search_user_container` / `row_search_keyword_title` | Medium |
| Leaving search | Explore detected right after Search: covered and sent to Home (or the profile when Home is closed), no lock screen (search has no tab bar to leave by) | n/a |
| Tab taps | A tap on the Search, Reels or (when closed) Home tab is acted on at once (`typeViewClicked`, matched by tab id or label), so the cover is up before Explore or Reels is drawn. Only the tapped tab's id/label is read | High |
| Home feed (closed only if "Home feed" is on; then opens the profile) | `feed_tab` selected and none of the above | High |
| End of new posts (always on) | Text "You're all caught up" / "You've completely caught up" (+ a few translations). Its feed-list position (vertical scroll `fromIndex`) is remembered for the visit, so scrolling past it via "View older posts" also stops | Medium: language-dependent |
| Suggested accounts page (allowed) | Text "Discover people" | Low–medium |

Unrecognised screens are classified `OTHER` and always allowed.

## YouTube, Facebook, Snapchat

A screen is the short-video feed when **either** a known player view covers ≥80% × ≥55% of the screen, **or** the selected tab's label is the short-video tab. The coverage check keeps small previews (such as the Shorts shelf in YouTube's home feed) from counting.

| App | Player view ids (after `<package>:id/`) | Selected tab labels | Moved to |
|---|---|---|---|
| YouTube | `reel_player_page_container`, `reel_player_page_view`, `reel_watch_player`, `reel_watch_pager`, `reel_watch_fragment_root`, `reel_watch_fragment_container`, `reel_video_player`, `reel_player_underlay`, `shorts_video_pager`, `shorts_player` | "Shorts" | Subscriptions, else Home, else Back |
| Facebook | `fb_shorts_viewer_fragment`, `fb_shorts_viewer_page`, `fb_shorts_video_container`, `fb_shorts_container` | "Reels", "Video", "Watch" | Home, else Feed, else Back |
| Snapchat | `spotlight_player`, `spotlight_fullscreen`, `spotlight_story_player`, `spotlight_swipe_layer`, `spotlight_video`, `spotlight_fragment`, `spotlight_container` | "Spotlight" | Chat, else Camera, else Back |

A tap on the short-video tab itself is acted on at once, like Instagram's tabs. Tab labels are matched in English (with the ", tab" suffix TalkBack adds); in other languages detection relies on the player ids. These lists are **not yet confirmed on current app versions**: check them on a real phone before each release (see [TESTING.md](TESTING.md)).

## When an app updates

1. Install the new version on a test phone with Glance's protection on.
2. Open the screen that misbehaves and dump its view hierarchy: `adb shell uiautomator dump /sdcard/dump.xml && adb pull /sdcard/dump.xml`.
3. Update the id or label sets (`InstagramSignatures.kt` or `ShortVideoSignatures` in `Apps.kt`), add a test with the new ids (`ScreenClassifierTest.kt` or `ShortVideoClassifierTest.kt`), and run `./gradlew testDebugUnitTest`.

## Sources

Instagram view ids come from public accessibility dumps in [Scrollhalt](https://github.com/Mostafa-DE/Scrollhalt) (GPL-3.0) and [ReelGuard](https://github.com/sharvinhariharan-hub/reelguard) (MIT). YouTube, Facebook and Snapchat ids come from public dumps shared by open-source Shorts and Reels blockers and from uiautomator dumps. Only the facts (id names) were used; no code was copied.
