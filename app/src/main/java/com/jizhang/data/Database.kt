package com.jizhang.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jizhang.domain.Budget
import com.jizhang.domain.BudgetPeriod
import com.jizhang.domain.Category
import com.jizhang.domain.CategoryCatalog
import com.jizhang.domain.CategoryPref
import com.jizhang.domain.EntryKind
import com.jizhang.domain.Hue
import com.jizhang.domain.Record
import com.jizhang.domain.RecurringRule
import com.jizhang.domain.Repeat
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Entity(tableName = "records", indices = [Index("occurredAt")])
data class RecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [Category.key]; the kind (expense / income) follows from the category. */
    val category: String,
    val amountCents: Long,
    val note: String,
    /** Epoch millis in the device's zone at the time of entry. */
    val occurredAt: Long,
    val createdAt: Long,
    /** Debug-build sample rows, so they can be removed in one go. */
    val demo: Boolean = false,
    /** Photo file names in [ImageStore], one per line; empty for none. */
    @ColumnInfo(defaultValue = "") val images: String = "",
    /** The [RecurringEntity] that wrote this row, if any. */
    val ruleId: Long? = null,
)

@Dao
interface RecordDao {
    @Query("SELECT * FROM records WHERE occurredAt >= :from AND occurredAt < :to ORDER BY occurredAt DESC")
    fun observeRange(from: Long, to: Long): Flow<List<RecordEntity>>

    @Query("SELECT MIN(occurredAt) FROM records")
    fun observeEarliest(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM records WHERE demo = 1")
    fun observeDemoCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM records")
    suspend fun count(): Int

    @Query("SELECT * FROM records ORDER BY occurredAt")
    suspend fun all(): List<RecordEntity>

    @Insert
    suspend fun insert(record: RecordEntity): Long

    @Insert
    suspend fun insertAll(records: List<RecordEntity>)

    @Update
    suspend fun update(record: RecordEntity)

    @Query("DELETE FROM records WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM records WHERE demo = 1")
    suspend fun deleteDemo()

    @Query("DELETE FROM records")
    suspend fun deleteAll()

    @Query("SELECT * FROM records ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<RecordEntity>>

    /**
     * Notes written at least [min] times in one category, most used first. Grouped on the trimmed
     * text so "午饭" and "午饭 " (older rows, imports) count as one note; blank notes never count.
     */
    @Query(
        "SELECT TRIM(note) AS t FROM records WHERE category = :category AND TRIM(note) != '' " +
            "GROUP BY TRIM(note) HAVING COUNT(*) >= :min ORDER BY COUNT(*) DESC, MAX(occurredAt) DESC LIMIT :limit",
    )
    fun observeFrequentNotes(category: String, min: Int, limit: Int): Flow<List<String>>

    /** Moves records of a deleted custom category into "其他". */
    @Query("UPDATE records SET category = :to WHERE category = :from")
    suspend fun reassign(from: String, to: String)

    @Transaction
    suspend fun replaceAll(records: List<RecordEntity>) {
        deleteAll()
        insertAll(records)
    }
}

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Blank means "use the generated name", see [Budget.displayName]. */
    val name: String,
    /** [BudgetPeriod.key] */
    val period: String,
    /** [Category.key] of an expense category, or null for all spending. */
    val category: String?,
    val amountCents: Long,
    val createdAt: Long,
)

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY createdAt")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets ORDER BY createdAt")
    suspend fun all(): List<BudgetEntity>

    @Insert
    suspend fun insert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM budgets WHERE category = :category")
    suspend fun deleteForCategory(category: String)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(budgets: List<BudgetEntity>) {
        deleteAll()
        budgets.forEach { insert(it) }
    }
}

/** A category the user made. Records refer to it by [Category.customKey] ("c" + id). */
@Entity(tableName = "custom_categories")
data class CustomCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** [EntryKind.key] */
    val kind: String,
    /** Icon name, see [com.jizhang.ui.theme.LedgerIcons]. */
    val icon: String,
    /** [Hue.name] */
    val hue: String,
    val sortOrder: Int,
    val createdAt: Long,
    /** A colour the user picked (ARGB), used instead of [hue]; null for a theme hue. */
    val color: Int? = null,
)

@Dao
interface CustomCategoryDao {
    @Query("SELECT * FROM custom_categories ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<CustomCategoryEntity>>

    @Query("SELECT * FROM custom_categories ORDER BY sortOrder, id")
    suspend fun all(): List<CustomCategoryEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM custom_categories")
    suspend fun maxSort(): Int

    @Insert
    suspend fun insert(category: CustomCategoryEntity): Long

    @Update
    suspend fun update(category: CustomCategoryEntity)

    @Query("DELETE FROM custom_categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM custom_categories")
    suspend fun deleteAll()

    /** Keeps ids, because records point at "c" + id. */
    @Transaction
    suspend fun replaceAll(categories: List<CustomCategoryEntity>) {
        deleteAll()
        categories.forEach { insert(it) }
    }
}

/**
 * Per-category note chips the user changed: [pinned] true = added by hand with "+",
 * false = a learned (frequent) note the user removed, so it stays hidden.
 */
@Entity(tableName = "note_chips", primaryKeys = ["category", "text"])
data class NoteChipEntity(
    val category: String,
    val text: String,
    val pinned: Boolean,
    val createdAt: Long,
)

@Dao
interface NoteChipDao {
    @Query("SELECT * FROM note_chips WHERE category = :category ORDER BY createdAt")
    fun observe(category: String): Flow<List<NoteChipEntity>>

    @Query("SELECT * FROM note_chips ORDER BY createdAt")
    suspend fun all(): List<NoteChipEntity>

    @androidx.room.Upsert
    suspend fun upsert(chip: NoteChipEntity)

    @Query("DELETE FROM note_chips WHERE category = :category AND text = :text")
    suspend fun delete(category: String, text: String)

    @Query("DELETE FROM note_chips WHERE category = :category")
    suspend fun deleteForCategory(category: String)

    @Query("DELETE FROM note_chips")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(chips: List<NoteChipEntity>) {
        deleteAll()
        chips.forEach { upsert(it) }
    }
}

/**
 * Per-category edits, keyed by [Category.key]. For built-ins: a new name / icon / hue / colour
 * (null = unchanged). For every category: its position in the add sheet's grid after a drag.
 */
@Entity(tableName = "category_prefs")
data class CategoryPrefEntity(
    @PrimaryKey val key: String,
    val name: String? = null,
    val icon: String? = null,
    val hue: String? = null,
    val color: Int? = null,
    val sortOrder: Int? = null,
)

@Dao
interface CategoryPrefDao {
    @Query("SELECT * FROM category_prefs")
    fun observeAll(): Flow<List<CategoryPrefEntity>>

    @Query("SELECT * FROM category_prefs")
    suspend fun all(): List<CategoryPrefEntity>

    @Query("SELECT * FROM category_prefs WHERE `key` = :key")
    suspend fun get(key: String): CategoryPrefEntity?

    @androidx.room.Upsert
    suspend fun upsert(pref: CategoryPrefEntity)

    @androidx.room.Upsert
    suspend fun upsertAll(prefs: List<CategoryPrefEntity>)

    @Query("DELETE FROM category_prefs WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM category_prefs")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(prefs: List<CategoryPrefEntity>) {
        deleteAll()
        upsertAll(prefs)
    }
}

/** A "定时记账" rule, see [RecurringRule]. Days are [java.time.LocalDate.toEpochDay] values. */
@Entity(tableName = "recurring")
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val amountCents: Long,
    val note: String,
    /** [Repeat.key] */
    val repeat: String,
    /** See [RecurringRule.days]. */
    val days: Int,
    /** [RecurringRule.yearDays] as "308,1225" (sorted); empty unless the rule is yearly. */
    @ColumnInfo(defaultValue = "") val yearDays: String = "",
    val startDay: Long,
    val endDay: Long? = null,
    /** The last day already handled; null until the rule first runs. */
    val lastDay: Long? = null,
    val minuteOfDay: Int = RecurringRule.DEFAULT_MINUTE,
    val paused: Boolean = false,
    val createdAt: Long,
)

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring ORDER BY createdAt")
    fun observeAll(): Flow<List<RecurringEntity>>

    @Query("SELECT * FROM recurring ORDER BY createdAt")
    suspend fun all(): List<RecurringEntity>

    @Insert
    suspend fun insert(rule: RecurringEntity): Long

    @Update
    suspend fun update(rule: RecurringEntity)

    /** Moves rules of a deleted custom category into "其他". */
    @Query("UPDATE recurring SET category = :to WHERE category = :from")
    suspend fun reassign(from: String, to: String)

    @Query("UPDATE recurring SET lastDay = :day WHERE id = :id")
    suspend fun markRun(id: Long, day: Long)

    @Query("DELETE FROM recurring WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM recurring")
    suspend fun deleteAll()

    /** Keeps ids, because records point back at their rule. */
    @Transaction
    suspend fun replaceAll(rules: List<RecurringEntity>) {
        deleteAll()
        rules.forEach { insert(it) }
    }
}

@Database(
    entities = [
        RecordEntity::class, BudgetEntity::class, CustomCategoryEntity::class, NoteChipEntity::class,
        CategoryPrefEntity::class, RecurringEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun records(): RecordDao
    abstract fun budgets(): BudgetDao
    abstract fun categories(): CustomCategoryDao
    abstract fun noteChips(): NoteChipDao
    abstract fun categoryPrefs(): CategoryPrefDao
    abstract fun recurring(): RecurringDao
}

/** v2 adds custom budgets; existing records are untouched. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `period` TEXT NOT NULL, `category` TEXT, " +
                "`amountCents` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
        )
    }
}

/** v3 adds custom categories and note chips; existing data is untouched. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `custom_categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `kind` TEXT NOT NULL, `icon` TEXT NOT NULL, `hue` TEXT NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `note_chips` (`category` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                "`pinned` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`category`, `text`))",
        )
    }
}

/**
 * v4: photos and the source rule on records, a picked colour on custom categories, built-in
 * category edits and grid order, and recurring records. Existing data is untouched.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `records` ADD COLUMN `images` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `records` ADD COLUMN `ruleId` INTEGER")
        db.execSQL("ALTER TABLE `custom_categories` ADD COLUMN `color` INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `category_prefs` (`key` TEXT NOT NULL, `name` TEXT, `icon` TEXT, " +
                "`hue` TEXT, `color` INTEGER, `sortOrder` INTEGER, PRIMARY KEY(`key`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recurring` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`category` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                "`repeat` TEXT NOT NULL, `days` INTEGER NOT NULL, `startDay` INTEGER NOT NULL, `endDay` INTEGER, " +
                "`lastDay` INTEGER, `minuteOfDay` INTEGER NOT NULL, `paused` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
        )
    }
}

/**
 * v5: recurring rules can pick several days of the month / dates of the year. Monthly [days] turns
 * from a day number into a day-of-month bit set, and a yearly rule's month × 100 + day moves to the
 * new `yearDays` list (see [RecurringRule.upgradeLegacy]). Existing records are untouched.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `recurring` ADD COLUMN `yearDays` TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE `recurring` SET `days` = (1 << (`days` - 1)) WHERE `repeat` = 'monthly' AND `days` BETWEEN 1 AND 31")
        db.execSQL("UPDATE `recurring` SET `yearDays` = CAST(`days` AS TEXT), `days` = 0 WHERE `repeat` = 'yearly'")
    }
}

fun BudgetEntity.toDomain(catalog: CategoryCatalog) = Budget(
    id = id,
    name = name,
    period = BudgetPeriod.of(period),
    category = category?.let(catalog::of),
    amountCents = amountCents,
)

fun CustomCategoryEntity.toDomain() = Category(
    key = Category.customKey(id),
    label = name,
    kind = EntryKind.of(kind),
    hue = Hue.entries.firstOrNull { it.name == hue } ?: Hue.Other,
    icon = icon,
    custom = true,
    color = color,
)

fun CategoryPrefEntity.toDomain() = CategoryPref(
    key = key,
    name = name,
    icon = icon,
    hue = hue?.let { h -> Hue.entries.firstOrNull { it.name == h } },
    color = color,
    sortOrder = sortOrder,
)

fun RecurringEntity.toDomain(catalog: CategoryCatalog) = RecurringRule(
    id = id,
    category = catalog.of(category),
    amountCents = amountCents,
    note = note,
    repeat = Repeat.of(repeat),
    days = days,
    start = LocalDate.ofEpochDay(startDay),
    end = endDay?.let(LocalDate::ofEpochDay),
    last = lastDay?.let(LocalDate::ofEpochDay),
    minuteOfDay = minuteOfDay,
    paused = paused,
    yearDays = RecurringRule.parseYearDays(yearDays),
)

private val zone: ZoneId get() = ZoneId.systemDefault()

fun LocalDateTime.toEpochMillis(): Long = atZone(zone).toInstant().toEpochMilli()

fun Long.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zone)

fun RecordEntity.toDomain(catalog: CategoryCatalog) = Record(
    id = id,
    category = catalog.of(category),
    amountCents = amountCents,
    note = note,
    occurredAt = occurredAt.toLocalDateTime(),
    demo = demo,
    images = ImageStore.split(images),
    ruleId = ruleId,
)
