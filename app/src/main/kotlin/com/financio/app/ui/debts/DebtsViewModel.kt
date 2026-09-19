package com.financio.app.ui.debts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.core.model.Debt
import com.financio.core.model.DebtDirection
import com.financio.core.model.Money
import com.financio.core.repository.DebtRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class DebtsUiState(
    val loaded: Boolean = false,
    val activeDebts: List<Debt> = emptyList(),
    /** Fully paid off (or overpaid), but not yet archived - its own "gehaald"-style section, same as [com.financio.app.ui.savings.SavingsGoalsUiState.achievedRows]. */
    val settledDebts: List<Debt> = emptyList(),
    val archivedDebts: List<Debt> = emptyList(),
) {
    val isEmpty: Boolean get() = activeDebts.isEmpty() && settledDebts.isEmpty() && archivedDebts.isEmpty()
}

/**
 * Schulden & leningen - the mirror image of [com.financio.app.ui.savings.SavingsGoalsViewModel],
 * except a debt's balance is never derived from transaction history: most of these counterparties
 * (a family loan, an informal IOU) aren't a bank account this app has an import feed for, so
 * [Debt.currentBalance] is the one number the user updates by hand, via [recordPayment].
 */
@HiltViewModel
class DebtsViewModel @Inject constructor(
    private val debtRepository: DebtRepository,
) : ViewModel() {

    val uiState: StateFlow<DebtsUiState> = debtRepository.observeDebts()
        .map { debts ->
            DebtsUiState(
                loaded = true,
                activeDebts = debts.filter { !it.archived && !it.settled },
                settledDebts = debts.filter { !it.archived && it.settled },
                archivedDebts = debts.filter { it.archived },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebtsUiState())

    fun addDebt(
        name: String,
        direction: DebtDirection,
        counterpartyName: String,
        principal: Money,
        interestRateBasisPoints: Int?,
        targetPayoffDate: LocalDate?,
        notes: String?,
    ) {
        viewModelScope.launch {
            debtRepository.addDebt(
                name = name,
                direction = direction,
                counterpartyName = counterpartyName,
                principal = principal,
                interestRateBasisPoints = interestRateBasisPoints,
                startDate = LocalDate.now(),
                targetPayoffDate = targetPayoffDate,
                notes = notes,
            )
        }
    }

    /** "Schuld bewerken" - tapping an existing debt, as opposed to [addDebt]'s "Nieuwe schuld". Never touches [Debt.currentBalance] or [Debt.archived] - see [recordPayment]/[archiveDebt]. */
    fun editDebt(
        debtId: Long,
        name: String,
        counterpartyName: String,
        principal: Money,
        interestRateBasisPoints: Int?,
        targetPayoffDate: LocalDate?,
        notes: String?,
    ) {
        viewModelScope.launch {
            debtRepository.updateDebt(debtId, name, counterpartyName, principal, interestRateBasisPoints, targetPayoffDate, notes)
        }
    }

    fun recordPayment(debtId: Long, amount: Money) {
        viewModelScope.launch { debtRepository.recordPayment(debtId, amount) }
    }

    fun archiveDebt(debtId: Long) {
        viewModelScope.launch { debtRepository.setArchived(debtId, true) }
    }

    fun deleteDebt(debtId: Long) {
        viewModelScope.launch { debtRepository.deleteDebt(debtId) }
    }
}
