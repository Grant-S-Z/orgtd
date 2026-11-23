package dev.orgtd

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiStateTest {

    private fun task(
        line: Int,
        done: Boolean,
        closed: LocalDateTime? = null,
    ) = TaskUi(
        line = line,
        title = "task-$line",
        done = done,
        date = null,
        time = null,
        repeater = null,
        priority = null,
        tags = emptyList(),
        listName = null,
        closed = closed,
    )

    @Test
    fun `没有已完成任务时为空`() {
        assertTrue(UiState().completed.isEmpty())
    }

    @Test
    fun `已完成只保留最近三十天`() {
        val now = LocalDateTime.now()
        val state = UiState(
            tasks = listOf(
                task(1, done = true, closed = now.minusDays(1)),
                task(2, done = true, closed = now.minusDays(29)),
                task(3, done = true, closed = now.minusDays(31)),
                task(4, done = false),
            ),
        )

        assertEquals(listOf(1, 2), state.completed.map { it.line }.sorted())
    }

    @Test
    fun `已完成按完成时间倒序`() {
        val now = LocalDateTime.now()
        val state = UiState(
            tasks = listOf(
                task(1, done = true, closed = now.minusDays(2)),
                task(2, done = true, closed = now.minusDays(1)),
            ),
        )

        assertEquals(listOf(2, 1), state.completed.map { it.line })
    }

    @Test
    fun `搜索匹配标题正文与标签`() {
        val task = task(1, done = false).copy(
            title = "写周报",
            body = "先找张三要数据",
            tags = listOf("工作"),
        )

        assertTrue(task.matches("周报"))
        assertTrue(task.matches("张三"))
        assertTrue(task.matches("工作"))
        assertTrue(!task.matches("无关"))
    }

    @Test
    fun `没有完成时间的 DONE 仍然展示`() {
        val state = UiState(tasks = listOf(task(7, done = true, closed = null)))
        assertEquals(listOf(7), state.completed.map { it.line })
    }
}
