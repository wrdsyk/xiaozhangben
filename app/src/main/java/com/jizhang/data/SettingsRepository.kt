package com.jizhang.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jizhang.domain.BudgetPeriod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeId(val key: String, val label: String) {
    Pearl("pearl", "珍珠"),
    Glacier("glacier", "冰川"),
    Dusk("dusk", "暮蓝"),
    Matcha("matcha", "抹茶"),
    Sakura("sakura", "樱粉"),
    Abyss("abyss", "深海"),
    Ink("ink", "墨夜");

    companion object {
        fun of(key: String?): ThemeId = entries.firstOrNull { it.key == key } ?: Pearl
    }
}

/** How glass surfaces are finished. Keys are stored, so never rename one. */
enum class GlassFinish(val key: String, val label: String) {
    /** Set A of the design canvas: thin glass, a bevel lit at two corners, a faint pink/blue fringe. */
    Crystal("clear", "晶透"),
    /**
     * QWEA0/Liquid-Glass-Android's lens (LiquidGlass.kt). Shipped as 晶透 in 0.5.8, so it keeps
     * that finish's key and owners who picked it stay on it.
     */
    Liquid("crystal", "液态"),
    /** Frosted: a wider blur that also takes in the list scrolling behind the nav bar and sheets. */
    Frost("frost", "雾透");

    companion object {
        fun of(key: String?): GlassFinish = entries.firstOrNull { it.key == key } ?: Liquid
    }
}

data class Settings(
    val theme: ThemeId = ThemeId.Pearl,
    val glass: GlassFinish = GlassFinish.Liquid,
    /** Monthly total budget; drives the home ring. */
    val budgetCents: Long = DEFAULT_BUDGET_CENTS,
    /** Yearly / daily total budgets; 0 = not set. */
    val yearBudgetCents: Long = 0,
    val dayBudgetCents: Long = 0,
) {
    fun totalFor(period: BudgetPeriod): Long = when (period) {
        BudgetPeriod.Year -> yearBudgetCents
        BudgetPeriod.Month -> budgetCents
        BudgetPeriod.Day -> dayBudgetCents
    }

    companion object {
        const val DEFAULT_BUDGET_CENTS = 450_000L
    }
}

/** What the in-app updater remembers between launches ([Updater]). */
data class UpdatePrefs(
    val autoCheck: Boolean = true,
    /** Epoch millis of the last successful check; 0 = never. */
    val lastCheckAt: Long = 0,
    /** Version the owner chose 跳过这个版本 for ("" = none); only the auto-prompt honours it. */
    val skippedVersion: String = "",
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val glassKey = stringPreferencesKey("glass_finish")
    private val budgetKey = longPreferencesKey("budget_cents")
    private val yearBudgetKey = longPreferencesKey("year_budget_cents")
    private val dayBudgetKey = longPreferencesKey("day_budget_cents")
    private val seededKey = booleanPreferencesKey("demo_seeded")

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            theme = ThemeId.of(p[themeKey]),
            glass = GlassFinish.of(p[glassKey]),
            budgetCents = p[budgetKey] ?: Settings.DEFAULT_BUDGET_CENTS,
            yearBudgetCents = p[yearBudgetKey] ?: 0,
            dayBudgetCents = p[dayBudgetKey] ?: 0,
        )
    }

    suspend fun setTheme(theme: ThemeId) = context.dataStore.edit { it[themeKey] = theme.key }

    suspend fun setGlass(finish: GlassFinish) = context.dataStore.edit { it[glassKey] = finish.key }

    suspend fun setBudget(cents: Long) = context.dataStore.edit { it[budgetKey] = cents }

    /** Sets one of the three total budgets; 0 clears the year / day one. */
    suspend fun setTotalBudget(period: BudgetPeriod, cents: Long) = context.dataStore.edit {
        when (period) {
            BudgetPeriod.Year -> it[yearBudgetKey] = cents
            BudgetPeriod.Month -> it[budgetKey] = cents
            BudgetPeriod.Day -> it[dayBudgetKey] = cents
        }
    }

    suspend fun current(): Settings = settings.first()

    private val autoUpdateKey = booleanPreferencesKey("update_auto_check")
    private val lastUpdateCheckKey = longPreferencesKey("update_last_check")
    private val skippedVersionKey = stringPreferencesKey("update_skipped_version")

    /** Kept apart from [settings] so a check finishing never re-emits the ledger state. */
    val updatePrefs: Flow<UpdatePrefs> = context.dataStore.data.map { p ->
        UpdatePrefs(
            autoCheck = p[autoUpdateKey] ?: true,
            lastCheckAt = p[lastUpdateCheckKey] ?: 0,
            skippedVersion = p[skippedVersionKey] ?: "",
        )
    }

    suspend fun setAutoCheckUpdates(on: Boolean) = context.dataStore.edit { it[autoUpdateKey] = on }

    suspend fun markUpdateChecked(at: Long) = context.dataStore.edit { it[lastUpdateCheckKey] = at }

    suspend fun skipUpdateVersion(version: String) = context.dataStore.edit { it[skippedVersionKey] = version }

    /** Returns true only the first time it is called with [name]; for one-off data fixes. */
    suspend fun markOnce(name: String): Boolean {
        val key = booleanPreferencesKey("once_" + name)
        var first = false
        context.dataStore.edit {
            first = it[key] != true
            it[key] = true
        }
        return first
    }

    /** Returns true only the first time it is called, so demo data is seeded at most once. */
    suspend fun markSeeded(): Boolean {
        var first = false
        context.dataStore.edit {
            first = it[seededKey] != true
            it[seededKey] = true
        }
        return first
    }
}
