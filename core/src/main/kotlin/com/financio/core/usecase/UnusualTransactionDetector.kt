package com.financio.core.usecase

import com.financio.core.model.Transaction

/**
 * "Dit is veel meer dan normaal bij deze tegenpartij" - the one heuristic simple and explainable
 * enough to run on-device with no ML model: a new debit whose magnitude is [MULTIPLIER]x or more
 * the historical average for that exact counterparty. Income (credits) is never flagged - an
 * unusually large deposit isn't the kind of surprise this exists to catch. Requires at least
 * [MIN_HISTORY] prior transactions with the same counterparty before judging anything "unusual" -
 * a brand new counterparty has no baseline to be unusual against yet.
 */
object UnusualTransactionDetector {
    private const val MULTIPLIER = 3.0
    private const val MIN_HISTORY = 3

    /** [history] is every transaction that existed *before* [newTransaction] - never includes it. */
    fun isUnusual(newTransaction: Transaction, history: List<Transaction>): Boolean {
        if (newTransaction.amount.cents >= 0) return false
        val sameCounterpartyDebits = history.filter { it.counterpartyName == newTransaction.counterpartyName && it.amount.cents < 0 }
        if (sameCounterpartyDebits.size < MIN_HISTORY) return false
        val averageAbsCents = sameCounterpartyDebits.map { kotlin.math.abs(it.amount.cents) }.average()
        return kotlin.math.abs(newTransaction.amount.cents) > averageAbsCents * MULTIPLIER
    }

    /** Every unusual transaction in [candidates], each judged against [history] plus whichever earlier candidates already passed - so two unusually large charges from the same new counterparty on the same day don't both silently pass just because neither alone had "history" yet. */
    fun findUnusual(candidates: List<Transaction>, history: List<Transaction>): List<Transaction> {
        val runningHistory = history.toMutableList()
        val unusual = mutableListOf<Transaction>()
        for (candidate in candidates) {
            if (isUnusual(candidate, runningHistory)) unusual += candidate
            runningHistory += candidate
        }
        return unusual
    }
}
