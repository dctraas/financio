package com.financio.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val ibanMasked: String,
    /** See [com.financio.core.model.Account.hidden]. Added in schema v4. */
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,
    /** See [com.financio.core.model.Account.excludedFromTotal]. Added in schema v4. */
    @ColumnInfo(defaultValue = "0") val excludedFromTotal: Boolean = false,
    /** See [com.financio.core.model.Account.manualBalance]. Added in schema v4. */
    @ColumnInfo(defaultValue = "NULL") val manualBalanceCents: Long? = null,
    /** See [com.financio.core.model.Account.importIdentifier]. Added in schema v6. */
    @ColumnInfo(defaultValue = "NULL") val importIdentifier: String? = null,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String,
    val parentId: Long? = null,
)

@Entity(
    tableName = "category_rules",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("categoryId")],
)
data class CategoryRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    /** [com.financio.core.model.MatchType] name — kept as a plain string so Room needs no custom converter. */
    val matchType: String,
    val pattern: String,
    val priority: Int,
)

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("categoryId"), Index("yearMonth")],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    /** ISO "yyyy-MM", e.g. "2026-09". */
    val yearMonth: String,
    val limitCents: Long,
    val rollover: Boolean,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("accountId"), Index("categoryId"), Index("dedupHash", unique = true), Index("date")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    /** ISO "yyyy-MM-dd" — sorts and prefix-matches correctly as plain text. */
    val date: String,
    val amountCents: Long,
    val counterpartyIban: String?,
    val counterpartyName: String,
    val description: String,
    val categoryId: Long?,
    @ColumnInfo(defaultValue = "CSV") val sourceFormat: String,
    val dedupHash: String,
    /** Account balance right after this transaction, per the bank export — null for older rows and for MT940 imports. Added in schema v2. */
    @ColumnInfo(defaultValue = "NULL") val balanceCents: Long? = null,
    /** ING's own CSV "Tag" column. Added in schema v2. */
    @ColumnInfo(defaultValue = "NULL") val tag: String? = null,
    /** Free-text note typed on the transaction detail screen. Added in schema v3. */
    @ColumnInfo(defaultValue = "NULL") val note: String? = null,
)

/**
 * One category's share of a split transaction — see [com.financio.core.model.TransactionSplit].
 * Added in schema v2.
 */
@Entity(
    tableName = "transaction_splits",
    foreignKeys = [
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("transactionId"), Index("categoryId")],
)
data class TransactionSplitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val categoryId: Long,
    val amountCents: Long,
)

/** See [com.financio.core.model.SavingsGoal]. Added in schema v2. */
@Entity(
    tableName = "savings_goals",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("categoryId")],
)
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetAmountCents: Long,
    val categoryId: Long,
    /** See [com.financio.core.model.SavingsGoal.linkedAccountId]. Added in schema v4. */
    @ColumnInfo(defaultValue = "NULL") val linkedAccountId: Long? = null,
    /** ISO "yyyy-MM-dd", or null for no streefdatum. Added in schema v4. */
    @ColumnInfo(defaultValue = "NULL") val targetDate: String? = null,
    /** See [com.financio.core.model.SavingsGoal.archived]. Added in schema v4. */
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    /** See [com.financio.core.model.SavingsGoal.manualAdjustment]. Added in schema v5. */
    @ColumnInfo(defaultValue = "0") val manualAdjustmentCents: Long = 0,
)

/**
 * See [com.financio.core.model.Debt]. Added in schema v7. Deliberately no foreign keys - a debt's
 * counterparty is usually not one of this app's own accounts or categories (a family loan, an
 * informal IOU), so it's a self-contained table, unlike [SavingsGoalEntity]'s link to a category.
 */
@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** [com.financio.core.model.DebtDirection] name. */
    val direction: String,
    val counterpartyName: String,
    val principalCents: Long,
    val currentBalanceCents: Long,
    val interestRateBasisPoints: Int? = null,
    /** ISO "yyyy-MM-dd". */
    val startDate: String,
    /** ISO "yyyy-MM-dd", or null for no streefdatum. */
    val targetPayoffDate: String? = null,
    val notes: String? = null,
    val archived: Boolean = false,
)
