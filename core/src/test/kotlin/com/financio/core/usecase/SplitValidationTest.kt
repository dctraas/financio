package com.financio.core.usecase

import com.financio.core.model.Money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SplitValidationTest {

    @Test
    fun `valid when the splits sum exactly to the total`() {
        val result = SplitValidation.validate(Money(-5000), listOf(Money(-2000), Money(-3000)))
        assertTrue(result.isValid)
        assertEquals(Money.ZERO, result.difference)
    }

    @Test
    fun `invalid when the splits don't add up, and reports by how much`() {
        val result = SplitValidation.validate(Money(-5000), listOf(Money(-2000), Money(-2000)))
        assertFalse(result.isValid)
        assertEquals(Money(-1000), result.difference)
    }

    @Test
    fun `a single split is invalid - that's just a normal category assignment, not a split`() {
        val result = SplitValidation.validate(Money(-5000), listOf(Money(-5000)))
        assertFalse(result.isValid)
    }

    @Test
    fun `three-way split that sums correctly is valid`() {
        val result = SplitValidation.validate(Money(-9999), listOf(Money(-3333), Money(-3333), Money(-3333)))
        assertTrue(result.isValid)
    }
}
