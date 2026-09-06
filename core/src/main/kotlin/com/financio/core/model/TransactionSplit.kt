package com.financio.core.model

/**
 * One category's share of a transaction that's been split across several categories — e.g. a
 * single Bol.com purchase that was half kleding, half elektronica. A transaction with any splits
 * has its own [Transaction.categoryId] set to null; the splits are authoritative instead (see
 * [com.financio.core.usecase.SplitValidation] for the invariant splits must satisfy).
 */
data class TransactionSplit(
    val id: Long = 0,
    val transactionId: Long,
    val categoryId: Long,
    val amount: Money,
)
