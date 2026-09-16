package com.financio.core.usecase

import com.financio.core.model.Money
import java.time.LocalDate

/**
 * Projects the account balance from today to the end of the month — Vandaag's forecast line,
 * the schermontwerp redesign's "kernfeature". Built entirely from data the app already has (no
 * bank API): the current balance, a flat average daily spend, the dates/amounts of subscriptions
 * expected to bill before month-end (see [SubscriptionDetector]), and any recurring income (e.g.
 * salary) expected to land before month-end (see [RecurringIncomeDetector]).
 *
 * Deliberately simple and honest about it: a flat daily average is not a real spending forecast,
 * just today's balance minus "what a typical day costs you" repeated forward, with the known,
 * near-certain subscription charges and recurring income layered on top of that. It's precise
 * about what it *can* be precise about (recurring charges and income) and approximate about the
 * rest, rather than pretending to predict everything.
 */
object BalanceForecastCalculator {
    data class ForecastPoint(val date: LocalDate, val balance: Money, val isProjected: Boolean)

    /** [tightDate] is the first *projected* day the balance is expected to reach zero or below — null if it never does this month. */
    data class Result(val points: List<ForecastPoint>, val tightDate: LocalDate?)

    /**
     * @param averageDailySpend a positive magnitude — what a typical day costs, excluding subscription charges (those are added separately via [upcomingSubscriptions] so they aren't double-counted).
     * @param upcomingSubscriptions (date, amount) pairs, amount as a positive magnitude debit, for charges expected to land on or after [today] and on/before the end of [today]'s month.
     * @param upcomingIncome (date, amount) pairs, amount as a positive magnitude credit, for recurring income (e.g. salary) expected to land in that same window - see [RecurringIncomeDetector]. Without this, a low balance early in the month projected steadily downward even when a paycheck was actually still due before month-end.
     */
    fun forecast(
        currentBalance: Money,
        today: LocalDate,
        averageDailySpend: Money,
        upcomingSubscriptions: List<Pair<LocalDate, Money>>,
        upcomingIncome: List<Pair<LocalDate, Money>> = emptyList(),
    ): Result {
        val endOfMonth = today.withDayOfMonth(today.lengthOfMonth())
        val points = mutableListOf(ForecastPoint(today, currentBalance, isProjected = false))

        var runningBalance = currentBalance
        var date = today.plusDays(1)
        while (!date.isAfter(endOfMonth)) {
            val subscriptionChargeThatDay = upcomingSubscriptions.filter { it.first == date }.sumOf { it.second.cents }
            val incomeThatDay = upcomingIncome.filter { it.first == date }.sumOf { it.second.cents }
            runningBalance = Money(runningBalance.cents - averageDailySpend.cents - subscriptionChargeThatDay + incomeThatDay)
            points.add(ForecastPoint(date, runningBalance, isProjected = true))
            date = date.plusDays(1)
        }

        val tightDate = points.firstOrNull { it.isProjected && it.balance.cents <= 0 }?.date
        return Result(points, tightDate)
    }
}
