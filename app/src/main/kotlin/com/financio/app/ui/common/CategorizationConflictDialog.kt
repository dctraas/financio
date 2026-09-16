package com.financio.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shown instead of silently learning a whole-counterparty rule whenever a counterparty already
 * has transactions in a *different* category — a counterparty like the Belastingdienst
 * legitimately sends transactions for more than one purpose (motorrijtuigenbelasting vs.
 * kinderopvangtoeslag) under one name, and a rule keyed on the bare name would wrongly capture
 * both. Reused identically by Transacties and the transaction detail screen.
 */
@Composable
fun CategorizationConflictDialog(
    counterpartyName: String,
    existingCategoryName: String?,
    previewCount: (String) -> Int,
    onApplyToAll: () -> Unit,
    onApplyToKeyword: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var keyword by remember { mutableStateOf("") }
    val matchCount = if (keyword.isBlank()) 0 else previewCount(keyword)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Meerdere soorten transacties bij $counterpartyName?") },
        text = {
            Column {
                Text(
                    buildString {
                        append("'$counterpartyName' valt nu ook al onder")
                        append(if (existingCategoryName != null) " '$existingCategoryName'" else " een andere categorie")
                        append(" voor andere transacties. Moet de nieuwe regel voor alle transacties van '$counterpartyName' gelden, of alleen voor transacties zoals deze?")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onApplyToAll, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text("Voor alle transacties van '$counterpartyName'")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    "Alleen voor transacties met dit woord in de omschrijving:",
                    style = MaterialTheme.typography.labelMedium,
                )
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    placeholder = { Text("bijv. motorrijtuigenbelasting") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
                if (keyword.isNotBlank()) {
                    Text(
                        "Raakt nu $matchCount ${if (matchCount == 1) "transactie" else "transacties"} van '$counterpartyName'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                TextButton(
                    onClick = { onApplyToKeyword(keyword) },
                    enabled = keyword.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("Toepassen op deze transacties") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}
