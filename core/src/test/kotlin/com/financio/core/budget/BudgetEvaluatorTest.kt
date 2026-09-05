package com.financio.core.budget

import com.financio.core.model.Money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class BudgetEvaluatorTest {

    // Figures from the routekaart/schermontwerp mockups, checked against the rule the
    // architecture doc states in prose: <80% OK, 80-100% WARNING, >100% OVER. Note that this
    // makes Boodschappen (92%) WARNING, not the OK/green the mockups happened to show — those
    // picked a color by eye rather than by computing it, so the mockup is the one that's
    // slightly off; this evaluator is the actual rule going forward.
    @ParameterizedTest(name = "€{0} of €{1} limit -> {2}")
    @CsvSource(
        "412,450,WARNING",  // Boodschappen — 92%
        "38,40,WARNING",    // Abonnementen — 95%
        "265,200,OVER",     // Uit eten — 133%
        "96,150,OK",        // Vervoer — 64%
        "80,100,WARNING",   // exactly on the 80% boundary
        "100,100,WARNING",  // exactly on the 100% boundary is still "80-100%", not over yet
        "79,100,OK",        // just under the boundary
    )
    fun `matches the thresholds from the design mockups`(spent: Long, limit: Long, expected: BudgetStatus) {
        assertEquals(expected, BudgetEvaluator.evaluate(Money(spent * 100), Money(limit * 100)))
    }

    @ParameterizedTest(name = "€{0} of €{1} limit = {2}%")
    @CsvSource("50,100,50", "133,100,133", "0,100,0")
    fun `computes the percentage used for progress bars`(spent: Long, limit: Long, expected: Int) {
        assertEquals(expected, BudgetEvaluator.percentage(Money(spent * 100), Money(limit * 100)))
    }
}
