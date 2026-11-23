package dev.orgtd.orgmode

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OrgFileTest {

    @Test
    fun `真实结构可以逐字节往返`() {
        val source = buildString {
            append("#+TODO: TODO WAITING | DONE CANCELLED\n")
            append("\n")
            append("* 工作\n")
            append("** TODO 写周报\n")
            append("SCHEDULED: <2026-11-24 Mon 09:00>\n")
            append("** DONE 交报销\n")
            append("CLOSED: [2026-11-23 Sun 18:00]\n")
            append(":PROPERTIES:\n")
            append(":ID: 8f14e45f-ceea-467f-a123-11c2b3a4ea11\n")
            append(":END:\n")
        }

        assertEquals(source, OrgFile.parse(source).toString())
    }

    @Test
    fun `空文件与末行无换行`() {
        assertEquals("", OrgFile.parse("").toString())

        val noTrailingNewline = "* TODO 最后一行没有换行"
        assertEquals(noTrailingNewline, OrgFile.parse(noTrailingNewline).toString())
    }

    @Test
    fun `CRLF 原样保留`() {
        val source = "* 工作\r\n** TODO 一条\r\n"
        assertEquals(source, OrgFile.parse(source).toString())
    }

    @Test
    fun `标题行识别`() {
        val file = OrgFile.parse("* 工作\n** TODO 子任务\n\n正文不算标题\n")
        assertEquals(2, file.headlineCount)
        assertEquals(4, file.lineCount)
    }

    @Test
    fun `星号开头的正文不误判`() {
        val file = OrgFile.parse("* 清单\n*不是标题\n")
        assertEquals(1, file.headlineCount)
        assertTrue(file.toString().contains("*不是标题"))
    }
}
