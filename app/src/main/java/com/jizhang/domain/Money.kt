package com.jizhang.domain

import kotlin.math.abs

/** "1,342.00" — amounts are always stored as cents to avoid floating point drift. */
fun formatCents(cents: Long, grouping: Boolean = true): String {
    val a = abs(cents)
    val whole = (a / 100).toString()
    val grouped = if (grouping) whole.reversed().chunked(3).joinToString(",").reversed() else whole
    val sign = if (cents < 0) "-" else ""
    return "$sign$grouped.${(a % 100).toString().padStart(2, '0')}"
}

/** "1,342" or "210.5" — drops trailing zero decimals for compact labels. */
fun formatCentsShort(cents: Long): String {
    val full = formatCents(cents)
    return if (full.endsWith(".00")) full.removeSuffix(".00") else full.trimEnd('0')
}

/** Signed amount for list rows: expenses read as "-32.00", income as "+5,000.00". */
fun formatSigned(cents: Long, kind: EntryKind): String =
    (if (kind == EntryKind.Expense) "-" else "+") + formatCents(cents)
