package com.financio.core.model

import java.time.LocalDate

enum class SourceFormat { CSV, MT940 }

/**
 * A transaction as parsed straight out of a bank export, before it has an id, a category,
 * or has been checked against the database for duplicates.
 */
data class ParsedTransaction(
    val accountId: Long,
    val date: LocalDate,
    val amount: Money,
    val counterpartyIban: String?,
    val counterpartyName: String,
    val description: String,
    val balanceAfter: Money?,
    val sourceFormat: SourceFormat,
    /** ING's own CSV "Tag" column (set from within the ING app) — always null for MT940, which has no equivalent field. */
    val tag: String? = null,
) {
    /** Identity for dedup: same account, date, amount, counterparty and description = same transaction. */
    val dedupKey: String
        get() = listOf(accountId, date, amount.cents, counterpartyIban.orEmpty(), description)
            .joinToString("|")
}

/** A transaction once it has been persisted and (maybe) categorized. */
data class Transaction(
    val id: Long = 0,
    val accountId: Long,
    val date: LocalDate,
    val amount: Money,
    val counterpartyIban: String?,
    val counterpartyName: String,
    val description: String,
    val categoryId: Long?,
    val sourceFormat: SourceFormat,
    val dedupHash: String,
    /** Account balance right after this transaction, if the export included one — powers the saldoverloop chart. */
    val balanceAfter: Money? = null,
    val tag: String? = null,
)
