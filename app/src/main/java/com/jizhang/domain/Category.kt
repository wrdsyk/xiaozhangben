package com.jizhang.domain

enum class EntryKind(val key: String) {
    Expense("expense"),
    Income("income");

    companion object {
        fun of(key: String): EntryKind = entries.firstOrNull { it.key == key } ?: Expense
    }
}

/** Colour slots shared by every theme; several income categories reuse an expense hue. */
enum class Hue { Food, Transit, Shop, Daily, Fun, Home, Med, Gift, Study, Other }

/**
 * A bill category: one of the built-ins below, or one the user made ([custom], key "c<id>").
 * [key] is what Room and backups store, so never rename a built-in key. [icon] names an icon
 * in [com.jizhang.ui.theme.LedgerIcons] (built-in glyphs or the icon library).
 */
data class Category(
    val key: String,
    val label: String,
    val kind: EntryKind,
    val hue: Hue,
    val icon: String,
    val custom: Boolean = false,
    /** A colour the user picked (ARGB); when set it replaces [hue] in every theme. */
    val color: Int? = null,
) {
    /** The catch-all bucket of the same kind; records of a deleted custom category move here. */
    val isOther: Boolean get() = key == Other.key || key == OtherIncome.key

    companion object {
        val Food = Category("food", "餐饮", EntryKind.Expense, Hue.Food, "food")
        val Transit = Category("transit", "交通", EntryKind.Expense, Hue.Transit, "transit")
        val Shop = Category("shop", "购物", EntryKind.Expense, Hue.Shop, "shop")
        val Daily = Category("daily", "日用", EntryKind.Expense, Hue.Daily, "daily")
        val Fun = Category("fun", "娱乐", EntryKind.Expense, Hue.Fun, "fun")
        val Home = Category("home", "居住", EntryKind.Expense, Hue.Home, "home")
        val Med = Category("med", "医疗", EntryKind.Expense, Hue.Med, "med")
        val Gift = Category("gift", "人情", EntryKind.Expense, Hue.Gift, "gift")
        val Study = Category("study", "学习", EntryKind.Expense, Hue.Study, "study")
        val Other = Category("other", "其他", EntryKind.Expense, Hue.Other, "other")
        val Salary = Category("salary", "工资", EntryKind.Income, Hue.Daily, "salary")
        val Bonus = Category("bonus", "奖金", EntryKind.Income, Hue.Home, "bonus")
        val Invest = Category("invest", "理财", EntryKind.Income, Hue.Transit, "invest")
        val OtherIncome = Category("other_in", "其他", EntryKind.Income, Hue.Other, "other")

        val builtins: List<Category> = listOf(
            Food, Transit, Shop, Daily, Fun, Home, Med, Gift, Study, Other,
            Salary, Bonus, Invest, OtherIncome,
        )
        private val builtinByKey = builtins.associateBy { it.key }

        fun customKey(id: Long) = "c$id"

        /** Built-in lookup only; prefer [CategoryCatalog.of] wherever custom categories may appear. */
        fun of(key: String): Category = builtinByKey[key] ?: Other

        fun otherOf(kind: EntryKind): Category = if (kind == EntryKind.Expense) Other else OtherIncome

        /** Built-ins of one kind, "其他" last. */
        fun forKind(kind: EntryKind): List<Category> = builtins.filter { it.kind == kind }
    }
}

/** The user's changes to a category: a new name / icon / colour (built-ins only) and its grid position. */
data class CategoryPref(
    val key: String,
    val name: String? = null,
    val icon: String? = null,
    val hue: Hue? = null,
    val color: Int? = null,
    val sortOrder: Int? = null,
) {
    /** A built-in with this pref's edits applied. */
    fun applyTo(c: Category): Category = c.copy(
        label = name ?: c.label,
        icon = icon ?: c.icon,
        hue = hue ?: c.hue,
        color = color ?: c.color,
    )
}

/**
 * Built-in plus user-made categories; the single place that turns a stored key into a [Category].
 * [prefs] rename / recolour built-ins and hold the grid order the user dragged into place.
 */
data class CategoryCatalog(
    val customs: List<Category> = emptyList(),
    val prefs: Map<String, CategoryPref> = emptyMap(),
) {
    private val builtins: List<Category> = Category.builtins.map { b -> prefs[b.key]?.applyTo(b) ?: b }
    private val byKey: Map<String, Category> = (builtins + customs).associateBy { it.key }

    /** Unknown keys (a custom category deleted elsewhere) fall back to "其他". */
    fun of(key: String): Category = byKey[key] ?: byKey.getValue(Category.Other.key)

    /** "其他" of [kind], with the user's edits. */
    fun otherOf(kind: EntryKind): Category = byKey.getValue(Category.otherOf(kind).key)

    /**
     * What the add sheet's grid shows. Default order: built-ins, then the user's own, then "其他".
     * Once the user drags, every category of that kind gets a saved position (new ones are slotted
     * in by the ViewModel); any still without one keep the default order after the saved ones.
     */
    fun forKind(kind: EntryKind): List<Category> {
        val mine = builtins.filter { it.kind == kind }
        val default = mine.filterNot { it.isOther } + customs.filter { it.kind == kind } + mine.filter { it.isOther }
        return default.withIndex()
            .sortedBy { (i, cat) -> prefs[cat.key]?.sortOrder ?: (DEFAULT_RANK + i) }
            .map { it.value }
    }

    companion object {
        private const val DEFAULT_RANK = 100_000
        val Builtin = CategoryCatalog()
    }
}
