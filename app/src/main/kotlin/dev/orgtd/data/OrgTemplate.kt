package dev.orgtd.data

import dev.orgtd.AppStrings

/** 新建 org 文件时写入的最小模板，让第一次打开的人立刻有东西可点。 */
object OrgTemplate {

    fun initial(strings: AppStrings): String = buildString {
        append("#+TITLE: orgtd\n")
        append("\n")
        append("* Inbox\n")
        append("** TODO ${strings.sampleTask}\n")
        append("   ${strings.sampleNote}\n")
        append("* ${strings.sampleList}\n")
    }
}
