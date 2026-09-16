package com.financio.app.ui.importing

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.common.toShortDisplayString
import com.financio.core.model.Account
import com.financio.core.model.SourceFormat
import com.financio.core.usecase.ImportPreview
import com.financio.core.usecase.UncategorizedGroup
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun ImportScreen(onDone: () -> Unit, viewModel: ImportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val selectedAccountId by viewModel.selectedAccountId.collectAsState()
    val hasAnyTransactions by viewModel.hasAnyTransactions.collectAsState()
    val context = LocalContext.current
    // Screen 03 "Categoriseren" (see CategorizeScreen.kt) takes over full-screen from Ready's
    // summary, with its own header instead of the standard "Importeren" app bar. Keyed on the
    // Ready state's own `preview` instance (stable across a choice made within it, since
    // assignCategory/skip both produce a new Ready via .copy() without touching preview) so this
    // resets to false only when a genuinely new import starts, not on every choice.
    val categorizeKey = (state as? ImportUiState.Ready)?.preview
    var categorizing by remember(categorizeKey) { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val content = context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream)).readText()
        }
        if (content != null) viewModel.onFilePicked(content)
    }

    LaunchedEffect(state) {
        if (state is ImportUiState.Imported) onDone()
    }

    Scaffold(
        topBar = {
            if (!(state is ImportUiState.Ready && categorizing)) {
                TopAppBar(
                    title = { Text("Importeren") },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        },
    ) { padding ->
        when (val current = state) {
            is ImportUiState.PickFile -> {
                val onPickFile = { filePicker.launch(arrayOf("text/*", "application/octet-stream")) }
                if (hasAnyTransactions) {
                    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                        // Only surfaced once a second account actually exists - a single-account
                        // install (still the common case) never sees this and always imports into
                        // DefaultAccount.
                        if (accounts.size > 1) {
                            AccountPicker(
                                accounts = accounts,
                                selectedAccountId = selectedAccountId,
                                onSelect = viewModel::selectAccount,
                            )
                        }
                        Text("Kies een CSV- of MT940-export uit Mijn ING.", style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = onPickFile, modifier = Modifier.padding(top = 16.dp)) { Text("Bestand kiezen") }
                    }
                } else {
                    FirstLaunchContent(padding = padding, onPickFile = onPickFile)
                }
            }

            is ImportUiState.Loading -> Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                Text("Bezig met inlezen…")
            }

            is ImportUiState.Failed -> FailedContent(current, padding, onRetryWithDateColumn = viewModel::retryWithDateColumn)

            is ImportUiState.AccountDetected -> AccountDetectedContent(
                state = current,
                padding = padding,
                onConfirm = viewModel::confirmNewAccount,
                onLinkToExisting = viewModel::linkToExistingAccount,
                onCancel = viewModel::cancelAccountDetection,
            )

            is ImportUiState.Ready -> if (categorizing) {
                CategorizeContent(
                    state = current,
                    categories = categories,
                    padding = padding,
                    viewModel = viewModel,
                    onExit = { categorizing = false },
                )
            } else {
                ReadyContent(current, padding, viewModel, onStartCategorizing = { categorizing = true })
            }

            is ImportUiState.Imported -> Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                Text("Geïmporteerd.")
            }
        }
    }
}

/**
 * The one screen that decides whether someone ever uses the app at all (R12): the critical step —
 * exporting from Mijn ING — happens entirely outside this app, in the user's own bank environment,
 * where nothing can be steered after the fact. So this leads with exactly where to click, which
 * format, and which period, instead of the single terse line a returning user (see
 * [ImportViewModel.hasAnyTransactions]) doing their Nth import actually wants.
 *
 * Deliberately doesn't offer a bundled "voorbeelddata" demo mode, unlike the original redesign
 * mockup: that would mean either seeding fake-looking transactions into the same real, encrypted
 * database a genuine import writes to (a real risk of permanently mixing demo and real financial
 * history if anything about clearing it later went wrong), or a second, fully parallel
 * fake-data-rendering path through every screen's ViewModel — both a much larger surface than this
 * pass's effort budget justifies without a way to test either interactively.
 */
@Composable
private fun FirstLaunchContent(padding: PaddingValues, onPickFile: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(
            "Geen account, geen bankkoppeling. Je bankexport wordt hier op dit toestel " +
                "ingelezen en versleuteld opgeslagen — Financio heeft geen server en geen " +
                "internettoegang, dus je gegevens verlaten dit toestel nooit.",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )

        Text(
            "Zo exporteer je je transacties uit Mijn ING",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 28.dp, bottom = 12.dp),
        )
        NumberedStep(1, "Open Mijn ING (app of website) en ga naar de rekening die je wilt importeren.")
        NumberedStep(2, "Kies \"Afschriften\" of \"Exporteren\".")
        NumberedStep(3, "Kies als periode \"Afgelopen 2 jaar\" — dat geeft de beste resultaten voor abonnementen-detectie en gemiddeldes, ook al importeer je zelf misschien maar voor een paar maanden.")
        NumberedStep(4, "Kies CSV of MT940 als bestandsformaat en download het bestand.")

        Text(
            "Financio categoriseert automatisch ongeveer 80% van je transacties. De rest kies " +
                "je zelf — dat kost een paar minuten, eenmalig. Daarna hoeft dat nooit meer.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp, bottom = 20.dp),
        )

        Button(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) { Text("Bestand kiezen") }
    }
}

@Composable
private fun NumberedStep(number: Int, text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp).weight(1f))
    }
}

@Composable
private fun AccountPicker(accounts: List<Account>, selectedAccountId: Long, onSelect: (Long) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val selectedName = accounts.firstOrNull { it.id == selectedAccountId }?.name ?: "Kies rekening"

    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text("Importeren naar", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "$selectedName ▾",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { menuOpen = true }.padding(vertical = 4.dp),
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.name) },
                    onClick = { onSelect(account.id); menuOpen = false },
                )
            }
        }
    }
}

/**
 * The improved error state (R7): instead of a dead-end message, a missing-date-column failure
 * shows the file's own first lines and detected header so the user can point at the column
 * themselves — see [com.financio.core.importer.UnrecognizedFormatException]'s doc comment for why
 * only the date column gets this recovery path.
 */
@Composable
private fun FailedContent(state: ImportUiState.Failed, padding: PaddingValues, onRetryWithDateColumn: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
        Text("Importeren mislukt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(state.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))

        if (state.detectedColumns.isNotEmpty()) {
            Text(
                "Is een van deze kolommen eigenlijk de datumkolom?",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.detectedColumns.withIndex().toList(), key = { it.index }) { (index, column) ->
                    FilterChip(selected = false, onClick = { onRetryWithDateColumn(index) }, label = { Text(column) })
                }
            }

            Text(
                "Eerste regels van het bestand",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
            ) {
                state.rawLines.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

/**
 * Shown when the imported file's own account (an IBAN, or an internal ING code for an account
 * with none visible) matches none of the app's known accounts yet — see
 * [ImportViewModel.handleUnknownAccount]. Naam/IBAN mirror [com.financio.app.ui.accounts.AccountsScreen]'s
 * "Nieuwe rekening" dialog, just as a full-page step in the import flow instead of a dialog, so
 * confirming here flows straight back into importing into the newly created account.
 */
@Composable
private fun AccountDetectedContent(
    state: ImportUiState.AccountDetected,
    padding: PaddingValues,
    onConfirm: (name: String, ibanMasked: String) -> Unit,
    onLinkToExisting: (accountId: Long) -> Unit,
    onCancel: () -> Unit,
) {
    // A checking account's "Rekening" column and an MT940 :25: tag are both real IBANs; a savings
    // account's own export instead gives an internal ING code like "L866-14401" - showing that
    // under "IBAN" would just be wrong, so the field (and its label) follow what was actually
    // detected instead of always assuming an IBAN.
    val isIban = remember(state.rawIdentifier) { looksLikeIban(state.rawIdentifier) }
    var name by remember { mutableStateOf(state.suggestedName) }
    var accountNumber by remember { mutableStateOf(state.rawIdentifier) }
    var showExistingPicker by remember { mutableStateOf(false) }
    val isValid = name.isNotBlank() && accountNumber.isNotBlank()

    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("Nieuwe rekening gevonden", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Dit bestand hoort bij een rekening die nog niet in Financio bestaat. Voeg hem toe " +
                "om deze transacties aan de juiste rekening te koppelen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Naam") },
            placeholder = { Text("bijv. ING Spaarrekening") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = accountNumber,
            onValueChange = { accountNumber = it },
            label = { Text(if (isIban) "IBAN" else "Rekeningnummer") },
            placeholder = { Text(if (isIban) "bijv. NL91 INGB 0008 0286 52" else "bijv. L866-14401") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        if (!isIban) {
            Text(
                "Dit bestand geeft geen IBAN voor deze rekening, alleen dit interne rekeningnummer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Button(
            onClick = { onConfirm(name.trim(), accountNumber.trim()) },
            enabled = isValid,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        ) { Text("Rekening toevoegen") }

        if (state.existingAccounts.isNotEmpty()) {
            Text(
                "Dit is eigenlijk een bestaande rekening →",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { showExistingPicker = true }.padding(top = 20.dp),
            )
        }
        Text(
            "Annuleren",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onCancel).padding(top = 16.dp),
        )
    }

    if (showExistingPicker) {
        AlertDialog(
            onDismissRequest = { showExistingPicker = false },
            title = { Text("Aan welke rekening horen deze transacties?") },
            text = {
                Column {
                    state.existingAccounts.forEach { account ->
                        Text(
                            account.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showExistingPicker = false
                                    onLinkToExisting(account.id)
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showExistingPicker = false }) { Text("Annuleren") } },
        )
    }
}

/** ISO 13616 shape (2-letter country + 2 check digits + up to 30 alphanumeric BBAN) - just enough to tell a real IBAN (checking accounts, every MT940 :25: tag) apart from a savings account export's internal ING code like "L866-14401", which doesn't fit this at all. */
private val IBAN_PATTERN = Regex("^[A-Z]{2}[0-9]{2}[A-Z0-9]{10,30}$")
private fun looksLikeIban(value: String): Boolean = IBAN_PATTERN.matches(value.trim().uppercase())

@Composable
private fun ReadyContent(
    state: ImportUiState.Ready,
    padding: PaddingValues,
    viewModel: ImportViewModel,
    onStartCategorizing: () -> Unit,
) {
    val preview = state.preview
    val groups = preview.needsCategoryGrouped
    val remainingGroups = computeRemainingGroups(groups, state.manualCategoryChoices, state.skippedGroups)
    var showDuplicateInfo by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(padding)) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            ImportHeader(preview, state.accountName)
            SummaryTiles(preview, onDuplicateInfoClick = { showDuplicateInfo = true })
        }

        Box(Modifier.weight(1f).padding(horizontal = 20.dp)) {
            if (remainingGroups.isEmpty()) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    Text(
                        if (groups.isEmpty()) "Alles is automatisch gecategoriseerd." else "Alle tegenpartijen doorgenomen.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                // The screen 03 "Categoriseren" flow (see CategorizeScreen.kt) takes over full-screen
                // from here - the summary above already spelled out that 24 transactions are really
                // just 9 decisions, so the button names that instead of a generic "doorgaan".
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    Text(
                        "${remainingGroups.size} tegenpartij${if (remainingGroups.size == 1) "" else "en"} nog te categoriseren.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Button(onClick = onStartCategorizing, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Text("Nu categoriseren · ${remainingGroups.size} groep${if (remainingGroups.size == 1) "" else "en"}")
                    }
                    TextButton(onClick = viewModel::confirm, modifier = Modifier.fillMaxWidth()) {
                        Text("Later, zet ze in de lijst")
                    }
                }
            }
        }

        if (remainingGroups.isEmpty()) {
            StickyImportBar(total = preview.total, onImport = viewModel::confirm)
        }
    }

    if (showDuplicateInfo) {
        AlertDialog(
            onDismissRequest = { showDuplicateInfo = false },
            title = { Text("Wat telt als dubbel?") },
            text = {
                Text(
                    "Een transactie telt als dubbel wanneer datum, bedrag, tegenrekening én " +
                        "omschrijving allemaal al bestaan op deze rekening — die wordt dan " +
                        "overgeslagen in plaats van nogmaals geïmporteerd.",
                )
            },
            confirmButton = { TextButton(onClick = { showDuplicateInfo = false }) { Text("Begrepen") } },
        )
    }
}

/**
 * Which groups still need a review card, and which of each group's own transactions are still
 * pending — a group whose transactions are only *partly* resolved (see [ManualCategoryChoice])
 * reappears here as a smaller card containing just the rest, instead of disappearing entirely or
 * staying stuck at its original full size.
 */
fun computeRemainingGroups(groups: List<UncategorizedGroup>, choices: List<ManualCategoryChoice>, skippedGroups: Set<String>): List<UncategorizedGroup> =
    groups.mapNotNull { group ->
        if (group.counterpartyName in skippedGroups) return@mapNotNull null
        val pending = group.transactions.filter { transaction -> choices.none { it.matches(transaction) } }
        if (pending.isEmpty()) null else UncategorizedGroup(group.counterpartyName, pending)
    }

/** Period + account + format, replacing a raw filename that told you nothing about what's actually in the file. */
@Composable
private fun ImportHeader(preview: ImportPreview, accountName: String) {
    val allTransactions = preview.ready + preview.needsCategory
    val period = allTransactions.map { it.date }.let { dates ->
        if (dates.isEmpty()) null else {
            val first = dates.min()
            val last = dates.max()
            if (first == last) first.toShortDisplayString() else "${first.toShortDisplayString()} – ${last.toShortDisplayString()}"
        }
    }
    val format = allTransactions.firstOrNull()?.sourceFormat?.let { if (it == SourceFormat.CSV) "CSV" else "MT940" }

    Text(
        listOfNotNull(period, accountName, format).joinToString(" · "),
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
    )
}

@Composable
private fun SummaryTiles(preview: ImportPreview, onDuplicateInfoClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryTile("Gevonden", preview.foundInFile.toString(), modifier = Modifier.weight(1f))
        SummaryTile("Automatisch", preview.ready.size.toString(), modifier = Modifier.weight(1f))
        SummaryTile("Te kiezen", preview.needsCategoryGrouped.size.toString(), modifier = Modifier.weight(1f))
        SummaryTile("Dubbel", preview.duplicateCount.toString(), modifier = Modifier.weight(1f), onInfoClick = onDuplicateInfoClick)
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier, onInfoClick: (() -> Unit)? = null) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            onInfoClick?.let {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline)
                        .clickable(onClick = it),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("?", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.surface)
                }
            }
        }
    }
}

/**
 * Always docked at the bottom, never scrolled away (R7) - importing doesn't require finishing
 * the card stack first, and the reassurance text says so explicitly.
 */
@Composable
private fun StickyImportBar(total: Int, onImport: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Button(onClick = onImport, enabled = total > 0, modifier = Modifier.fillMaxWidth()) {
            Text("$total transacties importeren")
        }
        Text(
            "Nog niet alles gekozen? Geen probleem — dat verschijnt straks op Vandaag als " +
                "\"nog te categoriseren\".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

