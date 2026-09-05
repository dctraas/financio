package com.financio.core.importer

import com.financio.core.model.ParsedTransaction
import java.security.MessageDigest

/**
 * Hashes a transaction's identity fields so re-importing the same period twice doesn't
 * duplicate rows. The repository looks up existing hashes before insert; this object only
 * computes them.
 */
object Dedup {

    fun hashOf(transaction: ParsedTransaction): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(transaction.dedupKey.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
