package com.financio.core.repository

import com.financio.core.model.Account
import com.financio.core.model.Budget
import com.financio.core.model.Category
import com.financio.core.model.CategoryRule
import com.financio.core.model.Money
import com.financio.core.model.SavingsGoal
import com.financio.core.model.Transaction
import com.financio.core.model.TransactionSplit
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

/**
 * Pure interfaces — no Room, no Android import anywhere in this file. The `:app` module
 * provides the Room-backed implementation; this is the seam the architecture diagram draws
 * between the domain layer and the data layer, and where a future aggregator adapter would
 * plug in without either of the layers above ever changing.
 */
interface TransactionRepository {
    fun observeTransactions(accountId: Long): Flow<List<Transaction>>

    /** Every transaction across every account — categorization/budgets/charts are account-agnostic on purpose. */
    fun observeAllTransactions(): Flow<List<Transaction>>
    suspend fun existingDedupHashes(accountId: Long): Set<String>
    suspend fun insertAll(transactions: List<Transaction>)

    /**
     * The one "how much did this category cost this month" number — shared by Budget's progress
     * bars, Inzicht's charts, and the notification threshold checks, so the same category/month
     * can no longer show three different totals depending on which screen you're looking at (the
     * previous split between a debits-only `observeSpent` and an all-activity `observeCategoryTotal`
     * meant an income category always showed €0 on Budget-style totals, and a category with an
     * occasional refund overcounted on the debits-only one). Nets debits and credits together
     * first, then takes the magnitude — see the Room DAO's doc comment for the exact reasoning.
     * Splits are folded in alongside whole transactions categorized directly.
     */
    fun observeCategorySpent(categoryId: Long, yearMonth: YearMonth): Flow<Money>

    /** Net amount ever "spent" (debits minus credits) into a category, unscoped by month — a savings goal's progress. */
    fun observeCategoryNetAllTime(categoryId: Long): Flow<Money>

    /** Manual categorization of an already-persisted transaction — from the transaction list's "Te categoriseren" state. Also clears any existing splits on it. */
    suspend fun updateCategory(transactionId: Long, categoryId: Long)

    /** Back to "te categoriseren" - the undo path for a bulk rule application that had categorized a previously-uncategorized transaction. */
    suspend fun clearCategory(transactionId: Long)

    /** Applies [categoryId] to every transaction sharing [counterpartyName] on this account. Returns the number of rows changed. */
    suspend fun updateCategoryForCounterparty(accountId: Long, counterpartyName: String, categoryId: Long): Int

    fun observeSplits(transactionId: Long): Flow<List<TransactionSplit>>

    /** Replaces the transaction's splits entirely and nulls its own categoryId — the splits become authoritative. Pass an empty list to un-split it back to a single [categoryId]. */
    suspend fun setSplits(transactionId: Long, splits: List<TransactionSplit>, fallbackCategoryId: Long?)

    /** Ids of every transaction that currently has at least one split — lets the transaction list show "Gesplitst" instead of "Tik om te categoriseren" for one, without loading every row's splits individually. */
    fun observeSplitTransactionIds(): Flow<Set<Long>>

    /** Every stored split, keyed by transactionId — the redesigned transaction list shows a split row's actual parts inline ("Verzorging €14,95 · Vakantie €10,00"), which needs the amounts, not just [observeSplitTransactionIds]'s membership. */
    fun observeAllSplits(): Flow<Map<Long, List<TransactionSplit>>>

    /** The transaction detail screen's free-text note field. Pass null to clear it. */
    suspend fun setNote(transactionId: Long, note: String?)

    /** Every whole transaction currently in [oldCategoryId] moves to [newCategoryId] (or null) - the "kies waar deze transacties naartoe gaan" step before deleting a category. */
    suspend fun reassignCategory(oldCategoryId: Long, newCategoryId: Long?)
}

interface CategoryRepository {
    fun observeCategories(): Flow<List<Category>>
    fun observeRules(): Flow<List<CategoryRule>>
    suspend fun addRule(rule: CategoryRule)
    suspend fun addRules(rules: List<CategoryRule>)

    /** Returns the new category's id. */
    suspend fun addCategory(name: String, colorHex: String): Long

    suspend fun renameCategory(categoryId: Long, name: String)
    suspend fun setCategoryColor(categoryId: Long, colorHex: String)

    /** Deleting a category also deletes any rule pointing at it and un-categorizes its transactions - reassign them first (see [TransactionRepository.reassignCategory]) if they should go somewhere else instead. */
    suspend fun deleteCategory(categoryId: Long)
    suspend fun deleteRule(ruleId: Long)

    /** Rewrites every listed rule's priority to match its position in [orderedRuleIds] (1-indexed) - the "sleep om de volgorde te wijzigen" reorder action. */
    suspend fun reorderRules(orderedRuleIds: List<Long>)
}

interface BudgetRepository {
    fun observeBudgets(yearMonth: YearMonth): Flow<List<Budget>>
    suspend fun setLimit(categoryId: Long, yearMonth: YearMonth, limit: Money)
    suspend fun setRollover(categoryId: Long, yearMonth: YearMonth, rollover: Boolean)

    /** How many budget rows (any month) point at this category - the delete-category confirmation's cascade count. */
    suspend fun countBudgetsForCategory(categoryId: Long): Int
}

interface AccountRepository {
    fun observeAccounts(): Flow<List<Account>>

    /** Returns the new account's id. */
    suspend fun addAccount(name: String, ibanMasked: String): Long

    /** An IBAN is a bank's identifier, not a name someone picked - this is the "naam wijzigen" action on Rekeningen. */
    suspend fun renameAccount(accountId: Long, name: String)

    /** Hidden from the day-to-day accounts list without deleting its transaction history - e.g. a closed account. */
    suspend fun setAccountHidden(accountId: Long, hidden: Boolean)

    /** Left out of every cross-account total without hiding the account itself - e.g. a shared account where only part of the balance is really yours. */
    suspend fun setAccountExcludedFromTotal(accountId: Long, excluded: Boolean)

    /** Fills in (or clears, with null) the balance for an account whose imports carry no closing balance at all - see [com.financio.core.usecase.AccountBalanceResolver]. */
    suspend fun setManualBalance(accountId: Long, balance: Money?)
}

interface SavingsGoalRepository {
    fun observeGoals(): Flow<List<SavingsGoal>>

    /** Returns the new goal's id. [linkedAccountId] and [targetDate] are purely informational/display - see [SavingsGoal]. */
    suspend fun addGoal(name: String, targetAmount: Money, categoryId: Long, linkedAccountId: Long? = null, targetDate: LocalDate? = null): Long

    suspend fun deleteGoal(goalId: Long)

    /** "Archiveren" on a gehaald doel - moves it out of the active/gehaald sections without deleting its history. */
    suspend fun setArchived(goalId: Long, archived: Boolean)

    /** Adds [delta] (positive or negative) to the goal's manual top-up total - see [SavingsGoal.manualAdjustment]. */
    suspend fun addManualAdjustment(goalId: Long, delta: Money)
}
