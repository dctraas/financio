package com.financio.core.model

data class Account(
    val id: Long = 0,
    val name: String,
    val ibanMasked: String,
)

data class Category(
    val id: Long = 0,
    val name: String,
    val colorHex: String,
    val parentId: Long? = null,
)

enum class MatchType { COUNTERPARTY_EXACT, KEYWORD }

/** A categorization rule. Lower [priority] number wins when several rules match. */
data class CategoryRule(
    val id: Long = 0,
    val categoryId: Long,
    val matchType: MatchType,
    val pattern: String,
    val priority: Int,
)
