package com.financio.core.backup

import com.financio.core.model.Account
import com.financio.core.model.Budget
import com.financio.core.model.Category
import com.financio.core.model.CategoryRule
import com.financio.core.model.Debt
import com.financio.core.model.SavingsGoal
import com.financio.core.model.Transaction
import com.financio.core.model.TransactionSplit
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Turns the app's data into portable JSON and back. Kept in `:core` (framework-free, no Room) so
 * the format itself — and the decision of what's "the same" category, account or transaction
 * across two different installs — is unit-testable without an Android SDK, like everything else
 * risky here.
 */
object BackupSerializer {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true // forward-compatible: an older app version can still open a newer export
    }

    fun exportCategories(categories: List<Category>): String =
        encode(BackupBundle(categories = categories.map { it.toExport() }))

    fun exportRules(rules: List<CategoryRule>, categoriesById: Map<Long, Category>): String =
        encode(BackupBundle(rules = rules.toRuleExports(categoriesById)))

    /**
     * The "volledige back-up" - everything a fresh install needs to look the same again:
     * accounts, categories, rules, every transaction (with its categorization, splits, tag and
     * note), budgets, spaardoelen and schulden/leningen. [splitsByTransactionId] mirrors
     * [com.financio.core.repository.TransactionRepository.observeAllSplits]'s shape.
     */
    fun exportAll(
        accounts: List<Account>,
        categories: List<Category>,
        rules: List<CategoryRule>,
        transactions: List<Transaction>,
        splitsByTransactionId: Map<Long, List<TransactionSplit>>,
        budgets: List<Budget>,
        savingsGoals: List<SavingsGoal>,
        debts: List<Debt>,
    ): String {
        val categoriesById = categories.associateBy { it.id }
        val ibanByAccountId = accounts.associate { it.id to it.ibanMasked }
        return encode(
            BackupBundle(
                categories = categories.map { it.toExport() },
                rules = rules.toRuleExports(categoriesById),
                accounts = accounts.map { it.toExport() },
                transactions = transactions.toTransactionExports(ibanByAccountId, categoriesById, splitsByTransactionId),
                budgets = budgets.toBudgetExports(categoriesById),
                savingsGoals = savingsGoals.toSavingsGoalExports(categoriesById, ibanByAccountId),
                debts = debts.map { it.toExport() },
            ),
        )
    }

    /** @throws SerializationException on malformed or unrecognizable JSON — the caller turns that into a user-facing message. */
    fun parse(content: String): BackupBundle = json.decodeFromString(BackupBundle.serializer(), content)

    private fun encode(bundle: BackupBundle): String = json.encodeToString(BackupBundle.serializer(), bundle)

    private fun Category.toExport() = CategoryExport(name = name, colorHex = colorHex)

    private fun Debt.toExport() = DebtExport(
        name = name,
        direction = direction.name,
        counterpartyName = counterpartyName,
        principalCents = principal.cents,
        currentBalanceCents = currentBalance.cents,
        interestRateBasisPoints = interestRateBasisPoints,
        startDate = startDate.toString(),
        targetPayoffDate = targetPayoffDate?.toString(),
        notes = notes,
        archived = archived,
    )

    private fun Account.toExport() = AccountExport(
        name = name,
        ibanMasked = ibanMasked,
        importIdentifier = importIdentifier,
        hidden = hidden,
        excludedFromTotal = excludedFromTotal,
        manualBalanceCents = manualBalance?.cents,
    )

    /** Rules whose category was deleted between loading and exporting (id no longer in [categoriesById]) are silently dropped: they're orphaned either way. */
    private fun List<CategoryRule>.toRuleExports(categoriesById: Map<Long, Category>): List<RuleExport> =
        mapNotNull { rule ->
            categoriesById[rule.categoryId]?.let { category ->
                RuleExport(categoryName = category.name, matchType = rule.matchType.name, pattern = rule.pattern, priority = rule.priority)
            }
        }

    /** A transaction whose account was deleted between loading and exporting is dropped, same as an orphaned rule above - it can't be restored without an account to attach it to anyway. */
    private fun List<Transaction>.toTransactionExports(
        ibanByAccountId: Map<Long, String>,
        categoriesById: Map<Long, Category>,
        splitsByTransactionId: Map<Long, List<TransactionSplit>>,
    ): List<TransactionExport> = mapNotNull { transaction ->
        val iban = ibanByAccountId[transaction.accountId] ?: return@mapNotNull null
        TransactionExport(
            accountIban = iban,
            date = transaction.date.toString(),
            amountCents = transaction.amount.cents,
            counterpartyIban = transaction.counterpartyIban,
            counterpartyName = transaction.counterpartyName,
            description = transaction.description,
            categoryName = transaction.categoryId?.let { categoriesById[it]?.name },
            sourceFormat = transaction.sourceFormat.name,
            balanceAfterCents = transaction.balanceAfter?.cents,
            tag = transaction.tag,
            note = transaction.note,
            splits = splitsByTransactionId[transaction.id].orEmpty().mapNotNull { split ->
                categoriesById[split.categoryId]?.let { category -> TransactionSplitExport(category.name, split.amount.cents) }
            },
        )
    }

    /** A budget whose category was deleted is dropped, same reasoning as an orphaned rule. */
    private fun List<Budget>.toBudgetExports(categoriesById: Map<Long, Category>): List<BudgetExport> =
        mapNotNull { budget ->
            categoriesById[budget.categoryId]?.let { category ->
                BudgetExport(categoryName = category.name, yearMonth = budget.yearMonth.toString(), limitCents = budget.limit.cents, rollover = budget.rollover)
            }
        }

    /** A goal whose category was deleted is dropped, same reasoning as an orphaned rule - [SavingsGoal.linkedAccountId], unlike the category, is purely informational, so a deleted account just loses that display detail rather than the whole goal. */
    private fun List<SavingsGoal>.toSavingsGoalExports(categoriesById: Map<Long, Category>, ibanByAccountId: Map<Long, String>): List<SavingsGoalExport> =
        mapNotNull { goal ->
            categoriesById[goal.categoryId]?.let { category ->
                SavingsGoalExport(
                    name = goal.name,
                    targetAmountCents = goal.targetAmount.cents,
                    categoryName = category.name,
                    linkedAccountIban = goal.linkedAccountId?.let { ibanByAccountId[it] },
                    targetDate = goal.targetDate?.toString(),
                    archived = goal.archived,
                    manualAdjustmentCents = goal.manualAdjustment.cents,
                )
            }
        }
}
