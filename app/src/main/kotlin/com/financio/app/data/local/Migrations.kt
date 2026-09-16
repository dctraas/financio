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

/**
 * v2 -> v3: one new nullable column, same "purely additive, no existing data touched" shape as
 * [MIGRATION_1_2] — the new transaction-detail screen's free-text note field.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN note TEXT DEFAULT NULL")
    }
}

/**
 * v3 -> v4: six new columns across two existing tables, all nullable or defaulted, same
 * "purely additive" shape as [MIGRATION_1_2] and [MIGRATION_2_3] — the Rekeningen redesign's
 * hidden/excludedFromTotal/manualBalance, and Spaardoelen's linkedAccountId/targetDate/archived.
 * Deliberately no new FOREIGN KEY on `linkedAccountId`: SQLite's ALTER TABLE ADD COLUMN can't add
 * one without rebuilding the whole table, and this app's own encrypted database - already
 * carrying a real person's imported transaction history by the time this runs - is exactly the
 * kind of migration where "smaller and purely additive" beats "in-place table rebuild" even
 * though the rebuild would be the more textbook-correct schema. The column is still validated at
 * the app layer (SavingsGoalRepository), same as any other cross-table reference this app doesn't
 * enforce at the SQL level.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE accounts ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE accounts ADD COLUMN excludedFromTotal INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE accounts ADD COLUMN manualBalanceCents INTEGER DEFAULT NULL")

        db.execSQL("ALTER TABLE savings_goals ADD COLUMN linkedAccountId INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE savings_goals ADD COLUMN targetDate TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE savings_goals ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v4 -> v5: one new defaulted column, same purely-additive shape as every migration above - the
 * Spaardoelen redesign's manual top-up ("+") exception on top of a goal's transaction-derived
 * progress.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE savings_goals ADD COLUMN manualAdjustmentCents INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v5 -> v6: one new nullable column, same purely-additive shape as every migration above - the
 * import screen's new-account detection (see [com.financio.core.model.Account.importIdentifier]).
 * Every pre-existing account starts out NULL here, i.e. "not yet learned" - the import flow
 * backfills it the first time an import for that account carries a detectable identifier.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE accounts ADD COLUMN importIdentifier TEXT DEFAULT NULL")
    }
}
