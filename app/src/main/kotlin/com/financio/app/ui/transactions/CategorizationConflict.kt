package com.financio.app.ui.transactions

import com.financio.core.model.Transaction

/**
 * Shown instead of silently learning a whole-counterparty rule whenever [transaction]'s
 * counterparty already has transactions in a *different* category ([existingCategoryName]) —
 * shared by [TransactionsViewModel] and [TransactionDetailViewModel], since both screens' manual
 * "kies een categorie" flow needs the exact same check.
 */
data class CategorizationConflict(
    val transaction: Transaction,
    val categoryId: Long,
    val existingCategoryName: String?,
    /**
     * Every transaction, snapshotted at the moment this conflict was detected — lets the dialog's
     * keyword field compute a live match count synchronously per keystroke instead of
     * round-tripping through a suspend fetch on every character typed.
     */
    val allTransactionsSnapshot: List<Transaction>,
)

/**
 * Set right after a categorize call actually persists — either no conflict existed, or the user
 * chose "voor alle transacties" on one. The screen turns this into its existing "ook toepassen op
 * de rest?" follow-up prompt, then clears it. Never set after a keyword-scoped resolution:
 * blindly bulk-applying to every same-counterparty transaction would defeat the whole point of
 * scoping the new rule down in the first place.
 */
data class AppliedCategorization(val transaction: Transaction, val categoryId: Long)
