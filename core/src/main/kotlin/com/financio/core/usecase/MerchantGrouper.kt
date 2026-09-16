package com.financio.core.usecase

/**
 * Suggests merging counterparty names that are almost certainly the same merchant chain under
 * different branches - "Albert Heijn 2200 Gorinchem NLD" and "Albert Heijn 1359 Gouda" both
 * reducing to "Albert Heijn" - so a spend breakdown can show one row per chain instead of one per
 * physical store.
 *
 * Deliberately a single conservative rule rather than a merchant database or fuzzy string
 * matching: a POS-exported counterparty name almost always follows "<chain name> <store number>
 * <city> [country code]", so cutting at the first token that contains a digit and keeping
 * whatever came before it isolates the chain name with very low false-positive risk. A name with
 * no digit anywhere (an online merchant, a one-off payee, "T-Mobile") has nothing to cut, so it's
 * treated as its own candidate canonical form - it only ever groups with a digit-bearing variant
 * that reduces to that exact same name (a generic "Albert Heijn" order alongside "Albert Heijn
 * 2200 Gorinchem NLD" visits), never with another unrelated no-digit merchant.
 *
 * This intentionally only ever *suggests* groups - [candidateGroups] is read-only analysis, never
 * a merge itself. A false negative (two branches that vary only by city, with no store number at
 * all, so nothing to cut at) costs nothing beyond a missed shortcut; a false positive would
 * silently blend two unrelated payees' spend into one number, which is why the caller (see
 * ChartsViewModel/AppPreferences) always asks the user to confirm a specific group before treating
 * it as one merchant, and remembers a "nee" so the same suggestion doesn't keep coming back.
 */
object MerchantGrouper {

    /** One suggested merge: [canonicalName] is the shared prefix, [rawNames] the ≥2 distinct counterparty names that produced it, alphabetical. */
    data class MerchantGroupCandidate(val canonicalName: String, val rawNames: List<String>)

    // Below this, a "shared prefix" is too generic to trust ("De 12" from two unrelated payees
    // both starting with "De" and then a number for unrelated reasons, say).
    private const val MIN_CANONICAL_LENGTH = 3

    /** Every group of ≥2 distinct [counterpartyNames] sharing a [chainPrefix], alphabetical by canonical name. */
    fun candidateGroups(counterpartyNames: Collection<String>): List<MerchantGroupCandidate> =
        counterpartyNames.toSet()
            .groupBy { chainPrefix(it) }
            .mapNotNull { (prefix, names) -> prefix?.let { MerchantGroupCandidate(it, names.sorted()) } }
            .filter { it.canonicalName.length >= MIN_CANONICAL_LENGTH && it.rawNames.size >= 2 }
            .sortedBy { it.canonicalName }

    /**
     * "Albert Heijn 2200 Gorinchem NLD" -> "Albert Heijn"; the name itself when it has no
     * digit-bearing token to cut at (so it can still match a variant that does); null when the
     * very first token already contains a digit, leaving nothing to cut before it.
     */
    private fun chainPrefix(name: String): String? {
        val trimmed = name.trim()
        val tokens = trimmed.split(Regex("\\s+"))
        val cutIndex = tokens.indexOfFirst { token -> token.any { it.isDigit() } }
        return when {
            cutIndex == 0 -> null
            cutIndex > 0 -> tokens.subList(0, cutIndex).joinToString(" ")
            else -> trimmed
        }
    }
}
