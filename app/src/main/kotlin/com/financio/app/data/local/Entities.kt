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
)
