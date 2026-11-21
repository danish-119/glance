package app.screenbalance.ui.screens

/**
 * The accessibility disclosure (Google Play "prominent disclosure"), shown before the user is sent
 * to Accessibility settings. Keep in step with OnboardingFlow's disclosure and docs/PLAY_COMPLIANCE.md.
 */
internal const val ACCESSIBILITY_DISCLOSURE =
    "Glance uses Android's Accessibility Service to recognise which screen is open in Instagram, YouTube, " +
        "Facebook and Snapchat, so it can close the parts you chose (such as Reels, Shorts and Explore). For every " +
        "other app, including TikTok, X and Reddit, it only notices which app comes to the front, by name, to apply " +
        "your daily limits, quiet hours, focus sessions and the pause before opening. It processes everything on your phone, " +
        "never reads or stores your messages or posts, and has no internet access. If you turn on strict mode, during " +
        "focus sessions or quiet hours it also watches Settings so protection can't be switched off."
