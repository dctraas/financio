package com.financio.app.data.local

import com.financio.core.model.Budget
import com.financio.core.model.Category
import com.financio.core.model.CategoryRule
import com.financio.core.model.MatchType
import com.financio.core.model.Money
import com.financio.core.model.SourceFormat
import com.financio.core.model.Transaction
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
