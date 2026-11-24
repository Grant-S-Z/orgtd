package dev.orgtd.orgmode

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OrgDocumentTest {

    private val source = Fixtures.gtd()
    private val document = OrgDocument.parse(source)
    private val now = LocalDateTime.of(2026, 11, 24, 9, 30)

    // ------------------------------------------------------------ 解析

    @Test
    fun `往返字节相等`() {
        assertEquals(source, document.serialize())
    }

    @Test
    fun `解析出八个条目与状态分布`() {
        assertEquals(8, document.entries.size)
        assertEquals(6, document.entries.count { it.keyword == "TODO" })
        assertEquals(2, document.entries.count { it.keyword == "DONE" })
        assertTrue(document.entries.all { !it.isList })
    }

    @Test
    fun `计划行写在属性抽屉之后仍能解析`() {
        val sleep = document.entries.first()

        assertEquals("Sleep", sleep.title)
        assertEquals("DONE", sleep.keyword)
        assertEquals(LocalDate.of(2026, 11, 22), sleep.scheduled?.date)
        assertEquals(LocalTime.of(0, 0), sleep.scheduled?.time)
        assertEquals("+1d", sleep.scheduled?.repeater)
        assertEquals(LocalDateTime.of(2026, 11, 22, 11, 14), sleep.closed)
        assertEquals("[2026-11-22 Sun 11:14]", sleep.properties["MODIFIED"])
    }

    @Test
    fun `只有日期的计划行没有时刻`() {
        val exercise = document.entryAt(lineOf("* TODO Exercise"))

        assertNotNull(exercise)
        assertEquals(LocalDate.of(2026, 11, 22), exercise!!.scheduled?.date)
        assertNull(exercise.scheduled?.time)
        assertEquals("+1d", exercise.scheduled?.repeater)
    }

    // ------------------------------------------------------------ 打勾

    @Test
    fun `打勾只改标题行并插入 CLOSED`() {
        val exercise = document.entryAt(lineOf("* TODO Exercise"))!!
        val updated = document.toggleDone(exercise, now)

        val expected = source.replace(
            "* TODO Exercise\nSCHEDULED: <2026-11-22 Sun +1d>\n",
            "* DONE Exercise\nCLOSED: [2026-11-24 Tue 09:30]\nSCHEDULED: <2026-11-22 Sun +1d>\n",
        )
        assertEquals(expected, updated.serialize())
    }

    @Test
    fun `取消打勾会删掉 CLOSED 行`() {
        val sleep = document.entries.first()
        val updated = document.toggleDone(sleep, now)

        val expected = source
            .replace("* DONE Sleep\nCLOSED: [2026-11-22 Sun 11:14]\n", "* TODO Sleep\n")
        assertEquals(expected, updated.serialize())
    }

    @Test
    fun `已经是 DONE 的不重复插入 CLOSED`() {
        val once = document.toggleDone(document.entryAt(lineOf("* TODO Exercise"))!!, now)
        val twice = once.toggleDone(once.entryAt(lineOf("* TODO Exercise"))!!, now)
        assertEquals(source, twice.serialize())
    }

    // ------------------------------------------------------------ 改期

    @Test
    fun `改期保留重复规则`() {
        val exercise = document.entryAt(lineOf("* TODO Exercise"))!!
        val updated = document.setScheduled(
            exercise,
            DateSpec(LocalDate.of(2026, 11, 23), repeater = "+1d"),
        )
        assertTrue(updated.serialize().contains("SCHEDULED: <2026-11-23 Mon +1d>"))
        assertEquals(source.count { it == '\n' }, updated.serialize().count { it == '\n' })
    }

    @Test
    fun `没有计划行时插入 SCHEDULED`() {
        val target = document.entryAt(lineOf("* TODO 修改培养计划"))!!
        val updated = document.setScheduled(target, DateSpec(LocalDate.of(2026, 11, 25)))

        val expected = source.replace(
            "* TODO 修改培养计划\n",
            "* TODO 修改培养计划\nSCHEDULED: <2026-11-25 Wed>\n",
        )
        assertEquals(expected, updated.serialize())
    }

    @Test
    fun `传 null 删除计划行`() {
        val exercise = document.entryAt(lineOf("* TODO Exercise"))!!
        val updated = document.setScheduled(exercise, null)

        val expected = source.replace("SCHEDULED: <2026-11-22 Sun +1d>\n", "")
        assertEquals(expected, updated.serialize())
    }

    // ------------------------------------------------------------ 标题与优先级

    @Test
    fun `改名只动标题行`() {
        val target = document.entryAt(lineOf("* TODO 修改培养计划"))!!
        val updated = document.rename(target, "重写培养计划")
        assertEquals(source.replace("修改培养计划", "重写培养计划"), updated.serialize())
    }

    @Test
    fun `设置优先级`() {
        val target = document.entryAt(lineOf("* TODO 修改培养计划"))!!
        val updated = document.setPriority(target, 'A')
        assertTrue(updated.serialize().contains("* TODO [#A] 修改培养计划\n"))
    }

    // ------------------------------------------------------------ 新建与删除

    @Test
    fun `追加任务到文件末尾`() {
        val updated = document.appendTask("买牛奶", "11111111-2222-3333-4444-555555555555")
        val expected = source +
            "* TODO 买牛奶\n" +
            "  :PROPERTIES:\n" +
            "  :ID: 11111111-2222-3333-4444-555555555555\n" +
            "  :END:\n"
        assertEquals(expected, updated.serialize())
        assertEquals(9, updated.entries.size)
        assertEquals("11111111-2222-3333-4444-555555555555", updated.entries.last().id)
    }

    @Test
    fun `新建带排期`() {
        val updated = document.appendTask(
            "交报告",
            "id-1",
            scheduled = LocalDate.of(2026, 11, 26),
        )
        assertTrue(updated.serialize().contains("SCHEDULED: <2026-11-26 Thu>"))
        assertEquals(LocalDate.of(2026, 11, 26), updated.entries.last().scheduled?.date)
    }

    @Test
    fun `删除条目只影响该条目`() {
        val target = document.entryAt(lineOf("* TODO Exercise"))!!
        val updated = document.delete(target)
        val expected = source.replace("* TODO Exercise\nSCHEDULED: <2026-11-22 Sun +1d>\n", "")
        assertEquals(expected, updated.serialize())
        assertEquals(7, updated.entries.size)
    }

    // ------------------------------------------------------------ ID

    @Test
    fun `没有抽屉时整块插入 ID`() {
        val target = document.entryAt(lineOf("* TODO 修改培养计划"))!!
        val updated = document.ensureId(target, "fixed-id")

        val expected = source.replace(
            "* TODO 修改培养计划\n",
            "* TODO 修改培养计划\n  :PROPERTIES:\n  :ID: fixed-id\n  :END:\n",
        )
        assertEquals(expected, updated.serialize())
        assertNotNull(updated.entryAt(lineOf("* TODO 修改培养计划"))?.id)
    }

    @Test
    fun `已有抽屉时插在 END 之前且保留原属性`() {
        val sleep = document.entries.first()
        val updated = document.ensureId(sleep, "fixed-id")

        val expected = source.replace(
            "  :MODIFIED: [2026-11-22 Sun 11:14]\n  :END:\n",
            "  :MODIFIED: [2026-11-22 Sun 11:14]\n  :ID: fixed-id\n  :END:\n",
        )
        assertEquals(expected, updated.serialize())
        assertEquals("fixed-id", updated.entries.first().id)
        assertEquals("[2026-11-22 Sun 11:14]", updated.entries.first().properties["MODIFIED"])
    }

    // ------------------------------------------------------------ 边界

    @Test
    fun `CRLF 文件保持 CRLF`() {
        val crlf = source.replace("\n", "\r\n")
        val doc = OrgDocument.parse(crlf)
        assertEquals(crlf, doc.serialize())

        val updated = doc.toggleDone(doc.entryAt(lineOf("* TODO Exercise"))!!, now)
        assertTrue(updated.serialize().contains("CLOSED: [2026-11-24 Tue 09:30]\r\n"))
        assertTrue(!updated.serialize().contains("\n\n"))
    }

    @Test
    fun `末行没有换行也能解析和追加`() {
        val text = "* TODO 唯一一条"
        val doc = OrgDocument.parse(text)
        assertEquals(1, doc.entries.size)
        assertEquals(text, doc.serialize())

        val appended = doc.appendTask("第二条", "id-2").serialize()
        assertTrue(appended.startsWith("* TODO 唯一一条\n"))
    }

    @Test
    fun `空文件`() {
        assertEquals("", OrgDocument.parse("").serialize())
        assertTrue(OrgDocument.parse("").entries.isEmpty())
    }

    @Test
    fun `未声明关键字的字母词不当成状态`() {
        val doc = OrgDocument.parse("* API 设计\n")
        assertEquals(1, doc.entries.size)
        assertNull(doc.entries.first().keyword)
        assertEquals("API 设计", doc.entries.first().title)
    }

    @Test
    fun `文件级声明里的关键字能被识别`() {
        val doc = OrgDocument.parse("#+TODO: TODO WAITING | DONE CANCELLED\n* WAITING 等回复\n")
        assertEquals("WAITING", doc.entries.first().keyword)
        assertEquals("等回复", doc.entries.first().title)
    }

    @Test
    fun `同一行里的多个计划 token 都能解析`() {
        val doc = OrgDocument.parse("* TODO 一件事\nSCHEDULED: <2026-11-22 Sun> DEADLINE: <2026-11-25 Wed>\n")
        val entry = doc.entries.first()
        assertEquals(LocalDate.of(2026, 11, 22), entry.scheduled?.date)
        assertEquals(LocalDate.of(2026, 11, 25), entry.deadline?.date)

        val updated = doc.setScheduled(entry, DateSpec(LocalDate.of(2026, 11, 23)))
        assertTrue(updated.serialize().contains("SCHEDULED: <2026-11-23 Mon> DEADLINE: <2026-11-25 Wed>"))
    }

    @Test
    fun `中文标签与优先级`() {
        val doc = OrgDocument.parse("* TODO [#B] 写周报 :工作:@电脑:\n")
        val entry = doc.entries.first()
        assertEquals('B', entry.priority)
        assertEquals("写周报", entry.title)
        assertEquals(listOf("工作", "@电脑"), entry.tags)

        val renamed = doc.rename(entry, "写月报")
        assertEquals("* TODO [#B] 写月报 :工作:@电脑:\n", renamed.serialize())
    }

    @Test
    fun `清单下的任务归属正确`() {
        val doc = OrgDocument.parse("* 工作\n** TODO 写周报\n** DONE 交报销\n* 生活\n** TODO 买牛奶\n")
        assertEquals(5, doc.entries.size)
        assertTrue(doc.entries[0].isList)
        assertEquals(1, doc.entryAt(0)?.level)

        val work = doc.entries[0]
        assertEquals(3, work.endExclusive)

        val appended = doc.appendTask("订机票", "id-3", list = work)
        assertEquals(
            "* 工作\n** TODO 写周报\n** DONE 交报销\n** TODO 订机票\n  :PROPERTIES:\n  :ID: id-3\n  :END:\n* 生活\n** TODO 买牛奶\n",
            appended.serialize(),
        )
    }

    // ------------------------------------------------------------ 重复任务

    @Test
    fun `加号重复按原定日期补到未来`() {
        val doc = OrgDocument.parse("* TODO 喝水\nSCHEDULED: <2026-11-22 Sun +1d>\n")
        val next = doc.advanceRepeat(doc.entries.first(), LocalDate.of(2026, 11, 24))!!

        assertTrue(next.serialize().contains("SCHEDULED: <2026-11-25 Wed +1d>"))
        assertEquals("TODO", next.entries.first().keyword)
    }

    @Test
    fun `点号重复从完成日算起`() {
        val doc = OrgDocument.parse("* TODO 浇花\nSCHEDULED: <2026-11-01 Sun .+3d>\n")
        val next = doc.advanceRepeat(doc.entries.first(), LocalDate.of(2026, 11, 24))!!

        assertTrue(next.serialize().contains("SCHEDULED: <2026-11-27 Fri .+3d>"))
    }

    @Test
    fun `非重复任务不推进`() {
        val doc = OrgDocument.parse("* TODO 一次性\nSCHEDULED: <2026-11-22 Sun>\n")
        assertNull(doc.advanceRepeat(doc.entries.first(), LocalDate.of(2026, 11, 24)))
    }

    // ------------------------------------------------------------ 清单操作

    @Test
    fun `新建清单追加到末尾`() {
        val doc = OrgDocument.parse("* 工作\n** TODO a\n")
        assertEquals("* 工作\n** TODO a\n* 生活\n", doc.appendList("生活").serialize())
    }

    @Test
    fun `删除清单降级并挂到已删除`() {
        val doc = OrgDocument.parse("* 工作\n** TODO a\n* 生活\n** TODO b\n")
        val work = doc.entries.first { it.title == "工作" }
        val next = doc.archiveList(work, LocalDateTime.of(2026, 11, 24, 10, 0))

        val expected =
            "* 生活\n** TODO b\n* 已删除\n** 工作\n  :PROPERTIES:\n" +
                "  :DELETED_AT: [2026-11-24 Tue 10:00]\n  :END:\n*** TODO a\n"
        assertEquals(expected, next.serialize())
    }

    @Test
    fun `清单上移整块交换`() {
        val doc = OrgDocument.parse("* 工作\n** TODO a\n* 生活\n** TODO b\n")
        val life = doc.entries.first { it.title == "生活" }
        assertEquals(
            "* 生活\n** TODO b\n* 工作\n** TODO a\n",
            doc.moveList(life, -1).serialize(),
        )
    }

    @Test
    fun `已删除存在时新清单插在它前面`() {
        val doc = OrgDocument.parse("* 工作\n* 已删除\n** 旧清单\n")
        val next = doc.appendList("生活").serialize()
        assertEquals("* 工作\n* 生活\n* 已删除\n** 旧清单\n", next)
    }

    // ------------------------------------------------------------ 属性

    @Test
    fun `没有抽屉时 setProperty 会创建抽屉`() {
        val target = document.entryAt(lineOf("* TODO 修改培养计划"))!!
        val updated = document.setProperty(target, "ARCHIVED_AT", "[2026-11-24 Tue 16:00]")

        val expected = source.replace(
            "* TODO 修改培养计划\n",
            "* TODO 修改培养计划\n  :PROPERTIES:\n  :ARCHIVED_AT: [2026-11-24 Tue 16:00]\n  :END:\n",
        )
        assertEquals(expected, updated.serialize())
    }

    @Test
    fun `已有抽屉时 setProperty 覆盖同名属性`() {
        val sleep = document.entries.first()
        val updated = document.setProperty(sleep, "MODIFIED", "[2026-11-24 Tue 16:00]")

        assertEquals("[2026-11-24 Tue 16:00]", updated.entries.first().properties["MODIFIED"])
        // 原文件本来就有两条 MODIFIED（Sleep / Get up），覆盖不应新增
        assertEquals(2, Regex(":MODIFIED:").findAll(updated.serialize()).count())
    }

    @Test
    fun `removeProperty 删除属性并在抽屉变空时清掉抽屉`() {
        val sleep = document.entries.first()
        val withId = document.setProperty(sleep, "ID", "x")
        val withoutId = withId.removeProperty(withId.entries.first(), "ID")
        assertEquals(source, withoutId.serialize())

        val single = OrgDocument.parse("* TODO 一件\n  :PROPERTIES:\n  :ID: abc\n  :END:\n")
        assertEquals(
            "* TODO 一件\n",
            single.removeProperty(single.entries.first(), "ID").serialize(),
        )
    }

    private fun lineOf(headline: String): Int {
        val lines = source.split("\n")
        val index = lines.indexOfFirst { it == headline }
        check(index >= 0) { "fixture 里找不到标题行: $headline" }
        return index
    }
}
