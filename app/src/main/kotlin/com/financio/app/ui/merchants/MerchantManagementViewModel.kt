package com.financio.app.ui.merchants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.app.data.local.AppPreferences
import com.financio.core.repository.TransactionRepository
import com.financio.core.usecase.MerchantGrouper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** One merchant, confirmed or still just suggested — see [MerchantManagementUiState]. */
data class MerchantGroup(val canonicalName: String, val memberNames: List<String>)

data class MerchantManagementUiState(
    val loaded: Boolean = false,
    /** Ondernemingen the user already confirmed or created themselves, alphabetical. */
    val confirmedGroups: List<MerchantGroup> = emptyList(),
    /** [MerchantGrouper]'s own automatic reading of every counterparty name ever seen, minus whatever's already confirmed or dismissed - the same "Dit lijkt dezelfde onderneming" suggestions Inzicht surfaces contextually, all in one place here. */
    val suggestedGroups: List<MerchantGroup> = emptyList(),
    /** Every counterparty name not currently part of a confirmed onderneming - the picker list for "naam toevoegen" and "nieuwe onderneming". */
    val unassignedNames: List<String> = emptyList(),
)

/**
 * The full picture behind Inzicht's inline "Dit lijkt dezelfde onderneming" prompts: every
 * confirmed onderneming with its members (add/remove individually), every automatic suggestion
 * not yet answered, and the ability to build a brand-new onderneming from scratch out of any
 * counterparty names that aren't part of one yet. All of it reads and writes the same
 * AppPreferences aliases ChartsViewModel already uses, so a change here is immediately reflected
 * in every category's "Waar komt dit vandaan?".
 */
@HiltViewModel
class MerchantManagementViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    val uiState: StateFlow<MerchantManagementUiState> = combine(
        transactionRepository.observeAllTransactions(),
        appPreferences.confirmedMerchantAliases,
        appPreferences.dismissedMerchantGroups,
    ) { transactions, aliases, dismissedGroups ->
        buildState(transactions.map { it.counterpartyName }.distinct(), aliases, dismissedGroups)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MerchantManagementUiState())

    private fun buildState(allNames: List<String>, aliases: Map<String, String>, dismissedGroups: Set<String>): MerchantManagementUiState {
        val confirmedGroups = aliases.values.toSet().sorted().map { canonical ->
            MerchantGroup(canonical, aliases.filterValues { it == canonical }.keys.sorted())
        }
        val suggestedGroups = MerchantGrouper.candidateGroups(allNames)
            .filter { candidate ->
                candidate.canonicalName !in dismissedGroups &&
                    candidate.rawNames.any { aliases[it] != candidate.canonicalName }
            }
            .map { MerchantGroup(it.canonicalName, it.rawNames) }
        val unassignedNames = allNames.filter { it !in aliases.keys }.sorted()
        return MerchantManagementUiState(
            loaded = true,
            confirmedGroups = confirmedGroups,
            suggestedGroups = suggestedGroups,
            unassignedNames = unassignedNames,
        )
    }

    /** "Ja, dit is dezelfde onderneming" on a suggestion found here, not from one specific category's breakdown. */
    fun confirmSuggestion(group: MerchantGroup) {
        appPreferences.confirmMerchantGroup(group.canonicalName, group.memberNames)
    }

    fun dismissSuggestion(group: MerchantGroup) {
        appPreferences.dismissMerchantGroup(group.canonicalName)
    }

    /** Folds one more counterparty name into an already-confirmed onderneming. */
    fun addNameToGroup(canonicalName: String, rawName: String) {
        appPreferences.confirmMerchantGroup(canonicalName, listOf(rawName))
    }

    /** Removes just this one name from whichever onderneming it's currently under - the other members stay grouped. */
    fun removeNameFromGroup(rawName: String) {
        appPreferences.removeMerchantAlias(rawName)
    }

    /** Builds a brand-new onderneming by hand out of [rawNames] the automatic grouping never suggested together. */
    fun createGroup(canonicalName: String, rawNames: List<String>) {
        val trimmed = canonicalName.trim()
        if (trimmed.isBlank() || rawNames.isEmpty()) return
        appPreferences.confirmMerchantGroup(trimmed, rawNames)
    }
}
