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
    fun `正文保留换行并带上归档标记`() {
        val doc = OrgDocument.parse(
            "* 工作\n** DONE 写周报\nCLOSED: [2026-11-24 Tue 16:00]\n  :PROPERTIES:\n  :ARCHIVED_AT: [2026-11-24 Tue 16:00]\n  :END:\n第一行\n第二行\n",
        )
        val task = TaskMapper.toTasks(doc).first { it.title == "写周报" }

        assertEquals("第一行\n第二行", task.body)
        assertTrue(task.archived)
    }

    @Test
    fun `三层结构映射出项目与完成状态`() {
        val doc = OrgDocument.parse(
            """
* Work
** 项目甲 :PROJECT_DONE:
*** TODO 待办
** 项目乙
*** TODO 待办二
* PhD
** TODO 直挂任务
""".trimIndent() + "\n",
        )
        val tasks = TaskMapper.toTasks(doc)
        val lists = TaskMapper.toLists(doc, tasks)
        val groups = TaskMapper.toGroups(doc, tasks)

        val done = tasks.first { it.title == "待办" }
        assertEquals("项目甲", done.projectName)
        assertEquals("Work", done.listName)
        assertTrue(done.projectCompleted)

        val open = tasks.first { it.title == "待办二" }
        assertEquals("项目乙", open.projectName)
        assertTrue(!open.projectCompleted)

        // 已完成项目的任务不再计入清单待办
        assertEquals(1, lists.first { it.name == "Work" }.open)
        assertEquals(2, groups.size)
        assertTrue(groups.first { it.name == "项目甲" }.completed)
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
