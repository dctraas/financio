package com.financio.core.usecase

import com.financio.core.model.Money
import java.time.LocalDate

/**
 * Projects the account balance from today to the end of the month — Vandaag's forecast line,
 * the schermontwerp redesign's "kernfeature". Built entirely from data the app already has (no
 * bank API): the current balance, a flat average daily spend, and the dates/amounts of
 * subscriptions expected to bill before month-end (see [SubscriptionDetector]).
 *
 * Deliberately simple and honest about it: a flat daily average is not a real spending forecast,
 * just today's balance minus "what a typical day costs you" repeated forward, with the known,
 * near-certain subscription charges layered on top of that. It's precise about what it *can* be
 * precise about (recurring charges) and approximate about the rest, rather than pretending to
 * predict everything.
 */
object BalanceForecastCalculator {
    data class ForecastPoint(val date: LocalDate, val balance: Money, val isProjected: Boolean)

    /** [tightDate] is the first *projected* day the balance is expected to reach zero or below — null if it never does this month. */
    data class Result(val points: List<ForecastPoint>, val tightDate: LocalDate?)

    /**
     * @param averageDailySpend a positive magnitude — what a typical day costs, excluding subscription charges (those are added separately via [upcomingSubscriptions] so they aren't double-counted).
     * @param upcomingSubscriptions (date, amount) pairs, amount as a positive magnitude debit, for charges expected to land on or after [today] and on/before the end of [today]'s month.
     */
    fun forecast(currentBalance: Money, today: LocalDate, averageDailySpend: Money, upcomingSubscriptions: List<Pair<LocalDate, Money>>): Result {
        val endOfMonth = today.withDayOfMonth(today.lengthOfMonth())
        val points = mutableListOf(ForecastPoint(today, currentBalance, isProjected = false))

        var runningBalance = currentBalance
        var date = today.plusDays(1)
        while (!date.isAfter(endOfMonth)) {
            val subscriptionChargeThatDay = upcomingSubscriptions.filter { it.first == date }.sumOf { it.second.cents }
            runningBalance = Money(runningBalance.cents - averageDailySpend.cents - subscriptionChargeThatDay)
            points.add(ForecastPoint(date, runningBalance, isProjected = true))
            date = date.plusDays(1)
        }

        val tightDate = points.firstOrNull { it.isProjected && it.balance.cents <= 0 }?.date
        return Result(points, tightDate)
    }
}
