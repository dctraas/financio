package com.financio.core.categorize

import com.financio.core.model.Transaction

/** One ranked guess for a not-yet-categorized counterparty - [matchingTransactionCount] is exactly the number of prior transactions backing this rank, shown to the user as "N× eerder" (or "N× vergelijkbaar" when [isSimilarityBased]). */
data class CategorySuggestion(val categoryId: Long, val matchingTransactionCount: Int, val isSimilarityBased: Boolean)

/**
 * Ranks categories for a counterparty no rule matches yet (the categorize-flow's "waar hoort dit
 * bij?" question) by textual similarity to every already-categorized transaction's own
 * counterparty name — so a never-seen branch like "Albert Heijn 2841" gets suggested
 * "Boodschappen" because "Albert Heijn 1354" was categorized that way before, not just whichever
 * category happens to be used most often across the whole account. Falls back to plain frequency
 * only when nothing shares a word with the new name at all (a genuinely new kind of counterparty,
 * where similarity has no signal to offer).
 */
object CategorySuggester {
    /** Every category with at least one supporting prior transaction, most-supported first. Empty when nothing has ever been categorized. */
    fun rank(categorizedTransactions: List<Transaction>, counterpartyName: String): List<CategorySuggestion> {
        val targetTokens = tokenize(counterpartyName)
        if (targetTokens.isNotEmpty()) {
            val similarByCategory = categorizedTransactions
                .filter { it.categoryId != null && tokenize(it.counterpartyName).any { token -> token in targetTokens } }
                .groupBy { it.categoryId!! }
            if (similarByCategory.isNotEmpty()) {
                return similarByCategory.entries
                    .sortedByDescending { it.value.size }
                    .map { (categoryId, matches) -> CategorySuggestion(categoryId, matches.size, isSimilarityBased = true) }
            }
        }
        return categorizedTransactions.mapNotNull { it.categoryId }.groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }
            .map { (categoryId, count) -> CategorySuggestion(categoryId, count, isSimilarityBased = false) }
    }

    /** Words of more than 2 characters - long enough to be a meaningful brand/place name rather than noise like "de", "bv", or a lone store-number digit. */
    private fun tokenize(name: String): Set<String> =
        name.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 }.toSet()
}
