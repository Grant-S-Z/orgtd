package dev.orgtd.orgmode

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/**
 * org 时间戳的解析与格式化。
 *
 * 关键约束：星期必须固定输出英文缩写。设备是中文 locale 时，
 * [java.time.format.DateTimeFormatter] 会给出「周日」，和现有文件对不上。
 */
object OrgTime {

    private val DAYS = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun dayAbbrev(date: LocalDate): String = DAYS[date.dayOfWeek.value - 1]

    /** `[2026-11-24 Tue 09:30]` */
    fun formatClosed(dateTime: LocalDateTime): String = "[${formatTimestamp(dateTime)}]"

    /** `2026-11-24 Tue 09:30` */
    fun formatTimestamp(dateTime: LocalDateTime): String = buildString {
        append(formatDate(dateTime.toLocalDate()))
        append(' ')
        append(String.format(Locale.ENGLISH, "%02d:%02d", dateTime.hour, dateTime.minute))
    }

    /** `2026-11-24 Tue` */
    fun formatDate(date: LocalDate): String = String.format(
        Locale.ENGLISH,
        "%04d-%02d-%02d %s",
        date.year,
        date.monthValue,
        date.dayOfMonth,
        dayAbbrev(date),
    )

    /** `<2026-11-24 Tue 09:30 +1d -2d>` */
    fun formatSpec(spec: DateSpec): String = buildString {
        append(if (spec.active) '<' else '[')
        append(formatDate(spec.date))
        spec.time?.let { append(' ').append(formatTime(it)) }
        spec.repeater?.let { append(' ').append(it) }
        spec.warning?.let { append(' ').append(it) }
        append(if (spec.active) '>' else ']')
    }

    fun formatTime(time: LocalTime): String =
        String.format(Locale.ENGLISH, "%02d:%02d", time.hour, time.minute)
}
