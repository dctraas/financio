package com.financio.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.app.DefaultAccount
import com.financio.core.model.Category
import com.financio.core.model.Transaction
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class TransactionsUiState(
    val transactions: List<Transaction> = emptyList(),
    val categoriesById: Map<Long, Category> = emptyMap(),
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    val uiState: StateFlow<TransactionsUiState> = combine(
        transactionRepository.observeTransactions(DefaultAccount.ID),
        categoryRepository.observeCategories(),
    ) { transactions, categories ->
        TransactionsUiState(transactions = transactions, categoriesById = categories.associateBy { it.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())
}
