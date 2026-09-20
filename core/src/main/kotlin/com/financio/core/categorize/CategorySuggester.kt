package com.financio.core.categorize

import com.financio.core.model.Transaction
import kotlin.math.ln

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
 *
 * A shared word only counts for as much as it's actually worth: "amsterdam" appearing in a dozen
 * unrelated counterparty names carries almost no information about which one a new "X Amsterdam"
 * belongs with, while "makro" appearing nowhere else is a near-certain match. [rank] weighs each
 * overlapping word by a simple idf ("inverse document frequency", the same well-known statistic
 * search engines use to downweight common words) over every distinct counterparty name already
 * seen, rather than treating every shared word as equally meaningful - a better heuristic, not a
 * machine-learning model: no training, no external data, just fewer false ties between a genuine
 * brand match and a coincidental shared city or legal-suffix word.
 */
object CategorySuggester {
    /** Every category with at least one supporting prior transaction, strongest match first. Empty when nothing has ever been categorized. */
    fun rank(categorizedTransactions: List<Transaction>, counterpartyName: String): List<CategorySuggestion> {
        val targetTokens = tokenize(counterpartyName)
        if (targetTokens.isNotEmpty()) {
            val categorized = categorizedTransactions.filter { it.categoryId != null }
            val tokenWeight = idfWeights(categorized.map { it.counterpartyName }.distinct(), targetTokens)

            val scoredMatches = categorized.mapNotNull { transaction ->
                val overlap = tokenize(transaction.counterpartyName).intersect(targetTokens)
                if (overlap.isEmpty()) return@mapNotNull null
                Triple(transaction.categoryId!!, overlap.sumOf { tokenWeight.getValue(it) }, transaction)
            }
            if (scoredMatches.isNotEmpty()) {
                return scoredMatches.groupBy({ it.first }, { it.second to it.third })
                    .entries
                    .sortedByDescending { (_, scored) -> scored.sumOf { it.first } }
                    .map { (categoryId, scored) -> CategorySuggestion(categoryId, scored.size, isSimilarityBased = true) }
            }
        }
        return categorizedTransactions.mapNotNull { it.categoryId }.groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }
            .map { (categoryId, count) -> CategorySuggestion(categoryId, count, isSimilarityBased = false) }
    }

    /**
     * `ln(totalNames / namesContainingToken) + 1` per [targetTokens] entry - the classic idf
     * formula, "+1" so a word that (im)plausibly appears in every single prior name still counts
     * for something rather than zeroing out entirely. A token absent from every prior name weighs
     * 0 - there's nothing to divide by, and it can never actually appear in an overlap anyway.
     */
    private fun idfWeights(distinctCounterpartyNames: List<String>, targetTokens: Set<String>): Map<String, Double> {
        val totalNames = distinctCounterpartyNames.size.coerceAtLeast(1)
        val nameTokenSets = distinctCounterpartyNames.map(::tokenize)
        return targetTokens.associateWith { token ->
            val documentFrequency = nameTokenSets.count { token in it }
            if (documentFrequency == 0) 0.0 else ln(totalNames.toDouble() / documentFrequency) + 1.0
        }
    }

    /** Words of more than 2 characters - long enough to be a meaningful brand/place name rather than noise like "de", "bv", or a lone store-number digit. */
    private fun tokenize(name: String): Set<String> =
        name.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 }.toSet()
}
