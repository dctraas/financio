package com.financio.core.usecase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MerchantGrouperTest {

    @Test
    fun `groups branches of the same chain by their shared prefix before the store number`() {
        val names = listOf("Albert Heijn 2200 Gorinchem NLD", "Albert Heijn 1359 Gouda", "Jumbo 456 Utrecht")
        val groups = MerchantGrouper.candidateGroups(names)

        assertEquals(1, groups.size)
        val group = groups.single()
        assertEquals("Albert Heijn", group.canonicalName)
        assertEquals(listOf("Albert Heijn 1359 Gouda", "Albert Heijn 2200 Gorinchem NLD"), group.rawNames)
    }

    @Test
    fun `also groups a generic name in with its branch-specific variants`() {
        // Sometimes recorded without a store number at all (an online order, say) alongside branch visits.
        val names = listOf("Albert Heijn", "Albert Heijn 2200 Gorinchem NLD")
        val group = MerchantGrouper.candidateGroups(names).single()
        assertEquals("Albert Heijn", group.canonicalName)
        assertEquals(2, group.rawNames.size)
    }

    @Test
    fun `does not group a single occurrence`() {
        assertTrue(MerchantGrouper.candidateGroups(listOf("Albert Heijn 2200 Gorinchem NLD")).isEmpty())
    }

    @Test
    fun `does not group names with no digit-bearing token at all`() {
        // Nothing to cut at, so these are never merged with each other even though they share a first word.
        val names = listOf("Coolblue B.V.", "Coolblue Retail")
        assertTrue(MerchantGrouper.candidateGroups(names).isEmpty())
    }

    @Test
    fun `does not group a name whose very first token already contains a digit`() {
        // "7-Eleven" style names - cutting at token 0 would leave an empty canonical name.
        val names = listOf("7-Eleven Amsterdam", "7-Eleven Rotterdam")
        assertTrue(MerchantGrouper.candidateGroups(names).isEmpty())
    }

    @Test
    fun `unrelated merchants never collide`() {
        val names = listOf("Albert Heijn 2200 Gorinchem NLD", "Netflix", "Shell 88 Rotterdam")
        assertTrue(MerchantGrouper.candidateGroups(names).isEmpty())
    }

    @Test
    fun `fuzzy grouping catches punctuation-only differences the exact-prefix rule misses`() {
        // No digit anywhere, so candidateGroups itself never groups these (see the test above) -
        // normalizing away the punctuation makes both reduce to the identical "coolbluebv".
        val names = listOf("Coolblue B.V.", "Coolblue BV")
        val group = MerchantGrouper.fuzzyCandidateGroups(names).single()
        assertEquals("Coolblue BV", group.canonicalName)
        assertEquals(listOf("Coolblue B.V.", "Coolblue BV"), group.rawNames)
    }

    @Test
    fun `fuzzy grouping catches a single-character typo`() {
        val names = listOf("Spotify", "Spotfy")
        val group = MerchantGrouper.fuzzyCandidateGroups(names).single()
        assertEquals(listOf("Spotfy", "Spotify"), group.rawNames)
    }

    @Test
    fun `fuzzy grouping never reconsiders names the exact-prefix rule already grouped`() {
        val names = listOf("Albert Heijn 2200 Gorinchem NLD", "Albert Heijn 1359 Gouda")
        assertTrue(MerchantGrouper.fuzzyCandidateGroups(names).isEmpty())
    }

    @Test
    fun `fuzzy grouping ignores short normalized names even at edit distance 1`() {
        // "ing" vs "bing" is a single-character edit but far too short to trust - unrelated short
        // names collide with each other by chance far too easily.
        assertTrue(MerchantGrouper.fuzzyCandidateGroups(listOf("ING", "Bing")).isEmpty())
    }

    @Test
    fun `fuzzy grouping does not merge clearly unrelated merchants`() {
        val names = listOf("Netflix", "Netflix.com", "Spotify")
        assertTrue(MerchantGrouper.fuzzyCandidateGroups(listOf("Netflix", "Spotify")).isEmpty())
        // "Netflix" vs "Netflix.com" IS within edit distance... actually normalized "netflix" (7)
        // vs "netflixcom" (10) is distance 3, safely outside the cap.
        assertTrue(MerchantGrouper.fuzzyCandidateGroups(names).none { "Spotify" in it.rawNames })
    }
}
