package com.chordx.app.ui.devotion

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chordx.app.data.DailyVerses
import com.chordx.app.data.Devotion
import com.chordx.app.data.DevotionDao
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

private fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** A Monday-to-Sunday review of the SOAP journal. */
class DevotionWeekViewModel(private val dao: DevotionDao, private val today: () -> LocalDate = { LocalDate.now() }) : ViewModel() {
    var weekStart by mutableStateOf(mondayOf(today()))
        private set
    var entries by mutableStateOf<Map<LocalDate, Devotion>>(emptyMap())
        private set

    val days: List<LocalDate> get() = (0L..6L).map { weekStart.plusDays(it) }
    val canGoForward: Boolean get() = weekStart.plusWeeks(1) <= today()

    init { load() }

    fun shift(weeks: Long) {
        val next = weekStart.plusWeeks(weeks)
        if (next > today()) return
        weekStart = next
        load()
    }

    private fun load() {
        val start = weekStart
        viewModelScope.launch {
            val found = dao.getBetween(start.toString(), start.plusDays(6).toString())
            if (start == weekStart) entries = found.associateBy { LocalDate.parse(it.date) }
        }
    }

    companion object {
        fun factory(dao: DevotionDao): ViewModelProvider.Factory = viewModelFactory {
            initializer { DevotionWeekViewModel(dao) }
        }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMM")
private val rangeFormat = DateTimeFormatter.ofPattern("d MMM")

private fun weekRange(start: LocalDate): String =
    "${start.format(rangeFormat)} – ${start.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"

private fun sections(entry: Devotion): List<Pair<String, String>> = listOf(
    "Scripture" to entry.scripture,
    "Observation" to entry.observation,
    "Application" to entry.application,
    "Prayer" to entry.prayer,
).filter { it.second.isNotBlank() }

/** The week as plain text, for sharing or pasting elsewhere. */
internal fun weekAsText(days: List<LocalDate>, entries: Map<LocalDate, Devotion>, today: LocalDate): String = buildString {
    appendLine("Devotion summary, ${weekRange(days.first())}")
    appendLine("Journaled ${entries.size} of ${days.count { it <= today }} days")
    days.filter { it <= today }.forEach { day ->
        appendLine()
        appendLine("${day.format(dayFormat)} - ${DailyVerses.forDate(day).reference}")
        val entry = entries[day]
        if (entry == null) appendLine("No entry") else sections(entry).forEach { (label, text) -> appendLine("$label: ${text.trim()}") }
    }
}.trimEnd()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevotionWeekScreen(viewModel: DevotionWeekViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val entries = viewModel.entries
    val elapsed = viewModel.days.count { it <= today }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weekly summary") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Devotion summary, ${weekRange(viewModel.weekStart)}")
                                putExtra(Intent.EXTRA_TEXT, weekAsText(viewModel.days, entries, today))
                            }
                            context.startActivity(Intent.createChooser(send, "Share weekly summary"))
                        },
                        modifier = Modifier.testTag("share_week"),
                    ) { Icon(Icons.Default.Share, contentDescription = "Share weekly summary") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { viewModel.shift(-1) }, modifier = Modifier.testTag("week_prev")) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous week")
                }
                Text(weekRange(viewModel.weekStart), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { viewModel.shift(1) }, enabled = viewModel.canGoForward, modifier = Modifier.testTag("week_next")) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next week")
                }
            }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Journaled ${entries.size} of $elapsed ${if (elapsed == 1) "day" else "days"}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        viewModel.days.joinToString("  ") { day ->
                            "${day.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${if (entries.containsKey(day)) "✓" else "–"}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            viewModel.days.filter { it <= today }.forEach { day -> DayCard(day, entries[day]) }
        }
    }
}

@Composable
private fun DayCard(day: LocalDate, entry: Devotion?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(day.format(dayFormat), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(DailyVerses.forDate(day).reference, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            if (entry == null) {
                Text("No entry", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                sections(entry).forEach { (label, text) ->
                    Column {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text.trim(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
