package com.financio.core.backup

import com.financio.core.model.Account
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AccountImportTest {

    @Test
    fun `plans to create an account not yet known locally`() {
        val plan = AccountImport.plan(
            accounts = listOf(AccountExport(name = "ING Betaalrekening", ibanMasked = "NL••INGB••••••1234")),
            existing = emptyList(),
        )

        assertEquals(1, plan.toCreate.size)
        assertEquals(0, plan.skippedExisting)
    }

    @Test
    fun `skips an account whose iban already exists locally, leaving it unmodified`() {
        val existing = listOf(Account(id = 1, name = "Anders genoemd", ibanMasked = "NL••INGB••••••1234"))
        val plan = AccountImport.plan(
            accounts = listOf(AccountExport(name = "ING Betaalrekening", ibanMasked = "NL••INGB••••••1234")),
            existing = existing,
        )

        assertEquals(0, plan.toCreate.size)
        assertEquals(1, plan.skippedExisting)
    }
}
