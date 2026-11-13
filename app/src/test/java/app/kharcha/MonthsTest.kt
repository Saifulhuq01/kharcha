package app.kharcha

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class MonthsTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance().apply { clear(); set(year, month, day, hour, 0) }.timeInMillis

    private fun spend(amount: Double, t: Long, category: String = "Other") =
        Expense(amount = amount, timestamp = t, category = category)

    @Test
    fun groupsByMonthNewestFirst() {
        val oct = spend(100.0, at(2026, Calendar.OCTOBER, 5))
        val nov1 = spend(50.0, at(2026, Calendar.NOVEMBER, 2))
        val nov2 = spend(25.0, at(2026, Calendar.NOVEMBER, 20))

        val months = groupByMonth(listOf(oct, nov1, nov2))

        assertEquals(2, months.size)
        assertEquals(at(2026, Calendar.NOVEMBER, 1, 0), months[0].first)
        assertEquals(listOf(nov2, nov1), months[0].second)
        assertEquals(listOf(oct), months[1].second)
    }

    @Test
    fun lastSecondOfMonthStaysInThatMonth() {
        val end = Calendar.getInstance().apply { clear(); set(2026, Calendar.OCTOBER, 31, 23, 59, 59) }.timeInMillis
        assertEquals(at(2026, Calendar.OCTOBER, 1, 0), startOfMonth(end))
    }

    @Test
    fun categorySplitSumsAndSortsBiggestFirst() {
        val t = at(2026, Calendar.NOVEMBER, 3)
        val split = categorySplit(
            listOf(spend(10.0, t, "Food"), spend(200.0, t, "Bills"), spend(15.0, t, "Food"))
        )
        assertEquals(listOf("Bills" to 200.0, "Food" to 25.0), split)
    }

    @Test
    fun extraIsCountedInTotals() {
        val t = at(2026, Calendar.NOVEMBER, 12)
        val gpayPlusCash = Expense(amount = 160.0, extra = 100.0, timestamp = t, category = "Food")
        assertEquals(260.0, gpayPlusCash.total, 0.001)
        assertEquals(listOf("Food" to 260.0), categorySplit(listOf(gpayPlusCash)))
    }

    @Test
    fun dayLabelSaysTodayAndYesterday() {
        val now = at(2026, Calendar.NOVEMBER, 13, 15)
        assertEquals("Today", dayLabel(startOfDay(now), now))
        assertEquals("Yesterday", dayLabel(startOfDay(at(2026, Calendar.NOVEMBER, 12)), now))
    }

    @Test
    fun datePickerRoundTripKeepsTheSameDay() {
        val day = startOfDay(at(2026, Calendar.OCTOBER, 5))
        assertEquals(day, utcDateToLocalDay(localDayToUtcDate(day)))
    }
}
