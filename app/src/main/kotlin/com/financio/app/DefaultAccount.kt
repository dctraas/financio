package com.financio.app

/**
 * The one account every install starts with, seeded by [com.financio.app.data.local.DatabaseSeeder]
 * — the routekaart's fase-1 scope decision was exactly one account. Multiple accounts (still fully
 * local: each with its own separate CSV/MT940 import, no aggregator) is supported since, but this
 * first one keeps this fixed id so it stays what every screen falls back to when nothing else has
 * been chosen yet, and what any pre-multi-account install's existing data already belongs to.
 */
object DefaultAccount {
    const val ID = 1L
    const val DISPLAY_NAME = "ING Betaalrekening"
}
