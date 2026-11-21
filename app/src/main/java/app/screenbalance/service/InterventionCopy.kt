package app.screenbalance.service

import app.screenbalance.engine.BlockReason

/** Calm, non-judgemental lock-screen copy: a headline and one line under it. */
object InterventionCopy {

    data class Copy(val eyebrow: String, val line: String)

    /**
     * Only lock screens have copy: whole-app closures and the Settings guard.
     * Section blocks redirect under a blank cover; the pause and the reminders have their own screens.
     */
    fun forReason(reason: BlockReason, appLabel: String): Copy? = when (reason) {
        BlockReason.DAILY_LIMIT -> Copy("Daily limit reached", "That's your $appLabel time for today.")
        BlockReason.FOCUS_SESSION -> Copy("Focus session", "$appLabel can wait until it's over.")
        BlockReason.SCHEDULE -> Copy("Quiet hours", "$appLabel is resting.")
        BlockReason.SETTINGS_LOCKED -> Copy("Protection is locked", "Strict mode is on, so it can't be turned off until this focus session or quiet hours end.")
        BlockReason.REELS, BlockReason.EXPLORE, BlockReason.HOME_FEED, BlockReason.FEED_END, BlockReason.SHORT_VIDEO,
        BlockReason.OPEN_PAUSE, BlockReason.USE_REMINDER -> null
    }
}
