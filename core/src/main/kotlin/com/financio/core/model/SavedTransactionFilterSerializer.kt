package com.financio.core.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Turns the Transacties saved-filter list into one portable JSON string and back, so
 * `AppPreferences` can store it as a single SharedPreferences string entry - same reasoning as
 * [com.financio.core.backup.BackupSerializer] keeping serialization decisions in `:core`,
 * testable without an Android SDK.
 */
object SavedTransactionFilterSerializer {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(filters: List<SavedTransactionFilter>): String = json.encodeToString(filters)

    /** Never throws - a corrupted or pre-migration empty value just means "no saved filters yet". */
    fun decode(raw: String): List<SavedTransactionFilter> {
        if (raw.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<SavedTransactionFilter>>(raw)
        } catch (e: SerializationException) {
            emptyList()
        }
    }
}
