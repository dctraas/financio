package com.financio.core.model

import java.time.LocalDate

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
    /**
     * The account this goal is shown as "volgend" (e.g. "Volgt Spaarrekening ...7766") - purely
     * informational and for the "stortingen dit jaar" count, not the progress calculation itself,
     * which still runs on [categoryId] as described above. A goal can be linked to an account it
     * makes sense to associate with even though the actual accounting stays category-based.
     */
    val linkedAccountId: Long? = null,
    /** Optional streefdatum - powers the pace tick and the "€X per maand tot [datum]" pace label. Null means no deadline, just a target amount. */
    val targetDate: LocalDate? = null,
    /** "Gehaald" goals move to their own section and stop counting toward the active total, without losing their history. */
    val archived: Boolean = false,
    /**
     * A manual correction on top of the category's real transaction history - the "+" exception
     * for a one-off top-up that didn't come through as its own categorized transaction (e.g. cash,
     * or a transfer this app never saw). Deliberately a single running total rather than its own
     * ledger: it only ever affects this goal's own progress number, never Transacties, Budget, or
     * any other screen's totals, which all stay purely transaction-derived.
     */
    val manualAdjustment: Money = Money.ZERO,
)
