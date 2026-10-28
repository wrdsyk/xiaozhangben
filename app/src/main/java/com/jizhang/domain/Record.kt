package com.jizhang.domain

import java.time.LocalDate
import java.time.LocalDateTime

data class Record(
    val id: Long,
    val category: Category,
    val amountCents: Long,
    val note: String,
    val occurredAt: LocalDateTime,
    val demo: Boolean = false,
    /** Photo file names in [com.jizhang.data.ImageStore], in the order they were added. */
    val images: List<String> = emptyList(),
    /** The [RecurringRule] that wrote this record, if any. */
    val ruleId: Long? = null,
) {
    val kind: EntryKind get() = category.kind
    val date: LocalDate get() = occurredAt.toLocalDate()

    /** Row title: the note when there is one, otherwise the category name. */
    val title: String get() = note.ifBlank { category.label }
}
