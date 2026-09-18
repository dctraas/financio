package com.financio.app.ui.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.financio.app.ui.common.toShortDisplayString
import com.financio.app.ui.common.toSignedMagnitudeString
import com.financio.app.ui.theme.LocalFinancioColors
import com.financio.core.model.Money
import com.financio.core.usecase.DetectedSubscription
import com.financio.core.usecase.PriceChange
import com.financio.core.usecase.SubscriptionCadence
import com.financio.core.usecase.UncertainSubscription
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun SubscriptionsScreen(onBackClick: () -> Unit, viewModel: SubscriptionsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var showCalendar by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            VasteLastenHeader(onBackClick)

            if (!state.loaded) return@Scaffold

            val nothingDetected = state.dueThisMonth.isEmpty() && state.upcomingLater.isEmpty() &&
                state.uncertain.isEmpty() && state.manuallyConfirmed.isEmpty()
            if (nothingDetected) {
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    Text("Nog geen vaste lasten herkend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Zodra een tegenpartij minstens twee of drie keer met een vergelijkbaar bedrag " +
                            "rond dezelfde datum afschrijft, verschijnt die hier automatisch — geen " +
                            "bankkoppeling nodig, alleen je eigen transactiehistorie.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    AddSubscriptionButton(enabled = state.addableNames.isNotEmpty(), onClick = { showAddDialog = true }, modifier = Modifier.padding(top = 16.dp))
                }
                return@Scaffold
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                item {
                    VasteLastenHero(
                        state = state,
                        showCalendar = showCalendar,
                        onToggleCalendar = { showCalendar = !showCalendar },
                    )
                }

                if (showCalendar) {
                    item { SubscriptionCalendar(state.dueThisMonth, onDismiss = viewModel::dismiss) }
                }

                if (state.dueThisMonth.isNotEmpty()) {
                    item { SectionLabel("KOMT NOG") }
                    itemsIndexed(state.dueThisMonth) { index, subscription ->
                        if (index > 0) androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        TimelineRow(subscription, onDismiss = { viewModel.dismiss(subscription.counterpartyName) })
                    }
                }

                if (state.upcomingLater.isNotEmpty()) {
                    item { SectionLabel("LATER DIT JAAR", topPadding = 24.dp) }
                    items(state.upcomingLater, key = { "later-${it.counterpartyName}" }) { subscription ->
                        LaterRow(subscription, onDismiss = { viewModel.dismiss(subscription.counterpartyName) })
                    }
                }

                if (state.manuallyConfirmed.isNotEmpty()) {
                    item { SectionLabel("HANDMATIG BEVESTIGD", topPadding = 24.dp) }
                    items(state.manuallyConfirmed, key = { "manual-${it.counterpartyName}" }) { candidate ->
                        ManuallyConfirmedRow(candidate, onDismiss = { viewModel.dismiss(candidate.counterpartyName) })
                    }
                }

                // Only ever one at a time - the same "don't ask everything at once" restraint the
                // overview's own "Opvallend" tip and Inzicht's merge suggestion use.
                state.uncertain.firstOrNull()?.let { candidate ->
                    item {
                        UncertainCard(
                            candidate = candidate,
                            onConfirm = { viewModel.confirm(candidate.counterpartyName) },
                            onDismiss = { viewModel.dismiss(candidate.counterpartyName) },
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                }

                item { AddSubscriptionButton(enabled = state.addableNames.isNotEmpty(), onClick = { showAddDialog = true }, modifier = Modifier.padding(top = 24.dp, bottom = 24.dp)) }
            }
        }
    }

    if (showAddDialog) {
        AddSubscriptionDialog(
            candidates = state.addableNames,
            onDismiss = { showAddDialog = false },
            onPick = { name -> viewModel.confirm(name); showAddDialog = false },
        )
    }
}

/** "← Vaste lasten" — the redesign's plain header convention (see ImportTopBar/CategorizeGameHeader). */
@Composable
private fun VasteLastenHeader(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
        ) { Icon(Icons.Filled.ArrowBack, contentDescription = "Terug") }
        Text("Vaste lasten", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SectionLabel(text: String, topPadding: Dp = 8.dp) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = LocalFinancioColors.current.inkFaint,
        modifier = Modifier.padding(top = topPadding, bottom = 10.dp),
    )
}

private fun Money.magnitudeString(): String = toDisplayString().removePrefix("-").removePrefix("€")

@Composable
private fun VasteLastenHero(state: SubscriptionsUiState, showCalendar: Boolean, onToggleCalendar: () -> Unit) {
    val totalThisMonth = state.alreadyBilledThisMonthCount + state.dueThisMonth.size
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp)) {
        Text("Nog te gaan deze maand", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            state.dueThisMonthTotal.toDisplayString(),
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (totalThisMonth > 0) {
            Text(
                "${state.alreadyBilledThisMonthCount} van $totalThisMonth afschrijvingen zijn al geweest",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            if (showCalendar) "Lijstweergave" else "Kalenderweergave",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 10.dp).clickable(onClick = onToggleCalendar),
        )
    }
}

/** "18 sep, Vattenfall, maandelijks, − 118,45" — the timeline row for a still-due subscription, with an amberSoft pill under the name when its price just rose. */
@Composable
private fun TimelineRow(subscription: DetectedSubscription, onDismiss: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Column(Modifier.width(44.dp)) {
            Text(
                subscription.estimatedNextDate.dayOfMonth.toString(),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                subscription.estimatedNextDate.month.getDisplayName(TextStyle.SHORT, Locale("nl")).replace(".", ""),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(subscription.counterpartyName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(cadenceLabel(subscription.cadence), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            subscription.priceChange?.let { change -> PriceIncreasePill(change) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(subscription.lastAmount.toSignedMagnitudeString(), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(
                "verwijderen",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp).clickable(onClick = onDismiss),
            )
        }
    }
}

private fun cadenceLabel(cadence: SubscriptionCadence): String = when (cadence) {
    SubscriptionCadence.MONTHLY -> "maandelijks"
    SubscriptionCadence.YEARLY -> "jaarlijks"
}

@Composable
private fun PriceIncreasePill(change: PriceChange) {
    Text(
        "prijs omhoog: ${change.previousAmount.magnitudeString()} → ${change.newAmount.magnitudeString()}",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** "nov, Domeinnaam, jaarlijks, − 14,95" as one compact line - no cadans/date column, since these are far enough out that the exact day doesn't matter yet. */
@Composable
private fun LaterRow(subscription: DetectedSubscription, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            subscription.estimatedNextDate.month.getDisplayName(TextStyle.SHORT, Locale("nl")).replace(".", ""),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp),
        )
        Text(subscription.counterpartyName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(subscription.lastAmount.toSignedMagnitudeString(), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 15.sp)
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Verwijder ${subscription.counterpartyName} als vaste last")
        }
    }
}

@Composable
private fun ManuallyConfirmedRow(candidate: UncertainSubscription, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(candidate.counterpartyName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                "${candidate.occurrences}× gezien · laatst ${candidate.lastDate.toShortDisplayString()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(candidate.lastAmount.toSignedMagnitudeString(), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Verwijder ${candidate.counterpartyName} als vaste last")
        }
    }
}

/**
 * "Twijfelgeval" — a yes/no confirmation instead of either silently listing it as a confirmed
 * subscription (it might not be one) or silently dropping it (it might well be one, just with
 * amounts or timing a bit less regular than [com.financio.core.usecase.SubscriptionDetector]'s
 * strict bar). Only the single most relevant one is ever shown at once (see the call site).
 */
@Composable
private fun UncertainCard(candidate: UncertainSubscription, onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) {
        Text("Is ${candidate.counterpartyName} een vaste last?", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "${candidate.occurrences} keer · steeds ± ${candidate.lastAmount.magnitudeString()} · ${candidate.reason}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(modifier = Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onConfirm)
                    .padding(horizontal = 28.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Ja", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 28.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Nee", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun AddSubscriptionButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dashColor = if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = dashColor,
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                )
            }
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Vaste last toevoegen",
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * A month grid for the current calendar month with a dot on every day something in
 * [dueThisMonth] is expected to be charged — tapping a marked day shows which one(s) below the
 * grid. Deliberately just this month, not navigable: [dueThisMonth] itself is only ever "still to
 * come this real calendar month" (see SubscriptionsViewModel), so a different month has nothing
 * of its own to show here without a bigger change to how that split works.
 */
@Composable
private fun SubscriptionCalendar(dueThisMonth: List<DetectedSubscription>, onDismiss: (String) -> Unit) {
    val month = YearMonth.now()
    val byDay = dueThisMonth.groupBy { it.estimatedNextDate.dayOfMonth }
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    // Monday-first grid, matching the Ma/Di/Wo/.../Zo header below.
    val leadingBlanks = month.atDay(1).dayOfWeek.value - 1
    val totalCells = leadingBlanks + month.lengthOfMonth()
    val rowCount = (totalCells + 6) / 7
    val today = LocalDate.now()

    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("Ma", "Di", "Wo", "Do", "Vr", "Za", "Zo").forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        for (row in 0 until rowCount) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                for (col in 0 until 7) {
                    val day = row * 7 + col - leadingBlanks + 1
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (day in 1..month.lengthOfMonth()) {
                            CalendarDayCell(
                                day = day,
                                isToday = day == today.dayOfMonth,
                                isSelected = day == selectedDay,
                                subscriptionCount = byDay[day]?.size ?: 0,
                                onClick = { selectedDay = if (selectedDay == day) null else day },
                            )
                        }
                    }
                }
            }
        }
        selectedDay?.let { day ->
            byDay[day]?.let { subscriptions ->
                Column(Modifier.padding(top = 12.dp)) {
                    subscriptions.forEachIndexed { index, subscription ->
                        if (index > 0) androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        TimelineRow(subscription, onDismiss = { onDismiss(subscription.counterpartyName) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(day: Int, isToday: Boolean, isSelected: Boolean, subscriptionCount: Int, onClick: () -> Unit) {
    val hasSubscription = subscriptionCount > 0
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(2.dp)
            .clip(CircleShape)
            .then(
                when {
                    isSelected -> Modifier.background(MaterialTheme.colorScheme.primary)
                    isToday -> Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    else -> Modifier
                },
            )
            .clickable(enabled = hasSubscription, onClick = onClick)
            .padding(top = 6.dp),
    ) {
        Text(
            day.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (hasSubscription) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
        if (hasSubscription) {
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/** A searchable, tap-to-pick list of debit counterparties not already shown as a subscription - "vaste last toevoegen" for a merchant [com.financio.core.usecase.SubscriptionDetector] never flagged on its own. */
@Composable
private fun AddSubscriptionDialog(candidates: List<String>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(candidates, query) { candidates.filter { it.contains(query, ignoreCase = true) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vaste last toevoegen") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Zoeken…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (filtered.isEmpty()) {
                    Text(
                        "Niets gevonden.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp).padding(top = 8.dp)) {
                        items(filtered, key = { it }) { name ->
                            Text(
                                name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(name) }
                                    .padding(vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}
