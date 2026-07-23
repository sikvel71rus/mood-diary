package com.mooddiary

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private const val PREFS_NAME = "mood_diary"
private const val KEY_ENTRIES = "entries"
private const val KEY_DISCLAIMER_SHOWN = "disclaimer_shown"
private const val KEY_FEEDBACK_USEFUL = "feedback_useful"
private const val KEY_FEEDBACK_NOT_USEFUL = "feedback_not_useful"
private const val KEY_FEEDBACK_SKIPPED = "feedback_skipped"
private const val KEY_INSIGHT_OPENED = "insight_opened"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MoodDiaryApp(
                store = LocalMoodStore(
                    prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                    context = this,
                ),
            )
        }
    }
}

private enum class Screen {
    Onboarding,
    Home,
    CheckIn,
    History,
    Insight,
    Settings,
}

private enum class InsightStatus {
    NotEnoughData,
    Waiting,
    Found,
    NoClearPattern,
}

private enum class InsightSubtype {
    Contrast,
    StableLow,
    StableHigh,
    NoPolarity,
    NoCorrelation,
    Intermediate,
}

private enum class ObservationScale {
    Energy,
    Anxiety,
    Stress,
}

private enum class ObservationDirection {
    BetterSleepHigher,
    BetterSleepLower,
    Stable,
}

private data class Tag(
    val id: String,
    val label: String,
    val category: String,
)

private data class Entry(
    val id: Long,
    val date: LocalDate,
    val mood: Int,
    val energy: Int,
    val anxiety: Int,
    val stress: Int,
    val sleep: Int,
    val tags: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
)

private data class SecondaryObservation(
    val scale: ObservationScale,
    val direction: ObservationDirection,
    val score: Double,
)

private data class Insight(
    val status: InsightStatus,
    val subtype: InsightSubtype?,
    val message: String,
    val confidenceLabel: String?,
    val entriesUsedCount: Int,
    val secondaryObservations: List<SecondaryObservation> = emptyList(),
)

private data class DraftEntry(
    val id: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val mood: Int? = null,
    val energy: Int? = null,
    val anxiety: Int? = null,
    val stress: Int? = null,
    val sleep: Int? = null,
    val tags: Set<String> = emptySet(),
    val createdAt: Long? = null,
)

private val Tags = listOf(
    Tag("work", "Работа", "Нагрузка"),
    Tag("deadline", "Дедлайн", "Нагрузка"),
    Tag("conflict", "Конфликт", "Социальное"),
    Tag("meeting", "Много встреч", "Нагрузка"),
    Tag("rest", "Отдых", "Восстановление"),
    Tag("walk", "Прогулка", "Восстановление"),
    Tag("sport", "Спорт", "Активность"),
    Tag("social", "Общение", "Социальное"),
    Tag("family", "Семья", "Социальное"),
    Tag("health", "Самочувствие", "Тело"),
    Tag("screen_time", "Много экрана", "Контекст"),
    Tag("chores", "Бытовые дела", "Контекст"),
)

private object MoodColors {
    val Background = Color(0xFFF8F7F3)
    val Surface = Color(0xFFFFFFFF)
    val Text = Color(0xFF22242A)
    val Muted = Color(0xFF666A73)
    val Accent = Color(0xFF4F46E5)
    val Recommendation = Color(0xFFF1E6D2)

    val Mood = mapOf(
        5 to Color(0xFF8EB69B),
        4 to Color(0xFF7EC9C3),
        3 to Color(0xFFD7C4A3),
        2 to Color(0xFFB8A8D9),
        1 to Color(0xFF6E7CA8),
    )

    val Sleep = mapOf(
        5 to Color(0xFFA8D8F0),
        4 to Color(0xFF84BDE8),
        3 to Color(0xFF6E99C8),
        2 to Color(0xFF5E78A3),
        1 to Color(0xFF485C80),
    )

    val Energy = Color(0xFFC8A84B)
    val Anxiety = Color(0xFFC0A8B8)
    val Stress = Color(0xFF9890C4)
}

private class LocalMoodStore(
    private val prefs: SharedPreferences,
    private val context: Context,
) {
    fun disclaimerShown(): Boolean = prefs.getBoolean(KEY_DISCLAIMER_SHOWN, false)

    fun markDisclaimerShown() {
        prefs.edit().putBoolean(KEY_DISCLAIMER_SHOWN, true).apply()
    }

    fun entries(): List<Entry> {
        val raw = prefs.getString(KEY_ENTRIES, "[]") ?: "[]"
        val json = JSONArray(raw)
        return (0 until json.length()).map { index ->
            val item = json.getJSONObject(index)
            val tagsJson = item.optJSONArray("tags") ?: JSONArray()
            Entry(
                id = item.getLong("id"),
                date = LocalDate.parse(item.getString("date")),
                mood = item.getInt("mood"),
                energy = item.getInt("energy"),
                anxiety = item.getInt("anxiety"),
                stress = item.getInt("stress"),
                sleep = item.getInt("sleep"),
                tags = (0 until tagsJson.length()).map { tagsJson.getString(it) },
                createdAt = item.getLong("createdAt"),
                updatedAt = item.getLong("updatedAt"),
            )
        }.sortedByDescending { it.date }
    }

    fun save(draft: DraftEntry) {
        val existing = entries()
        val now = System.currentTimeMillis()
        val sameDate = existing.firstOrNull { it.date == draft.date }
        val id = draft.id ?: sameDate?.id ?: now
        val createdAt = draft.createdAt ?: sameDate?.createdAt ?: now
        val entry = Entry(
            id = id,
            date = draft.date,
            mood = requireNotNull(draft.mood),
            energy = requireNotNull(draft.energy),
            anxiety = requireNotNull(draft.anxiety),
            stress = requireNotNull(draft.stress),
            sleep = requireNotNull(draft.sleep),
            tags = draft.tags.sorted(),
            createdAt = createdAt,
            updatedAt = now,
        )
        val next = existing.filterNot { it.id == id || it.date == draft.date } + entry
        saveEntries(next)
    }

    fun deleteEntry(id: Long) {
        saveEntries(entries().filterNot { it.id == id })
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        context.cacheDir.resolve("exports").deleteRecursively()
    }

    fun saveFeedback(value: String) {
        val key = when (value) {
            "useful" -> KEY_FEEDBACK_USEFUL
            "not_useful" -> KEY_FEEDBACK_NOT_USEFUL
            else -> KEY_FEEDBACK_SKIPPED
        }
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    fun logInsightOpened() {
        prefs.edit().putInt(KEY_INSIGHT_OPENED, prefs.getInt(KEY_INSIGHT_OPENED, 0) + 1).apply()
    }

    fun shareStats() {
        val allEntries = entries()
        val since = LocalDate.now().minusDays(13)
        val stats = JSONObject()
            .put("exportedAt", Instant.now().toString())
            .put("appVersion", "1.0")
            .put(
                "metrics",
                JSONObject()
                    .put("totalEntries", allEntries.size)
                    .put("entriesLast14Days", allEntries.count { !it.date.isBefore(since) })
                    .put("reached5PlusEntries", allEntries.size >= 5)
                    .put("insightScreenOpenedCount", prefs.getInt(KEY_INSIGHT_OPENED, 0))
                    .put(
                        "insightFeedback",
                        JSONObject()
                            .put("useful", prefs.getInt(KEY_FEEDBACK_USEFUL, 0))
                            .put("not_useful", prefs.getInt(KEY_FEEDBACK_NOT_USEFUL, 0))
                            .put("skipped", prefs.getInt(KEY_FEEDBACK_SKIPPED, 0)),
                    ),
            )
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "mood_diary_stats.json").apply {
            writeText(stats.toString(2))
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Экспорт статистики"))
    }

    private fun saveEntries(entries: List<Entry>) {
        val json = JSONArray()
        entries.sortedByDescending { it.date }.forEach { entry ->
            json.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("date", entry.date.toString())
                    .put("mood", entry.mood)
                    .put("energy", entry.energy)
                    .put("anxiety", entry.anxiety)
                    .put("stress", entry.stress)
                    .put("sleep", entry.sleep)
                    .put("tags", JSONArray(entry.tags))
                    .put("createdAt", entry.createdAt)
                    .put("updatedAt", entry.updatedAt),
            )
        }
        prefs.edit().putString(KEY_ENTRIES, json.toString()).apply()
    }
}

@Composable
private fun MoodDiaryApp(store: LocalMoodStore) {
    var entries by remember { mutableStateOf(store.entries()) }
    var screen by remember {
        mutableStateOf(
            if (!store.disclaimerShown() && entries.isEmpty()) Screen.Onboarding else Screen.Home,
        )
    }
    var editingEntry by remember { mutableStateOf<Entry?>(null) }
    var selectedHistoryEntry by remember { mutableStateOf<Entry?>(null) }

    fun refresh() {
        entries = store.entries()
    }

    MoodDiaryTheme {
        when (screen) {
            Screen.Onboarding -> OnboardingScreen(
                onStart = {
                    store.markDisclaimerShown()
                    editingEntry = entries.firstOrNull { it.date == LocalDate.now() }
                    screen = Screen.CheckIn
                },
            )

            Screen.Home -> HomeScreen(
                entries = entries,
                onCheckIn = {
                    editingEntry = entries.firstOrNull { it.date == LocalDate.now() }
                    screen = Screen.CheckIn
                },
                onHistory = { screen = Screen.History },
                onInsight = {
                    store.logInsightOpened()
                    screen = Screen.Insight
                },
                onSettings = { screen = Screen.Settings },
            )

            Screen.CheckIn -> CheckInScreen(
                entry = editingEntry,
                onSave = {
                    store.save(it)
                    refresh()
                    editingEntry = null
                    screen = Screen.Home
                },
                onBack = {
                    editingEntry = null
                    screen = Screen.Home
                },
            )

            Screen.History -> HistoryScreen(
                entries = entries,
                selectedEntry = selectedHistoryEntry,
                onSelect = { selectedHistoryEntry = it },
                onCreate = {
                    editingEntry = entries.firstOrNull { entry -> entry.date == LocalDate.now() }
                    selectedHistoryEntry = null
                    screen = Screen.CheckIn
                },
                onEdit = {
                    editingEntry = it
                    selectedHistoryEntry = null
                    screen = Screen.CheckIn
                },
                onDelete = {
                    store.deleteEntry(it.id)
                    refresh()
                    selectedHistoryEntry = null
                },
                onBack = {
                    selectedHistoryEntry = null
                    screen = Screen.Home
                },
            )

            Screen.Insight -> InsightScreen(
                insight = calculateInsight(entries),
                recommendation = recommendationFor(calculateInsight(entries)),
                onFeedback = {
                    store.saveFeedback(it)
                },
                onBack = { screen = Screen.Home },
            )

            Screen.Settings -> SettingsScreen(
                onBack = { screen = Screen.Home },
                onExport = { store.shareStats() },
                onDeleteAll = {
                    store.clearAll()
                    refresh()
                    selectedHistoryEntry = null
                    editingEntry = null
                    screen = Screen.Onboarding
                },
            )
        }
    }
}

@Composable
private fun MoodDiaryTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MoodColors.Background,
            content = content,
        )
    }
}

@Composable
private fun OnboardingScreen(onStart: () -> Unit) {
    Scaffold(
        containerColor = MoodColors.Background,
        bottomBar = {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                onClick = onStart,
            ) {
                Text("Начать")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Mood Diary", fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Text(
                "Быстрый дневник настроения: 5 коротких оценок, теги дня и первые бережные наблюдения о связи сна и настроения.",
                fontSize = 17.sp,
                lineHeight = 24.sp,
            )
            Spacer(Modifier.height(20.dp))
            InfoCard("Не диагноз и не замена специалиста. Приложение помогает наблюдать за состоянием, но не делает медицинских выводов.")
            Spacer(Modifier.height(12.dp))
            InfoCard("Данные хранятся только на устройстве. Регистрация и интернет для P0 не нужны.")
        }
    }
}

@Composable
private fun HomeScreen(
    entries: List<Entry>,
    onCheckIn: () -> Unit,
    onHistory: () -> Unit,
    onInsight: () -> Unit,
    onSettings: () -> Unit,
) {
    val todayEntry = entries.firstOrNull { it.date == LocalDate.now() }
    val insight = calculateInsight(entries)
    Scaffold(
        containerColor = MoodColors.Background,
        bottomBar = {
            BottomNav(onHome = {}, onHistory = onHistory, onSettings = onSettings)
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("Mood Diary", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Сегодня ${formatDate(LocalDate.now())}", color = MoodColors.Muted)
            }
            item {
                Button(modifier = Modifier.fillMaxWidth(), onClick = onCheckIn) {
                    Text(if (todayEntry == null) "Отметить состояние" else "Редактировать запись сегодня")
                }
            }
            item {
                ProgressBlock(insight = insight, entryCount = entries.size)
            }
            item {
                InsightCard(insight = insight, onOpen = onInsight)
            }
            item {
                MiniChart(entries = entries)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun CheckInScreen(
    entry: Entry?,
    onSave: (DraftEntry) -> Unit,
    onBack: () -> Unit,
) {
    var mood by remember(entry) { mutableStateOf(entry?.mood) }
    var energy by remember(entry) { mutableStateOf(entry?.energy) }
    var anxiety by remember(entry) { mutableStateOf(entry?.anxiety) }
    var stress by remember(entry) { mutableStateOf(entry?.stress) }
    var sleep by remember(entry) { mutableStateOf(entry?.sleep) }
    var selectedTags by remember(entry) { mutableStateOf(entry?.tags?.toSet() ?: emptySet()) }
    val canSave = listOf(mood, energy, anxiety, stress, sleep).all { it != null }
    Scaffold(
        containerColor = MoodColors.Background,
        topBar = { TopBar(title = if (entry == null) "Check-in" else "Редактирование", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            RatingRow("Настроение", mood, ScaleKind.Mood) { mood = it }
            RatingRow("Энергия", energy, ScaleKind.Energy) { energy = it }
            RatingRow("Тревожность", anxiety, ScaleKind.Anxiety) { anxiety = it }
            RatingRow("Стресс", stress, ScaleKind.Stress) { stress = it }
            RatingRow("Сон", sleep, ScaleKind.Sleep) { sleep = it }
            Text("Контекст дня", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Tags.forEach { tag ->
                    TagChip(
                        tag = tag,
                        selected = tag.id in selectedTags,
                        onToggle = {
                            selectedTags = if (tag.id in selectedTags) {
                                selectedTags - tag.id
                            } else {
                                selectedTags + tag.id
                            }
                        },
                    )
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = canSave,
                onClick = {
                    onSave(
                        DraftEntry(
                            id = entry?.id,
                            date = entry?.date ?: LocalDate.now(),
                            mood = mood,
                            energy = energy,
                            anxiety = anxiety,
                            stress = stress,
                            sleep = sleep,
                            tags = selectedTags,
                            createdAt = entry?.createdAt,
                        ),
                    )
                },
            ) {
                Text("Сохранить")
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    entries: List<Entry>,
    selectedEntry: Entry?,
    onSelect: (Entry) -> Unit,
    onCreate: () -> Unit,
    onEdit: (Entry) -> Unit,
    onDelete: (Entry) -> Unit,
    onBack: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Entry?>(null) }
    Scaffold(
        containerColor = MoodColors.Background,
        topBar = { TopBar("История", onBack) },
    ) { padding ->
        if (entries.isEmpty()) {
            EmptyState(
                modifier = Modifier.padding(padding),
                text = "Записей пока нет. Сделайте первую запись.",
                actionLabel = "Создать запись",
                onAction = onCreate,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(entries.take(14), key = { it.id }) { entry ->
                    HistoryItem(
                        entry = entry,
                        selected = selectedEntry?.id == entry.id,
                        onClick = { onSelect(entry) },
                    )
                    if (selectedEntry?.id == entry.id) {
                        EntryDetails(
                            entry = entry,
                            onEdit = { onEdit(entry) },
                            onDelete = { pendingDelete = entry },
                        )
                    }
                }
            }
        }
    }
    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить запись?") },
            text = { Text("Запись за ${formatDate(entry.date)} исчезнет из истории, графика и инсайтов.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(entry)
                        pendingDelete = null
                    },
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun InsightScreen(
    insight: Insight,
    recommendation: String?,
    onFeedback: (String) -> Unit,
    onBack: () -> Unit,
) {
    var feedbackDone by remember(insight.status, insight.subtype, insight.entriesUsedCount) { mutableStateOf<String?>(null) }
    Scaffold(
        containerColor = MoodColors.Background,
        topBar = { TopBar("Инсайт", onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            InfoCard(insight.message)
            Text("Использовано записей: ${insight.entriesUsedCount}", color = MoodColors.Muted)
            insight.confidenceLabel?.let {
                Text("Уверенность: $it", color = MoodColors.Muted)
            }
            insight.secondaryObservations.firstOrNull()?.let {
                InfoCard(secondaryObservationText(it))
            }
            recommendation?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MoodColors.Recommendation)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Маленькое действие", fontWeight = FontWeight.SemiBold)
                        Text(it)
                    }
                }
            }
            if (insight.status == InsightStatus.Found && feedbackDone == null) {
                FeedbackRow(
                    onFeedback = { value ->
                        if (value != "skipped") {
                            onFeedback(value)
                            feedbackDone = value
                        } else {
                            onFeedback(value)
                            feedbackDone = "skipped"
                        }
                    },
                )
            }
            when (feedbackDone) {
                "useful" -> Text("Хорошо — наблюдения становятся точнее с каждой записью.")
                "not_useful" -> Text("Понятно. Продолжай отмечать — картина будет меняться.")
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    onBack: () -> Unit,
    onExport: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MoodColors.Background,
        topBar = { TopBar("Настройки", onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Доверие и данные", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            InfoCard("Mood Diary не ставит диагнозы и не заменяет специалиста. Рекомендации — это бережные идеи для самонаблюдения.")
            InfoCard("P0 работает офлайн: записи, оценки инсайтов и счётчики статистики хранятся только на устройстве.")
            Button(modifier = Modifier.fillMaxWidth(), onClick = onExport) {
                Text("Экспорт статистики для команды")
            }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { confirmDelete = true }) {
                Text("Удалить все данные приложения")
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить все данные?") },
            text = { Text("Будут удалены записи, оценки инсайтов, локальные настройки и временные файлы экспорта.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDeleteAll()
                    },
                ) {
                    Text("Удалить всё")
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

private enum class ScaleKind {
    Mood,
    Energy,
    Anxiety,
    Stress,
    Sleep,
}

@Composable
private fun RatingRow(
    label: String,
    selected: Int?,
    kind: ScaleKind,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            (1..5).forEach { value ->
                val color = when (kind) {
                    ScaleKind.Mood -> MoodColors.Mood[value] ?: MoodColors.Accent
                    ScaleKind.Sleep -> MoodColors.Sleep[value] ?: MoodColors.Accent
                    ScaleKind.Energy -> MoodColors.Energy
                    ScaleKind.Anxiety -> MoodColors.Anxiety
                    ScaleKind.Stress -> MoodColors.Stress
                }
                val selectedModifier = if (selected == value) {
                    Modifier.border(2.dp, MoodColors.Accent, CircleShape)
                } else {
                    Modifier
                }
                Column(
                    modifier = Modifier.width(58.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (selected == value) 48.dp else 42.dp)
                            .then(selectedModifier)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.34f))
                            .clickable { onSelect(value) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(iconFor(kind, value), fontSize = 18.sp)
                    }
                    Text(value.toString(), fontSize = 12.sp, color = MoodColors.Muted)
                }
            }
        }
    }
}

@Composable
private fun TagChip(tag: Tag, selected: Boolean, onToggle: () -> Unit) {
    val background = if (selected) Color(0xFFE8E7FF) else MoodColors.Surface
    val border = if (selected) MoodColors.Accent else Color(0xFFD8D7D2)
    Text(
        text = tag.label,
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .border(1.dp, border, RoundedCornerShape(100.dp))
            .background(background)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        fontSize = 14.sp,
    )
}

@Composable
private fun ProgressBlock(insight: Insight, entryCount: Int) {
    val progress = when {
        entryCount < 3 -> entryCount / 3f
        entryCount < 5 && insight.status == InsightStatus.Waiting -> entryCount / 5f
        else -> return
    }
    Card(colors = CardDefaults.cardColors(containerColor = MoodColors.Surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text(insight.message)
        }
    }
}

@Composable
private fun InsightCard(insight: Insight, onOpen: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MoodColors.Surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Инсайт", fontWeight = FontWeight.SemiBold)
            Text(insight.message)
            insight.confidenceLabel?.let { Text("Уверенность: $it", color = MoodColors.Muted, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun MiniChart(entries: List<Entry>) {
    val today = LocalDate.now()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val byDate = entries.associateBy { it.date }
    Card(colors = CardDefaults.cardColors(containerColor = MoodColors.Surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Динамика за 7 дней", fontWeight = FontWeight.SemiBold)
            if (entries.isEmpty()) {
                Text("Сделайте первую запись, чтобы увидеть график.", color = MoodColors.Muted)
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                ) {
                    val left = 8.dp.toPx()
                    val right = size.width - 8.dp.toPx()
                    val top = 12.dp.toPx()
                    val bottom = size.height - 18.dp.toPx()
                    (1..5).forEach { level ->
                        val y = bottom - (level - 1) / 4f * (bottom - top)
                        drawLine(Color(0xFFE6E2D9), Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
                    }
                    fun drawSeries(values: List<Float?>, color: Color, stroke: Float, dash: FloatArray? = null) {
                        val effect = dash?.let { PathEffect.dashPathEffect(it.map { v -> v.dp.toPx() }.toFloatArray()) }
                        var previous: Offset? = null
                        values.forEachIndexed { index, value ->
                            val x = left + index / 6f * (right - left)
                            if (value == null) {
                                previous = null
                            } else {
                                val y = bottom - (value - 1f) / 4f * (bottom - top)
                                val point = Offset(x, y)
                                previous?.let {
                                    drawLine(color, it, point, strokeWidth = stroke.dp.toPx(), cap = StrokeCap.Round, pathEffect = effect)
                                }
                                drawCircle(color, radius = max(3.dp.toPx(), stroke.dp.toPx()), center = point)
                                previous = point
                            }
                        }
                    }
                    drawSeries(days.map { byDate[it]?.mood?.toFloat() }, MoodColors.Mood[5] ?: MoodColors.Accent, 2.5f)
                    drawSeries(days.map { byDate[it]?.sleep?.toFloat() }, MoodColors.Sleep[4] ?: MoodColors.Accent, 2f, floatArrayOf(8f, 6f))
                    drawSeries(days.map { byDate[it]?.energy?.toFloat() }, MoodColors.Energy, 1.5f)
                    drawSeries(days.map { byDate[it]?.anxiety?.toFloat() }, MoodColors.Anxiety, 1.5f, floatArrayOf(2f, 5f))
                    drawSeries(days.map { byDate[it]?.stress?.toFloat() }, MoodColors.Stress, 1.5f, floatArrayOf(8f, 4f, 2f, 4f))
                }
                ChartLegend()
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ChartLegend() {
    val items = listOf(
        "● настроение" to (MoodColors.Mood[5] ?: MoodColors.Accent),
        "– – сон" to (MoodColors.Sleep[4] ?: MoodColors.Accent),
        "● энергия" to MoodColors.Energy,
        ". . тревожность" to MoodColors.Anxiety,
        "-· стресс" to MoodColors.Stress,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { (label, color) ->
            Text(label, color = color, fontSize = 12.sp)
        }
    }
}

@Composable
private fun FeedbackRow(onFeedback: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(modifier = Modifier.weight(1f), onClick = { onFeedback("useful") }) { Text("Полезно") }
        OutlinedButton(modifier = Modifier.weight(1f), onClick = { onFeedback("not_useful") }) { Text("Не полезно") }
        TextButton(modifier = Modifier.weight(1f), onClick = { onFeedback("skipped") }) { Text("Пропустить") }
    }
}

@Composable
private fun HistoryItem(entry: Entry, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFF0F0FF) else MoodColors.Surface),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Dot(MoodColors.Mood[entry.mood] ?: MoodColors.Accent)
            Dot(MoodColors.Sleep[entry.sleep] ?: MoodColors.Accent)
            Column(Modifier.weight(1f)) {
                Text(formatDate(entry.date), fontWeight = FontWeight.SemiBold)
                Text("Настроение ${entry.mood} · Сон ${entry.sleep}", color = MoodColors.Muted, fontSize = 13.sp)
                if (entry.tags.isNotEmpty()) {
                    Text(entry.tags.mapNotNull { id -> Tags.firstOrNull { it.id == id }?.label }.joinToString(", "), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun EntryDetails(entry: Entry, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MoodColors.Surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Детали записи", fontWeight = FontWeight.SemiBold)
            Text("Настроение: ${entry.mood}; энергия: ${entry.energy}; тревожность: ${entry.anxiety}; стресс: ${entry.stress}; сон: ${entry.sleep}")
            Text("Теги: ${entry.tags.mapNotNull { id -> Tags.firstOrNull { it.id == id }?.label }.ifEmpty { listOf("нет") }.joinToString(", ")}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onEdit) { Text("Редактировать") }
                OutlinedButton(onClick = onDelete) { Text("Удалить") }
            }
        }
    }
}

@Composable
private fun BottomNav(onHome: () -> Unit, onHistory: () -> Unit, onSettings: () -> Unit) {
    Surface(color = MoodColors.Surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(modifier = Modifier.weight(1f), onClick = onHome) { Text("Домой") }
            TextButton(modifier = Modifier.weight(1f), onClick = onHistory) { Text("История") }
            TextButton(modifier = Modifier.weight(1f), onClick = onSettings) { Text("Настройки") }
        }
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Surface(color = MoodColors.Surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Назад") }
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MoodColors.Surface)) {
        Text(text, modifier = Modifier.padding(16.dp), lineHeight = 22.sp)
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text, textAlign = TextAlign.Center, color = MoodColors.Muted)
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

private fun calculateInsight(entries: List<Entry>): Insight {
    val used = entries.sortedByDescending { it.date }.take(7).sortedBy { it.date }
    val count = used.size
    if (count < 3) {
        val left = 3 - count
        return Insight(
            status = InsightStatus.NotEnoughData,
            subtype = null,
            message = "Пока данных мало. Ещё $left ${recordWord(left)} — и можно будет проверить связь сна и настроения.",
            confidenceLabel = null,
            entriesUsedCount = count,
        )
    }

    val lowSleep = used.filter { it.sleep <= 2 }
    val highSleep = used.filter { it.sleep >= 4 }
    val contrastFound = lowSleep.isNotEmpty() && highSleep.isNotEmpty() &&
        highSleep.averageOf { it.mood } - lowSleep.averageOf { it.mood } >= 1.0 &&
        (count >= 5 || (lowSleep.all { it.mood <= 3 } && highSleep.all { it.mood >= 3 }))

    if (contrastFound) {
        return Insight(
            status = InsightStatus.Found,
            subtype = InsightSubtype.Contrast,
            message = "В твоих последних записях прослеживается паттерн: в дни с лучшим сном настроение было заметно выше. Это не доказывает причинно-следственную связь — но достаточно устойчиво, чтобы понаблюдать за ним осознанно. Не диагноз — просто интересная закономерность.",
            confidenceLabel = if (count >= 5) "средняя" else "низкая",
            entriesUsedCount = count,
            secondaryObservations = secondaryObservations(used, stableHigh = false),
        )
    }

    val stableLow = highSleep.isEmpty() && lowSleep.size > count / 2.0
    if (stableLow) {
        return Insight(
            status = InsightStatus.Found,
            subtype = InsightSubtype.StableLow,
            message = "Сон в последних записях стабильно низкий. Сравнить его с чем-то другим пока сложно — но стабильно низкий сон сам по себе фактор, который часто влияет на самочувствие в течение дня. Это не диагноз — просто наблюдение, которое стоит держать в голове.",
            confidenceLabel = "низкая",
            entriesUsedCount = count,
            secondaryObservations = secondaryObservations(used, stableHigh = false),
        )
    }

    val stableHigh = lowSleep.isEmpty() && highSleep.size > count / 2.0 && used.averageOf { it.mood } >= 3.5
    if (stableHigh) {
        return Insight(
            status = InsightStatus.Found,
            subtype = InsightSubtype.StableHigh,
            message = "Сон в последних записях стабильно хороший, и настроение держится уверенно. Значит, что-то в твоём нынешнем ритме работает. Стоит это замечать — и стараться сохранить, когда ритм будет под давлением.",
            confidenceLabel = "низкая",
            entriesUsedCount = count,
        )
    }

    if (count < 5) {
        val left = 5 - count
        return Insight(
            status = InsightStatus.Waiting,
            subtype = null,
            message = "Уже есть первые записи. Чтобы не делать поспешный вывод, нужно ещё немного данных — ${if (left == 1) "осталась" else "осталось"} $left ${recordWord(left)} до 5.",
            confidenceLabel = null,
            entriesUsedCount = count,
        )
    }

    val subtype = when {
        lowSleep.isNotEmpty() && highSleep.isNotEmpty() -> InsightSubtype.NoCorrelation
        lowSleep.isEmpty() && highSleep.isEmpty() -> InsightSubtype.NoPolarity
        else -> InsightSubtype.NoPolarity
    }
    val message = when (subtype) {
        InsightSubtype.NoCorrelation -> "Сон менялся, но настроение в эти периоды двигалось независимо. Явной связи пока не видно — возможно, в этот промежуток больше влияли другие факторы. Это нормально."
        InsightSubtype.NoPolarity -> "Сон в последних записях держался без выраженных контрастов — дней с заметно низким или заметно высоким сном пока недостаточно для сравнения. Продолжай отмечать — картина прояснится."
        else -> "В последних записях устойчивого паттерна между сном и настроением не нашлось. Паттерны часто проявляются позже или при большем разбросе данных — продолжай наблюдения."
    }
    return Insight(
        status = InsightStatus.NoClearPattern,
        subtype = subtype,
        message = message,
        confidenceLabel = null,
        entriesUsedCount = count,
        secondaryObservations = secondaryObservations(used, stableHigh = false),
    )
}

private fun secondaryObservations(entries: List<Entry>, stableHigh: Boolean): List<SecondaryObservation> {
    if (entries.size < 3 || stableHigh) return emptyList()
    val lowSleep = entries.filter { it.sleep <= 2 }
    val highSleep = entries.filter { it.sleep >= 4 }
    val observations = mutableListOf<SecondaryObservation>()

    if (lowSleep.isNotEmpty() && highSleep.isNotEmpty()) {
        val energyDelta = highSleep.averageOf { it.energy } - lowSleep.averageOf { it.energy }
        if (energyDelta >= 1.0) observations += SecondaryObservation(ObservationScale.Energy, ObservationDirection.BetterSleepHigher, energyDelta)
        val anxietyDelta = lowSleep.averageOf { it.anxiety } - highSleep.averageOf { it.anxiety }
        if (anxietyDelta >= 1.0) observations += SecondaryObservation(ObservationScale.Anxiety, ObservationDirection.BetterSleepLower, anxietyDelta)
        val stressDelta = lowSleep.averageOf { it.stress } - highSleep.averageOf { it.stress }
        if (stressDelta >= 1.0) observations += SecondaryObservation(ObservationScale.Stress, ObservationDirection.BetterSleepLower, stressDelta)
    } else if (highSleep.isEmpty() && lowSleep.size > entries.size / 2.0) {
        if (entries.averageOf { it.energy } <= 2.5) observations += SecondaryObservation(ObservationScale.Energy, ObservationDirection.Stable, 1.0)
        if (entries.averageOf { it.anxiety } >= 3.5) observations += SecondaryObservation(ObservationScale.Anxiety, ObservationDirection.Stable, 1.0)
        if (entries.averageOf { it.stress } >= 3.5) observations += SecondaryObservation(ObservationScale.Stress, ObservationDirection.Stable, 1.0)
    }

    return observations.sortedWith(
        compareByDescending<SecondaryObservation> { abs(it.score) }
            .thenBy {
                when (it.scale) {
                    ObservationScale.Energy -> 0
                    ObservationScale.Anxiety -> 1
                    ObservationScale.Stress -> 2
                }
            },
    ).take(1)
}

private fun recommendationFor(insight: Insight): String? = when (insight.status to insight.subtype) {
    InsightStatus.Found to InsightSubtype.Contrast ->
        "Попробуй сегодня один небольшой шаг: лечь на 20 минут раньше — и завтра отметь, заметна ли разница в настроении."
    InsightStatus.Found to InsightSubtype.StableLow ->
        "Можно начать с одного маленького шага этим вечером: попробуй лечь на 15 минут раньше обычного — без давления на результат."
    InsightStatus.Found to InsightSubtype.StableHigh ->
        "Отметь для себя, что сейчас работает хорошо — это поможет вернуться к этому ритму, если что-то изменится."
    else -> null
}

private fun secondaryObservationText(observation: SecondaryObservation): String = when (observation.scale to observation.direction) {
    ObservationScale.Energy to ObservationDirection.BetterSleepHigher ->
        "Ещё одно наблюдение: в дни с лучшим сном энергия тоже была заметно выше."
    ObservationScale.Anxiety to ObservationDirection.BetterSleepLower ->
        "Ещё одно наблюдение: в дни с лучшим сном тревожность была заметно ниже."
    ObservationScale.Stress to ObservationDirection.BetterSleepLower ->
        "Ещё одно наблюдение: в дни с лучшим сном уровень стресса тоже был ниже."
    ObservationScale.Energy to ObservationDirection.Stable ->
        "Ещё одно наблюдение: энергия в последних записях оставалась на низком уровне — это стоит иметь в виду при продолжении наблюдения."
    ObservationScale.Anxiety to ObservationDirection.Stable ->
        "Ещё одно наблюдение: тревожность в последних записях была на повышенном уровне — это важный контекст для продолжения дневника."
    ObservationScale.Stress to ObservationDirection.Stable ->
        "Ещё одно наблюдение: уровень стресса в последних записях оставался высоким — это важный контекст для продолжения дневника."
    else -> ""
}

private fun List<Entry>.averageOf(selector: (Entry) -> Int): Double = map(selector).average()

private fun iconFor(kind: ScaleKind, value: Int): String = when (kind) {
    ScaleKind.Mood -> when (value) {
        5 -> "😊"
        4 -> "🙂"
        3 -> "😐"
        2 -> "😔"
        else -> "😞"
    }
    ScaleKind.Sleep -> when (value) {
        5 -> "☀️"
        4 -> "🌤"
        3 -> "☁️"
        2 -> "🌙"
        else -> "🌑"
    }
    ScaleKind.Energy -> "⚡"
    ScaleKind.Anxiety -> "◌"
    ScaleKind.Stress -> "◆"
}

private fun recordWord(n: Int): String = when (n) {
    1 -> "запись"
    2, 3, 4 -> "записи"
    else -> "записей"
}

private fun formatDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru")))

@Preview(showBackground = true)
@Composable
private fun OnboardingPreview() {
    MoodDiaryTheme {
        OnboardingScreen(onStart = {})
    }
}
