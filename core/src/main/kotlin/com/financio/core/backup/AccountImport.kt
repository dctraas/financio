package com.financio.core.backup

import com.financio.core.model.Account

/**
 * Additive-only, same philosophy as [CategoryImport]: an account whose [AccountExport.ibanMasked]
 * already exists locally is left alone rather than renamed or re-flagged, since ibanMasked is the
 * one field on [Account] that's both stable across a reinstall and actually shown to the user.
 */
object AccountImport {

    data class Plan(val toCreate: List<AccountExport>, val skippedExisting: Int)

    fun plan(accounts: List<AccountExport>, existing: List<Account>): Plan {
        val existingIbans = existing.map { it.ibanMasked }.toSet()
        val toCreate = accounts.filter { it.ibanMasked !in existingIbans }
        return Plan(toCreate = toCreate, skippedExisting = accounts.size - toCreate.size)
    }
}
