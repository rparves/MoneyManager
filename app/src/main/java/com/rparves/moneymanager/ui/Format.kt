package com.rparves.moneymanager.ui

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

fun money(minor: Long, cur: String): String {
    val a = abs(minor)
    val body = String.format(Locale.US, "%,d.%02d", a / 100, a % 100)
    return (if (minor < 0) "−" else "") + cur + body
}

fun moneyShort(minor: Long, cur: String): String = cur + String.format(Locale.US, "%,d", minor / 100)

fun signed(minor: Long, income: Boolean, cur: String): String = (if (income) "+" else "−") + money(minor, cur)

fun plainAmount(minor: Long): String =
    if (minor % 100 == 0L) (minor / 100).toString() else String.format(Locale.US, "%d.%02d", minor / 100, minor % 100)

fun parseMinor(s: String): Long? {
    val bd = s.replace(",", "").trim().toBigDecimalOrNull() ?: return null
    if (bd.signum() <= 0) return null
    return bd.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong()
}

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

fun LocalDate.startMillis(): Long = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun monthRange(ym: YearMonth): Pair<Long, Long> = ym.atDay(1).startMillis() to ym.plusMonths(1).atDay(1).startMillis()

fun YearMonth.label(): String = format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))

fun LocalDate.fmt(pattern: String): String = format(DateTimeFormatter.ofPattern(pattern, Locale.US))

fun relativeDay(d: LocalDate): String {
    val t = LocalDate.now()
    return when (d) {
        t -> "Today"
        t.minusDays(1) -> "Yesterday"
        else -> d.fmt(if (d.year == t.year) "EEE, MMM d" else "EEE, MMM d, yyyy")
    }
}

fun timeLabel(h: Int, m: Int): String = LocalTime.of(h, m).format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))

fun dueLabel(days: Long): String = when {
    days < 0 -> "Overdue by ${-days} ${if (days == -1L) "day" else "days"}"
    days == 0L -> "Due today"
    days == 1L -> "Due tomorrow"
    else -> "Due in $days days"
}
