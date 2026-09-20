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

    // Below this normalized length, a 1-character edit distance is too easy to hit by chance
    // between two genuinely unrelated short names ("ING" vs "BING").
    private const val MIN_FUZZY_NORMALIZED_LENGTH = 6
    private const val MAX_FUZZY_EDIT_DISTANCE = 1

    /**
     * A second, looser suggestion source than [candidateGroups]'s exact-prefix rule - catches the
     * kind of near-duplicate spelling the digit-cut heuristic can't, because there's no digit
     * anywhere to cut at: punctuation ("Coolblue B.V." vs "Coolblue BV"), a stray typo ("Spotify"
     * vs "Spotfy"). Grouping is by edit distance on a normalized (lowercased, letters/digits only)
     * form, capped at [MAX_FUZZY_EDIT_DISTANCE] and only above [MIN_FUZZY_NORMALIZED_LENGTH] - both
     * deliberately tight, same "never blend two unrelated payees' spend" reasoning as
     * [candidateGroups]'s own doc comment. Names [candidateGroups] already grouped are excluded
     * here, so the two suggestion sources never overlap or contradict each other.
     */
    fun fuzzyCandidateGroups(counterpartyNames: Collection<String>): List<MerchantGroupCandidate> {
        val distinct = counterpartyNames.toSet()
        val alreadyGrouped = candidateGroups(distinct).flatMap { it.rawNames }.toSet()
        val normalizedByName = distinct
            .filter { it !in alreadyGrouped }
            .associateWith { normalize(it) }
            .filterValues { it.length >= MIN_FUZZY_NORMALIZED_LENGTH }
        val names = normalizedByName.keys.toList()

        // Union-find over pairwise similarity - a chain of near-matches (A~B~C) ends up in one
        // group even if A and C themselves aren't within the edit-distance cap of each other.
        val parent = names.associateWith { it }.toMutableMap()
        fun find(name: String): String {
            var root = name
            while (parent.getValue(root) != root) root = parent.getValue(root)
            return root
        }
        fun union(a: String, b: String) {
            val rootA = find(a)
            val rootB = find(b)
            if (rootA != rootB) parent[rootA] = rootB
        }

        for (i in names.indices) {
            for (j in i + 1 until names.size) {
                if (editDistance(normalizedByName.getValue(names[i]), normalizedByName.getValue(names[j])) <= MAX_FUZZY_EDIT_DISTANCE) {
                    union(names[i], names[j])
                }
            }
        }

        return names.groupBy(::find).values
            .filter { it.size >= 2 }
            .map { group ->
                val sorted = group.sorted()
                // The shortest spelling is usually the cleanest one (no extra punctuation/legal
                // suffix) - alphabetical order is just the tiebreaker for a deterministic result.
                MerchantGroupCandidate(sorted.minByOrNull { it.length } ?: sorted.first(), sorted)
            }
            .sortedBy { it.canonicalName }
    }

    private fun normalize(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    /** Classic Levenshtein edit distance, single-row dynamic programming. */
    private fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        val previousRow = IntArray(b.length + 1) { it }
        val currentRow = IntArray(b.length + 1)
        for (i in 1..a.length) {
            currentRow[0] = i
            for (j in 1..b.length) {
                currentRow[j] = if (a[i - 1] == b[j - 1]) {
                    previousRow[j - 1]
                } else {
                    1 + minOf(previousRow[j - 1], previousRow[j], currentRow[j - 1])
                }
            }
            for (j in 0..b.length) previousRow[j] = currentRow[j]
        }
        return previousRow[b.length]
    }
}
