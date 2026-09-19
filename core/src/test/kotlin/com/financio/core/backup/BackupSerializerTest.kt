package com.financio.core.backup

import com.financio.core.model.Account
import com.financio.core.model.Budget
import com.financio.core.model.Category
import com.financio.core.model.CategoryRule
import com.financio.core.model.Debt
import com.financio.core.model.DebtDirection
import com.financio.core.model.MatchType
import com.financio.core.model.Money
import com.financio.core.model.SavingsGoal
import com.financio.core.model.SourceFormat
import com.financio.core.model.Transaction
import com.financio.core.model.TransactionSplit
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupSerializerTest {

    private val groceries = Category(id = 1, name = "Boodschappen", colorHex = "#5B7A52")
    private val transport = Category(id = 2, name = "Vervoer", colorHex = "#4C6E77")
    private val rule = CategoryRule(id = 10, categoryId = 1, matchType = MatchType.KEYWORD, pattern = "Albert Heijn", priority = 20)
    private val account = Account(id = 1, name = "ING Betaalrekening", ibanMasked = "NL••INGB••••••1234")

    @Test
    fun `round-trips a categories-only export`() {
        val json = BackupSerializer.exportCategories(listOf(groceries, transport))
        val bundle = BackupSerializer.parse(json)

        assertEquals(listOf(CategoryExport("Boodschappen", "#5B7A52"), CategoryExport("Vervoer", "#4C6E77")), bundle.categories)
        assertTrue(bundle.rules.isEmpty())
    }

    @Test
    fun `round-trips a rules-only export, resolving the category by name`() {
        val json = BackupSerializer.exportRules(listOf(rule), mapOf(1L to groceries))
        val bundle = BackupSerializer.parse(json)

        assertTrue(bundle.categories.isEmpty())
        assertEquals(listOf(RuleExport("Boodschappen", "KEYWORD", "Albert Heijn", 20)), bundle.rules)
    }

    @Test
    fun `round-trips a full back-up covering every entity type`() {
        val transaction = Transaction(
            id = 5,
            accountId = 1,
            date = LocalDate.of(2026, 9, 1),
            amount = Money(-2345),
            counterpartyIban = null,
            counterpartyName = "Albert Heijn",
            description = "Boodschappen",
            categoryId = 1,
            sourceFormat = SourceFormat.CSV,
            dedupHash = "hash",
            tag = "vast",
            note = "wekelijkse boodschappen",
        )
        val budget = Budget(categoryId = 1, yearMonth = YearMonth.of(2026, 9), limit = Money(30000), rollover = true)
        val goal = SavingsGoal(name = "Vakantie", targetAmount = Money(100_000), categoryId = 2, linkedAccountId = 1, targetDate = LocalDate.of(2027, 6, 1))
        val debt = Debt(
            name = "Lening verbouwing",
            direction = DebtDirection.I_OWE,
            counterpartyName = "Jan",
            principal = Money(500_000),
            currentBalance = Money(300_000),
            startDate = LocalDate.of(2025, 1, 1),
        )

        val json = BackupSerializer.exportAll(
            accounts = listOf(account),
            categories = listOf(groceries, transport),
            rules = listOf(rule),
            transactions = listOf(transaction),
            splitsByTransactionId = emptyMap(),
            budgets = listOf(budget),
            savingsGoals = listOf(goal),
            debts = listOf(debt),
        )
        val bundle = BackupSerializer.parse(json)

        assertEquals(listOf(AccountExport("ING Betaalrekening", "NL••INGB••••••1234")), bundle.accounts)
        assertEquals(2, bundle.categories.size)
        assertEquals(1, bundle.rules.size)

        val exportedTransaction = bundle.transactions.single()
        assertEquals("NL••INGB••••••1234", exportedTransaction.accountIban)
        assertEquals("Boodschappen", exportedTransaction.categoryName)
        assertEquals("vast", exportedTransaction.tag)
        assertEquals("wekelijkse boodschappen", exportedTransaction.note)
        assertTrue(exportedTransaction.splits.isEmpty())

        val exportedBudget = bundle.budgets.single()
        assertEquals("Boodschappen", exportedBudget.categoryName)
        assertEquals("2026-09", exportedBudget.yearMonth)
        assertTrue(exportedBudget.rollover)

        val exportedGoal = bundle.savingsGoals.single()
        assertEquals("Vakantie", exportedGoal.name)
        assertEquals("Vervoer", exportedGoal.categoryName)
        assertEquals("NL••INGB••••••1234", exportedGoal.linkedAccountIban)

        val exportedDebt = bundle.debts.single()
        assertEquals("Lening verbouwing", exportedDebt.name)
        assertEquals("I_OWE", exportedDebt.direction)
        assertEquals(500_000, exportedDebt.principalCents)
        assertEquals(300_000, exportedDebt.currentBalanceCents)
    }

    @Test
    fun `a split transaction exports its parts by category name, own category left null`() {
        val transaction = Transaction(
            id = 5,
            accountId = 1,
            date = LocalDate.of(2026, 9, 1),
            amount = Money(-2345),
            counterpartyIban = null,
            counterpartyName = "Bol.com",
            description = "Bestelling",
            categoryId = null,
            sourceFormat = SourceFormat.CSV,
            dedupHash = "hash",
        )
        val splits = listOf(
            TransactionSplit(transactionId = 5, categoryId = 1, amount = Money(-1500)),
            TransactionSplit(transactionId = 5, categoryId = 2, amount = Money(-845)),
        )

        val json = BackupSerializer.exportAll(
            accounts = listOf(account),
            categories = listOf(groceries, transport),
            rules = emptyList(),
            transactions = listOf(transaction),
            splitsByTransactionId = mapOf(5L to splits),
            budgets = emptyList(),
            savingsGoals = emptyList(),
            debts = emptyList(),
        )
        val bundle = BackupSerializer.parse(json)

        val exported = bundle.transactions.single()
        assertEquals(null, exported.categoryName)
        assertEquals(setOf("Boodschappen", "Vervoer"), exported.splits.map { it.categoryName }.toSet())
    }

    @Test
    fun `a rule pointing at a category not in the id map is dropped rather than exported broken`() {
        val json = BackupSerializer.exportRules(listOf(rule), categoriesById = emptyMap())
        val bundle = BackupSerializer.parse(json)
        assertTrue(bundle.rules.isEmpty())
    }

    @Test
    fun `fails loudly on malformed input instead of guessing`() {
        assertThrows(SerializationException::class.java) {
            BackupSerializer.parse("dit is geen json")
        }
    }

    @Test
    fun `ignores unknown fields for forward compatibility with a newer export format`() {
        val bundle = BackupSerializer.parse(
            """{"categories":[{"name":"Boodschappen","colorHex":"#5B7A52","futureField":"x"}],"rules":[]}""",
        )
        assertEquals("Boodschappen", bundle.categories.single().name)
    }
}
