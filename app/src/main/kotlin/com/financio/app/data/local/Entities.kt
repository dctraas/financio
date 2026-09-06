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
)
