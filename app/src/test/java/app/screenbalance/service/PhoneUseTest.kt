package app.screenbalance.service

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PhoneUseTest {
    private val zone = ZoneId.of("UTC")
    private val day = LocalDate.of(2026, 11, 18)
    private val noon = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val min = 60_000L

    @Test fun countsUnlockedTime() {
        val p = PhoneUse({ zone })
        p.setStoredToday(day, 10 * min)
        p.onUnlocked(noon)
        assertEquals(15 * min, p.todayMs(noon + 5 * min))
        assertEquals(5 * min, p.onScreenOff(noon + 5 * min))
        assertEquals(10 * min, p.todayMs(noon + 6 * min)) // The service adds the finished stretch to the stored total.
    }

    @Test fun newDayStartsAtZero() {
        val p = PhoneUse({ zone })
        p.setStoredToday(day, 50 * min)
        val nextMorning = day.plusDays(1).atTime(0, 30).atZone(zone).toInstant().toEpochMilli()
        p.onUnlocked(nextMorning - 60 * min) // 23:30 the evening before.
        assertEquals(30 * min, p.todayMs(nextMorning))
    }
}
