package com.jizhang.data

import android.util.Base64
import com.jizhang.domain.RecurringRule
import com.jizhang.domain.Repeat
import org.json.JSONArray
import org.json.JSONObject

/**
 * Plain JSON so a backup stays readable and can be imported by later versions.
 * Demo rows are left out of exports.
 */
object Backup {
    /**
     * v2 adds custom budgets, v3 custom categories, note chips and year/day budgets, v4 record
     * photos (embedded as Base64), category edits / order / colours and recurring rules, v5 several
     * days of the month / dates of the year per recurring rule (`days` is a bit set for monthly,
     * `yearDays` a list for yearly), v6 hidden built-in categories. Older files still import.
     */
    const val VERSION = 6

    /** Everything a backup holds; [images] maps a photo file name to its JPEG bytes. */
    data class Decoded(
        val records: List<RecordEntity>,
        val budgets: List<BudgetEntity>,
        val categories: List<CustomCategoryEntity>,
        val chips: List<NoteChipEntity>,
        val settings: Settings,
        val prefs: List<CategoryPrefEntity> = emptyList(),
        val rules: List<RecurringEntity> = emptyList(),
        val images: Map<String, ByteArray> = emptyMap(),
    )

    fun encode(data: Decoded, now: Long): String {
        val rows = JSONArray()
        data.records.filterNot { it.demo }.forEach { r ->
            rows.put(
                JSONObject()
                    .put("category", r.category)
                    .put("amountCents", r.amountCents)
                    .put("note", r.note)
                    .put("occurredAt", r.occurredAt)
                    .put("createdAt", r.createdAt)
                    .put("images", JSONArray(ImageStore.split(r.images)))
                    .put("ruleId", r.ruleId ?: JSONObject.NULL),
            )
        }
        val budgetRows = JSONArray()
        data.budgets.forEach { b ->
            budgetRows.put(
                JSONObject()
                    .put("name", b.name)
                    .put("period", b.period)
                    .put("category", b.category ?: JSONObject.NULL)
                    .put("amountCents", b.amountCents)
                    .put("createdAt", b.createdAt),
            )
        }
        val categoryRows = JSONArray()
        data.categories.forEach { c ->
            categoryRows.put(
                JSONObject()
                    .put("id", c.id)
                    .put("name", c.name)
                    .put("kind", c.kind)
                    .put("icon", c.icon)
                    .put("hue", c.hue)
                    .put("sortOrder", c.sortOrder)
                    .put("createdAt", c.createdAt)
                    .put("color", c.color ?: JSONObject.NULL),
            )
        }
        val chipRows = JSONArray()
        data.chips.forEach { c ->
            chipRows.put(
                JSONObject().put("category", c.category).put("text", c.text).put("pinned", c.pinned).put("createdAt", c.createdAt),
            )
        }
        val prefRows = JSONArray()
        data.prefs.forEach { p ->
            prefRows.put(
                JSONObject()
                    .put("key", p.key)
                    .put("name", p.name ?: JSONObject.NULL)
                    .put("icon", p.icon ?: JSONObject.NULL)
                    .put("hue", p.hue ?: JSONObject.NULL)
                    .put("color", p.color ?: JSONObject.NULL)
                    .put("sortOrder", p.sortOrder ?: JSONObject.NULL)
                    .put("hidden", p.hidden),
            )
        }
        val ruleRows = JSONArray()
        data.rules.forEach { r ->
            ruleRows.put(
                JSONObject()
                    .put("id", r.id)
                    .put("category", r.category)
                    .put("amountCents", r.amountCents)
                    .put("note", r.note)
                    .put("repeat", r.repeat)
                    .put("days", r.days)
                    .put("yearDays", JSONArray(RecurringRule.parseYearDays(r.yearDays)))
                    .put("startDay", r.startDay)
                    .put("endDay", r.endDay ?: JSONObject.NULL)
                    .put("lastDay", r.lastDay ?: JSONObject.NULL)
                    .put("minuteOfDay", r.minuteOfDay)
                    .put("paused", r.paused)
                    .put("createdAt", r.createdAt),
            )
        }
        val imageRows = JSONObject()
        data.images.forEach { (name, bytes) -> imageRows.put(name, Base64.encodeToString(bytes, Base64.NO_WRAP)) }
        val settings = data.settings
        return JSONObject()
            .put("app", "jizhang")
            .put("version", VERSION)
            .put("exportedAt", now)
            .put(
                "settings",
                JSONObject()
                    .put("theme", settings.theme.key)
                    .put("budgetCents", settings.budgetCents)
                    .put("yearBudgetCents", settings.yearBudgetCents)
                    .put("dayBudgetCents", settings.dayBudgetCents),
            )
            .put("records", rows)
            .put("budgets", budgetRows)
            .put("categories", categoryRows)
            .put("noteChips", chipRows)
            .put("categoryPrefs", prefRows)
            .put("recurring", ruleRows)
            // Photos last, so the readable part of the file comes first.
            .put("images", imageRows)
            .toString(1)
    }

    /** Throws [IllegalArgumentException] with a user-facing message when the file is not a backup. */
    fun decode(json: String): Decoded {
        val root = runCatching { JSONObject(json) }.getOrElse { throw IllegalArgumentException("文件不是有效的备份") }
        require(root.optString("app") == "jizhang") { "这不是记账的备份文件" }
        require(root.optInt("version") <= VERSION) { "备份来自更新的版本，请先升级应用" }
        val s = root.optJSONObject("settings")
        val settings = Settings(
            theme = ThemeId.of(s?.optString("theme")),
            budgetCents = s?.optLong("budgetCents", Settings.DEFAULT_BUDGET_CENTS) ?: Settings.DEFAULT_BUDGET_CENTS,
            yearBudgetCents = s?.optLong("yearBudgetCents", 0) ?: 0,
            dayBudgetCents = s?.optLong("dayBudgetCents", 0) ?: 0,
        )
        val rows = root.optJSONArray("records") ?: JSONArray()
        val records = (0 until rows.length()).map { i ->
            val o = rows.getJSONObject(i)
            RecordEntity(
                category = o.getString("category"),
                amountCents = o.getLong("amountCents"),
                note = o.optString("note"),
                occurredAt = o.getLong("occurredAt"),
                createdAt = o.optLong("createdAt", o.getLong("occurredAt")),
                images = o.optJSONArray("images")?.let { a ->
                    ImageStore.join((0 until a.length()).map { a.getString(it) }.filter(ImageStore::isSafeName))
                } ?: "",
                ruleId = o.optLongOrNull("ruleId"),
            )
        }
        val budgetRows = root.optJSONArray("budgets") ?: JSONArray()
        val budgets = (0 until budgetRows.length()).map { i ->
            val o = budgetRows.getJSONObject(i)
            BudgetEntity(
                name = o.optString("name"),
                period = o.getString("period"),
                category = if (o.isNull("category")) null else o.optString("category"),
                amountCents = o.getLong("amountCents"),
                createdAt = o.optLong("createdAt", i.toLong()),
            )
        }
        val categoryRows = root.optJSONArray("categories") ?: JSONArray()
        val categories = (0 until categoryRows.length()).map { i ->
            val o = categoryRows.getJSONObject(i)
            CustomCategoryEntity(
                id = o.getLong("id"),
                name = o.getString("name"),
                kind = o.getString("kind"),
                icon = o.getString("icon"),
                hue = o.getString("hue"),
                sortOrder = o.optInt("sortOrder", i),
                createdAt = o.optLong("createdAt", i.toLong()),
                color = o.optIntOrNull("color"),
            )
        }
        val chipRows = root.optJSONArray("noteChips") ?: JSONArray()
        val chips = (0 until chipRows.length()).map { i ->
            val o = chipRows.getJSONObject(i)
            NoteChipEntity(o.getString("category"), o.getString("text"), o.getBoolean("pinned"), o.optLong("createdAt", i.toLong()))
        }
        val prefRows = root.optJSONArray("categoryPrefs") ?: JSONArray()
        val prefs = (0 until prefRows.length()).map { i ->
            val o = prefRows.getJSONObject(i)
            CategoryPrefEntity(
                key = o.getString("key"),
                name = o.optStringOrNull("name"),
                icon = o.optStringOrNull("icon"),
                hue = o.optStringOrNull("hue"),
                color = o.optIntOrNull("color"),
                sortOrder = o.optIntOrNull("sortOrder"),
                hidden = o.optBoolean("hidden", false),
            )
        }
        val ruleRows = root.optJSONArray("recurring") ?: JSONArray()
        val rules = (0 until ruleRows.length()).map { i ->
            val o = ruleRows.getJSONObject(i)
            val repeat = o.getString("repeat")
            // Files before v5 hold one number: a day of month (monthly) or month × 100 + day (yearly).
            val (days, yearDays) = if (root.optInt("version") <= 4) {
                RecurringRule.upgradeLegacy(Repeat.of(repeat), o.optInt("days"))
            } else {
                val dates = o.optJSONArray("yearDays")?.let { a -> (0 until a.length()).map { a.optInt(it) } }.orEmpty()
                o.optInt("days") to dates
            }
            RecurringEntity(
                id = o.getLong("id"),
                category = o.getString("category"),
                amountCents = o.getLong("amountCents"),
                note = o.optString("note"),
                repeat = repeat,
                days = days,
                yearDays = RecurringRule.formatYearDays(yearDays),
                startDay = o.getLong("startDay"),
                endDay = o.optLongOrNull("endDay"),
                lastDay = o.optLongOrNull("lastDay"),
                minuteOfDay = o.optInt("minuteOfDay", RecurringRule.DEFAULT_MINUTE).coerceIn(0, RecurringRule.MINUTES_PER_DAY - 1),
                paused = o.optBoolean("paused"),
                createdAt = o.optLong("createdAt", i.toLong()),
            )
        }
        val imageRows = root.optJSONObject("images")
        val images = imageRows?.keys()?.asSequence()
            ?.filter(ImageStore::isSafeName)
            ?.associateWith { Base64.decode(imageRows.getString(it), Base64.DEFAULT) }
            .orEmpty()
        return Decoded(records, budgets, categories, chips, settings, prefs, rules, images)
    }

    private fun JSONObject.optLongOrNull(name: String): Long? = if (!has(name) || isNull(name)) null else optLong(name)

    private fun JSONObject.optIntOrNull(name: String): Int? = if (!has(name) || isNull(name)) null else optInt(name)

    private fun JSONObject.optStringOrNull(name: String): String? = if (!has(name) || isNull(name)) null else optString(name)
}
