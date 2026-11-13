package app.kharcha

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Date and grouping helpers. Plain Kotlin (no Android), so they're unit-tested in MonthsTest.

fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
    timeInMillis = t
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun startOfMonth(t: Long = System.currentTimeMillis()): Long = Calendar.getInstance().apply {
    timeInMillis = startOfDay(t)
    set(Calendar.DAY_OF_MONTH, 1)
}.timeInMillis

fun monthLabel(monthStart: Long): String =
    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(monthStart))

fun dayLabel(day: Long, now: Long = System.currentTimeMillis()): String {
    val today = startOfDay(now)
    val yesterday = startOfDay(today - 1)
    return when (day) {
        today -> "Today"
        yesterday -> "Yesterday"
        else -> SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(Date(day))
    }
}

// Months newest first, each with its spends newest first.
fun groupByMonth(expenses: List<Expense>): List<Pair<Long, List<Expense>>> =
    expenses.sortedByDescending { it.timestamp }.groupBy { startOfMonth(it.timestamp) }.toList()

// Total per category, biggest first.
fun categorySplit(list: List<Expense>): List<Pair<String, Double>> =
    list.groupBy { it.category }
        .map { (name, items) -> name to items.sumOf { it.total } }
        .sortedByDescending { it.second }

// The date picker works in UTC midnights; the app works in local days. These convert between them.
fun utcDateToLocalDay(utc: Long): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
    return Calendar.getInstance().apply {
        clear()
        set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

fun localDayToUtcDate(day: Long): Long {
    val l = Calendar.getInstance().apply { timeInMillis = day }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}
