package com.financio.app.data.local

import com.financio.core.model.Account
import com.financio.core.model.Budget
import com.financio.core.model.Category
import com.financio.core.model.CategoryRule
import com.financio.core.model.MatchType
import com.financio.core.model.Money
import com.financio.core.model.SavingsGoal
import com.financio.core.model.SourceFormat
import com.financio.core.model.Transaction
import com.financio.core.model.TransactionSplit
import java.time.LocalDate
import java.time.YearMonth

fun TransactionEntity.toDomain() = Transaction(
    id = id,
    accountId = accountId,
    date = LocalDate.parse(date),
    amount = Money(amountCents),
    counterpartyIban = counterpartyIban,
    counterpartyName = counterpartyName,
    description = description,
    categoryId = categoryId,
    sourceFormat = SourceFormat.valueOf(sourceFormat),
    dedupHash = dedupHash,
    balanceAfter = balanceCents?.let { Money(it) },
    tag = tag,
)

fun Transaction.toEntity() = TransactionEntity(
    id = id,
    accountId = accountId,
    date = date.toString(),
    amountCents = amount.cents,
    counterpartyIban = counterpartyIban,
    counterpartyName = counterpartyName,
    description = description,
    categoryId = categoryId,
    sourceFormat = sourceFormat.name,
    dedupHash = dedupHash,
    balanceCents = balanceAfter?.cents,
    tag = tag,
)

fun AccountEntity.toDomain() = Account(id = id, name = name, ibanMasked = ibanMasked)

fun TransactionSplitEntity.toDomain() = TransactionSplit(
    id = id,
    transactionId = transactionId,
    categoryId = categoryId,
    amount = Money(amountCents),
)

fun TransactionSplit.toEntity() = TransactionSplitEntity(
    id = id,
    transactionId = transactionId,
    categoryId = categoryId,
    amountCents = amount.cents,
)

fun SavingsGoalEntity.toDomain() = SavingsGoal(
    id = id,
    name = name,
    targetAmount = Money(targetAmountCents),
    categoryId = categoryId,
)

fun CategoryEntity.toDomain() = Category(id = id, name = name, colorHex = colorHex, parentId = parentId)

fun CategoryRuleEntity.toDomain() = CategoryRule(
    id = id,
    categoryId = categoryId,
    matchType = MatchType.valueOf(matchType),
    pattern = pattern,
    priority = priority,
)

fun BudgetEntity.toDomain() = Budget(
    id = id,
    categoryId = categoryId,
    yearMonth = YearMonth.parse(yearMonth),
    limit = Money(limitCents),
    rollover = rollover,
)

fun Budget.toEntity() = BudgetEntity(
    id = id,
    categoryId = categoryId,
    yearMonth = yearMonth.toString(),
    limitCents = limit.cents,
    rollover = rollover,
)
