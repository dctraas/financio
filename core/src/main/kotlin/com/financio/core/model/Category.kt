package com.financio.core.model

data class Account(
    val id: Long = 0,
    val name: String,
    val ibanMasked: String,
    /** Hidden from the accounts list's day-to-day view without deleting its (real) transaction history — e.g. a closed account. */
    val hidden: Boolean = false,
    /** Left out of every cross-account total (Meer's tile, Vermogen) without hiding the account itself — e.g. a shared account where only part of the balance is really yours. */
    val excludedFromTotal: Boolean = false,
    /**
     * A user-typed balance for an account whose imports carry no [Transaction.balanceAfter] at
     * all (an MT940 export with no closing-balance record, say) - the account's balance is
     * "onbekend" rather than a silently-wrong €0 until this is filled in. Ignored once a real
     * balanceAfter exists; see AccountBalance.resolve.
     */
    val manualBalance: Money? = null,
    /**
     * The raw account identifier a file import reported for this account (an IBAN, or an
     * internal ING code for an account with none visible, like "L866-14401") — internal-only,
     * never shown to the user. Distinct from [ibanMasked], which is a user-typed, deliberately
     * masked display string and can't reliably be matched against an import file. Null until a
     * successful import learns it, either by user confirmation (a newly detected account) or by
     * backfilling a pre-existing account's first import after upgrading.
     */
    val importIdentifier: String? = null,
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
