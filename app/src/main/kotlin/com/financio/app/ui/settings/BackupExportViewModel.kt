package com.financio.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.app.backup.BackupRestoreUseCase
import com.financio.app.backup.ImportResult
import com.financio.core.model.Account
import com.financio.core.model.Budget
import com.financio.core.model.Category
import com.financio.core.model.CategoryRule
import com.financio.core.model.Debt
import com.financio.core.model.SavingsGoal
import com.financio.core.model.Transaction
import com.financio.core.model.TransactionSplit
import com.financio.core.repository.AccountRepository
import com.financio.core.repository.BudgetRepository
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.DebtRepository
import com.financio.core.repository.SavingsGoalRepository
import com.financio.core.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    val debts: List<Debt> = emptyList(),
)

/**
 * Backs the "Back-up & export" section nested inside Meer. Covers the full "volledige back-up"
 * (accounts, categories, rules, transactions incl. categorization/splits/tag/note, budgets,
 * spaardoelen, schulden) plus the narrower categories/regels-only exports and the transactions
 * CSV export that existed before. The actual import logic lives in [BackupRestoreUseCase], shared
 * with [com.financio.app.backup.AutoBackupManager]'s automatic restore.
 */
@HiltViewModel
class BackupExportViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository,
    private val savingsGoalRepository: SavingsGoalRepository,
    private val debtRepository: DebtRepository,
    private val backupRestoreUseCase: BackupRestoreUseCase,
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
        .combine(debtRepository.observeDebts()) { state, debts -> state.copy(debts = debts) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupExportUiState())

    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult: StateFlow<ImportResult?> = _importResult.asStateFlow()

    fun importBackup(content: String) {
        viewModelScope.launch { _importResult.value = backupRestoreUseCase.restore(content) }
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
