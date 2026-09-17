package com.financio.app.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financio.core.categorize.ManualRule
import com.financio.core.categorize.RuleMatcher
import com.financio.core.model.Category
import com.financio.core.model.CategoryRule
import com.financio.core.model.MatchType
import com.financio.core.model.Money
import com.financio.core.model.Transaction
import com.financio.core.repository.BudgetRepository
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CategoryManagementTab { CATEGORIES, RULES }

/** How many transactions a rule matches, considering priority order (its real, current effect), plus whether it shadows a differently-categorized lower-priority rule. */
data class RuleRow(
    val rule: CategoryRule,
    val categoryName: String?,
    val actualMatchCount: Int,
    val conflict: RuleConflict?,
)

/** [otherRulePattern] is the shadowed rule's own pattern text, [overlapCount] how many transactions both rules' patterns actually match. */
data class RuleConflict(val otherRulePattern: String, val overlapCount: Int)

data class CategoryRow(val category: Category, val ruleCount: Int, val transactionCount: Int)

/** Live preview shown while composing a new rule, before it's saved - see [CategoryManagementViewModel.previewRule]. */
data class RulePreview(
    val matchCount: Int,
    val totalAmount: Money,
    val topCounterparties: List<Pair<String, Int>>,
    val currentlyUncategorizedCount: Int,
    val currentlyOtherCategoryCount: Int,
)

/** What deleting [category] would actually touch - shown before the user confirms. */
data class CategoryDeletePreview(val category: Category, val transactionCount: Int, val budgetCount: Int, val ruleCount: Int)

/** Enough state to reverse the one destructive bulk action that just happened - see [CategoryManagementViewModel.undo]. */
sealed interface UndoableAction {
    /** [changes] is transactionId -> its categoryId *before* the bulk rule application (null meaning it was uncategorized). */
    data class RulesApplied(val changes: List<Pair<Long, Long?>>) : UndoableAction

    data class CategoryDeleted(
        val name: String,
        val colorHex: String,
        val rules: List<CategoryRule>,
        val reassignedTransactionIds: List<Long>,
    ) : UndoableAction

    /** The swipe-to-delete gesture on a rule row commits immediately - this is its "Ongedaan maken". */
    data class RuleDeleted(val rule: CategoryRule) : UndoableAction
}

data class CategoryManagementUiState(
    val tab: CategoryManagementTab = CategoryManagementTab.CATEGORIES,
    val categories: List<Category> = emptyList(),
    val rules: List<CategoryRule> = emptyList(),
    val ruleRows: List<RuleRow> = emptyList(),
    val categoryRows: List<CategoryRow> = emptyList(),
    /** Every transaction, kept in state purely so the "Nieuwe regel" dialog can show a live match preview without a ViewModel round-trip per keystroke - see [CategoryManagementViewModel.previewRule]. */
    val transactions: List<Transaction> = emptyList(),
    /** Non-null while the "regels met terugwerkende kracht toepassen" confirmation is open - how many transactions would actually change, computed once on request rather than kept live. */
    val ruleApplicationPreview: Int? = null,
    /** Set right after applying, so the screen can show "X transacties bijgewerkt" once; cleared via [CategoryManagementViewModel.dismissRuleApplicationResult]. */
    val ruleApplicationResult: Int? = null,
    val categoryDeletePreview: CategoryDeletePreview? = null,
    /** The most recent reversible bulk action, if any - drives the "Ongedaan maken" snackbar. */
    val undoableAction: UndoableAction? = null,
)

/** A small rotation, not a picker: keeps "add a category" down to just typing a name. */
private val COLOR_ROTATION = listOf(
    "#5B7A52", "#4C6E77", "#8A4A3D", "#7A6A45", "#6B6485",
    "#4A5A8A", "#3D8A6E", "#9C7A3D", "#3D8FA3", "#A35D82",
)

@HiltViewModel
class CategoryManagementViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository,
) : ViewModel() {

    private val tab = MutableStateFlow(CategoryManagementTab.CATEGORIES)
    private val ruleApplicationPreview = MutableStateFlow<Int?>(null)
    private val ruleApplicationResult = MutableStateFlow<Int?>(null)
    private val categoryDeletePreview = MutableStateFlow<CategoryDeletePreview?>(null)
    private val undoableAction = MutableStateFlow<UndoableAction?>(null)

    val uiState: StateFlow<CategoryManagementUiState> = combine(
        tab,
        categoryRepository.observeCategories(),
        categoryRepository.observeRules(),
        transactionRepository.observeAllTransactions(),
        combine(ruleApplicationPreview, ruleApplicationResult, categoryDeletePreview, undoableAction) { a, b, c, d -> Quad(a, b, c, d) },
    ) { currentTab, categories, rules, transactions, extras ->
        buildState(currentTab, categories, rules, transactions, extras)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryManagementUiState())

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    private fun buildState(
        currentTab: CategoryManagementTab,
        categories: List<Category>,
        rules: List<CategoryRule>,
        transactions: List<Transaction>,
        extras: Quad<Int?, Int?, CategoryDeletePreview?, UndoableAction?>,
    ): CategoryManagementUiState {
        val categoriesById = categories.associateBy { it.id }
        val sortedRules = rules.sortedBy { it.priority }

        // Single pass over transactions × rules instead of two: one for RuleMatcher.matchingRule's
        // per-transaction winner (actualMatchCount) and a separate one for rawMatchesByRuleId's
        // per-rule .filter (conflict detection) - both needed the exact same matches() calls.
        val actualMatchCounts = mutableMapOf<Long, Int>()
        val rawMatchesByRuleId = sortedRules.associate { it.id to mutableSetOf<Long>() }
        for (transaction in transactions) {
            var winningRuleId: Long? = null
            for (rule in sortedRules) {
                if (!matchesRule(rule, transaction)) continue
                rawMatchesByRuleId.getValue(rule.id).add(transaction.id)
                if (winningRuleId == null) winningRuleId = rule.id
            }
            winningRuleId?.let { actualMatchCounts[it] = (actualMatchCounts[it] ?: 0) + 1 }
        }

        val ruleRows = sortedRules.map { rule ->
            val laterRules = sortedRules.filter { it.priority > rule.priority }
            val conflict = laterRules.firstNotNullOfOrNull { other ->
                if (other.categoryId == rule.categoryId) return@firstNotNullOfOrNull null
                val overlap = rawMatchesByRuleId.getValue(rule.id).intersect(rawMatchesByRuleId.getValue(other.id))
                if (overlap.isEmpty()) null else RuleConflict(other.pattern, overlap.size)
            }
            RuleRow(
                rule = rule,
                categoryName = categoriesById[rule.categoryId]?.name,
                actualMatchCount = actualMatchCounts[rule.id] ?: 0,
                conflict = conflict,
            )
        }

        val categoryRows = categories.map { category ->
            CategoryRow(
                category = category,
                ruleCount = rules.count { it.categoryId == category.id },
                transactionCount = transactions.count { it.categoryId == category.id },
            )
        }

        return CategoryManagementUiState(
            tab = currentTab,
            categories = categories,
            rules = rules,
            ruleRows = ruleRows,
            categoryRows = categoryRows,
            transactions = transactions,
            ruleApplicationPreview = extras.a,
            ruleApplicationResult = extras.b,
            categoryDeletePreview = extras.c,
            undoableAction = extras.d,
        )
    }

    fun selectTab(newTab: CategoryManagementTab) {
        tab.value = newTab
    }

    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val color = COLOR_ROTATION[uiState.value.categories.size % COLOR_ROTATION.size]
            categoryRepository.addCategory(trimmed, color)
        }
    }

    fun renameCategory(categoryId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch { categoryRepository.renameCategory(categoryId, trimmed) }
    }

    fun setCategoryColor(categoryId: Long, colorHex: String) {
        viewModelScope.launch { categoryRepository.setCategoryColor(categoryId, colorHex) }
    }

    /**
     * "Opslaan en toepassen": a new rule affects future imports on its own, but per the redesign
     * it should also immediately re-categorize existing transactions, not just the ones that
     * arrive after today. Reuses the exact same retroactive-apply logic as
     * [confirmRuleApplication] (scoped to every current rule, not just this new one, since a
     * higher-priority existing rule should still win where it already does) rather than
     * duplicating that logic for "just this rule".
     */
    fun addRuleAndApply(categoryId: Long, matchType: MatchType, pattern: String) {
        val trimmed = pattern.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            categoryRepository.addRule(ManualRule.from(categoryId, matchType, trimmed))
            val changes = pendingRuleChanges()
            changes.forEach { (transactionId, _, newCategoryId) -> transactionRepository.updateCategory(transactionId, newCategoryId) }
            if (changes.isNotEmpty()) {
                undoableAction.value = UndoableAction.RulesApplied(changes.map { it.first to it.second })
            }
        }
    }

    /** Deletes immediately (the swipe gesture itself is the confirmation) but keeps the rule around for [undo]. */
    fun deleteRule(ruleId: Long) {
        val rule = uiState.value.rules.firstOrNull { it.id == ruleId } ?: return
        viewModelScope.launch {
            categoryRepository.deleteRule(ruleId)
            undoableAction.value = UndoableAction.RuleDeleted(rule)
        }
    }

    /** The "regel bewerken" dialog's save action. */
    fun updateRule(ruleId: Long, categoryId: Long, matchType: MatchType, pattern: String) {
        val trimmed = pattern.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch { categoryRepository.updateRule(ruleId, categoryId, matchType, trimmed) }
    }

    /** Moves [ruleId] one spot up (-1) or down (+1) in priority order and persists the whole new order - the up/down alternative to dragging a row. */
    fun moveRule(ruleId: Long, offset: Int) {
        val ordered = uiState.value.rules.sortedBy { it.priority }.map { it.id }.toMutableList()
        val index = ordered.indexOf(ruleId)
        val target = index + offset
        if (index < 0 || target < 0 || target >= ordered.size) return
        ordered.add(target, ordered.removeAt(index))
        viewModelScope.launch { categoryRepository.reorderRules(ordered) }
    }

    /** A live "what would this rule do" preview for the new-rule dialog - a pure computation over already-loaded state, called straight from Compose on every keystroke rather than round-tripping through a suspend function. */
    fun previewRule(matchType: MatchType, pattern: String, categoryId: Long?): RulePreview? {
        val trimmed = pattern.trim()
        if (trimmed.isBlank()) return null
        val draft = CategoryRule(id = -1, categoryId = categoryId ?: -1, matchType = matchType, pattern = trimmed, priority = 0)
        val matches = uiState.value.transactions.filter { matchesRule(draft, it) }
        if (matches.isEmpty()) return RulePreview(0, Money.ZERO, emptyList(), 0, 0)
        val topCounterparties = matches.groupingBy { it.counterpartyName }.eachCount().entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key to it.value }
        return RulePreview(
            matchCount = matches.size,
            totalAmount = Money(matches.sumOf { kotlin.math.abs(it.amount.cents) }),
            topCounterparties = topCounterparties,
            currentlyUncategorizedCount = matches.count { it.categoryId == null },
            currentlyOtherCategoryCount = matches.count { it.categoryId != null && it.categoryId != categoryId },
        )
    }

    /** Opens the "regels met terugwerkende kracht toepassen" confirmation, with a real count of what it would change. */
    fun requestRuleApplicationPreview() {
        viewModelScope.launch {
            ruleApplicationPreview.value = pendingRuleChanges().size
        }
    }

    fun cancelRuleApplicationPreview() {
        ruleApplicationPreview.value = null
    }

    /** Applies every current rule to every non-split transaction, updating only the ones whose matched category actually differs from what they have now. */
    fun confirmRuleApplication() {
        viewModelScope.launch {
            val changes = pendingRuleChanges()
            changes.forEach { (transactionId, _, newCategoryId) -> transactionRepository.updateCategory(transactionId, newCategoryId) }
            ruleApplicationPreview.value = null
            ruleApplicationResult.value = changes.size
            if (changes.isNotEmpty()) {
                undoableAction.value = UndoableAction.RulesApplied(changes.map { it.first to it.second })
            }
        }
    }

    fun dismissRuleApplicationResult() {
        ruleApplicationResult.value = null
    }

    /**
     * (transactionId, previousCategoryId, matchedCategoryId) for every transaction that would
     * actually change: a currently-split transaction is skipped entirely (its own categoryId is
     * null by design, and blindly filling that in would silently discard the split), and a
     * transaction whose matched rule agrees with its current category is a no-op, not a "change".
     *
     * Fetches its own fresh rule list rather than reading `uiState.value.rules` - called right
     * after inserting a brand-new rule (see [addRuleAndApply]), and a cached StateFlow snapshot
     * isn't guaranteed to already reflect an insert that only just suspended-returned. A fresh
     * `Flow.first()` re-runs the underlying Room query immediately against the current database
     * state instead of waiting on that same invalidation signal.
     */
    private suspend fun pendingRuleChanges(): List<Triple<Long, Long?, Long>> {
        val matcher = RuleMatcher(categoryRepository.observeRules().first())
        val transactions = transactionRepository.observeAllTransactions().first()
        val splitIds = transactionRepository.observeSplitTransactionIds().first()
        return transactions.mapNotNull { t ->
            if (t.id in splitIds) return@mapNotNull null
            val matchedCategoryId = matcher.matchingRule(t)?.categoryId ?: return@mapNotNull null
            if (matchedCategoryId == t.categoryId) null else Triple(t.id, t.categoryId, matchedCategoryId)
        }
    }

    /** Opens the delete-category confirmation with real cascade counts, instead of deleting blind. */
    fun requestCategoryDelete(category: Category) {
        viewModelScope.launch {
            val transactionCount = uiState.value.transactions.count { it.categoryId == category.id }
            val budgetCount = budgetRepository.countBudgetsForCategory(category.id)
            val ruleCount = uiState.value.rules.count { it.categoryId == category.id }
            categoryDeletePreview.value = CategoryDeletePreview(category, transactionCount, budgetCount, ruleCount)
        }
    }

    fun cancelCategoryDelete() {
        categoryDeletePreview.value = null
    }

    /** [reassignTo] null means "niet categoriseren" (today's default: transactions.categoryId just goes back to null via the FK's ON DELETE SET NULL). */
    fun confirmCategoryDelete(reassignTo: Long?) {
        val preview = categoryDeletePreview.value ?: return
        viewModelScope.launch {
            val rulesToRestore = uiState.value.rules.filter { it.categoryId == preview.category.id }
            val affectedTransactionIds = uiState.value.transactions.filter { it.categoryId == preview.category.id }.map { it.id }
            if (reassignTo != null) {
                transactionRepository.reassignCategory(preview.category.id, reassignTo)
            }
            categoryRepository.deleteCategory(preview.category.id)
            categoryDeletePreview.value = null
            undoableAction.value = UndoableAction.CategoryDeleted(
                name = preview.category.name,
                colorHex = preview.category.colorHex,
                rules = rulesToRestore,
                // Only the ones NOT already redirected elsewhere need putting back - a reassigned
                // transaction already has its (correct) new category and undoing the delete
                // shouldn't also undo that reassignment.
                reassignedTransactionIds = if (reassignTo != null) emptyList() else affectedTransactionIds,
            )
        }
    }

    fun dismissUndoBanner() {
        undoableAction.value = null
    }

    fun undo(action: UndoableAction) {
        viewModelScope.launch {
            when (action) {
                is UndoableAction.RulesApplied -> action.changes.forEach { (transactionId, previousCategoryId) ->
                    if (previousCategoryId != null) {
                        transactionRepository.updateCategory(transactionId, previousCategoryId)
                    } else {
                        transactionRepository.clearCategory(transactionId)
                    }
                }
                is UndoableAction.CategoryDeleted -> {
                    val newCategoryId = categoryRepository.addCategory(action.name, action.colorHex)
                    action.rules.forEach { rule -> categoryRepository.addRule(rule.copy(id = 0, categoryId = newCategoryId)) }
                    action.reassignedTransactionIds.forEach { transactionId -> transactionRepository.updateCategory(transactionId, newCategoryId) }
                }
                is UndoableAction.RuleDeleted -> categoryRepository.addRule(action.rule.copy(id = 0))
            }
            undoableAction.value = null
        }
    }

    private fun matchesRule(rule: CategoryRule, transaction: Transaction): Boolean = when (rule.matchType) {
        MatchType.COUNTERPARTY_EXACT -> transaction.counterpartyIban?.equals(rule.pattern, ignoreCase = true) == true
        MatchType.KEYWORD -> "${transaction.counterpartyName} ${transaction.description}".contains(rule.pattern, ignoreCase = true)
    }
}
