package com.financio.core.usecase

import com.financio.core.categorize.RuleMatcher
import com.financio.core.importer.BankStatementParser
import com.financio.core.importer.CsvIngParser
import com.financio.core.importer.Dedup
import com.financio.core.importer.DetectedAccount
import com.financio.core.importer.FormatDetector
import com.financio.core.importer.Mt940Parser
import com.financio.core.model.Account
import com.financio.core.model.ParsedTransaction
import com.financio.core.model.Transaction
import com.financio.core.repository.CategoryRepository
import com.financio.core.repository.TransactionRepository
import kotlinx.coroutines.flow.first

/**
 * The file's own account, matched against the app's known accounts by [Account.importIdentifier].
 * [Undetectable] (the format/parser has no own-account identifier, or the file doesn't carry one)
 * is deliberately distinct from [Unknown] (an identifier was read but matches no known account):
 * the former means "can't tell, proceed as before", the latter "this looks like a new account".
 */
sealed interface AccountDetectionResult {
    data class Matched(val accountId: Long) : AccountDetectionResult
    data class Unknown(val detected: DetectedAccount) : AccountDetectionResult
    data object Undetectable : AccountDetectionResult
}

/** What the import screen shows before the user confirms — nothing is persisted yet. */
data class ImportPreview(
    val ready: List<Transaction>,
    val needsCategory: List<Transaction>,
    val duplicateCount: Int,
) {
    val total: Int get() = ready.size + needsCategory.size

    /** Everything the file contained, duplicates included — the "gevonden" summary tile's number, as distinct from [total] (what actually gets imported). */
    val foundInFile: Int get() = total + duplicateCount

    /** [needsCategory], grouped by merchant so the review screen asks once per merchant, not once per line. */
    val needsCategoryGrouped: List<UncategorizedGroup> get() = needsCategory.groupForReview()
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
    /**
     * Reads the file's own account, if the format exposes one, and checks it against
     * [knownAccounts] by [Account.importIdentifier]. Called before [preview] — an accountId has
     * to be decided first, and this is how the import screen decides whether to ask "is this a
     * new account?" instead of just using whichever account happens to be selected.
     */
    fun detectAccount(fileContent: String, knownAccounts: List<Account>): AccountDetectionResult {
        val format = runCatching { FormatDetector.detect(fileContent) }.getOrNull()
            ?: return AccountDetectionResult.Undetectable
        val parser = parsers.firstOrNull { it.format == format } ?: return AccountDetectionResult.Undetectable
        val detected = runCatching { parser.detectOwnAccount(fileContent) }.getOrNull()
            ?: return AccountDetectionResult.Undetectable
        val match = knownAccounts.firstOrNull { it.importIdentifier == detected.rawIdentifier }
        return if (match != null) AccountDetectionResult.Matched(match.id) else AccountDetectionResult.Unknown(detected)
    }

    suspend fun preview(fileContent: String, accountId: Long, dateColumnOverrideIndex: Int? = null): ImportPreview {
        val format = FormatDetector.detect(fileContent)
        val parser = parsers.first { it.format == format }
        val parsed = parser.parse(fileContent, accountId, dateColumnOverrideIndex)

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
        balanceAfter = balanceAfter,
        tag = tag,
    )
}
