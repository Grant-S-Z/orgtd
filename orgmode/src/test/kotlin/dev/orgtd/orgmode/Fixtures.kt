package dev.orgtd.orgmode

/**
 * 测试用的真实文件。
 *
 * TODO(M5 之前)：`gtd.org` 来自 ~/org/orglist，含个人项目名。开源发布前需要脱敏，
 * 届时同步更新这里的读取逻辑与黄金断言。
 */
object Fixtures {

    fun gtd(): String =
        Fixtures::class.java.getResourceAsStream("/fixtures/gtd.org")
            ?.readBytes()
            ?.decodeToString()
            ?: error("缺少 fixture: /fixtures/gtd.org")
}
