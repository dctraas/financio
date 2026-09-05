package com.financio.core.model

import java.time.YearMonth

data class Budget(
    val id: Long = 0,
    val categoryId: Long,
    val yearMonth: YearMonth,
    val limit: Money,
    val rollover: Boolean = false,
)
