package com.jizhang.data

import com.jizhang.domain.Category
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

/** Sample rows for debug builds, so the screens have something to show on first launch. */
object DemoData {
    private val expenses = listOf(
        Triple(Category.Food, listOf("早餐", "午饭 黄焖鸡", "晚饭", "咖啡", "奶茶", "外卖"), 1200L..6800L),
        Triple(Category.Transit, listOf("地铁", "打车回家", "公交", "加油"), 300L..4800L),
        Triple(Category.Shop, listOf("优衣库", "书包", "数据线"), 3000L..22000L),
        Triple(Category.Daily, listOf("盒马", "超市", "纸巾洗衣液"), 1800L..12000L),
        Triple(Category.Fun, listOf("电影", "游戏", "KTV"), 3500L..16000L),
        Triple(Category.Med, listOf("药店"), 2000L..9000L),
        Triple(Category.Study, listOf("教材", "网课"), 4000L..19000L),
    )

    fun build(today: LocalDate, now: Long): List<RecordEntity> {
        val random = Random(20261025)
        val rows = mutableListOf<RecordEntity>()
        fun add(day: LocalDate, cat: Category, cents: Long, note: String, time: LocalTime) {
            val at = day.atTime(time).toEpochMillis()
            rows += RecordEntity(category = cat.key, amountCents = cents, note = note, occurredAt = at, createdAt = now, demo = true)
        }
        val first = today.withDayOfMonth(1).minusMonths(2)
        var day = first
        while (!day.isAfter(today)) {
            val count = random.nextInt(1, 3)
            repeat(count) {
                val (cat, notes, range) = expenses[weightedIndex(random)]
                val cents = (random.nextLong(range.first, range.last) / 10) * 10
                val time = LocalTime.of(random.nextInt(7, 23), random.nextInt(0, 60))
                add(day, cat, cents, notes.random(random), time)
            }
            if (day.dayOfMonth == 1) add(day, Category.Home, 80000, "水电网费", LocalTime.of(9, 0))
            if (day.dayOfMonth == 10) add(day, Category.Salary, 1200000, "工资", LocalTime.of(10, 0))
            day = day.plusDays(1)
        }
        return rows
    }

    /** Food and transit dominate, like a real month. */
    private fun weightedIndex(random: Random): Int {
        val weights = intArrayOf(9, 5, 1, 3, 2, 1, 1)
        var r = random.nextInt(weights.sum())
        weights.forEachIndexed { i, w -> if (r < w) return i else r -= w }
        return 0
    }
}
