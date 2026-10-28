package com.jizhang.data

import com.jizhang.domain.Category
import com.jizhang.domain.CategoryCatalog
import com.jizhang.domain.EntryKind
import com.jizhang.domain.Hue
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The file can't be read as a bill export; [message] is Chinese and fit to show as is. */
class SharkFormatException(message: String) : Exception(message)

/** One valid CSV row. [time] is null when the file only has dates. */
data class SharkRow(
    /** Line in the file the row starts on (1-based), for messages. */
    val line: Int,
    val date: LocalDate,
    val time: LocalTime?,
    val kind: EntryKind,
    /** 类别 as written, trimmed; empty when the file has none. */
    val category: String,
    val cents: Long,
    val note: String,
)

/** A row that could not be read; the rest of the file still imports. */
data class SharkError(val line: Int, val reason: String)

data class SharkParse(val rows: List<SharkRow>, val errors: List<SharkError>)

/** A custom category the import would create. */
data class SharkNewCategory(val name: String, val kind: EntryKind, val icon: String, val hue: Hue)

sealed interface SharkTarget {
    /** An existing category (built-in or the user's own), by key. */
    data class Existing(val key: String) : SharkTarget

    /** A category that does not exist yet: created on import, or replaced by 其他. */
    data class Create(val category: SharkNewCategory) : SharkTarget
}

/** A row ready to become a record. */
data class SharkEntry(
    val line: Int,
    val at: LocalDateTime,
    val kind: EntryKind,
    val target: SharkTarget,
    val cents: Long,
    val note: String,
)

/** What the ledger already holds, reduced to what decides whether an imported row is a duplicate. */
data class SharkExisting(val date: LocalDate, val category: String, val cents: Long, val note: String)

/** [RecordEntity] as a duplicate-check key. */
fun RecordEntity.toSharkExisting() = SharkExisting(occurredAt.toLocalDateTime().toLocalDate(), category, amountCents, note.trim())

/** What an import writes once the category choice and duplicates are settled. */
data class SharkResolved(val entries: List<SharkEntry>, val skipped: Int, val created: List<SharkNewCategory>)

/**
 * A parsed file mapped onto the ledger's categories. [existing] is a snapshot taken when the file
 * was read, enough for the preview; the import itself checks the ledger again inside its transaction.
 */
data class SharkPlan(
    val entries: List<SharkEntry>,
    val errors: List<SharkError>,
    /** Distinct categories the file uses that the ledger does not have, in order of appearance. */
    val newCategories: List<SharkNewCategory>,
    val existing: List<SharkExisting> = emptyList(),
) {
    val expenseCount: Int = entries.count { it.kind == EntryKind.Expense }
    val incomeCount: Int = entries.size - expenseCount
    val expenseCents: Long = entries.filter { it.kind == EntryKind.Expense }.sumOf { it.cents }
    val incomeCents: Long = entries.filter { it.kind == EntryKind.Income }.sumOf { it.cents }
    val firstDay: LocalDate? = entries.minOfOrNull { it.at.toLocalDate() }
    val lastDay: LocalDate? = entries.maxOfOrNull { it.at.toLocalDate() }

    /**
     * This plan against a [catalog] that may have gained categories since the file was read (an
     * earlier tap on 导入 made them): rows bound for a category that is there now go to it, so
     * they count as duplicates like any other and nothing is created twice.
     */
    fun against(catalog: CategoryCatalog): SharkPlan {
        val found = newCategories.mapNotNull { nc ->
            catalog.forKind(nc.kind).firstOrNull { it.label == nc.name }?.let { nc to it.key }
        }.toMap()
        if (found.isEmpty()) return this
        return copy(
            entries = entries.map { e ->
                val key = (e.target as? SharkTarget.Create)?.let { found[it.category] }
                if (key == null) e else e.copy(target = SharkTarget.Existing(key))
            },
            newCategories = newCategories.filter { it !in found },
        )
    }

    /**
     * Settles what would be written. With [createCategories] false, rows of new categories go to 其他.
     * A row is skipped when [existing] already has one with the same day, category, amount and note;
     * duplicates are counted as a multiset, so a file with two identical rows against a ledger with
     * one imports one. Rows of a category that is about to be created are never duplicates.
     */
    fun resolve(createCategories: Boolean, existing: List<SharkExisting> = this.existing): SharkResolved {
        val spare = HashMap<SharkExisting, Int>()
        existing.forEach { spare.merge(it.copy(note = it.note.trim()), 1, Int::plus) }
        val kept = ArrayList<SharkEntry>(entries.size)
        var skipped = 0
        for (e in entries) {
            val entry = when {
                createCategories || e.target !is SharkTarget.Create -> e
                else -> e.copy(target = SharkTarget.Existing(Category.otherOf(e.kind).key))
            }
            val target = entry.target
            if (target is SharkTarget.Existing) {
                val key = SharkExisting(entry.at.toLocalDate(), target.key, entry.cents, entry.note)
                val left = spare[key] ?: 0
                if (left > 0) {
                    spare[key] = left - 1
                    skipped++
                    continue
                }
            }
            kept += entry
        }
        val used = kept.mapNotNull { (it.target as? SharkTarget.Create)?.category }.toSet()
        return SharkResolved(kept, skipped, newCategories.filter { it in used })
    }
}

/**
 * Reads the CSV that 鲨鱼记账 exports (日期, 收支类型, 类别, 账户, 金额, 备注) and maps it onto this
 * app's categories. Pure Kotlin, no Android APIs, so it is unit-tested on the JVM.
 */
object SharkImport {
    /** Bigger files are not bill exports. */
    const val MAX_BYTES = 16 * 1024 * 1024

    private const val NAME_MAX = 6
    private const val HEADER_SCAN = 30

    /** What the add sheet can type (7 digits before the point), so an imported row can be edited later. */
    private const val MAX_CENTS = 999_999_999L
    private const val BOM = '\uFEFF'
    private val NOON = LocalTime.NOON

    private val GB18030: Charset = runCatching { Charset.forName("GB18030") }.getOrElse { Charset.forName("GBK") }

    // Text ----------------------------------------------------------------------------------

    /** Reads [input] whole; a file bigger than [limit] is not a bill export, so it stops there with a message. */
    fun read(input: InputStream, limit: Int = MAX_BYTES): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) return out.toByteArray()
            out.write(buf, 0, n)
            if (out.size() > limit) throw SharkFormatException("文件太大了（超过 ${limit / (1024 * 1024)} MB），不像是账单导出")
        }
    }

    /** BOM says UTF-8 / UTF-16; bytes that are valid UTF-8 are UTF-8; anything else is GB18030. */
    fun decode(bytes: ByteArray): String {
        fun at(i: Int) = bytes.getOrNull(i)?.toInt()?.and(0xFF) ?: -1
        return when {
            at(0) == 0xEF && at(1) == 0xBB && at(2) == 0xBF -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            at(0) == 0xFF && at(1) == 0xFE -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            at(0) == 0xFE && at(1) == 0xFF -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            else -> try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString()
            } catch (_: CharacterCodingException) {
                String(bytes, GB18030)
            }
        }
    }

    /** One CSV record and the line it starts on. */
    internal class CsvRecord(val line: Int, val fields: List<String>, val unterminated: Boolean = false) {
        val blank: Boolean get() = fields.all { it.isBlank() }
    }

    /**
     * RFC 4180: fields in double quotes may hold the delimiter, line breaks and "" for a quote.
     * A stray quote inside an unquoted field is kept as it is. Line numbers count every break, so
     * they match what a text editor shows even after a multi-line note. A quote that never closes
     * must not swallow the rest of the file: that one line is reported and reading resumes below it.
     */
    internal fun readCsv(text: String, delimiter: Char = ','): List<CsvRecord> {
        val out = ArrayList<CsvRecord>()
        var fields = ArrayList<String>()
        val sb = StringBuilder()
        var line = 1
        var start = 1
        var startAt = 0
        var quoted = false
        var closed = false
        var i = 0
        fun endField() {
            fields.add(sb.toString())
            sb.setLength(0)
            closed = false
        }
        fun endRecord() {
            endField()
            out += CsvRecord(start, fields)
            fields = ArrayList()
            start = line
            startAt = i + 1
        }
        fun scan() {
            while (i < text.length) {
                val ch = text[i]
                if (quoted) {
                    when {
                        ch == '"' && text.getOrNull(i + 1) == '"' -> {
                            sb.append('"')
                            i++
                        }
                        ch == '"' -> {
                            quoted = false
                            closed = true
                        }
                        ch == '\r' || ch == '\n' -> {
                            if (ch == '\r' && text.getOrNull(i + 1) == '\n') i++
                            line++
                            sb.append('\n')
                        }
                        else -> sb.append(ch)
                    }
                } else {
                    when {
                        ch == '"' && sb.isEmpty() && !closed -> quoted = true
                        ch == delimiter -> endField()
                        ch == '\r' || ch == '\n' -> {
                            if (ch == '\r' && text.getOrNull(i + 1) == '\n') i++
                            line++
                            endRecord()
                        }
                        else -> sb.append(ch)
                    }
                }
                i++
            }
        }
        scan()
        while (quoted) {
            endField()
            out += CsvRecord(start, fields, unterminated = true)
            val brk = text.indexOfAny(LINE_BREAKS, startAt)
            if (brk < 0) return out
            i = if (text[brk] == '\r' && text.getOrNull(brk + 1) == '\n') brk + 2 else brk + 1
            fields = ArrayList()
            sb.setLength(0)
            quoted = false
            closed = false
            line = start + 1
            start = line
            startAt = i
            scan()
        }
        if (sb.isNotEmpty() || fields.isNotEmpty() || closed) endRecord()
        return out
    }

    private val LINE_BREAKS = charArrayOf('\r', '\n')

    /** The first line decides: comma, tab or semicolon, whichever appears most. */
    private fun sniffDelimiter(text: String): Char {
        val first = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return ','
        return listOf(',', '\t', ';').maxByOrNull { d -> first.count { it == d } }
            ?.takeIf { d -> first.contains(d) } ?: ','
    }

    // Columns -------------------------------------------------------------------------------

    private enum class Field { Date, Type, Category, Amount, Note }

    private val ALIASES: Map<String, Field> = buildMap {
        listOf("日期", "时间", "记账日期", "记账时间", "交易日期", "交易时间", "日期时间", "date", "time").forEach { put(it, Field.Date) }
        listOf("收支类型", "收支", "类型", "收/支", "收支方向", "type").forEach { put(it, Field.Type) }
        listOf("类别", "分类", "类目", "category").forEach { put(it, Field.Category) }
        listOf("金额", "金额(元)", "金额（元）", "交易金额", "amount").forEach { put(it, Field.Amount) }
        listOf("备注", "备注说明", "说明", "摘要", "商品说明", "note", "remark").forEach { put(it, Field.Note) }
    }

    /** Column index per field; -1 for one the file does not have. 账户 and anything else is ignored. */
    private class Columns(val date: Int, val type: Int, val category: Int, val amount: Int, val note: Int) {
        companion object {
            fun of(cells: List<String>): Columns? {
                val at = IntArray(Field.entries.size) { -1 }
                cells.forEachIndexed { i, cell ->
                    val field = ALIASES[cell.trim().trim(BOM).replace(" ", "").replace("\u3000", "").lowercase()]
                    if (field != null && at[field.ordinal] < 0) at[field.ordinal] = i
                }
                if (at[Field.Date.ordinal] < 0 || at[Field.Amount.ordinal] < 0) return null
                return Columns(at[Field.Date.ordinal], at[Field.Type.ordinal], at[Field.Category.ordinal], at[Field.Amount.ordinal], at[Field.Note.ordinal])
            }
        }
    }

    // Rows ----------------------------------------------------------------------------------

    private val DATE = Regex("""^(\d{4})\s*[年/.\-]\s*(\d{1,2})\s*[月/.\-]\s*(\d{1,2})\s*日?(?:[\sT]+(\d{1,2})[:：](\d{2})(?:[:：](\d{2}))?)?$""")
    private val AMOUNT = Regex("""^[+-]?(\d+(\.\d*)?|\.\d+)$""")

    /** Date and optional time; null when [text] is not a real date. */
    private fun parseDate(text: String): Pair<LocalDate, LocalTime?>? {
        val m = DATE.matchEntire(text) ?: return null
        val g = m.groupValues
        return try {
            val date = LocalDate.of(g[1].toInt(), g[2].toInt(), g[3].toInt())
            val time = if (g[4].isEmpty()) null else LocalTime.of(g[4].toInt(), g[5].toInt(), g[6].ifEmpty { "0" }.toInt())
            date to time
        } catch (_: DateTimeException) {
            null
        }
    }

    private class Amount(val cents: Long, val negative: Boolean)

    /** "¥1,234.5" → 123450; more than two decimals round half up. Null when it is not a number. */
    private fun parseAmount(text: String): Amount? {
        val clean = buildString {
            text.forEach { ch ->
                when (ch) {
                    '¥', '￥', '$', ',', '，', ' ', '\u00A0', '\u3000', '元' -> Unit
                    '−', '－' -> append('-')
                    else -> append(ch)
                }
            }
        }
        if (!AMOUNT.matches(clean)) return null
        val scaled = BigDecimal(clean).abs().setScale(2, RoundingMode.HALF_UP).unscaledValue()
        // Beyond the cap is reported as "too big" by the caller; any value above it will do.
        val cents = if (scaled > BigInteger.valueOf(MAX_CENTS)) MAX_CENTS + 1 else scaled.toLong()
        return Amount(cents, clean.startsWith("-"))
    }

    private fun kindOf(text: String): EntryKind? = when (text.trim().lowercase()) {
        "支出", "支", "消费", "花费", "expense", "out" -> EntryKind.Expense
        "收入", "收", "income", "in" -> EntryKind.Income
        else -> null
    }

    private fun quote(s: String) = "「" + (if (s.length > 12) s.take(12) + "…" else s) + "」"

    /**
     * Parses [text] (already decoded). Bad rows are collected in [SharkParse.errors] and skipped;
     * only a file without a recognisable header (a 日期 and a 金额 column) fails as a whole.
     */
    fun parse(text: String): SharkParse {
        val body = text.removePrefix(BOM.toString())
        // The first line's delimiter is tried first; a title line above the header may have misled it.
        val sniffed = sniffDelimiter(body)
        var records = emptyList<CsvRecord>()
        var header = -1
        var cols: Columns? = null
        for (delimiter in listOf(sniffed) + listOf(',', '\t', ';').filter { it != sniffed }) {
            records = readCsv(body, delimiter)
            var seen = 0
            for ((i, rec) in records.withIndex()) {
                if (rec.blank) continue
                if (++seen > HEADER_SCAN) break
                cols = Columns.of(rec.fields)
                if (cols != null) {
                    header = i
                    break
                }
            }
            if (cols != null) break
        }
        if (cols == null) {
            throw SharkFormatException(
                when {
                    records.all { it.blank } -> "文件是空的"
                    body.trimStart().startsWith("{") -> "这像是本应用的备份文件，请在「备份与恢复」里选「导入备份」"
                    else -> "认不出这个文件：表头里需要有「日期」和「金额」这两列"
                },
            )
        }
        val rows = ArrayList<SharkRow>()
        val errors = ArrayList<SharkError>()
        for (rec in records.drop(header + 1)) {
            if (rec.blank) continue
            if (rec.unterminated) {
                errors += SharkError(rec.line, "引号没有闭合")
                continue
            }
            val f = rec.fields
            fun cell(i: Int) = if (i in f.indices) f[i].trim() else ""
            if (f.size <= maxOf(cols.date, cols.amount)) {
                errors += SharkError(rec.line, "列数不够，这一行只有 ${f.size} 列")
                continue
            }
            val dateText = cell(cols.date)
            val (date, time) = parseDate(dateText) ?: run {
                errors += SharkError(rec.line, "日期${quote(dateText)}不对")
                continue
            }
            val amountText = cell(cols.amount)
            if (amountText.isEmpty()) {
                errors += SharkError(rec.line, "金额是空的")
                continue
            }
            val amount = parseAmount(amountText) ?: run {
                errors += SharkError(rec.line, "金额${quote(amountText)}读不出来")
                continue
            }
            if (amount.cents == 0L) {
                errors += SharkError(rec.line, "金额为 0")
                continue
            }
            if (amount.cents > MAX_CENTS) {
                errors += SharkError(rec.line, "金额${quote(amountText)}太大")
                continue
            }
            val typeText = cell(cols.type)
            val kind = when {
                typeText.isNotEmpty() -> kindOf(typeText) ?: run {
                    errors += SharkError(rec.line, "收支类型${quote(typeText)}认不出来，应是「支出」或「收入」")
                    continue
                }
                // No 收支类型 to go by: the sign says it, as in a plain signed export.
                amount.negative -> EntryKind.Expense
                else -> EntryKind.Income
            }
            rows += SharkRow(rec.line, date, time, kind, cell(cols.category), amount.cents, cell(cols.note))
        }
        return SharkParse(rows, errors)
    }

    /** Decodes and parses a file's bytes. */
    fun parse(bytes: ByteArray): SharkParse {
        // "PK" is a zip: an .xlsx the export was saved as, or any archive.
        if (bytes.size > 3 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() && bytes[2] == 3.toByte()) {
            throw SharkFormatException("这是 Excel 或压缩文件，需要的是 鲨鱼记账 导出的 CSV 明细")
        }
        return parse(decode(bytes))
    }

    // Times ---------------------------------------------------------------------------------

    /**
     * Rows without a time sit at 12:00 on their day, one second earlier per row before them, so
     * the order of the file survives: its first row of a day comes last in the day. (A file that
     * runs oldest first is detected by its dates and counted from the other end.) Rows that
     * carry their own time keep it.
     */
    internal fun timesOf(rows: List<SharkRow>): List<LocalDateTime> {
        val oldestFirst = rows.size > 1 && rows.first().date < rows.last().date
        val order = if (oldestFirst) rows.indices.reversed() else rows.indices
        val seen = HashMap<LocalDate, Int>()
        val out = arrayOfNulls<LocalDateTime>(rows.size)
        for (i in order) {
            val r = rows[i]
            val time = r.time ?: run {
                val n = seen.getOrDefault(r.date, 0)
                seen[r.date] = n + 1
                NOON.minusSeconds(n.toLong().coerceAtMost(NOON.toSecondOfDay().toLong()))
            }
            out[i] = LocalDateTime.of(r.date, time)
        }
        return out.map { it!! }
    }

    // Categories ----------------------------------------------------------------------------

    /** File name → built-in key, per kind. Conservative: only names that clearly mean the same thing. */
    private val SYNONYMS: Map<EntryKind, Map<String, String>> = mapOf(
        EntryKind.Expense to mapOf(
            "其它" to Category.Other.key,
            "住房" to Category.Home.key, "房租" to Category.Home.key,
            "礼金" to Category.Gift.key, "红包" to Category.Gift.key, "人情往来" to Category.Gift.key,
        ),
        EntryKind.Income to mapOf(
            "其它" to Category.OtherIncome.key,
            "薪资" to Category.Salary.key, "薪水" to Category.Salary.key,
            "理财收益" to Category.Invest.key, "利息" to Category.Invest.key,
            "奖金" to Category.Bonus.key, "年终奖" to Category.Bonus.key,
        ),
    )

    /**
     * The key of the category [name] means in [catalog], or null when it needs a new one. Tried in
     * order: the same label within the kind (built-ins as the user renamed them, and their own
     * categories; a name cut to six characters counts too, so re-importing finds what an earlier
     * import made), a built-in's original label, then the synonym table. A blank name is 其他.
     */
    internal fun match(name: String, kind: EntryKind, catalog: CategoryCatalog): String? {
        val label = name.trim()
        if (label.isEmpty()) return catalog.otherOf(kind).key
        val cut = label.take(NAME_MAX)
        val mine = catalog.forKind(kind)
        return mine.firstOrNull { it.label == label }?.key
            ?: mine.firstOrNull { it.label == cut }?.key
            ?: Category.forKind(kind).firstOrNull { it.label == label }?.key
            ?: SYNONYMS[kind]?.get(label)
    }

    /** Library icon for a new category, by keyword in its name; first rule that fits wins. */
    internal val ICON_RULES: List<Pair<List<String>, String>> = listOf(
        listOf("书", "阅读") to "book",
        listOf("零食", "糖", "饼干", "小吃", "薯片") to "snack",
        listOf("水果", "苹果", "香蕉", "橙", "葡萄", "西瓜", "榴莲", "果") to "fruit",
        listOf("蔬菜", "买菜", "菜") to "vegetable",
        listOf("快递", "包裹", "物流", "邮寄", "邮费") to "box",
        listOf("美容", "美妆", "化妆", "彩妆", "口红") to "lipstick",
        listOf("护肤", "按摩", "足疗", "洗浴", "spa") to "spa",
        listOf("理发", "美发", "剪发") to "scissors",
        listOf("咖啡") to "coffee",
        listOf("奶茶", "饮料", "饮品", "茶") to "milk-tea",
        listOf("旅游", "旅行", "酒店", "住宿", "民宿") to "suitcase",
        listOf("酒") to "wine",
        listOf("烟") to "cigarette",
        listOf("蛋糕", "甜品", "面包") to "cake",
        listOf("早餐", "午餐", "晚餐", "夜宵", "外卖", "面条", "拉面") to "noodles",
        listOf("打车", "出租", "网约车") to "taxi",
        listOf("公交") to "bus",
        listOf("地铁", "火车", "高铁") to "train",
        listOf("机票", "飞机") to "plane",
        listOf("加油", "油费") to "fuel",
        listOf("停车") to "parking",
        listOf("单车", "骑行") to "bike",
        listOf("车") to "car",
        listOf("洗衣") to "laundry",
        listOf("鞋") to "shoes",
        listOf("衣", "服装", "服饰") to "shirt",
        listOf("包包", "箱包") to "handbag",
        listOf("首饰", "饰品", "珠宝") to "diamond",
        listOf("眼镜") to "glasses",
        listOf("电费", "水电") to "bolt",
        listOf("水费") to "drop",
        listOf("燃气", "煤气") to "flame",
        listOf("宽带", "网费") to "wifi",
        listOf("话费", "手机") to "phone",
        listOf("家具", "家居") to "sofa",
        listOf("维修") to "wrench",
        listOf("电脑", "数码") to "laptop",
        listOf("游戏") to "gamepad",
        listOf("摄影", "相机") to "camera",
        listOf("会员", "订阅") to "subscribe",
        listOf("药") to "pill",
        listOf("医院", "挂号", "体检") to "hospital",
        listOf("牙") to "tooth",
        listOf("健身", "运动") to "dumbbell",
        listOf("红包", "礼金") to "hongbao",
        listOf("孩子", "宝宝", "母婴") to "baby",
        listOf("宠物") to "paw",
        listOf("鲜花", "花卉") to "flower",
        listOf("捐") to "charity",
        listOf("聚会", "聚餐") to "party",
        listOf("朋友") to "friends",
        listOf("电影") to "film",
        listOf("音乐") to "music",
        listOf("保险") to "shield",
        listOf("税") to "tax",
        listOf("利息") to "interest",
        listOf("退款") to "refund",
        listOf("报销") to "reimburse",
        listOf("兼职") to "briefcase",
        listOf("工资", "薪") to "salary",
        listOf("奖金") to "bonus",
        listOf("理财", "基金", "股票") to "invest",
    )

    internal fun iconFor(name: String): String = ICON_RULES.firstOrNull { (words, _) -> words.any { name.contains(it) } }?.second ?: "other"

    // Plan ----------------------------------------------------------------------------------

    /**
     * Maps [parse] onto [catalog] and gives every row its time. [existing] (usually the ledger as
     * it is now) lets [SharkPlan.resolve] count duplicates for the preview.
     */
    fun plan(parse: SharkParse, catalog: CategoryCatalog = CategoryCatalog.Builtin, existing: List<SharkExisting> = emptyList()): SharkPlan {
        val times = timesOf(parse.rows)
        val fresh = LinkedHashMap<Pair<EntryKind, String>, SharkNewCategory>()
        // Hues rotate, starting after the ones the user's own categories have used up; grey is 其他's.
        val hues = Hue.entries.filter { it != Hue.Other }
        val entries = parse.rows.mapIndexed { i, r ->
            val key = match(r.category, r.kind, catalog)
            val target = if (key != null) {
                SharkTarget.Existing(key)
            } else {
                val name = r.category.trim().take(NAME_MAX)
                SharkTarget.Create(
                    fresh.getOrPut(r.kind to name) {
                        SharkNewCategory(name, r.kind, iconFor(name), hues[(catalog.customs.size + fresh.size) % hues.size])
                    },
                )
            }
            SharkEntry(r.line, times[i], r.kind, target, r.cents, r.note)
        }
        return SharkPlan(entries, parse.errors, fresh.values.toList(), existing)
    }
}
