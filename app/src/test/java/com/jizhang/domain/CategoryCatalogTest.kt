package com.jizhang.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class CategoryCatalogTest {
    private val pet = Category("c1", "宠物", EntryKind.Expense, Hue.Fun, "pet", custom = true)
    private val car = Category("c2", "养车", EntryKind.Expense, Hue.Transit, "car", custom = true, color = 0xFF336699.toInt())
    private val rent = Category("c3", "租金", EntryKind.Income, Hue.Home, "home", custom = true)

    private fun keys(list: List<Category>) = list.map { it.key }

    private val builtinExpense = listOf("food", "transit", "shop", "daily", "fun", "home", "med", "gift", "study")

    @Test fun prefsRenameAndRecolourBuiltins() {
        val color = 0xFF112233.toInt()
        val cat = CategoryCatalog(
            prefs = mapOf("food" to CategoryPref("food", name = "吃饭", icon = "coffee", hue = Hue.Fun, color = color)),
        )
        val food = cat.of("food")
        assertEquals("吃饭", food.label)
        assertEquals("coffee", food.icon)
        assertEquals(Hue.Fun, food.hue)
        assertEquals(color, food.color)
        // Key and kind never change.
        assertEquals("food", food.key)
        assertEquals(EntryKind.Expense, food.kind)
        // The edited instance is the one lists hand out too.
        assertEquals(food, cat.forKind(EntryKind.Expense).first { it.key == "food" })
    }

    @Test fun emptyPrefLeavesABuiltinAlone() {
        val cat = CategoryCatalog(prefs = mapOf("shop" to CategoryPref("shop", sortOrder = 3)))
        assertEquals(Category.Shop, cat.of("shop"))
        assertNull(cat.of("shop").color)
    }

    @Test fun defaultOrderIsBuiltinsThenCustomsThenOther() {
        val cat = CategoryCatalog(customs = listOf(pet, car, rent))
        assertEquals(builtinExpense + listOf("c1", "c2", "other"), keys(cat.forKind(EntryKind.Expense)))
        assertEquals(listOf("salary", "bonus", "invest", "c3", "other_in"), keys(cat.forKind(EntryKind.Income)))
        assertEquals(keys(Category.forKind(EntryKind.Expense)), keys(CategoryCatalog.Builtin.forKind(EntryKind.Expense)))
    }

    @Test fun savedOrderWins() {
        val order = listOf("other", "c1", "food", "transit", "shop", "daily", "fun", "home", "med", "gift", "study")
        val cat = CategoryCatalog(
            customs = listOf(pet),
            prefs = order.withIndex().associate { (i, k) -> k to CategoryPref(k, sortOrder = i) },
        )
        assertEquals(order, keys(cat.forKind(EntryKind.Expense)))
        // The other kind keeps its default order.
        assertEquals(listOf("salary", "bonus", "invest", "other_in"), keys(cat.forKind(EntryKind.Income)))
    }

    @Test fun unsavedItemsFollowTheSavedOnesInDefaultOrder() {
        val cat = CategoryCatalog(
            customs = listOf(pet, car),
            prefs = mapOf(
                "study" to CategoryPref("study", sortOrder = 0),
                "c2" to CategoryPref("c2", sortOrder = 1),
                // A pref without a position (only a new name) counts as unsaved.
                "food" to CategoryPref("food", name = "吃饭"),
            ),
        )
        assertEquals(
            listOf("study", "c2") + listOf("food", "transit", "shop", "daily", "fun", "home", "med", "gift") + listOf("c1", "other"),
            keys(cat.forKind(EntryKind.Expense)),
        )
    }

    @Test fun otherOfFollowsItsRename() {
        val cat = CategoryCatalog(prefs = mapOf("other" to CategoryPref("other", name = "杂项", hue = Hue.Gift)))
        val other = cat.otherOf(EntryKind.Expense)
        assertEquals("other", other.key)
        assertEquals("杂项", other.label)
        assertEquals(Hue.Gift, other.hue)
        assertEquals("other_in", cat.otherOf(EntryKind.Income).key)
        assertEquals("其他", cat.otherOf(EntryKind.Income).label)
    }

    @Test fun ofFindsCustomsAndFallsBackToOther() {
        val cat = CategoryCatalog(
            customs = listOf(pet, car),
            prefs = mapOf("other" to CategoryPref("other", name = "杂项")),
        )
        assertSame(pet, cat.of("c1"))
        assertEquals(0xFF336699.toInt(), cat.of("c2").color)
        // A custom category deleted elsewhere, or an unknown key, lands in the (renamed) 其他.
        assertEquals("other", cat.of("c99").key)
        assertEquals("杂项", cat.of("c99").label)
        assertEquals("other", CategoryCatalog.Builtin.of("whatever").key)
        assertEquals(Category.Salary, CategoryCatalog.Builtin.of("salary"))
    }
}
