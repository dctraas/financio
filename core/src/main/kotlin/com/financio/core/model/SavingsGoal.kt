package com.financio.core.model

/**
 * A savings target tied to one category — progress is
 * [com.financio.core.repository.TransactionRepository.observeCategoryNetAllTime] for that
 * category: categorizing a transfer to your savings account under this goal's
 * category counts as a contribution, and a later withdrawal categorized the same way reduces it
 * back down. No separate contribution-tracking mechanism — this reuses categorization entirely.
 */
data class SavingsGoal(
    val id: Long = 0,
    val name: String,
    val targetAmount: Money,
    val categoryId: Long,
)
