package com.financio.app

/**
 * Fase 1 assumes exactly one account (jouw ING-betaalrekening) — see the routekaart's
 * scope decision. Multiple accounts is fase-3 scope, once an aggregator is in the picture.
 */
object DefaultAccount {
    const val ID = 1L
    const val DISPLAY_NAME = "ING Betaalrekening"
}
