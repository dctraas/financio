package com.financio.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.core.backup.AccountImport
import com.financio.core.backup.BackupSerializer
import com.financio.core.backup.BudgetImport
import com.financio.core.backup.CategoryImport
import com.financio.core.backup.RuleImport
import com.financio.core.backup.SavingsGoalImport
import com.financio.core.backup.TransactionImport
import com.financio.core.model.Account
import com.financio.core.model.Budget
import com.financio.core.model.Category
import com.financio.core.model.CategoryRule
import com.financio.core.model.Money
import com.financio.core.model.SavingsGoal
import com.financio.core.model.Transaction
import com.financio.core.model.TransactionSplit
import com.financio.core.repository.AccountRepository
import com.financio.core.repository.BudgetRepository
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.SavingsGoalRepository
import com.financio.core.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class BackupExportUiState(
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val rules: List<CategoryRule> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val splitsByTransactionId: Map<Long, List<TransactionSplit>> = emptyMap(),
    val budgets: List<Budget> = emptyList(),
    val savingsGoals: List<SavingsGoal> = emptyList(),
)

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
        val transactionsAdded: Int,
        val transactionsSkipped: Int,
    ) : ImportResult
    data class Failed(val message: String) : ImportResult
}

/**
 * Backs the "Back-up & export" section nested inside Meer. Covers the full "volledige back-up"
 * (accounts, categories, rules, transactions incl. categorization/splits/tag/note, budgets,
 * spaardoelen) plus the narrower categories/regels-only exports and the transactions CSV export
 * that existed before.
 */
@HiltViewModel
class BackupExportViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository,
    private val savingsGoalRepository: SavingsGoalRepository,
) : ViewModel() {

    val uiState: StateFlow<BackupExportUiState> = combine(
        accountRepository.observeAccounts(),
        categoryRepository.observeCategories(),
        categoryRepository.observeRules(),
        transactionRepository.observeAllTransactions(),
        transactionRepository.observeAllSplits(),
    ) { accounts, categories, rules, transactions, splits ->
        BackupExportUiState(
            accounts = accounts,
            categories = categories,
            rules = rules,
            transactions = transactions,
            splitsByTransactionId = splits,
        )
    }.combine(budgetRepository.observeAllBudgets()) { state, budgets -> state.copy(budgets = budgets) }
        .combine(savingsGoalRepository.observeGoals()) { state, goals -> state.copy(savingsGoals = goals) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupExportUiState())

    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult: StateFlow<ImportResult?> = _importResult.asStateFlow()

    /**
     * Additive-only across every entity type, same promise the screen's own copy makes: anything
     * that already exists locally (by iban, name, categoryName+maand, or dedup hash) is left
     * untouched, only genuinely new rows get added. Accounts and categories are restored first so
     * later steps (transactions, budgets, spaardoelen) can resolve their names/ibans against ids
     * that include whatever this same import just created.
     */
    fun importBackup(content: String) {
        viewModelScope.launch {
            _importResult.value = runCatching {
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
                    transactionsAdded = transactionPlan.toInsert.size,
                    transactionsSkipped = transactionPlan.skippedUnresolvedAccount + transactionPlan.skippedDuplicate,
                )
            }.getOrElse { e -> ImportResult.Failed(e.message ?: "Kon het bestand niet lezen.") }
        }
    }

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

    fun clearImportResult() {
        _importResult.value = null
    }
}
