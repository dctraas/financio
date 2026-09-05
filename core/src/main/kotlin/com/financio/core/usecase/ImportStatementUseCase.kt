package com.financio.core.usecase

import com.financio.core.categorize.RuleMatcher
import com.financio.core.importer.BankStatementParser
import com.financio.core.importer.CsvIngParser
import com.financio.core.importer.Dedup
import com.financio.core.importer.FormatDetector
import com.financio.core.importer.Mt940Parser
import com.financio.core.model.ParsedTransaction
import com.financio.core.model.Transaction
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.TransactionRepository
import kotlinx.coroutines.flow.first

/** What the import screen shows before the user confirms — nothing is persisted yet. */
data class ImportPreview(
    val ready: List<Transaction>,
    val needsCategory: List<Transaction>,
    val duplicateCount: Int,
) {
    val total: Int get() = ready.size + needsCategory.size
}

/**
 * Orchestrates the whole pipeline from the import diagram: detect format, parse, drop
 * duplicates already in the database, apply categorization rules. Nothing here is
 * Android-specific — accepts already-decoded file text so the app module's SAF file picker
 * stays a thin adapter around this.
 */
class ImportStatementUseCase(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val parsers: List<BankStatementParser> = listOf(CsvIngParser(), Mt940Parser()),
) {
    suspend fun preview(fileContent: String, accountId: Long): ImportPreview {
        val format = FormatDetector.detect(fileContent)
        val parser = parsers.first { it.format == format }
        val parsed = parser.parse(fileContent, accountId)

        val existingHashes = transactionRepository.existingDedupHashes(accountId)
        val deduped = parsed.filterNot { existingHashes.contains(Dedup.hashOf(it)) }
        val duplicateCount = parsed.size - deduped.size

        val rules = categoryRepository.observeRules().first()
        val matcher = RuleMatcher(rules)

        val candidates = deduped.map { it.toTransaction(matcher) }
        val (ready, needsCategory) = candidates.partition { it.categoryId != null }

        return ImportPreview(ready = ready, needsCategory = needsCategory, duplicateCount = duplicateCount)
    }

    /** Persists a previously-shown preview once the user confirms (and fills in any manual categories). */
    suspend fun confirm(transactions: List<Transaction>) {
        transactionRepository.insertAll(transactions)
    }

    private fun ParsedTransaction.toTransaction(matcher: RuleMatcher): Transaction = Transaction(
        accountId = accountId,
        date = date,
        amount = amount,
        counterpartyIban = counterpartyIban,
        counterpartyName = counterpartyName,
        description = description,
        categoryId = matcher.categorize(this),
        sourceFormat = sourceFormat,
        dedupHash = Dedup.hashOf(this),
    )
}
