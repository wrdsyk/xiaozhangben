package com.jizhang

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.jizhang.data.Backup
import com.jizhang.data.BudgetEntity
import com.jizhang.data.CategoryPrefEntity
import com.jizhang.data.ImageStore
import com.jizhang.data.RecurringEntity
import com.jizhang.data.CustomCategoryEntity
import com.jizhang.data.DemoData
import com.jizhang.data.NoteChipEntity
import com.jizhang.data.RecordEntity
import com.jizhang.data.SharkFormatException
import com.jizhang.data.SharkImport
import com.jizhang.data.SharkNewCategory
import com.jizhang.data.SharkPlan
import com.jizhang.data.SharkTarget
import com.jizhang.data.toSharkExisting
import com.jizhang.data.Settings
import com.jizhang.data.ThemeId
import com.jizhang.data.toDomain
import com.jizhang.data.toEpochMillis
import com.jizhang.domain.BudgetPeriod
import com.jizhang.domain.BudgetProgress
import com.jizhang.domain.Category
import com.jizhang.domain.CategoryCatalog
import com.jizhang.domain.DayGroup
import com.jizhang.domain.EntryKind
import com.jizhang.domain.Hue
import com.jizhang.domain.MonthSummary
import com.jizhang.domain.NoteChip
import com.jizhang.domain.Record
import com.jizhang.domain.RecurringRule
import com.jizhang.domain.Repeat
import com.jizhang.domain.TotalBudget
import com.jizhang.domain.formatCents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.math.BigDecimal
import java.time.YearMonth

data class LedgerState(
    val month: YearMonth,
    val today: LocalDate,
    /** Records of [month], newest first. */
    val records: List<Record>,
    /** Every record, newest first: search, multi-year stats and the detail page use these. */
    val allRecords: List<Record>,
    val days: List<DayGroup>,
    val summary: MonthSummary,
    /** Months that can be picked, newest first. */
    val months: List<YearMonth>,
    val settings: Settings,
    val catalog: CategoryCatalog,
    /** Year, month and day total budgets (see [TotalBudget.of]). */
    val totals: List<TotalBudget>,
    val demoCount: Int,
    val loaded: Boolean,
)

/** A one-line message shown at the top; [undo] restores a deleted record. */
data class ToastMessage(val id: Long, val text: String, val undo: RecordEntity? = null)

class LedgerViewModel(private val container: AppContainer) : ViewModel() {
    private val db = container.database
    private val dao = db.records()
    private val budgetDao = db.budgets()
    private val categoryDao = db.categories()
    private val chipDao = db.noteChips()
    private val prefDao = db.categoryPrefs()
    private val ruleDao = db.recurring()
    private val images: ImageStore = container.images
    private val month = MutableStateFlow(YearMonth.now())

    private val _toast = MutableStateFlow<ToastMessage?>(null)
    val toast: StateFlow<ToastMessage?> = _toast.asStateFlow()

    /** Id of the record just saved, so its row can glow once when it appears. */
    private val _justAdded = MutableStateFlow<Long?>(null)
    val justAdded: StateFlow<Long?> = _justAdded.asStateFlow()

    private val catalog: Flow<CategoryCatalog> =
        combine(categoryDao.observeAll(), prefDao.observeAll()) { rows, prefs ->
            CategoryCatalog(rows.map { it.toDomain() }, prefs.associate { it.key to it.toDomain() })
        }

    /** A personal ledger is small enough to keep every record in memory. */
    private val allRecords: Flow<List<Record>> =
        combine(dao.observeAll(), catalog) { rows, cat -> rows.map { it.toDomain(cat) } }

    /** Custom budgets with what has been spent in their current day / month / year. */
    val budgets: StateFlow<List<BudgetProgress>> =
        combine(budgetDao.observeAll(), allRecords, catalog) { rows, records, cat ->
            BudgetProgress.of(rows.map { it.toDomain(cat) }, records, LocalDate.now())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<LedgerState> = combine(
        combine(month, allRecords, catalog, ::Triple),
        container.settings.settings,
        dao.observeDemoCount(),
    ) { (m, all, cat), settings, demo ->
        val today = LocalDate.now()
        val inMonth = all.filter { YearMonth.from(it.date) == m }
        val now = YearMonth.from(today)
        val first = all.lastOrNull()?.let { YearMonth.from(it.date) }?.takeIf { it.isBefore(now) } ?: now
        LedgerState(
            month = m,
            today = today,
            records = inMonth,
            allRecords = all,
            days = MonthSummary.groupByDay(inMonth),
            summary = MonthSummary.of(m, inMonth, settings.budgetCents, today),
            months = generateSequence(now) { it.minusMonths(1) }.takeWhile { !it.isBefore(first) }.take(240).toList(),
            settings = settings,
            catalog = cat,
            totals = TotalBudget.of(settings.yearBudgetCents, settings.budgetCents, settings.dayBudgetCents, all, today),
            demoCount = demo,
            loaded = true,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        LedgerState(
            month = month.value,
            today = LocalDate.now(),
            records = emptyList(),
            allRecords = emptyList(),
            days = emptyList(),
            summary = MonthSummary.of(month.value, emptyList(), Settings.DEFAULT_BUDGET_CENTS, LocalDate.now()),
            months = listOf(month.value),
            settings = Settings(),
            catalog = CategoryCatalog.Builtin,
            totals = TotalBudget.of(0, Settings.DEFAULT_BUDGET_CENTS, 0, emptyList(), LocalDate.now()),
            demoCount = 0,
            loaded = false,
        ),
    )

    /** "定时记账" rules, oldest first. */
    val recurring: StateFlow<List<RecurringRule>> =
        combine(ruleDao.observeAll(), catalog) { rows, cat -> rows.map { it.toDomain(cat) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            seedValueChips()
            // Photos of records deleted more than a day ago (undo long gone).
            images.prune(dao.all().flatMap { ImageStore.split(it.images) }.toSet())
        }
        if (BuildConfig.DEBUG) {
            viewModelScope.launch {
                if (container.settings.markSeeded() && dao.count() == 0) {
                    dao.insertAll(DemoData.build(LocalDate.now(), System.currentTimeMillis()))
                }
            }
        }
    }

    fun selectMonth(m: YearMonth) = month.update { m }

    // Records -------------------------------------------------------------------------------

    fun save(id: Long?, category: Category, cents: Long, note: String, at: LocalDateTime, photos: List<String> = emptyList()) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val stored = ImageStore.join(photos.take(ImageStore.MAX_PER_RECORD))
            if (id == null) {
                val newId = dao.insert(
                    RecordEntity(
                        category = category.key, amountCents = cents, note = note.trim(), occurredAt = at.toEpochMillis(),
                        createdAt = now, images = stored,
                    ),
                )
                _justAdded.value = newId
                month.value = YearMonth.from(at)
                showToast("已记一笔 · ${category.label} ¥${formatCents(cents)}")
            } else {
                val old = state.value.allRecords.firstOrNull { it.id == id }
                dao.update(
                    RecordEntity(
                        id = id, category = category.key, amountCents = cents, note = note.trim(), occurredAt = at.toEpochMillis(),
                        createdAt = now, demo = old?.demo ?: false, images = stored, ruleId = old?.ruleId,
                    ),
                )
                showToast("已保存修改")
            }
        }
    }

    fun delete(record: Record) {
        viewModelScope.launch {
            dao.delete(record.id)
            val entity = RecordEntity(
                id = record.id,
                category = record.category.key,
                amountCents = record.amountCents,
                note = record.note,
                occurredAt = record.occurredAt.toEpochMillis(),
                createdAt = System.currentTimeMillis(),
                demo = record.demo,
                images = ImageStore.join(record.images),
                ruleId = record.ruleId,
            )
            showToast("已删除 · ${record.title}", undo = entity)
        }
    }

    fun undo(message: ToastMessage) {
        val row = message.undo ?: return
        dismissToast(message.id)
        viewModelScope.launch { dao.insert(row) }
    }

    fun clearJustAdded() { _justAdded.value = null }

    fun dismissToast(id: Long) = _toast.update { if (it?.id == id) null else it }

    /** A plain top message, e.g. "已是最新版本". */
    fun showMessage(text: String) = showToast(text)

    /** The in-app updater (kept in [AppContainer] so a download outlives this ViewModel). */
    val updater get() = container.updater

    // Note chips ----------------------------------------------------------------------------

    /** Quick-fill notes for [category]: notes used 3+ times, then ones added with "+". */
    fun noteChips(category: Category): Flow<List<NoteChip>> =
        combine(
            dao.observeFrequentNotes(category.key, NoteChip.FREQUENT_MIN, NoteChip.FETCH),
            chipDao.observe(category.key),
        ) { frequent, rows ->
            NoteChip.merge(
                frequent = frequent.map { it.trim() },
                custom = rows.filter { it.pinned }.map { it.text },
                hidden = rows.filterNot { it.pinned }.map { it.text }.toSet(),
            )
        }

    fun addNoteChip(category: Category, text: String) {
        val t = text.trim().take(40)
        if (t.isEmpty()) return
        viewModelScope.launch { chipDao.upsert(NoteChipEntity(category.key, t, pinned = true, createdAt = System.currentTimeMillis())) }
    }

    /** Removes a chip. Stored as "hidden" so a frequently used note doesn't come straight back. */
    fun removeNoteChip(category: Category, chip: NoteChip) {
        viewModelScope.launch {
            chipDao.upsert(NoteChipEntity(category.key, chip.text, pinned = false, createdAt = System.currentTimeMillis()))
        }
    }

    // Value chips (quick amounts in budget forms) -------------------------------------------

    /**
     * Quick amounts for a form field, e.g. [budgetChipScope]. Unlike notes, any value used once
     * is kept ([rememberValue]); the user can also add (+) and remove (long-press ×) them.
     */
    fun valueChips(scope: String): Flow<List<NoteChip>> =
        chipDao.observe(scope).map { rows -> rows.filter { it.pinned }.map { NoteChip(it.text, custom = true) } }

    fun addValueChip(scope: String, text: String) {
        val t = text.trim().take(20)
        if (t.isEmpty()) return
        viewModelScope.launch { chipDao.upsert(NoteChipEntity(scope, t, pinned = true, createdAt = System.currentTimeMillis())) }
    }

    fun removeValueChip(scope: String, chip: NoteChip) {
        viewModelScope.launch { chipDao.delete(scope, chip.text) }
    }

    /** Adds [cents] to [scope]'s chips unless it is already there (keeps its position). */
    private suspend fun rememberValue(scope: String, cents: Long) {
        if (cents <= 0) return
        val text = plainYuan(cents)
        if (chipDao.all().none { it.category == scope && it.text == text }) {
            chipDao.upsert(NoteChipEntity(scope, text, pinned = true, createdAt = System.currentTimeMillis()))
        }
    }

    /** Once, after the update that replaced the fixed budget presets: start from the amounts in use. */
    private suspend fun seedValueChips() {
        if (!container.settings.markOnce("value_chips_v4")) return
        val s = container.settings.current()
        BudgetPeriod.entries.forEach { rememberValue(budgetChipScope(it), s.totalFor(it)) }
        budgetDao.all().forEach { rememberValue(budgetChipScope(BudgetPeriod.of(it.period)), it.amountCents) }
    }

    // Photos --------------------------------------------------------------------------------

    /** A target for the camera; pass the same Uri to [importPhoto] once it reports success. */
    fun newCaptureUri(): Uri = images.newCaptureUri()

    /** Copies a picked / captured photo into app storage; null if it couldn't be read. */
    suspend fun importPhoto(uri: Uri): String? = images.import(uri).also {
        if (it == null) showToast("这张图片读不出来")
    }

    fun photoFile(name: String): File = images.file(name)

    // Categories ----------------------------------------------------------------------------

    /**
     * Creates a custom category, or edits any category. Built-ins keep their key and kind; their
     * new name / icon / colour live in category_prefs. [color] (ARGB) overrides [hue] when set.
     */
    fun saveCategory(existing: Category?, name: String, kind: EntryKind, icon: String, hue: Hue, color: Int? = null) {
        val label = name.trim().take(6)
        if (label.isEmpty()) return
        val k = existing?.kind ?: kind
        if (state.value.catalog.forKind(k).any { it.label == label && it.key != existing?.key }) {
            showToast("已有同名分类「$label」")
            return
        }
        viewModelScope.launch {
            when {
                existing == null -> {
                    val id = categoryDao.insert(
                        CustomCategoryEntity(
                            name = label, kind = k.key, icon = icon, hue = hue.name,
                            sortOrder = categoryDao.maxSort() + 1, createdAt = System.currentTimeMillis(), color = color,
                        ),
                    )
                    slotIntoOrder(k, Category.customKey(id))
                    showToast("已添加分类 · $label")
                }
                existing.custom -> {
                    val id = existing.key.removePrefix("c").toLongOrNull() ?: return@launch
                    val old = categoryDao.all().firstOrNull { it.id == id } ?: return@launch
                    categoryDao.update(old.copy(name = label, icon = icon, hue = hue.name, color = color))
                    showToast("分类已保存")
                }
                else -> {
                    val base = Category.of(existing.key)
                    val old = prefDao.get(existing.key)
                    prefDao.upsert(
                        CategoryPrefEntity(
                            key = existing.key,
                            name = label.takeIf { it != base.label },
                            icon = icon.takeIf { it != base.icon },
                            hue = hue.name.takeIf { hue != base.hue || color != null },
                            color = color,
                            sortOrder = old?.sortOrder,
                        ),
                    )
                    showToast("分类已保存")
                }
            }
        }
    }

    /** Puts a built-in back to its original name, icon and colour; its grid position stays. */
    fun resetCategory(category: Category) {
        if (category.custom) return
        viewModelScope.launch {
            val old = prefDao.get(category.key) ?: return@launch
            if (old.sortOrder == null) prefDao.delete(category.key) else prefDao.upsert(CategoryPrefEntity(old.key, sortOrder = old.sortOrder))
            showToast("已恢复默认 · ${Category.of(category.key).label}")
        }
    }

    /** Saves the add sheet's grid order for one kind, after a drag. [keys] is the full new order. */
    fun reorderCategories(keys: List<String>) {
        viewModelScope.launch {
            val prefs = prefDao.all().associateBy { it.key }
            db.withTransaction {
                prefDao.upsertAll(keys.mapIndexed { i, key -> (prefs[key] ?: CategoryPrefEntity(key)).copy(sortOrder = i) })
            }
        }
    }

    /** A new custom category joins a dragged order just before "其他" (or last if 其他 was moved). */
    private suspend fun slotIntoOrder(kind: EntryKind, key: String, catalog: CategoryCatalog = state.value.catalog) {
        val prefs = prefDao.all().associateBy { it.key }
        val current = catalog.forKind(kind).map { it.key }.filter { it != key }
        if (current.none { prefs[it]?.sortOrder != null }) return
        val other = Category.otherOf(kind).key
        val order = if (current.lastOrNull() == other) current.dropLast(1) + key + other else current + key
        prefDao.upsertAll(order.mapIndexed { i, k -> (prefs[k] ?: CategoryPrefEntity(k)).copy(sortOrder = i) })
    }

    /** Deletes a custom category; its records and recurring rules move to "其他", its budgets go. */
    fun deleteCategory(category: Category) {
        if (!category.custom) return
        val id = category.key.removePrefix("c").toLongOrNull() ?: return
        viewModelScope.launch {
            val moved = state.value.allRecords.count { it.category.key == category.key }
            val other = Category.otherOf(category.kind).key
            db.withTransaction {
                dao.reassign(category.key, other)
                ruleDao.reassign(category.key, other)
                budgetDao.deleteForCategory(category.key)
                chipDao.deleteForCategory(category.key)
                prefDao.delete(category.key)
                categoryDao.delete(id)
            }
            showToast(if (moved > 0) "已删除 · $moved 笔移到「其他」" else "已删除分类 · ${category.label}")
        }
    }

    // Recurring records ---------------------------------------------------------------------

    private val recurringLock = Mutex()

    /**
     * Writes every due record of every active rule up to today. Runs at start-up and whenever the
     * app comes back to the foreground, so missed days are caught up (dated on their own day).
     */
    fun runRecurring() {
        viewModelScope.launch {
            val written = recurringLock.withLock {
                val today = LocalDate.now()
                val cat = state.value.catalog
                var n = 0
                db.withTransaction {
                    for (row in ruleDao.all()) {
                        val rule = row.toDomain(cat)
                        if (rule.paused) continue
                        val due = rule.pending(today)
                        if (due.isNotEmpty()) {
                            val now = System.currentTimeMillis()
                            dao.insertAll(
                                due.map { d ->
                                    RecordEntity(
                                        category = row.category, amountCents = row.amountCents, note = row.note,
                                        occurredAt = rule.at(d).toEpochMillis(), createdAt = now, ruleId = row.id,
                                    )
                                },
                            )
                            n += due.size
                        }
                        // Up to today even when nothing was due, so a long gap is never re-scanned.
                        val handled = if (due.size >= RecurringRule.MAX_CATCH_UP) due.last() else today
                        if (row.lastDay == null || row.lastDay < handled.toEpochDay()) ruleDao.markRun(row.id, handled.toEpochDay())
                    }
                }
                n
            }
            if (written > 0) showToast("已自动记账 $written 笔")
        }
    }

    /** Creates or edits a rule; a start date in the past back-fills the days since then. */
    fun saveRecurring(
        id: Long?,
        category: Category,
        cents: Long,
        note: String,
        repeat: Repeat,
        days: Int,
        yearDays: List<Int>,
        start: LocalDate,
        end: LocalDate?,
    ) {
        // Each repeat keeps only its own picks, and a rule that could never run is not saved (the form
        // already insists on one pick; this guards every other caller).
        val dates = if (repeat == Repeat.Yearly) RecurringRule.formatYearDays(yearDays) else ""
        val picked = repeat == Repeat.Weekly || repeat == Repeat.Monthly
        val bits = if (picked) days else 0
        if (repeat == Repeat.Yearly && dates.isEmpty() || picked && bits == 0) return
        viewModelScope.launch {
            recurringLock.withLock {
                if (id == null) {
                    ruleDao.insert(
                        RecurringEntity(
                            category = category.key, amountCents = cents, note = note.trim(), repeat = repeat.key, days = bits,
                            yearDays = dates, startDay = start.toEpochDay(), endDay = end?.toEpochDay(),
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                } else {
                    val old = ruleDao.all().firstOrNull { it.id == id } ?: return@withLock
                    ruleDao.update(
                        old.copy(
                            category = category.key, amountCents = cents, note = note.trim(), repeat = repeat.key, days = bits,
                            yearDays = dates, startDay = start.toEpochDay(), endDay = end?.toEpochDay(),
                        ),
                    )
                }
            }
            showToast(if (id == null) "已添加定时记账" else "定时记账已保存")
            runRecurring()
        }
    }

    /** Pausing skips the paused days for good: resuming starts from today, nothing is back-filled. */
    fun setRecurringPaused(rule: RecurringRule, paused: Boolean) {
        viewModelScope.launch {
            recurringLock.withLock {
                val old = ruleDao.all().firstOrNull { it.id == rule.id } ?: return@withLock
                val yesterday = LocalDate.now().minusDays(1).toEpochDay()
                ruleDao.update(
                    if (paused) old.copy(paused = true) else old.copy(paused = false, lastDay = maxOf(old.lastDay ?: yesterday, yesterday)),
                )
            }
            if (!paused) runRecurring()
        }
    }

    /** Deletes a rule; the records it already wrote stay. */
    fun deleteRecurring(rule: RecurringRule) {
        viewModelScope.launch {
            ruleDao.delete(rule.id)
            showToast("已删除定时记账")
        }
    }

    // Settings & budgets --------------------------------------------------------------------

    fun setTheme(theme: ThemeId) = viewModelScope.launch { container.settings.setTheme(theme) }

    fun setBudget(cents: Long) = setTotalBudget(BudgetPeriod.Month, cents)

    /** Sets the year / month / day total budget; 0 clears year or day. */
    fun setTotalBudget(period: BudgetPeriod, cents: Long) = viewModelScope.launch {
        container.settings.setTotalBudget(period, cents)
        rememberValue(budgetChipScope(period), cents)
        val name = when (period) {
            BudgetPeriod.Year -> "年预算"
            BudgetPeriod.Month -> "月预算"
            BudgetPeriod.Day -> "日预算"
        }
        showToast(if (cents > 0) "$name 已设为 ¥${formatCents(cents)}" else "已取消$name")
    }

    fun saveBudget(id: Long?, name: String, period: BudgetPeriod, category: Category?, cents: Long) = viewModelScope.launch {
        val row = BudgetEntity(
            id = id ?: 0,
            name = name.trim(),
            period = period.key,
            category = category?.key,
            amountCents = cents,
            createdAt = System.currentTimeMillis(),
        )
        if (id == null) budgetDao.insert(row) else budgetDao.update(row)
        rememberValue(budgetChipScope(period), cents)
        showToast(if (id == null) "已添加预算" else "预算已保存")
    }

    fun deleteBudget(id: Long) = viewModelScope.launch {
        budgetDao.delete(id)
        showToast("预算已删除")
    }

    fun clearDemo() = viewModelScope.launch {
        dao.deleteDemo()
        showToast("示例数据已清除")
    }

    // Backup --------------------------------------------------------------------------------

    fun exportTo(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val records = dao.all()
                val photos = records.filterNot { it.demo }.flatMap { ImageStore.split(it.images) }.distinct()
                val json = Backup.encode(
                    Backup.Decoded(
                        records = records,
                        budgets = budgetDao.all(),
                        categories = categoryDao.all(),
                        chips = chipDao.all(),
                        settings = container.settings.current(),
                        prefs = prefDao.all(),
                        rules = ruleDao.all(),
                        images = photos.mapNotNull { name -> images.read(name)?.let { name to it } }.toMap(),
                    ),
                    System.currentTimeMillis(),
                )
                resolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
                records.count { !it.demo }
            }
        }
        showToast(result.fold({ "已导出 $it 条记录" }, { "导出失败：${it.message}" }))
    }

    /** Reads a backup and returns how many records it holds, without changing anything yet. */
    suspend fun readBackup(resolver: ContentResolver, uri: Uri): Result<Backup.Decoded> = runCatching {
        withContext(Dispatchers.IO) {
            val text = resolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            Backup.decode(text)
        }
    }

    fun restore(backup: Backup.Decoded) = viewModelScope.launch {
        backup.images.forEach { (name, bytes) -> images.write(name, bytes) }
        recurringLock.withLock {
            db.withTransaction {
                categoryDao.replaceAll(backup.categories)
                dao.replaceAll(backup.records)
                budgetDao.replaceAll(backup.budgets)
                chipDao.replaceAll(backup.chips)
                prefDao.replaceAll(backup.prefs)
                ruleDao.replaceAll(backup.rules)
            }
        }
        with(container.settings) {
            setTotalBudget(BudgetPeriod.Month, backup.settings.budgetCents)
            setTotalBudget(BudgetPeriod.Year, backup.settings.yearBudgetCents)
            setTotalBudget(BudgetPeriod.Day, backup.settings.dayBudgetCents)
            setTheme(backup.settings.theme)
        }
        showToast("已导入 ${backup.records.size} 条记录")
        runRecurring()
    }

    // Import --------------------------------------------------------------------------------

    /** Reads a 鲨鱼记账 CSV and plans the import (categories, times, duplicates); nothing is written yet. */
    suspend fun readSharkCsv(resolver: ContentResolver, uri: Uri): Result<SharkPlan> = withContext(Dispatchers.IO) {
        try {
            val input = resolver.openInputStream(uri) ?: throw SharkFormatException("打不开这个文件")
            val parsed = SharkImport.parse(input.use { SharkImport.read(it) })
            val existing = dao.all().filterNot { it.demo }.map { it.toSharkExisting() }
            Result.success(SharkImport.plan(parsed, catalog.first(), existing))
        } catch (e: SharkFormatException) {
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (_: OutOfMemoryError) {
            // A near-limit file of tiny rows can still be more than the phone's heap holds.
            Result.failure(SharkFormatException("文件太大了，手机读不下，请拆成几份再导入"))
        } catch (_: Exception) {
            Result.failure(SharkFormatException("文件读取失败，换个位置再试试"))
        }
    }

    /**
     * Writes [plan] in one transaction: the new categories first (made the way [saveCategory] makes
     * them), then the records. A row already in the ledger (same day, category, amount and note) is
     * skipped, so importing the same file twice adds nothing. With [createCategories] false, rows
     * of categories the ledger lacks go to 其他.
     */
    fun importShark(plan: SharkPlan, createCategories: Boolean) {
        viewModelScope.launch {
            val result = runCatching {
                db.withTransaction {
                    suspend fun fresh() = CategoryCatalog(categoryDao.all().map { it.toDomain() }, prefDao.all().associate { it.key to it.toDomain() })
                    // Categories made since the file was read (an earlier tap on 导入) are used, not made again.
                    val base = if (createCategories) plan.against(fresh()) else plan
                    val todo = base.resolve(createCategories, dao.all().filterNot { it.demo }.map { it.toSharkExisting() })
                    val keys = HashMap<SharkNewCategory, String>()
                    for (nc in todo.created) {
                        val id = categoryDao.insert(
                            CustomCategoryEntity(
                                name = nc.name, kind = nc.kind.key, icon = nc.icon, hue = nc.hue.name,
                                sortOrder = categoryDao.maxSort() + 1, createdAt = System.currentTimeMillis(),
                            ),
                        )
                        keys[nc] = Category.customKey(id)
                        slotIntoOrder(nc.kind, Category.customKey(id), fresh())
                    }
                    val now = System.currentTimeMillis()
                    dao.insertAll(
                        todo.entries.map { e ->
                            RecordEntity(
                                category = when (val t = e.target) {
                                    is SharkTarget.Existing -> t.key
                                    is SharkTarget.Create -> keys.getValue(t.category)
                                },
                                amountCents = e.cents, note = e.note, occurredAt = e.at.toEpochMillis(), createdAt = now,
                            )
                        },
                    )
                    todo
                }
            }
            showToast(result.fold({ "已导入 ${it.entries.size} 条，跳过重复 ${it.skipped} 条" }, { "导入失败：${it.message}" }))
        }
    }

    private var toastSeq = 0L

    companion object {
        /** [valueChips] scope for the amount fields of [period]'s total and custom budgets. */
        fun budgetChipScope(period: BudgetPeriod) = "\$budget:" + period.key

        /** 4550 → "45.5", 300000 → "3000". */
        fun plainYuan(cents: Long): String = BigDecimal.valueOf(cents).movePointLeft(2).stripTrailingZeros().toPlainString()
    }

    private fun showToast(text: String, undo: RecordEntity? = null) {
        _toast.value = ToastMessage(++toastSeq, text, undo)
    }
}
