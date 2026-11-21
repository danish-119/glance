package app.screenbalance.data

import app.screenbalance.AppContainer
import java.time.LocalDate

/** The daily-goal streak, counted the same way on Today and the goal widget. */
object GoalStreak {

    /**
     * Days in a row, ending yesterday, whose screen time stayed within [goalMinutes]. A day with no
     * data (before Glance was installed, or nothing measured) ends the streak.
     */
    suspend fun days(c: AppContainer, goalMinutes: Int, today: LocalDate = LocalDate.now()): Int {
        if (goalMinutes <= 0) return 0
        val goalMs = goalMinutes * 60_000L
        var n = 0
        while (n < 90) {
            val day = today.minusDays(n + 1L)
            // Android's figures (saved history) when there are any; otherwise Glance's own phone time.
            val ms = c.history.day(day, today)?.totalMs?.takeIf { it > 0 }
                ?: c.stats.days(day, day).firstOrNull()?.phoneMs ?: 0L
            if (ms <= 0 || ms > goalMs) break
            n++
        }
        return n
    }
}
