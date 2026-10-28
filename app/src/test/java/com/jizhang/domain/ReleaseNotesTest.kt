package com.jizhang.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReleaseNotesTest {
    @Test
    fun `headings lose their hashes`() {
        assertEquals("更新内容\n\n新增", ReleaseNotes.plain("## 更新内容\n\n### 新增"))
        assertEquals("标题", ReleaseNotes.plain("# 标题 #"))
    }

    @Test
    fun `bullets become dots and emphasis goes away`() {
        val md = "- **新增** 应用内更新\n* 修复 `Bug`\n+ ~~旧~~ 新"
        assertEquals("• 新增 应用内更新\n• 修复 Bug\n• 旧 新", ReleaseNotes.plain(md))
    }

    @Test
    fun `numbered lists and indented bullets keep their shape`() {
        assertEquals("1. 第一\n2. 第二\n  • 子项", ReleaseNotes.plain("1. 第一\n2. 第二\n  - 子项"))
    }

    @Test
    fun `links and images keep only their text`() {
        assertEquals("见 完整说明", ReleaseNotes.plain("见 [完整说明](https://github.com/a/b/releases)"))
        assertEquals("截图", ReleaseNotes.plain("![截图](https://example.com/a.png)"))
    }

    @Test
    fun `quotes, rules and html tags are dropped`() {
        assertEquals("提示", ReleaseNotes.plain("> 提示"))
        assertEquals("上\n\n下", ReleaseNotes.plain("上\n---\n下"))
        assertEquals("换行", ReleaseNotes.plain("换<br>行"))
    }

    @Test
    fun `code fences are removed but their lines stay`() {
        assertEquals("先\ngradlew build\n后", ReleaseNotes.plain("先\n```bash\ngradlew build\n```\n后"))
    }

    @Test
    fun `underscores inside words and plain stars survive`() {
        assertEquals("snake_case_name", ReleaseNotes.plain("snake_case_name"))
        assertEquals("2 * 3 = 6", ReleaseNotes.plain("2 * 3 = 6"))
    }

    @Test
    fun `windows line endings and blank runs are tidied`() {
        assertEquals("一\n\n二", ReleaseNotes.plain("一\r\n\r\n\r\n\r\n二\r\n"))
    }

    @Test
    fun `empty notes stay empty`() {
        assertEquals("", ReleaseNotes.plain(""))
        assertFalse(ReleaseNotes.plain("\n\n  \n").isNotEmpty())
    }
}
