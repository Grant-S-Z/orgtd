package dev.orgtd.orgmode

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 一个计划时间：`<2026-11-22 Sun 00:00 +1d>` 或 `[2026-11-22 Sun]`。
 *
 * @param repeater 重复规则，如 `+1d`、`++1w`、`.+3d`，没有则为 null
 * @param warning 提前提醒，如 `-2d`，没有则为 null
 * @param active `<...>` 为 true，`[...]` 为 false
 */
data class DateSpec(
    val date: LocalDate,
    val time: LocalTime? = null,
    val repeater: String? = null,
    val warning: String? = null,
    val active: Boolean = true,
) {
    val hasTime: Boolean get() = time != null
}

/**
 * 一个 org headline 及其子树范围。
 *
 * [lineIndex] 是该标题在文件中的绝对行号，[endExclusive] 是子树结束位置（不含）。
 * 两者都只在解析时的文档快照里有效，文档一变就要重新解析。
 */
data class Headline(
    val lineIndex: Int,
    val endExclusive: Int,
    val level: Int,
    val keyword: String?,
    val priority: Char?,
    val title: String,
    val tags: List<String>,
    val scheduled: DateSpec?,
    val deadline: DateSpec?,
    val closed: LocalDateTime?,
    val properties: Map<String, String>,
    val bodyStart: Int,
) {
    val id: String? get() = properties["ID"]

    val isTask: Boolean get() = keyword != null

    val isDone: Boolean get() = keyword == "DONE"

    /** 顶层、且没有状态词 —— 按约定就是「清单」。 */
    val isList: Boolean get() = level == 1 && keyword == null

    /** 非顶层、且没有状态词 —— 按约定就是「项目 / 分组标题」。 */
    val isProject: Boolean get() = level > 1 && keyword == null

    val date: LocalDate? get() = scheduled?.date ?: deadline?.date
}
