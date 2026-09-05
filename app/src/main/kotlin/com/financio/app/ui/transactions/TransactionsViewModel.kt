package com.financio.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.app.DefaultAccount
import com.financio.core.categorize.LearnedRule
import com.financio.core.model.Category
import com.financio.core.model.Transaction
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionsUiState(
    val transactions: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val categoriesById: Map<Long, Category> = emptyMap(),
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    val uiState: StateFlow<TransactionsUiState> = combine(
        transactionRepository.observeTransactions(DefaultAccount.ID),
        categoryRepository.observeCategories(),
    ) { transactions, categories ->
        TransactionsUiState(
            transactions = transactions,
            categories = categories,
            categoriesById = categories.associateBy { it.id },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    /** Same "remember the choice as a rule" behavior as the import screen's manual categorization. */
    fun categorize(transaction: Transaction, categoryId: Long) {
        viewModelScope.launch {
            transactionRepository.updateCategory(transaction.id, categoryId)
            categoryRepository.addRule(LearnedRule.from(categoryId, transaction.counterpartyName))
        }
    }
}
