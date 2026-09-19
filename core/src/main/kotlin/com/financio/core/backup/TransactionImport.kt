package com.financio.core.backup

import com.financio.core.importer.Dedup
import com.financio.core.model.Money
import com.financio.core.model.ParsedTransaction
import com.financio.core.model.SourceFormat
import com.financio.core.model.Transaction
import java.time.LocalDate

/**
 * Restores transactions from a full back-up. Deduplication reuses the exact same [Dedup] hash
 * the normal file-import path uses (account + date + amount + counterparty iban + description),
 * so re-importing a back-up that overlaps with transactions already present (from a bank import,
 * or from importing the same back-up twice) is a no-op for the overlap, not a pile of duplicates.
 *
 * A split's category not resolving drops that one split rather than the whole transaction - it
 * comes back as a smaller split, or fully unsplit (falling back to [TransactionExport.categoryName])
 * if none of its splits resolve, same "drop just the broken part" approach as [RuleImport].
 */
object TransactionImport {

    data class ResolvedSplit(val categoryId: Long, val amountCents: Long)

    /** [splits] is empty when the transaction isn't split (or none of its exported splits resolved) - [transaction] carries [TransactionExport.categoryName] as its own categoryId in that case. */
    data class PlannedTransaction(val transaction: Transaction, val splits: List<ResolvedSplit>)

    data class Plan(val toInsert: List<PlannedTransaction>, val skippedUnresolvedAccount: Int, val skippedDuplicate: Int)

    fun plan(
        transactions: List<TransactionExport>,
        accountIdsByIban: Map<String, Long>,
        categoryIdsByName: Map<String, Long>,
        existingDedupHashesByAccount: Map<Long, Set<String>>,
    ): Plan {
        var unresolvedAccount = 0
        var duplicate = 0
        val toInsert = mutableListOf<PlannedTransaction>()
        val seenHashesByAccount = mutableMapOf<Long, MutableSet<String>>()

        for (export in transactions) {
            val accountId = accountIdsByIban[export.accountIban]
            if (accountId == null) {
                unresolvedAccount++
                continue
            }

            val date = LocalDate.parse(export.date)
            val amount = Money(export.amountCents)
            val parsed = ParsedTransaction(
                accountId = accountId,
                date = date,
                amount = amount,
                counterpartyIban = export.counterpartyIban,
                counterpartyName = export.counterpartyName,
                description = export.description,
                balanceAfter = export.balanceAfterCents?.let { Money(it) },
                sourceFormat = runCatching { SourceFormat.valueOf(export.sourceFormat) }.getOrDefault(SourceFormat.CSV),
                tag = export.tag,
            )
            val hash = Dedup.hashOf(parsed)
            val seenForAccount = seenHashesByAccount.getOrPut(accountId) { mutableSetOf() }
            if (existingDedupHashesByAccount[accountId]?.contains(hash) == true || !seenForAccount.add(hash)) {
                duplicate++
                continue
            }

            val resolvedSplits = export.splits.mapNotNull { split ->
                categoryIdsByName[split.categoryName]?.let { ResolvedSplit(it, split.amountCents) }
            }
            val categoryId = if (resolvedSplits.isNotEmpty()) null else export.categoryName?.let { categoryIdsByName[it] }

            val transaction = Transaction(
                accountId = accountId,
                date = date,
                amount = amount,
                counterpartyIban = export.counterpartyIban,
                counterpartyName = export.counterpartyName,
                description = export.description,
                categoryId = categoryId,
                sourceFormat = parsed.sourceFormat,
                dedupHash = hash,
                balanceAfter = parsed.balanceAfter,
                tag = export.tag,
                note = export.note,
            )
            toInsert += PlannedTransaction(transaction, resolvedSplits)
        }

        return Plan(toInsert = toInsert, skippedUnresolvedAccount = unresolvedAccount, skippedDuplicate = duplicate)
    }
}
