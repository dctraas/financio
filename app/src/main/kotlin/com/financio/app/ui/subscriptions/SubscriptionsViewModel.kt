package com.financio.app.ui.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.core.model.Money
import com.financio.core.repository.TransactionRepository
import com.financio.core.usecase.DetectedSubscription
import com.financio.core.usecase.SubscriptionDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SubscriptionsUiState(
    val subscriptions: List<DetectedSubscription> = emptyList(),
    val monthlyTotal: Money = Money.ZERO,
    /** True once the underlying transaction list has loaded at all, even if nothing was detected. */
    val loaded: Boolean = false,
)

/**
 * All of this screen's actual detection logic lives in [SubscriptionDetector] (`:core`, fully
 * unit-tested) — this ViewModel is just wiring it to the live transaction list. Uses
 * [TransactionRepository.observeAllTransactions] rather than one account's, so this already
 * covers every account once multiple accounts reach the UI.
 */
@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
) : ViewModel() {

    val uiState: StateFlow<SubscriptionsUiState> = transactionRepository.observeAllTransactions()
        .map { transactions ->
            val detected = SubscriptionDetector.detect(transactions)
            SubscriptionsUiState(
                subscriptions = detected,
                monthlyTotal = Money(detected.sumOf { kotlin.math.abs(it.averageAmount.cents) }),
                loaded = true,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionsUiState())
}
