package com.jizhang.data

import com.jizhang.domain.Category
import com.jizhang.domain.CategoryCatalog
import com.jizhang.domain.CategoryPref
import com.jizhang.domain.EntryKind
import com.jizhang.domain.Hue
import com.jizhang.ui.theme.IconLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class SharkImportTest {
    private val gb = Charset.forName("GB18030")
    private val header = "\"日期\",\"收支类型\",\"类别\",\"账户\",\"金额\",\"备注\""

    /** A file shaped like the export: every field quoted, CRLF line ends. */
    private fun csv(vararg rows: String) = (listOf(header) + rows).joinToString("\r\n", postfix = "\r\n")

    private fun row(date: String, type: String, category: String, amount: String, note: String = "") =
        "\"$date\",\"$type\",\"$category\",\"未关联\",\"$amount\",\"$note\""

    /** A bare two-column file, for the date and amount cases. */
    private fun simple(vararg rows: String) = (listOf("日期,金额") + rows).joinToString("\r\n")

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d)

    private val sample = csv(
        row("2023年12月31日", "支出", "餐饮", "16.8", "午饭"),
        row("2023年12月31日", "支出", "水果", "173"),
        row("2023年12月30日", "收入", "其它", "1000.00", "红包"),
    )

    // Text ----------------------------------------------------------------------------------

    @Test fun readsGb18030WithoutABom() {
        val bytes = sample.toByteArray(gb)
        // Not accidentally valid UTF-8, so the GB18030 path is what decodes it.
        assertFalse(bytes.contentEquals(sample.toByteArray(Charsets.UTF_8)))
        assertEquals(sample, SharkImport.decode(bytes))
        val parsed = SharkImport.parse(bytes)
        assertTrue(parsed.errors.isEmpty())
        assertEquals(3, parsed.rows.size)
        assertEquals(
            SharkRow(2, day(2023, 12, 31), null, EntryKind.Expense, "餐饮", 1680, "午饭"),
            parsed.rows[0],
        )
        assertEquals(17300, parsed.rows[1].cents)
        assertEquals("水果", parsed.rows[1].category)
        assertEquals("", parsed.rows[1].note)
        assertEquals(EntryKind.Income, parsed.rows[2].kind)
        assertEquals(100000, parsed.rows[2].cents)
        assertEquals("其它", parsed.rows[2].category)
    }

    @Test fun readsUtf8WithAndWithoutBom() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val plain = sample.toByteArray(Charsets.UTF_8)
        for (bytes in listOf(bom + plain, plain)) {
            assertEquals(sample, SharkImport.decode(bytes))
            val parsed = SharkImport.parse(bytes)
            assertEquals(3, parsed.rows.size)
            assertEquals("餐饮", parsed.rows[0].category)
        }
    }

    @Test fun readsUtf16WithABom() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + sample.toByteArray(Charsets.UTF_16LE)
        assertEquals(3, SharkImport.parse(bytes).rows.size)
    }

    @Test fun textThatIsOnlyAsciiIsStillUtf8() {
        val parsed = SharkImport.parse("date,amount\n2024-01-01,5\n".toByteArray())
        assertEquals(1, parsed.rows.size)
        assertEquals(500, parsed.rows[0].cents)
    }

    // CSV -----------------------------------------------------------------------------------

    @Test fun quotesCommasAndLineBreaksInsideFields() {
        val text = csv(
            row("2024-1-5", "支出", "餐饮", "12.5", "买了 \"\"苹果\"\", 梨"),
            row("2024-1-5", "支出", "餐饮", "3", "第一行\r\n第二行"),
            row("2024-1-6", "支出", "餐饮", "x", "坏"),
        )
        val parsed = SharkImport.parse(text)
        assertEquals(2, parsed.rows.size)
        assertEquals("买了 \"苹果\", 梨", parsed.rows[0].note)
        assertEquals("第一行\n第二行", parsed.rows[1].note)
        // The multi-line note started on line 3 and took two; the bad row is on line 5.
        assertEquals(3, parsed.rows[1].line)
        assertEquals(listOf(5), parsed.errors.map { it.line })
    }

    @Test fun lastRecordNeedsNoLineBreak() {
        assertEquals(2, SharkImport.parse("日期,金额\n2024-01-01,5\n2024-01-02,6").rows.size)
        assertEquals(1, SharkImport.parse("日期,金额\n2024-01-01,5\n\n\n").rows.size)
    }

    @Test fun unclosedQuoteIsAnErrorNotACrash() {
        val parsed = SharkImport.parse("日期,金额,备注\n2024-01-01,5,ok\n2024-01-02,6,\"never closed\n2024-01-03,7,x")
        // The broken line is reported; the quote does not swallow the rows below it.
        assertEquals(listOf(2, 4), parsed.rows.map { it.line })
        assertEquals(listOf(3), parsed.errors.map { it.line })
        assertTrue(parsed.errors[0].reason.contains("引号"))
        assertEquals("x", parsed.rows[1].note)
    }

    @Test fun lineEndsMayBeCrlfLfOrCr() {
        for (eol in listOf("\r\n", "\n", "\r")) {
            val parsed = SharkImport.parse(listOf("日期,金额", "2024-01-01,5", "2024-01-02,6", "").joinToString(eol))
            assertEquals(listOf(2, 3), parsed.rows.map { it.line })
        }
    }

    @Test fun aBomLeftInTheTextIsIgnored() {
        val parsed = SharkImport.parse("${Char(0xFEFF)}\"日期\",\"金额\"\r\n\"2024-01-01\",\"5\"\r\n")
        assertEquals(1, parsed.rows.size)
    }

    // Header --------------------------------------------------------------------------------

    @Test fun columnOrderAliasesAndExtraColumns() {
        val text = "\"金额\",\"类型\",\"分类\",\"时间\",\"说明\",\"余额\"\r\n" +
            "\"12.5\",\"支出\",\"餐饮\",\"2024-03-01\",\"晚饭\",\"999\"\r\n"
        val r = SharkImport.parse(text).rows.single()
        assertEquals(1250, r.cents)
        assertEquals(EntryKind.Expense, r.kind)
        assertEquals("餐饮", r.category)
        assertEquals(day(2024, 3, 1), r.date)
        assertEquals("晚饭", r.note)
    }

    @Test fun categoryAccountAndNoteColumnsAreOptional() {
        val parsed = SharkImport.parse("日期,收支类型,金额\n2024/3/2,收入,8\n")
        val r = parsed.rows.single()
        assertEquals(EntryKind.Income, r.kind)
        assertEquals("", r.category)
        assertEquals("", r.note)
    }

    @Test fun withoutATypeColumnTheSignDecides() {
        val parsed = SharkImport.parse(simple("2024/3/2,-8", "2024/3/3,+9", "2024/3/4,¥10"))
        assertEquals(listOf(EntryKind.Expense, EntryKind.Income, EntryKind.Income), parsed.rows.map { it.kind })
        assertEquals(listOf(800L, 900L, 1000L), parsed.rows.map { it.cents })
    }

    @Test fun skipsLinesBeforeTheHeader() {
        val parsed = SharkImport.parse("鲨鱼记账导出\n\n日期,金额\n2024-01-01,5\n")
        assertEquals(1, parsed.rows.size)
        assertEquals(4, parsed.rows[0].line)
    }

    @Test fun tabsAndSemicolonsWorkToo() {
        assertEquals(1, SharkImport.parse("日期\t金额\n2024-01-01\t5").rows.size)
        assertEquals(1, SharkImport.parse("日期;金额\n2024-01-01;5").rows.size)
        // A title line without any delimiter above the header must not decide it.
        assertEquals(1, SharkImport.parse("鲨鱼记账导出\n日期\t金额\n2024-01-01\t5").rows.size)
        assertEquals(1, SharkImport.parse("鲨鱼记账导出\n日期;金额\n2024-01-01;5").rows.size)
    }

    @Test fun anUnrecognisableHeaderFailsWithAChineseMessage() {
        val e = assertThrows(SharkFormatException::class.java) { SharkImport.parse("你好,世界\r\n1,2\r\n") }
        assertTrue(e.message!!.contains("日期") && e.message!!.contains("金额"))
        // A date alone is not enough either.
        assertThrows(SharkFormatException::class.java) { SharkImport.parse("日期,备注\n2024-01-01,x") }
        assertEquals("文件是空的", assertThrows(SharkFormatException::class.java) { SharkImport.parse(" \n\n") }.message)
        assertThrows(SharkFormatException::class.java) { SharkImport.parse(ByteArray(0)) }
    }

    @Test fun otherKindsOfFilesGetTheirOwnMessage() {
        // An .xlsx is a zip.
        val zip = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4, 0, 0)
        assertTrue(assertThrows(SharkFormatException::class.java) { SharkImport.parse(zip) }.message!!.contains("Excel"))
        // This app's own backup is JSON.
        val json = "{\"version\":5,\"records\":[]}".toByteArray()
        assertTrue(assertThrows(SharkFormatException::class.java) { SharkImport.parse(json) }.message!!.contains("备份"))
    }

    @Test fun aFileOverTheLimitIsRefusedWhileReading() {
        val zeros = object : java.io.InputStream() {
            override fun read() = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int = len.also { java.util.Arrays.fill(b, off, off + len, 0) }
        }
        val e = assertThrows(SharkFormatException::class.java) { SharkImport.read(zeros, limit = 1024 * 1024) }
        assertTrue(e.message!!.contains("太大"))
        assertEquals(3, SharkImport.read(java.io.ByteArrayInputStream(byteArrayOf(1, 2, 3))).size)
    }

    // Rows ----------------------------------------------------------------------------------

    @Test fun badLinesAreCollectedAndTheRestStillImports() {
        val text = csv(
            row("2023年12月31日", "支出", "餐饮", "16.8"),
            row("不是日期", "支出", "餐饮", "1"),
            row("2023年12月31日", "支出", "餐饮", "abc"),
            row("2023年12月31日", "转账", "餐饮", "1"),
            "\"2023年12月31日\",\"支出\"",
            row("2023年12月31日", "支出", "餐饮", "0"),
            row("2023年12月31日", "支出", "餐饮", ""),
            "",
            row("2023年12月30日", "收入", "其它", "5"),
        )
        val parsed = SharkImport.parse(text)
        assertEquals(listOf(2, 10), parsed.rows.map { it.line })
        assertEquals(listOf(3, 4, 5, 6, 7, 8), parsed.errors.map { it.line })
        val reasons = parsed.errors.map { it.reason }
        assertTrue(reasons[0].contains("日期") && reasons[0].contains("不是日期"))
        assertTrue(reasons[1].contains("金额"))
        assertTrue(reasons[2].contains("收支类型") && reasons[2].contains("转账"))
        assertTrue(reasons[3].contains("列数"))
        assertTrue(reasons[4].contains("0"))
        assertTrue(reasons[5].contains("空"))
    }

    @Test fun dateFormatsWithAndWithoutATime() {
        val parsed = SharkImport.parse(
            simple(
                "2023年12月31日,1",
                "2024-1-2,1",
                "2024/01/03 9:05,1",
                "2024-01-04 09:05:30,1",
                "2024年5月6日 18:30,1",
                "2024.7.8,1",
                "2024-01-04T10:00,1",
            ),
        )
        assertTrue(parsed.errors.isEmpty())
        assertEquals(
            listOf(day(2023, 12, 31), day(2024, 1, 2), day(2024, 1, 3), day(2024, 1, 4), day(2024, 5, 6), day(2024, 7, 8), day(2024, 1, 4)),
            parsed.rows.map { it.date },
        )
        assertEquals(
            listOf(null, null, LocalTime.of(9, 5), LocalTime.of(9, 5, 30), LocalTime.of(18, 30), null, LocalTime.of(10, 0)),
            parsed.rows.map { it.time },
        )
    }

    @Test fun impossibleDatesAreErrors() {
        val parsed = SharkImport.parse(simple("2023年2月30日,1", "2024-13-01,1", "2024-01-05 25:00,1", "5/6,1", "2024-01-01,1"))
        assertEquals(listOf(2, 3, 4, 5), parsed.errors.map { it.line })
        assertEquals(1, parsed.rows.size)
    }

    @Test fun amountsBecomeCents() {
        fun cents(amount: String, type: String = "支出") =
            SharkImport.parse(csv(row("2024-01-01", type, "餐饮", amount))).rows.singleOrNull()?.cents
        assertEquals(123450L, cents("¥1,234.5"))
        assertEquals(1680L, cents("-16.8"))
        assertEquals(500L, cents("-5", "收入"))
        assertEquals(1686L, cents("16.855"))
        assertEquals(1685L, cents("16.854"))
        assertEquals(150L, cents(" 1.5 "))
        assertEquals(1L, cents("0.005"))
        // Up to what the add sheet can type: seven digits before the point.
        assertEquals(999_999_999L, cents("9999999.99"))
        assertNull(cents("0"))
        assertNull(cents("0.004"))
        assertNull(cents("1e3"))
        assertNull(cents("1.2.3"))
        assertNull(cents("10000000"))
        assertNull(cents("99999999999999999999999"))
    }

    @Test fun aNegativeAmountWithATypeFollowsTheType() {
        val r = SharkImport.parse(csv(row("2024-01-01", "收入", "其它", "-5"))).rows.single()
        assertEquals(EntryKind.Income, r.kind)
        assertEquals(500, r.cents)
    }

    // Times ---------------------------------------------------------------------------------

    private fun times(vararg lines: String) = SharkImport.parse(simple(*lines)).let { SharkImport.timesOf(it.rows) }

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int, s: Int = 0) = LocalDateTime.of(y, m, d, h, min, s)

    @Test fun rowsOfADayKeepTheirFileOrder() {
        // Newest first: the first row of a day is the latest, one second apart going down.
        val t = times("2023-12-31,1", "2023-12-31,1", "2023-12-31,1", "2023-12-30,1", "2023-12-30,1")
        assertEquals(
            listOf(at(2023, 12, 31, 12, 0), at(2023, 12, 31, 11, 59, 59), at(2023, 12, 31, 11, 59, 58), at(2023, 12, 30, 12, 0), at(2023, 12, 30, 11, 59, 59)),
            t,
        )
    }

    @Test fun anOldestFirstFileKeepsItsOrderToo() {
        val t = times("2023-12-30,1", "2023-12-30,1", "2023-12-31,1")
        assertEquals(listOf(at(2023, 12, 30, 11, 59, 59), at(2023, 12, 30, 12, 0), at(2023, 12, 31, 12, 0)), t)
    }

    @Test fun rowsThatCarryATimeKeepIt() {
        val t = times("2023-12-31,1", "2023-12-31 09:30,1", "2023-12-31,1")
        assertEquals(listOf(at(2023, 12, 31, 12, 0), at(2023, 12, 31, 9, 30), at(2023, 12, 31, 11, 59, 59)), t)
    }

    // Categories ----------------------------------------------------------------------------

    private val pet = Category("c1", "宠物", EntryKind.Expense, Hue.Fun, "paw", custom = true)
    private val sideJob = Category("c2", "兼职", EntryKind.Income, Hue.Home, "briefcase", custom = true)
    private val catalog = CategoryCatalog(
        customs = listOf(pet, sideJob),
        prefs = mapOf("food" to CategoryPref("food", name = "吃饭")),
    )

    private fun plan(rows: List<String>, catalog: CategoryCatalog = CategoryCatalog.Builtin, existing: List<SharkExisting> = emptyList()) =
        SharkImport.plan(SharkImport.parse(csv(*rows.toTypedArray())), catalog, existing)

    private fun target(type: String, name: String, catalog: CategoryCatalog = this.catalog) =
        plan(listOf(row("2024-01-01", type, name, "1")), catalog).entries.single().target

    @Test fun exactLabelsWithinTheSameKind() {
        assertEquals(SharkTarget.Existing("shop"), target("支出", "购物"))
        assertEquals(SharkTarget.Existing("salary"), target("收入", "工资"))
        assertEquals(SharkTarget.Existing("other"), target("支出", "其他"))
        assertEquals(SharkTarget.Existing("other_in"), target("收入", "其他"))
        // 工资 is an income category; as an expense it is not found.
        assertTrue(target("支出", "工资") is SharkTarget.Create)
    }

    @Test fun renamedBuiltinsAndCustomCategoriesCount() {
        // The user's name for food, and the original one still finds it.
        assertEquals(SharkTarget.Existing("food"), target("支出", "吃饭"))
        assertEquals(SharkTarget.Existing("food"), target("支出", "餐饮"))
        assertEquals(SharkTarget.Existing("c1"), target("支出", "宠物"))
        assertEquals(SharkTarget.Existing("c2"), target("收入", "兼职"))
        // Same name, other kind: not the same category.
        assertTrue(target("支出", "兼职") is SharkTarget.Create)
    }

    @Test fun synonymsAndBlankNames() {
        assertEquals(SharkTarget.Existing("other"), target("支出", "其它"))
        assertEquals(SharkTarget.Existing("other_in"), target("收入", "其它"))
        assertEquals(SharkTarget.Existing("home"), target("支出", "房租"))
        assertEquals(SharkTarget.Existing("home"), target("支出", "住房"))
        assertEquals(SharkTarget.Existing("gift"), target("支出", "红包"))
        assertEquals(SharkTarget.Existing("gift"), target("支出", "人情往来"))
        assertEquals(SharkTarget.Existing("salary"), target("收入", "薪资"))
        assertEquals(SharkTarget.Existing("invest"), target("收入", "利息"))
        assertEquals(SharkTarget.Existing("bonus"), target("收入", "年终奖"))
        assertEquals(SharkTarget.Existing("other"), target("支出", ""))
        // A synonym only applies in its own kind, and 其他 follows the user's rename.
        assertTrue(target("收入", "红包") is SharkTarget.Create)
        val renamed = CategoryCatalog(prefs = mapOf("other" to CategoryPref("other", name = "杂项")))
        assertEquals(SharkTarget.Existing("other"), target("支出", "其它", renamed))
        assertEquals(SharkTarget.Existing("other"), target("支出", "杂项", renamed))
    }

    @Test fun unknownNamesBecomeOneNewCategoryEach() {
        val plan = plan(
            listOf(
                row("2024-01-03", "支出", "书籍", "30"),
                row("2024-01-02", "支出", "书籍", "20"),
                row("2024-01-02", "支出", "水果", "5"),
                row("2024-01-01", "收入", "红包", "8"),
                row("2024-01-01", "支出", "杂项", "8"),
            ),
            catalog,
        )
        assertEquals(
            listOf("书籍" to EntryKind.Expense, "水果" to EntryKind.Expense, "红包" to EntryKind.Income, "杂项" to EntryKind.Expense),
            plan.newCategories.map { it.name to it.kind },
        )
        // Two rows of 书籍 share the one new category.
        assertEquals(plan.entries[0].target, plan.entries[1].target)
        // 杂项 says nothing a keyword can pick up, so it gets the plain icon (a name that merely
        // contains a keyword, like 红包2, would take that keyword's icon).
        assertEquals(listOf("book", "fruit", "hongbao", "other"), plan.newCategories.map { it.icon })
        assertEquals("hongbao", SharkImport.iconFor("红包2"))
        // Hues continue after the two categories the user already made, and skip grey (其他's).
        assertEquals(listOf(Hue.Shop, Hue.Daily, Hue.Fun, Hue.Home), plan.newCategories.map { it.hue })
    }

    @Test fun newNamesAreCutToSixCharactersAndMatchOnReimport() {
        val long = "超长超长超长分类"
        val first = plan(listOf(row("2024-01-01", "支出", long, "1")))
        val made = first.newCategories.single()
        assertEquals("超长超长超长", made.name)
        // The next import finds the category the first one made, by its cut name.
        val after = CategoryCatalog(customs = listOf(Category("c9", made.name, made.kind, made.hue, made.icon, custom = true)))
        val again = plan(listOf(row("2024-01-01", "支出", long, "1")), after)
        assertTrue(again.newCategories.isEmpty())
        assertEquals(SharkTarget.Existing("c9"), again.entries.single().target)
    }

    @Test fun iconsFollowKeywords() {
        assertEquals("book", SharkImport.iconFor("书籍"))
        assertEquals("fruit", SharkImport.iconFor("水果"))
        assertEquals("snack", SharkImport.iconFor("零食"))
        assertEquals("box", SharkImport.iconFor("快递"))
        assertEquals("lipstick", SharkImport.iconFor("美容"))
        assertEquals("laundry", SharkImport.iconFor("洗衣"))
        assertEquals("suitcase", SharkImport.iconFor("酒店"))
        assertEquals("other", SharkImport.iconFor("杂七杂八"))
    }

    @Test fun everyIconTheTableNamesExists() {
        val known = IconLibrary.paths.keys + setOf("salary", "bonus", "invest", "other")
        val missing = SharkImport.ICON_RULES.map { it.second }.filter { it !in known }
        assertTrue("unknown icons: $missing", missing.isEmpty())
    }

    // Duplicates ----------------------------------------------------------------------------

    private val twice = listOf(
        row("2023年12月31日", "支出", "餐饮", "16.8", "午饭"),
        row("2023年12月31日", "支出", "餐饮", "16.8", "午饭"),
    )
    private val lunch = SharkExisting(day(2023, 12, 31), "food", 1680, "午饭")

    @Test fun duplicatesAreCountedAsAMultiset() {
        // One in the ledger, two in the file: one is new.
        plan(twice, existing = listOf(lunch)).resolve(true).let {
            assertEquals(1, it.entries.size)
            assertEquals(1, it.skipped)
        }
        // Two in the ledger, one in the file: nothing to add.
        plan(twice.take(1), existing = listOf(lunch, lunch)).resolve(true).let {
            assertEquals(0, it.entries.size)
            assertEquals(1, it.skipped)
        }
        // None in the ledger: both come in.
        plan(twice).resolve(true).let {
            assertEquals(2, it.entries.size)
            assertEquals(0, it.skipped)
        }
    }

    @Test fun aDuplicateNeedsTheSameDayCategoryAmountAndNote() {
        val file = plan(twice.take(1))
        for (other in listOf(
            lunch.copy(date = day(2023, 12, 30)),
            lunch.copy(category = "shop"),
            lunch.copy(cents = 1681),
            lunch.copy(note = "晚饭"),
        )) {
            assertEquals(1, file.resolve(true, listOf(other)).entries.size)
        }
        // Surrounding spaces in the ledger's note don't matter.
        assertEquals(0, file.resolve(true, listOf(lunch.copy(note = " 午饭 "))).entries.size)
    }

    @Test fun theTimeOfDayDoesNotMatterForDuplicates() {
        val late = SharkExisting(day(2023, 12, 31), "food", 1680, "午饭")
        assertEquals(1, plan(twice.take(1), existing = listOf(late)).resolve(true).skipped)
    }

    @Test fun newCategoriesNeverMatchButTheirFallbackDoes() {
        val rows = listOf(row("2024-01-01", "支出", "书籍", "30", "买书"))
        val inOther = SharkExisting(day(2024, 1, 1), "other", 3000, "买书")
        val p = plan(rows, existing = listOf(inOther))
        // Creating 书籍: nothing there can be a duplicate.
        p.resolve(true).let {
            assertEquals(1, it.entries.size)
            assertEquals(listOf("书籍"), it.created.map { c -> c.name })
        }
        // Into 其他: the same row is already there.
        p.resolve(false).let {
            assertEquals(0, it.entries.size)
            assertEquals(1, it.skipped)
            assertTrue(it.created.isEmpty())
        }
    }

    @Test fun categoriesMadeSinceTheFileWasReadAreUsedAndTheirRowsCheckedForDuplicates() {
        val p = plan(listOf(row("2024-01-01", "支出", "书籍", "30", "买书"), row("2024-01-01", "支出", "水果", "5")))
        // A first tap on 导入 has made 书籍 (key c7) and written its row; a second one runs on the old preview.
        val made = CategoryCatalog(customs = listOf(Category("c7", "书籍", EntryKind.Expense, Hue.Shop, "book", custom = true)))
        val held = listOf(SharkExisting(day(2024, 1, 1), "c7", 3000, "买书"))
        p.against(made).resolve(true, held).let {
            assertEquals(1, it.entries.size)
            assertEquals(1, it.skipped)
            assertEquals(listOf("水果"), it.created.map { c -> c.name })
        }
        // The same name of the other kind is another category.
        assertSame(p, p.against(CategoryCatalog(customs = listOf(Category("c8", "书籍", EntryKind.Income, Hue.Shop, "book", custom = true)))))
        assertSame(p, p.against(CategoryCatalog.Builtin))
    }

    @Test fun fallbackToOtherFollowsTheKind() {
        val p = plan(
            listOf(row("2024-01-01", "支出", "书籍", "1"), row("2024-01-01", "收入", "稿费", "1")),
        )
        val keys = p.resolve(false).entries.map { (it.target as SharkTarget.Existing).key }
        assertEquals(listOf("other", "other_in"), keys)
        assertEquals(2, p.resolve(true).created.size)
    }

    @Test fun categoriesNoRowUsesAreNotCreated() {
        // Both rows of 书籍 are already in the ledger as 其他... but while creating they are not,
        // so the only unused case is the fallback: nothing is created at all.
        val p = plan(listOf(row("2024-01-01", "支出", "书籍", "30")))
        assertTrue(p.resolve(false).created.isEmpty())
    }

    // Whole file ----------------------------------------------------------------------------

    @Test fun anExportShapedFileEndToEnd() {
        val rows = listOf(
            row("2023年12月31日", "支出", "餐饮", "16.8", "午饭"),
            row("2023年12月31日", "支出", "书籍", "58"),
            row("2023年12月31日", "支出", "水果", "12.5", "苹果"),
            row("2023年12月30日", "支出", "美容", "99", ""),
            row("2023年12月30日", "支出", "零食", "9.9", ""),
            row("2023年12月29日", "支出", "快递", "6", "取件"),
            row("2023年12月29日", "收入", "其它", "200", "红包"),
            row("2023年12月28日", "支出", "日用", "3.2", ""),
        )
        val p = plan(rows)
        assertEquals(8, p.entries.size)
        assertEquals(7, p.expenseCount)
        assertEquals(1, p.incomeCount)
        assertEquals(1680L + 5800 + 1250 + 9900 + 990 + 600 + 320, p.expenseCents)
        assertEquals(20000L, p.incomeCents)
        assertEquals(day(2023, 12, 28), p.firstDay)
        assertEquals(day(2023, 12, 31), p.lastDay)
        assertEquals(listOf("书籍", "水果", "美容", "零食", "快递"), p.newCategories.map { it.name })
        assertEquals(listOf("book", "fruit", "lipstick", "snack", "box"), p.newCategories.map { it.icon })
        // 其它 income is the built-in 其他.
        assertEquals(SharkTarget.Existing("other_in"), p.entries[6].target)
        // Importing it creates the five, and 书籍 in the file shares one tile.
        val done = p.resolve(true)
        assertEquals(8, done.entries.size)
        assertEquals(5, done.created.size)
    }

    @Test fun importingTheSameFileAgainAddsNothing() {
        val rows = listOf(
            row("2023年12月31日", "支出", "餐饮", "16.8", "午饭"),
            row("2023年12月31日", "支出", "书籍", "58"),
            row("2023年12月31日", "支出", "书籍", "58"),
            row("2023年12月30日", "收入", "其它", "200", "红包"),
        )
        val first = plan(rows).resolve(true)
        assertEquals(4, first.entries.size)
        // What the ledger holds after the first import: the made category got the key c7.
        val made = first.created.single()
        val after = CategoryCatalog(customs = listOf(Category("c7", made.name, made.kind, made.hue, made.icon, custom = true)))
        val held = first.entries.map {
            SharkExisting(
                it.at.toLocalDate(),
                when (val t = it.target) {
                    is SharkTarget.Existing -> t.key
                    is SharkTarget.Create -> "c7"
                },
                it.cents,
                it.note,
            )
        }
        val second = plan(rows, after, held)
        assertTrue(second.newCategories.isEmpty())
        second.resolve(true).let {
            assertEquals(0, it.entries.size)
            assertEquals(4, it.skipped)
        }
    }

    @Test fun aRecordBecomesADuplicateKeyByItsLocalDay() {
        val record = RecordEntity(
            category = "food", amountCents = 1680, note = " 午饭 ",
            occurredAt = LocalDateTime.of(2023, 12, 31, 23, 59).toEpochMillis(), createdAt = 0,
        )
        assertEquals(lunch, record.toSharkExisting())
    }
}
