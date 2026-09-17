package com.financio.app.ui.importing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.app.DefaultAccount
import com.financio.app.notifications.BudgetThresholdNotifier
import com.financio.core.categorize.LearnedRule
import com.financio.core.importer.DetectedAccount
import com.financio.core.importer.UnrecognizedFormatException
import com.financio.core.model.Account
import com.financio.core.model.Category
import com.financio.core.model.Transaction
import com.financio.core.repository.AccountRepository
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.TransactionRepository
import com.financio.core.usecase.AccountDetectionResult
import com.financio.core.usecase.ImportPreview
import com.financio.core.usecase.ImportStatementUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One category decision made during import review — for the whole counterparty group ([keyword]
 * null), or scoped to just the transactions within it whose description contains [keyword] (see
 * [ImportViewModel.assignCategoryToKeyword]). A counterparty like the Belastingdienst can send
 * transactions for more than one purpose under one name (motorrijtuigenbelasting vs.
 * kinderopvangtoeslag), so a group's transactions aren't necessarily all-or-nothing.
 */
data class ManualCategoryChoice(
    val counterpartyName: String,
    val categoryId: Long,
    val keyword: String? = null,
    /** The categorize flow's "Onthoud X → Y" switch - off means this decision applies only to this group's own transactions, with no [com.financio.core.categorize.LearnedRule] learned from it (see [ImportViewModel.confirm]). */
    val learnRule: Boolean = true,
) {
    /** Mirrors [com.financio.core.model.MatchType.KEYWORD]'s own matching exactly, so a transaction resolved here behaves identically to how the rule [ImportViewModel.confirm] learns from it will match in the future. */
    fun matches(transaction: Transaction): Boolean =
        transaction.counterpartyName == counterpartyName &&
            (keyword == null || "${transaction.counterpartyName} ${transaction.description}".contains(keyword, ignoreCase = true))
}

/** One undoable step in the categorize flow (screen 03) — "Terug-tik of 'Ongedaan maken' herstelt de vorige groep". */
sealed interface CategorizeAction {
    data class Assigned(val choice: ManualCategoryChoice) : CategorizeAction
    data class Skipped(val counterpartyName: String) : CategorizeAction
}

sealed interface ImportUiState {
    data object PickFile : ImportUiState
    data object Loading : ImportUiState

    /**
     * [manualCategoryChoices] is every category decision made so far, in the order they were made
     * — a transaction resolves to the *first* choice that [ManualCategoryChoice.matches] it, so an
     * earlier keyword-scoped choice for part of a group still wins even after a later, broader
     * choice covers the rest of that same counterparty. [skippedGroups] tracks which groups the
     * user explicitly moved past without a choice, purely so the card stack knows which one to
     * show next - it changes nothing about what gets imported (see [ImportViewModel.confirm]:
     * every transaction in [preview] is imported regardless, skipped or not, chosen or not).
     */
    data class Ready(
        val preview: ImportPreview,
        val accountName: String,
        /** [com.financio.core.model.Account.ibanMasked] - blank for an account that has none set, same as everywhere else this field is shown. */
        val accountIban: String = "",
        val manualCategoryChoices: List<ManualCategoryChoice> = emptyList(),
        val skippedGroups: Set<String> = emptySet(),
        /** How many already-imported transactions use each category — ranks the top-4 chips by the user's own habits instead of category-creation order. */
        val categoryUsageFrequency: Map<Long, Int> = emptyMap(),
        /** In lockstep with [manualCategoryChoices]/[skippedGroups] - the categorize flow's one-step undo reads the last entry here to know exactly what to reverse. */
        val actionHistory: List<CategorizeAction> = emptyList(),
    ) : ImportUiState

    data class Failed(
        val message: String,
        val rawLines: List<String> = emptyList(),
        val detectedColumns: List<String> = emptyList(),
    ) : ImportUiState

    /**
     * The file's own account (see [com.financio.core.importer.DetectedAccount]) matched no
     * account the app already knows about. [suggestedName] prefills the new-account form when
     * the file provided one (a savings account's "Rekening naam"); otherwise a generic default.
     * [rawIdentifier] is what the file actually gave for the account - an IBAN for a checking
     * account or an MT940 export, or an internal ING code (e.g. "L866-14401") for a savings
     * account with no visible IBAN in its own export - so the new-account form can prefill it
     * under whichever field actually describes it, rather than always calling it an IBAN.
     * [existingAccounts] backs the escape hatch for a false positive — "dit is eigenlijk een
     * bestaande rekening" — for the one case the zero-transaction backfill heuristic can't cover
     * safely: re-importing into an already-used sole account before it's ever been learned.
     */
    data class AccountDetected(
        val suggestedName: String,
        val rawIdentifier: String,
        val existingAccounts: List<Account>,
    ) : ImportUiState

    data object Imported : ImportUiState
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importStatementUseCase: ImportStatementUseCase,
    private val categoryRepository: CategoryRepository,
    private val budgetThresholdNotifier: BudgetThresholdNotifier,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImportUiState>(ImportUiState.PickFile)
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    val categories: StateFlow<List<Category>> = categoryRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accounts: StateFlow<List<Account>> = accountRepository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Whether anything has ever been imported, on any account - decides whether [ImportUiState.PickFile]
     * shows the full "eerste keer" onboarding speech or just the terse "kies een bestand" a returning
     * user doing their Nth import actually wants. Defaults to true (the terse view) so a returning
     * user with real data never sees even a one-frame flash of onboarding copy while this loads.
     */
    val hasAnyTransactions: StateFlow<Boolean> = transactionRepository.observeAllTransactions()
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Which account the next import goes into — only ever surfaced in the UI once a second account exists. */
    private val _selectedAccountId = MutableStateFlow(DefaultAccount.ID)
    val selectedAccountId: StateFlow<Long> = _selectedAccountId.asStateFlow()

    // Kept so a "pick the date column yourself" retry after a Failed state doesn't need the file
    // picker to run again - the file's own bytes are still right here.
    private var pendingFileContent: String? = null
    private var pendingAccountId: Long? = null

    // Set while ImportUiState.AccountDetected is showing, so its follow-up actions
    // (confirmNewAccount/linkToExistingAccount) know which identifier to learn.
    private var pendingDetectedAccount: DetectedAccount? = null

    fun selectAccount(accountId: Long) {
        _selectedAccountId.value = accountId
    }

    fun onFilePicked(content: String) {
        pendingFileContent = content
        _uiState.value = ImportUiState.Loading
        viewModelScope.launch {
            val knownAccounts = accounts.value
            when (val detection = importStatementUseCase.detectAccount(content, knownAccounts)) {
                is AccountDetectionResult.Matched -> {
                    _selectedAccountId.value = detection.accountId
                    pendingAccountId = detection.accountId
                    load(content, detection.accountId, dateColumnOverrideIndex = null)
                }
                is AccountDetectionResult.Unknown -> handleUnknownAccount(content, detection.detected, knownAccounts)
                AccountDetectionResult.Undetectable -> {
                    pendingAccountId = _selectedAccountId.value
                    load(content, _selectedAccountId.value, dateColumnOverrideIndex = null)
                }
            }
        }
    }

    /**
     * A file identifies an account none of [knownAccounts] has learned yet. Silently attaching
     * the identifier is only safe when there's exactly one candidate this could plausibly be —
     * an unlearned account with zero transactions of its own — since a pre-existing account with
     * real history but no learned identifier (every account before this feature shipped) must
     * never have a different account's transactions silently attached to it; that's the bug this
     * whole feature exists to fix. Anything less certain surfaces the new-account screen instead.
     */
    private suspend fun handleUnknownAccount(content: String, detected: DetectedAccount, knownAccounts: List<Account>) {
        val unlearned = knownAccounts.filter { it.importIdentifier == null }
        val soleVirginCandidate = unlearned.singleOrNull()
            ?.takeIf { transactionRepository.existingDedupHashes(it.id).isEmpty() }
        if (soleVirginCandidate != null) {
            accountRepository.setImportIdentifier(soleVirginCandidate.id, detected.rawIdentifier)
            _selectedAccountId.value = soleVirginCandidate.id
            pendingAccountId = soleVirginCandidate.id
            load(content, soleVirginCandidate.id, dateColumnOverrideIndex = null)
        } else {
            pendingDetectedAccount = detected
            _uiState.value = ImportUiState.AccountDetected(
                suggestedName = detected.suggestedName ?: "Nieuwe rekening",
                rawIdentifier = detected.rawIdentifier,
                existingAccounts = knownAccounts,
            )
        }
    }

    /** "Rekening toevoegen" on the new-account screen — creates the account and continues the import straight into it. */
    fun confirmNewAccount(name: String, ibanMasked: String) {
        val content = pendingFileContent ?: return
        val detected = pendingDetectedAccount ?: return
        viewModelScope.launch {
            val accountId = accountRepository.addAccount(name, ibanMasked, importIdentifier = detected.rawIdentifier)
            pendingDetectedAccount = null
            _selectedAccountId.value = accountId
            pendingAccountId = accountId
            load(content, accountId, dateColumnOverrideIndex = null)
        }
    }

    /** The escape hatch — "dit is eigenlijk een bestaande rekening" — for a false-positive new-account detection. */
    fun linkToExistingAccount(accountId: Long) {
        val content = pendingFileContent ?: return
        val detected = pendingDetectedAccount ?: return
        viewModelScope.launch {
            accountRepository.setImportIdentifier(accountId, detected.rawIdentifier)
            pendingDetectedAccount = null
            _selectedAccountId.value = accountId
            pendingAccountId = accountId
            load(content, accountId, dateColumnOverrideIndex = null)
        }
    }

    /** Backs out of the new-account screen without importing anything. */
    fun cancelAccountDetection() {
        pendingDetectedAccount = null
        _uiState.value = ImportUiState.PickFile
    }

    /** The error screen's recovery action - "column N is actually the date column". */
    fun retryWithDateColumn(columnIndex: Int) {
        val content = pendingFileContent ?: return
        val accountId = pendingAccountId ?: return
        load(content, accountId, dateColumnOverrideIndex = columnIndex)
    }

    private fun load(content: String, accountId: Long, dateColumnOverrideIndex: Int?) {
        _uiState.value = ImportUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                val preview = importStatementUseCase.preview(content, accountId, dateColumnOverrideIndex)
                val account = accounts.value.firstOrNull { it.id == accountId }
                ImportUiState.Ready(
                    preview = preview,
                    accountName = account?.name ?: "Rekening",
                    accountIban = account?.ibanMasked ?: "",
                    categoryUsageFrequency = categoryUsageFrequency(),
                )
            } catch (e: UnrecognizedFormatException) {
                ImportUiState.Failed(e.message ?: "Kon het bestand niet lezen.", e.rawLines, e.detectedColumns)
            } catch (e: Exception) {
                ImportUiState.Failed(e.message ?: "Kon het bestand niet lezen.")
            }
        }
    }

    /** One tally across every already-imported transaction — a stand-in for "how often you've actually picked this category", since there's no separate usage-count column to read. */
    private suspend fun categoryUsageFrequency(): Map<Long, Int> =
        transactionRepository.observeAllTransactions().first()
            .mapNotNull { it.categoryId }
            .groupingBy { it }
            .eachCount()

    /** [counterpartyName] is a group key from `preview.needsCategoryGrouped`, applying to every transaction that shares it. [learnRule] is the categorize flow's "Onthoud X → Y" switch. */
    fun assignCategory(counterpartyName: String, categoryId: Long, learnRule: Boolean = true) {
        val current = _uiState.value
        if (current !is ImportUiState.Ready) return
        val choice = ManualCategoryChoice(counterpartyName, categoryId, learnRule = learnRule)
        _uiState.value = current.copy(
            manualCategoryChoices = current.manualCategoryChoices + choice,
            actionHistory = current.actionHistory + CategorizeAction.Assigned(choice),
        )
    }

    /**
     * "Splitsen op trefwoord" — scopes this choice to just the transactions in [counterpartyName]'s
     * group whose description contains [keyword], instead of the whole group, so the rest can
     * still get their own (different) category on a follow-up card instead of being forced into
     * this same one.
     */
    fun assignCategoryToKeyword(counterpartyName: String, keyword: String, categoryId: Long) {
        val current = _uiState.value
        if (current !is ImportUiState.Ready) return
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return
        val choice = ManualCategoryChoice(counterpartyName, categoryId, trimmed)
        _uiState.value = current.copy(
            manualCategoryChoices = current.manualCategoryChoices + choice,
            actionHistory = current.actionHistory + CategorizeAction.Assigned(choice),
        )
    }

    /** Moves the card stack past this group without assigning it a category - it still gets imported uncategorized, same as if the user never saw this screen at all. */
    fun skip(counterpartyName: String) {
        val current = _uiState.value
        if (current !is ImportUiState.Ready) return
        _uiState.value = current.copy(
            skippedGroups = current.skippedGroups + counterpartyName,
            actionHistory = current.actionHistory + CategorizeAction.Skipped(counterpartyName),
        )
    }

    /** The categorize flow's "Ongedaan maken" - reverses exactly the last [assignCategory]/[assignCategoryToKeyword]/[skip] call, whichever it was. */
    fun undoLast() {
        val current = _uiState.value
        if (current !is ImportUiState.Ready) return
        when (val lastAction = current.actionHistory.lastOrNull() ?: return) {
            is CategorizeAction.Assigned -> _uiState.value = current.copy(
                manualCategoryChoices = current.manualCategoryChoices.dropLast(1),
                actionHistory = current.actionHistory.dropLast(1),
            )
            is CategorizeAction.Skipped -> _uiState.value = current.copy(
                skippedGroups = current.skippedGroups - lastAction.counterpartyName,
                actionHistory = current.actionHistory.dropLast(1),
            )
        }
    }

    /**
     * Persists the auto-categorized transactions, the ones the user just assigned by hand (which
     * also become a remembered rule, per the architecture's "geen match → vraag het → onthoud
     * het" behavior — one rule per merchant, not per line), and — unlike dropping them — the rest
     * of `needsCategory` too, uncategorized, so nothing an import found silently disappears; they
     * show up as "Te categoriseren" in the transaction list and can be fixed there instead.
     */
    fun confirm() {
        val current = _uiState.value
        if (current !is ImportUiState.Ready) return
        viewModelScope.launch {
            val manuallyCategorized = current.preview.needsCategory.map { transaction ->
                current.manualCategoryChoices.firstOrNull { it.matches(transaction) }
                    ?.let { choice -> transaction.copy(categoryId = choice.categoryId) }
                    ?: transaction
            }
            val toImport = current.preview.ready + manuallyCategorized

            // Snapshotted before the import itself: an import is exactly the moment a budget is
            // most likely to newly cross a threshold, since it can add many transactions to a
            // category at once instead of the one-at-a-time changes Transacties makes.
            val affectedCategoryIds = toImport.mapNotNull { it.categoryId }.distinct()
            val previousSpentByCategory = affectedCategoryIds.associateWith { budgetThresholdNotifier.currentSpent(it) }

            importStatementUseCase.confirm(toImport)

            // A keyword-scoped choice learns a rule on just that keyword, not the bare
            // counterparty name — otherwise the rule would wrongly capture the counterparty's
            // other, differently-categorized transactions too (see ManualCategoryChoice). A
            // choice made with the categorize flow's "Onthoud X → Y" switch off learns nothing -
            // it resolved only this group's own transactions above.
            current.manualCategoryChoices.filter { it.learnRule }.forEach { choice ->
                categoryRepository.addRule(LearnedRule.from(choice.categoryId, choice.keyword ?: choice.counterpartyName))
            }

            affectedCategoryIds.forEach { categoryId ->
                budgetThresholdNotifier.checkAndNotify(categoryId, previousSpentByCategory.getValue(categoryId))
            }

            _uiState.value = ImportUiState.Imported
        }
    }
}
