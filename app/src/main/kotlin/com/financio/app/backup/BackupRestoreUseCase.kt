package com.financio.app.backup

import com.financio.core.backup.AccountImport
import com.financio.core.backup.BackupSerializer
import com.financio.core.backup.BudgetImport
import com.financio.core.backup.CategoryImport
import com.financio.core.backup.DebtImport
import com.financio.core.backup.RuleImport
import com.financio.core.backup.SavingsGoalImport
import com.financio.core.backup.TransactionImport
import com.financio.core.model.DebtDirection
import com.financio.core.model.Money
import com.financio.core.model.TransactionSplit
import com.financio.core.repository.AccountRepository
import com.financio.core.repository.BudgetRepository
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.DebtRepository
import com.financio.core.repository.SavingsGoalRepository
import com.financio.core.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

sealed interface ImportResult {
    data class Success(
        val accountsAdded: Int,
        val accountsSkipped: Int,
        val categoriesAdded: Int,
        val categoriesSkipped: Int,
        val rulesAdded: Int,
        val rulesSkipped: Int,
        val budgetsAdded: Int,
        val budgetsSkipped: Int,
        val goalsAdded: Int,
        val goalsSkipped: Int,
        val debtsAdded: Int,
        val debtsSkipped: Int,
        val transactionsAdded: Int,
        val transactionsSkipped: Int,
    ) : ImportResult
    data class Failed(val message: String) : ImportResult
}

/**
 * The additive-only restore logic behind both the manual "Bestand importeren" flow
 * ([com.financio.app.ui.settings.BackupExportViewModel]) and the automatic restore
 * [AutoBackupManager] runs right after a fresh install - kept as its own plain, Hilt-injectable
 * class (not a ViewModel) so a [androidx.work.CoroutineWorker] and app-startup code can call it
 * too, neither of which has a `viewModelScope` to hang a ViewModel off of.
 *
 * Additive-only across every entity type, same promise the screen's own copy makes: anything
 * that already exists locally (by iban, name, categoryName+maand, or dedup hash) is left
 * untouched, only genuinely new rows get added. Accounts and categories are restored first so
 * later steps (transactions, budgets, spaardoelen, schulden) can resolve their names/ibans
 * against ids that include whatever this same import just created.
 */
class BackupRestoreUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository,
    private val savingsGoalRepository: SavingsGoalRepository,
    private val debtRepository: DebtRepository,
) {
    suspend fun restore(content: String): ImportResult = runCatching {
        val bundle = BackupSerializer.parse(content)

        val accountPlan = AccountImport.plan(bundle.accounts, accountRepository.observeAccounts().first())
        accountPlan.toCreate.forEach { export ->
            val id = accountRepository.addAccount(export.name, export.ibanMasked, export.importIdentifier)
            if (export.hidden) accountRepository.setAccountHidden(id, true)
            if (export.excludedFromTotal) accountRepository.setAccountExcludedFromTotal(id, true)
            export.manualBalanceCents?.let { accountRepository.setManualBalance(id, Money(it)) }
        }
        val accountIdsByIban = accountRepository.observeAccounts().first().associate { it.ibanMasked to it.id }

        val categoryPlan = CategoryImport.plan(bundle.categories, categoryRepository.observeCategories().first())
        categoryPlan.toCreate.forEach { categoryRepository.addCategory(it.name, it.colorHex) }
        val categoryIdsByName = categoryRepository.observeCategories().first().associate { it.name to it.id }

        val rulePlan = RuleImport.plan(bundle.rules, categoryIdsByName, categoryRepository.observeRules().first())
        categoryRepository.addRules(rulePlan.toCreate)

        val budgetPlan = BudgetImport.plan(bundle.budgets, categoryIdsByName, budgetRepository.observeAllBudgets().first())
        budgetPlan.toCreate.forEach { resolved ->
            budgetRepository.setLimit(resolved.categoryId, resolved.yearMonth, Money(resolved.export.limitCents))
            if (resolved.export.rollover) budgetRepository.setRollover(resolved.categoryId, resolved.yearMonth, true)
        }

        val goalPlan = SavingsGoalImport.plan(bundle.savingsGoals, categoryIdsByName, savingsGoalRepository.observeGoals().first())
        goalPlan.toCreate.forEach { export ->
            val id = savingsGoalRepository.addGoal(
                name = export.name,
                targetAmount = Money(export.targetAmountCents),
                categoryId = categoryIdsByName.getValue(export.categoryName),
                linkedAccountId = export.linkedAccountIban?.let { accountIdsByIban[it] },
                targetDate = export.targetDate?.let(LocalDate::parse),
            )
            if (export.archived) savingsGoalRepository.setArchived(id, true)
            if (export.manualAdjustmentCents != 0L) savingsGoalRepository.addManualAdjustment(id, Money(export.manualAdjustmentCents))
        }

        val debtPlan = DebtImport.plan(bundle.debts, debtRepository.observeDebts().first())
        debtPlan.toCreate.forEach { export ->
            val direction = runCatching { DebtDirection.valueOf(export.direction) }.getOrNull() ?: return@forEach
            val id = debtRepository.addDebt(
                name = export.name,
                direction = direction,
                counterpartyName = export.counterpartyName,
                principal = Money(export.principalCents),
                interestRateBasisPoints = export.interestRateBasisPoints,
                startDate = LocalDate.parse(export.startDate),
                targetPayoffDate = export.targetPayoffDate?.let(LocalDate::parse),
                notes = export.notes,
            )
            // addDebt always starts a fresh debt at its own principal - bring the balance down to
            // what was already paid off in the exported install, if anything.
            val alreadyPaid = export.principalCents - export.currentBalanceCents
            if (alreadyPaid > 0) debtRepository.recordPayment(id, Money(alreadyPaid))
            if (export.archived) debtRepository.setArchived(id, true)
        }

        val existingHashesByAccount = accountIdsByIban.values.associateWith { transactionRepository.existingDedupHashes(it) }
        val transactionPlan = TransactionImport.plan(bundle.transactions, accountIdsByIban, categoryIdsByName, existingHashesByAccount)
        transactionRepository.insertAll(transactionPlan.toInsert.map { it.transaction })
        applySplits(transactionPlan.toInsert)

        ImportResult.Success(
            accountsAdded = accountPlan.toCreate.size,
            accountsSkipped = accountPlan.skippedExisting,
            categoriesAdded = categoryPlan.toCreate.size,
            categoriesSkipped = categoryPlan.skippedExisting,
            rulesAdded = rulePlan.toCreate.size,
            rulesSkipped = rulePlan.skippedUnresolvedCategory + rulePlan.skippedDuplicate,
            budgetsAdded = budgetPlan.toCreate.size,
            budgetsSkipped = budgetPlan.skippedUnresolvedCategory + budgetPlan.skippedExisting,
            goalsAdded = goalPlan.toCreate.size,
            goalsSkipped = goalPlan.skippedExisting + goalPlan.skippedUnresolvedCategory,
            debtsAdded = debtPlan.toCreate.size,
            debtsSkipped = debtPlan.skippedExisting,
            transactionsAdded = transactionPlan.toInsert.size,
            transactionsSkipped = transactionPlan.skippedUnresolvedAccount + transactionPlan.skippedDuplicate,
        )
    }.getOrElse { e -> ImportResult.Failed(e.message ?: "Kon het bestand niet lezen.") }

    /** Newly inserted rows only got their id from Room just now - re-fetch and match back up by dedup hash to apply the (few) splits a back-up transaction might carry. */
    private suspend fun applySplits(inserted: List<TransactionImport.PlannedTransaction>) {
        val withSplits = inserted.filter { it.splits.isNotEmpty() }
        if (withSplits.isEmpty()) return

        val idsByHash = transactionRepository.observeAllTransactions().first().associate { it.dedupHash to it.id }
        withSplits.forEach { planned ->
            val transactionId = idsByHash[planned.transaction.dedupHash] ?: return@forEach
            val splits = planned.splits.map { TransactionSplit(transactionId = transactionId, categoryId = it.categoryId, amount = Money(it.amountCents)) }
            transactionRepository.setSplits(transactionId, splits, fallbackCategoryId = null)
        }
    }
}
