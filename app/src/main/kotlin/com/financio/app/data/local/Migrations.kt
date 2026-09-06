package com.financio.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: purely additive — two new nullable columns on an existing table, two new empty
 * tables. Nothing existing is altered, renamed, or dropped, and no existing row's data is
 * touched, so there's no data-loss risk the way a migration that also had to deduplicate or
 * transform existing rows would carry (see the README's note on why the earlier Budgetten
 * duplicate-row bug was fixed with a runtime repair pass instead of a schema migration — that
 * one needed to reconcile existing bad data, this one doesn't need to reconcile anything).
 *
 * Verified against a real, standalone SQLite database built to the exact v1 schema (see the
 * README) rather than just reasoned about — the actual ALTER TABLE/CREATE TABLE statements below
 * were executed for real and the resulting schema checked, since Room's migration validator
 * would otherwise be the very first thing to ever run this SQL, on a real device, with no way to
 * fix a mistake after the fact.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN balanceCents INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE transactions ADD COLUMN tag TEXT DEFAULT NULL")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS transaction_splits (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                transactionId INTEGER NOT NULL,
                categoryId INTEGER NOT NULL,
                amountCents INTEGER NOT NULL,
                FOREIGN KEY(transactionId) REFERENCES transactions(id) ON DELETE CASCADE,
                FOREIGN KEY(categoryId) REFERENCES categories(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_splits_transactionId ON transaction_splits(transactionId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_splits_categoryId ON transaction_splits(categoryId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS savings_goals (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                targetAmountCents INTEGER NOT NULL,
                categoryId INTEGER NOT NULL,
                FOREIGN KEY(categoryId) REFERENCES categories(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_savings_goals_categoryId ON savings_goals(categoryId)")
    }
}
