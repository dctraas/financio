package com.financio.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.core.model.Account
import com.financio.core.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Multiple accounts, still entirely local: fase 1 assumed exactly one ING betaalrekening (see
 * [com.financio.app.DefaultAccount]); this lets a second (or third) account exist by importing
 * its own separate CSV/MT940 export into it — no bank connection, no aggregator, none of the
 * fase-3 architecture step this whole batch was scoped to avoid needing.
 */
@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    val accounts: StateFlow<List<Account>> = accountRepository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addAccount(name: String, ibanMasked: String) {
        viewModelScope.launch { accountRepository.addAccount(name, ibanMasked) }
    }
}
