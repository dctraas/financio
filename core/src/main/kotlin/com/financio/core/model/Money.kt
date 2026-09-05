package com.financio.core.model

/**
 * A euro amount stored as whole cents, never as Float or Double.
 *
 * The architecture doc calls this out explicitly: summing Double amounts across hundreds of
 * transactions drifts by a few cents over time (classic 0.1 + 0.2 problem). An integer number
 * of cents doesn't have that failure mode.
 */
@JvmInline
value class Money(val cents: Long) : Comparable<Money> {

    operator fun plus(other: Money): Money = Money(cents + other.cents)
    operator fun minus(other: Money): Money = Money(cents - other.cents)
    operator fun unaryMinus(): Money = Money(-cents)

    override fun compareTo(other: Money): Int = cents.compareTo(other.cents)

    /** Renders as "€23,45" / "-€23,45", the Dutch decimal-comma convention. */
    fun toDisplayString(): String {
        val sign = if (cents < 0) "-" else ""
        val absCents = kotlin.math.abs(cents)
        val euros = absCents / 100
        val remainder = (absCents % 100).toString().padStart(2, '0')
        return "$sign€${formatThousands(euros)},$remainder"
    }

    private fun formatThousands(value: Long): String =
        value.toString().reversed().chunked(3).joinToString(".").reversed()

    companion object {
        val ZERO = Money(0)

        /**
         * Parses a Dutch decimal-comma amount as used by both ING export formats,
         * e.g. "23,45" or "1.284,56" -> 2345 / 128456 cents. Never goes through Double.
         */
        fun parseCommaDecimal(raw: String): Money {
            val cleaned = raw.trim().replace(".", "")
            val parts = cleaned.split(",")
            require(parts.size == 2 && parts[1].length == 2) {
                "Onverwacht bedragformaat: '$raw' (verwacht bijvoorbeeld '23,45')"
            }
            val euros = parts[0].toLong()
            val cents = parts[1].toLong()
            val magnitude = kotlin.math.abs(euros) * 100 + cents
            return Money(if (euros < 0) -magnitude else magnitude)
        }
    }
}
