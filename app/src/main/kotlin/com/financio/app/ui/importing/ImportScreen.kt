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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.common.toShortDisplayString
import com.financio.app.ui.theme.LocalFinancioColors
import com.financio.core.model.Account
import com.financio.core.usecase.ImportPreview
import com.financio.core.usecase.UncategorizedGroup
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun ImportScreen(
    onDone: () -> Unit,
    onGoToInsights: () -> Unit = onDone,
    onBackClick: () -> Unit = onDone,
    viewModel: ImportViewModel = hiltViewModel(),
) {
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
    // Screen 04's two finishing buttons both call ImportViewModel.confirm(), which only then
    // flips the state to Imported below - this just remembers which one was tapped so that
    // transition goes to the right place instead of always the generic onDone().
    var goToInsightsAfterImport by remember(categorizeKey) { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val content = context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream)).readText()
        }
        if (content != null) viewModel.onFilePicked(content)
    }

    LaunchedEffect(state) {
        if (state is ImportUiState.Imported) {
            if (goToInsightsAfterImport) onGoToInsights() else onDone()
        }
    }

    // Screen 01's onboarding is deliberately chrome-free (see FirstLaunchContent) - every other
    // state gets the schermontwerp redesign's ← + "Importeren" header, except while screen 03
    // "Categoriseren" is showing, which has its own (see CategorizeGameHeader).
    val isFirstLaunchOnboarding = state is ImportUiState.PickFile && !hasAnyTransactions
    val isCategorizing = state is ImportUiState.Ready && categorizing

    Scaffold(
        topBar = {
            if (!isFirstLaunchOnboarding && !isCategorizing) {
                ImportTopBar(onBackClick = onBackClick)
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
                    onConfirmGoToInsights = { goToInsightsAfterImport = true; viewModel.confirm() },
                    onConfirmGoToday = { viewModel.confirm() },
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

/** The schermontwerp redesign's "← Schermtitel" header, sitting directly on the page background rather than a Material app bar strip - used the same way across every redesigned screen with a back action. */
@Composable
private fun ImportTopBar(onBackClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
        ) { Icon(Icons.Filled.ArrowBack, contentDescription = "Terug") }
        Text("Importeren", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp))
    }
}

/**
 * Screen 01 "Onboarding" from the September 2026 layout-redesign handoff — the one screen that
 * decides whether someone ever uses the app at all (R12). Leads with exactly the one thing that
 * actually differentiates Financio from Monarch/YNAB (on-device, no server, ever), in three short
 * bullets, rather than the previous version's full "how to export from Mijn ING" walkthrough - a
 * returning user (see [ImportViewModel.hasAnyTransactions]) never saw that walkthrough anyway,
 * and a first-time user still gets it if their file fails to parse (the [FailedContent] recovery
 * screen), just not spent here on the one screen that has to convert someone in a single glance.
 *
 * Deliberately doesn't offer a bundled "Eerst rondkijken met voorbeelddata" demo mode from the
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
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.weight(1f))

        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text("F", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold, fontSize = 26.sp)
        }

        Text(
            "Zie waar je geld heen gaat",
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            "Upload een bestand van je bank. Financio zet je transacties in categorieën en " +
                "laat de trends zien — zonder bankkoppeling.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
            lineHeight = 26.sp,
            modifier = Modifier.padding(top = 14.dp),
        )

        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .padding(18.dp),
        ) {
            listOf(
                "Alles staat versleuteld op dit toestel",
                "Geen account, geen server, geen internet",
                "Je kunt altijd alles exporteren of wissen",
            ).forEachIndexed { index, line ->
                if (index > 0) Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primaryContainer))
                    Text(line, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Purely decorative, matching the mockup's paginatie-stipjes - this app only ever has the
        // one onboarding screen, there's nothing behind dot 2/3 to swipe to.
        Row(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(22.dp).height(5.dp).clip(RoundedCornerShape(999.dp)).background(MaterialTheme.colorScheme.primary))
            repeat(2) {
                Box(
                    Modifier
                        .padding(start = 6.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline),
                )
            }
        }

        Button(
            onClick = onPickFile,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("Bestand kiezen", fontWeight = FontWeight.SemiBold) }

        Spacer(Modifier.height(20.dp))
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

/**
 * Screen 02 "Importeren — resultaat" from the redesign handoff — the rekeningkaart, the two
 * status cards, and the "grootste groepen" section are all read-only summary; the sticky bottom
 * block is the only place a decision gets made (start categorizing, or import as-is for later).
 */
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
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            AccountSummaryCard(preview, state.accountName, state.accountIban, onDuplicateInfoClick = { showDuplicateInfo = true })
            StatusCards(preview, modifier = Modifier.padding(top = 16.dp))
            if (groups.isNotEmpty()) {
                LargestGroupsSection(groups.take(3), modifier = Modifier.padding(top = 24.dp, bottom = 20.dp))
            }
        }

        if (remainingGroups.isEmpty()) {
            StickyImportBar(total = preview.total, onImport = viewModel::confirm)
        } else {
            // The screen 03 "Categoriseren" flow (see CategorizeScreen.kt) takes over full-screen
            // from here - the summary above already spelled out that 24 transactions are really
            // just 9 decisions, so the button names that instead of a generic "doorgaan".
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Button(
                    onClick = onStartCategorizing,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        "Nu categoriseren · ${remainingGroups.size} groep${if (remainingGroups.size == 1) "" else "en"}",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                TextButton(onClick = viewModel::confirm, modifier = Modifier.fillMaxWidth()) {
                    Text("Later, zet ze in de lijst")
                }
            }
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

/** IBAN/naam + grote "N transacties gelezen" + periode en dubbele-teller - replaces the previous one-line "period · account · format" header. */
@Composable
private fun AccountSummaryCard(preview: ImportPreview, accountName: String, accountIban: String, onDuplicateInfoClick: () -> Unit) {
    val allTransactions = preview.ready + preview.needsCategory
    val period = allTransactions.map { it.date }.let { dates ->
        if (dates.isEmpty()) null else {
            val first = dates.min()
            val last = dates.max()
            if (first == last) first.toShortDisplayString() else "${first.toShortDisplayString()} – ${last.toShortDisplayString()}"
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) {
        Text(
            listOfNotNull(accountIban.takeIf { it.isNotBlank() }, accountName).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${preview.total} transacties gelezen",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp),
        )
        val duplicateLabel = if (preview.duplicateCount > 0) "${preview.duplicateCount} dubbele overgeslagen" else null
        Text(
            listOfNotNull(period, duplicateLabel).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 6.dp)
                .let { if (duplicateLabel != null) it.clickable(onClick = onDuplicateInfoClick) else it },
        )
    }
}

@Composable
private fun StatusCards(preview: ImportPreview, modifier: Modifier = Modifier) {
    Column(modifier) {
        StatusCard(
            count = preview.ready.size,
            title = "automatisch herkend",
            subtitle = "via je eigen regels",
            countColor = MaterialTheme.colorScheme.primary,
            containerColor = MaterialTheme.colorScheme.surface,
            borderColor = MaterialTheme.colorScheme.outline,
            subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (preview.needsCategoryGrouped.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            StatusCard(
                count = preview.needsCategory.size,
                title = "hebben een categorie nodig",
                subtitle = "samengevat in ${preview.needsCategoryGrouped.size} groepen",
                countColor = MaterialTheme.colorScheme.onSecondaryContainer,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                borderColor = MaterialTheme.colorScheme.secondary,
                subtitleColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun StatusCard(
    count: Int,
    title: String,
    subtitle: String,
    countColor: Color,
    containerColor: Color,
    borderColor: Color,
    subtitleColor: Color,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(count.toString(), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = countColor, modifier = Modifier.width(54.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun LargestGroupsSection(groups: List<UncategorizedGroup>, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            "GROOTSTE GROEPEN",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = LocalFinancioColors.current.inkFaint,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        groups.forEachIndexed { index, group ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(group.counterpartyName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    "${group.count} · ${group.totalAmount.toDisplayString()}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

