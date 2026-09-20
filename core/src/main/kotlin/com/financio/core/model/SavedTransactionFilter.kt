package com.financio.core.model

import kotlinx.serialization.Serializable

/**
 * A named, persisted combination of Transacties' filter controls — "opgeslagen filter" / smart
 * folder (schermontwerp #49/#52 treat these as the same feature, since Financio's filters are
 * already reactive/live-computed rather than a one-off saved search). [categoryId] null with
 * [uncategorizedOnly] false means "alle categorieën"; [uncategorizedOnly] true means "zonder
 * categorie" regardless of [categoryId]. [minAmountCents]/[maxAmountCents] are always
 * non-negative magnitudes (matching how someone types an amount filter - "meer dan €50", not a
 * signed cents value), compared against a transaction's absolute amount. Dates are ISO-8601
 * strings (`LocalDate.toString()`) rather than `LocalDate` itself, so this model - like
 * [com.financio.core.backup.BackupBundle] - stays free of any date-library choice.
 */
@Serializable
data class SavedTransactionFilter(
    val id: Long,
    val name: String,
    val searchQuery: String = "",
    val categoryId: Long? = null,
    val uncategorizedOnly: Boolean = false,
    val minAmountCents: Long? = null,
    val maxAmountCents: Long? = null,
    val dateFrom: String? = null,
    val dateTo: String? = null,
    /** Shown as a quick-access chip on Vandaag - the "pin favorites" half of this feature. */
    val pinnedOnVandaag: Boolean = false,
)
