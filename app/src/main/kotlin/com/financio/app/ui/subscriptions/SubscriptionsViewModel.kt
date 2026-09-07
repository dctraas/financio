package com.financio.app.ui.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.app.data.local.AppPreferences
import com.financio.core.model.Money
import com.financio.core.repository.TransactionRepository
import com.financio.core.usecase.DetectedSubscription
import com.financio.core.usecase.SubscriptionDetector
import com.financio.core.usecase.UncertainSubscription
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class SubscriptionsUiState(
    /** True once the underlying transaction list has loaded at all, even if nothing was detected. */
    val loaded: Boolean = false,
    /** Confirmed subscriptions whose next charge falls on or before the end of this month, chronological. */
    val dueThisMonth: List<DetectedSubscription> = emptyList(),
    val dueThisMonthTotal: Money = Money.ZERO,
    /** Confirmed subscriptions whose next charge is further out - a yearly one due in November, say - chronological, shown on their real billing month. */
    val upcomingLater: List<DetectedSubscription> = emptyList(),
    /** Plausible-but-unconfirmed merchants, excluding ones already answered via [SubscriptionsViewModel.confirm]/[SubscriptionsViewModel.dismiss]. */
    val uncertain: List<UncertainSubscription> = emptyList(),
    /** Twijfelgevallen the user said "ja" to - shown as their own simple list, since there's no estimated next date or cadence to show for something [SubscriptionDetector] itself never confirmed. */
    val manuallyConfirmed: List<UncertainSubscription> = emptyList(),
)

/**
 * All of this screen's actual detection logic lives in [SubscriptionDetector] (`:core`, fully
 * unit-tested) — this ViewModel is just wiring it to the live transaction list, splitting the
 * result into "due this month" vs "later" (R6), and layering the user's own twijfelgeval
 * yes/no answers ([AppPreferences]) over [SubscriptionDetector.detectUncertain]'s output. Uses
 * [TransactionRepository.observeAllTransactions] rather than one account's, so this already
 * covers every account once multiple accounts reach the UI.
 */
@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    val uiState: StateFlow<SubscriptionsUiState> = combine(
        transactionRepository.observeAllTransactions(),
        appPreferences.confirmedSubscriptionNames,
        appPreferences.dismissedSubscriptionNames,
    ) { transactions, confirmedNames, dismissedNames ->
        val today = LocalDate.now()
        val endOfMonth = today.withDayOfMonth(today.lengthOfMonth())

        val confirmed = SubscriptionDetector.detect(transactions).sortedBy { it.estimatedNextDate }
        val (due, later) = confirmed.partition { !it.estimatedNextDate.isAfter(endOfMonth) }

        val uncertainByName = SubscriptionDetector.detectUncertain(transactions).associateBy { it.counterpartyName }
        val uncertain = uncertainByName.values.filter { it.counterpartyName !in confirmedNames && it.counterpartyName !in dismissedNames }
        val manuallyConfirmed = confirmedNames.mapNotNull { uncertainByName[it] }

        SubscriptionsUiState(
            loaded = true,
            dueThisMonth = due,
            dueThisMonthTotal = Money(due.sumOf { kotlin.math.abs(it.averageAmount.cents) }),
            upcomingLater = later,
            uncertain = uncertain,
            manuallyConfirmed = manuallyConfirmed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionsUiState())

    fun confirm(counterpartyName: String) {
        appPreferences.confirmSubscription(counterpartyName)
    }

    fun dismiss(counterpartyName: String) {
        appPreferences.dismissSubscription(counterpartyName)
    }
}
