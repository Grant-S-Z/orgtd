package dev.orgtd

import dev.orgtd.orgmode.OrgDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskMapperTest {

    private val source = """
        * 工作
        ** TODO 写周报
        SCHEDULED: <2026-11-24 Tue>
           :PROPERTIES:
           :ID: abc
           :END:
        需要先整理数据

        联系人：张三
        *** DONE 子任务
    """.trimIndent() + "\n"

    @Test
    fun `正文提取跳过计划行与抽屉`() {
        val task = TaskMapper.toTasks(OrgDocument.parse(source)).first { it.title == "写周报" }

        assertTrue(task.body.contains("需要先整理数据"))
        assertTrue(task.body.contains("联系人：张三"))
        assertFalse(task.body.contains("SCHEDULED"))
        assertFalse(task.body.contains(":ID:"))
        assertFalse(task.body.contains("子任务"))
    }

    @Test
    fun `没有正文时为空串`() {
        val doc = OrgDocument.parse("* 工作\n** TODO 只有标题\n")
        assertEquals("", TaskMapper.toTasks(doc).first { it.title == "只有标题" }.body)
    }

    @Test
    fun `子任务计数只算直接下级`() {
        val doc = OrgDocument.parse(
            "* 工作\n** TODO 父任务\n*** TODO 子一\n*** TODO 子二\n** TODO 另一个\n",
        )
        val tasks = TaskMapper.toTasks(doc)
        assertEquals(2, tasks.first { it.title == "父任务" }.children)
        assertEquals(0, tasks.first { it.title == "另一个" }.children)
    }
}
