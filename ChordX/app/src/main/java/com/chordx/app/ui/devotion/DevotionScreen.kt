package com.chordx.app.ui.devotion

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chordx.app.data.DailyVerse
import com.chordx.app.data.DailyVerses
import com.chordx.app.data.Devotion
import com.chordx.app.data.DevotionDao
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The SOAP journal for one day at a time. Edits are saved automatically shortly after typing stops. */
class DevotionViewModel(private val dao: DevotionDao, private val today: () -> LocalDate = { LocalDate.now() }) : ViewModel() {
    var date by mutableStateOf(today())
        private set
    var scripture by mutableStateOf("")
        private set
    var observation by mutableStateOf("")
        private set
    var application by mutableStateOf("")
        private set
    var prayer by mutableStateOf("")
        private set
    var loaded by mutableStateOf(false)
        private set
    var saved by mutableStateOf(false)
        private set

    private var saveJob: Job? = null

    init { viewModelScope.launch { load() } }

    private suspend fun load() {
        val entry = dao.getByDate(date.toString())
        scripture = entry?.scripture.orEmpty()
        observation = entry?.observation.orEmpty()
        application = entry?.application.orEmpty()
        prayer = entry?.prayer.orEmpty()
        saved = entry != null
        loaded = true
    }

    fun edit(scripture: String? = null, observation: String? = null, application: String? = null, prayer: String? = null) {
        if (!loaded) return
        scripture?.let { this.scripture = it }
        observation?.let { this.observation = it }
        application?.let { this.application = it }
        prayer?.let { this.prayer = it }
        saved = false
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(600); persist() }
    }

    fun goTo(newDate: LocalDate) {
        if (newDate == date || newDate.isAfter(today())) return
        saveJob?.cancel()
        viewModelScope.launch {
            if (loaded) persist()
            loaded = false
            date = newDate
            load()
        }
    }

    private suspend fun persist() = withContext(NonCancellable) {
        val key = date.toString()
        if (listOf(scripture, observation, application, prayer).all { it.isBlank() }) {
            dao.deleteByDate(key)
        } else {
            dao.upsert(Devotion(date = key, scripture = scripture, observation = observation, application = application, prayer = prayer))
        }
        saved = true
    }

    companion object {
        fun factory(dao: DevotionDao): ViewModelProvider.Factory = viewModelFactory {
            initializer { DevotionViewModel(dao) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevotionScreen(viewModel: DevotionViewModel, onOpenWeek: () -> Unit, bottomBar: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val date = viewModel.date
    val verse = remember(date) { DailyVerses.forDate(date) }
    val today = LocalDate.now()
    var linkFailed by remember { mutableStateOf(false) }
    val dateLabel = remember(date) {
        when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        }
    }
    LaunchedEffect(date) { linkFailed = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily devotion") },
                actions = {
                    TextButton(onClick = onOpenWeek, modifier = Modifier.testTag("weekly_summary")) { Text("Weekly summary") }
                },
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { viewModel.goTo(date.minusDays(1)) }, modifier = Modifier.testTag("devotion_prev")) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(dateLabel, style = MaterialTheme.typography.titleMedium)
                    Text(
                        date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { viewModel.goTo(date.plusDays(1)) }, enabled = date.isBefore(today), modifier = Modifier.testTag("devotion_next")) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                }
            }

            VerseCard(verse)
            TextButton(
                onClick = {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.biblegateway.com/passage/?search=${Uri.encode(verse.reference)}&version=NIV")),
                        )
                    } catch (_: ActivityNotFoundException) {
                        linkFailed = true
                    }
                },
            ) { Text("Read ${verse.reference} in NIV") }
            if (linkFailed) Text("No app can open the link.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("SOAP journal", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (viewModel.saved) "Saved" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = { viewModel.edit(scripture = "${verse.reference} – ${verse.text}") },
                    enabled = viewModel.loaded,
                    modifier = Modifier.testTag("use_verse"),
                ) { Text("Use the verse of the day") }
            }
            SoapField("Scripture", "Which passage are you reading?", viewModel.scripture, "soap_scripture", 3) { viewModel.edit(scripture = it) }
            SoapField("Observation", "What does it say? What stands out?", viewModel.observation, "soap_observation", 4) { viewModel.edit(observation = it) }
            SoapField("Application", "What will you do about it today?", viewModel.application, "soap_application", 4) { viewModel.edit(application = it) }
            SoapField("Prayer", "Talk to God about what you have read.", viewModel.prayer, "soap_prayer", 4) { viewModel.edit(prayer = it) }
        }
    }
}

@Composable
private fun VerseCard(verse: DailyVerse) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Verse of the day", style = MaterialTheme.typography.labelMedium)
            Text("“${verse.text}”", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
            Text("${verse.reference} (KJV)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Insight", style = MaterialTheme.typography.labelMedium)
            Text(verse.insight, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SoapField(label: String, hint: String, value: String, tag: String, minLines: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = { Text(hint) },
        minLines = minLines,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}
