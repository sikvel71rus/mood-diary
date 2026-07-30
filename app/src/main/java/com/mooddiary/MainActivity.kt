package com.mooddiary

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
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
private const val MAX_DEMO_DAYS = 31

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
    DemoData,
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

private enum class DemoPreset {
    SleepMoodPattern,
    StressfulPeriod,
    StableGood,
    Manual,
}

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
    val Background = Color(0xFFFAF9FD)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFF4F2F8)
    val Text = Color(0xFF22242A)
    val Muted = Color(0xFF666A73)
    val Border = Color(0xFFE5E2EB)
    val Accent = Color(0xFF6963C7)
    val AccentDark = Color(0xFF46408F)
    val AccentSoft = Color(0xFFECEAFB)
    val InsightBlue = Color(0xFFEAF4FF)
    val Recommendation = Color(0xFFF3EEF9)
    val Error = Color(0xFF8C4A62)

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

    val Energy = mapOf(
        5 to Color(0xFF8EB69B),
        4 to Color(0xFF7EC9C3),
        3 to Color(0xFFD7C4A3),
        2 to Color(0xFFB8A8D9),
        1 to Color(0xFF6E7CA8),
    )
    val EnergyLine = Color(0xFFC8A84B)
    val Anxiety = mapOf(
        5 to Color(0xFF6E7CA8),
        4 to Color(0xFF9890C4),
        3 to Color(0xFFB8A8D9),
        2 to Color(0xFFD7C4A3),
        1 to Color(0xFF8EB69B),
    )
    val AnxietyLine = Color(0xFFC0A8B8)
    val Stress = mapOf(
        5 to Color(0xFF6E7CA8),
        4 to Color(0xFF9890C4),
        3 to Color(0xFFD7C4A3),
        2 to Color(0xFF7EC9C3),
        1 to Color(0xFF8EB69B),
    )
    val StressLine = Color(0xFF9890C4)
}

private val AppCardShape = RoundedCornerShape(8.dp)
private val PillShape = RoundedCornerShape(100.dp)

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

    fun save(draft: DraftEntry): Boolean = try {
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
    } catch (e: Exception) {
        false
    }

    fun saveAll(drafts: List<DraftEntry>): Boolean = try {
        val existing = entries()
        val now = System.currentTimeMillis()
        val byDate = existing.associateBy { it.date }
        var sequence = now
        val generated = drafts.map { draft ->
            val sameDate = byDate[draft.date]
            val id = draft.id ?: sameDate?.id ?: sequence++
            Entry(
                id = id,
                date = draft.date,
                mood = requireNotNull(draft.mood),
                energy = requireNotNull(draft.energy),
                anxiety = requireNotNull(draft.anxiety),
                stress = requireNotNull(draft.stress),
                sleep = requireNotNull(draft.sleep),
                tags = draft.tags.sorted(),
                createdAt = draft.createdAt ?: sameDate?.createdAt ?: now,
                updatedAt = now,
            )
        }
        val dates = generated.map { it.date }.toSet()
        saveEntries(existing.filterNot { it.date in dates } + generated)
    } catch (e: Exception) {
        false
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

    private fun saveEntries(entries: List<Entry>): Boolean {
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
        return prefs.edit().putString(KEY_ENTRIES, json.toString()).commit()
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

    fun goHome() {
        editingEntry = null
        selectedHistoryEntry = null
        screen = Screen.Home
    }

    BackHandler(enabled = screen != Screen.Onboarding && screen != Screen.Home) {
        if (screen == Screen.DemoData) {
            screen = Screen.Settings
        } else {
            goHome()
        }
    }

    MoodDiaryTheme {
        if (screen == Screen.Onboarding) {
            OnboardingScreen(
                onStart = {
                    store.markDisclaimerShown()
                    editingEntry = entries.firstOrNull { it.date == LocalDate.now() }
                    screen = Screen.CheckIn
                },
            )
        } else {
            Scaffold(
                containerColor = MoodColors.Background,
                bottomBar = {
                    BottomNav(
                        activeScreen = activeNavScreen(screen),
                        onHome = ::goHome,
                        onHistory = {
                            editingEntry = null
                            selectedHistoryEntry = null
                            screen = Screen.History
                        },
                        onSettings = {
                            editingEntry = null
                            selectedHistoryEntry = null
                            screen = Screen.Settings
                        },
                    )
                },
            ) { rootPadding ->
                Box(modifier = Modifier.padding(rootPadding)) {
                    when (screen) {
                        Screen.Onboarding -> Unit

                        Screen.Home -> HomeScreen(
                            entries = entries,
                            onCheckIn = {
                                editingEntry = entries.firstOrNull { it.date == LocalDate.now() }
                                screen = Screen.CheckIn
                            },
                            onInsight = {
                                store.logInsightOpened()
                                screen = Screen.Insight
                            },
                        )

                        Screen.CheckIn -> CheckInScreen(
                            entry = editingEntry,
                            onSave = {
                                val ok = store.save(it)
                                if (ok) {
                                    refresh()
                                    goHome()
                                }
                                ok
                            },
                            onBack = ::goHome,
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
                            onBack = ::goHome,
                        )

                        Screen.Insight -> {
                            val insight = calculateInsight(entries)
                            InsightScreen(
                                insight = insight,
                                recommendation = recommendationFor(insight),
                                onFeedback = {
                                    store.saveFeedback(it)
                                },
                                onBack = ::goHome,
                            )
                        }

                        Screen.Settings -> SettingsScreen(
                            onBack = ::goHome,
                            onExport = { store.shareStats() },
                            showDemoDataTools = BuildConfig.SHOW_DEMO_DATA_TOOLS,
                            onDemoData = {
                                editingEntry = null
                                selectedHistoryEntry = null
                                screen = Screen.DemoData
                            },
                            onDeleteAll = {
                                store.clearAll()
                                refresh()
                                selectedHistoryEntry = null
                                editingEntry = null
                                screen = Screen.Onboarding
                            },
                        )

                        Screen.DemoData -> DemoDataScreen(
                            entries = entries,
                            onSave = { drafts ->
                                val ok = store.saveAll(drafts)
                                if (ok) refresh()
                                ok
                            },
                            onDeleteToday = {
                                entries.firstOrNull { it.date == LocalDate.now() }?.let {
                                    store.deleteEntry(it.id)
                                    refresh()
                                }
                            },
                            onBack = { screen = Screen.Settings },
                        )
                    }
                }
            }
        }
    }
}

private fun activeNavScreen(screen: Screen): Screen = when (screen) {
    Screen.CheckIn -> Screen.Home
    Screen.Insight -> Screen.Home
    Screen.Onboarding -> Screen.Home
    Screen.DemoData -> Screen.Settings
    else -> screen
}

@Composable
private fun MoodDiaryTheme(content: @Composable () -> Unit) {
    val colorScheme = lightColorScheme(
        primary = MoodColors.Accent,
        onPrimary = Color.White,
        primaryContainer = MoodColors.AccentSoft,
        onPrimaryContainer = MoodColors.AccentDark,
        secondary = MoodColors.Mood[4] ?: MoodColors.Accent,
        onSecondary = MoodColors.Text,
        secondaryContainer = MoodColors.InsightBlue,
        onSecondaryContainer = MoodColors.Text,
        background = MoodColors.Background,
        onBackground = MoodColors.Text,
        surface = MoodColors.Surface,
        onSurface = MoodColors.Text,
        surfaceVariant = MoodColors.SurfaceMuted,
        onSurfaceVariant = MoodColors.Muted,
        outline = MoodColors.Border,
        error = MoodColors.Error,
        onError = Color.White,
    )
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = Shapes(
            extraSmall = AppCardShape,
            small = AppCardShape,
            medium = AppCardShape,
            large = AppCardShape,
            extraLarge = AppCardShape,
        ),
    ) {
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
                Text("Сделать первую отметку")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MoodColors.Accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("✦", color = MoodColors.Accent, fontSize = 32.sp)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Mood Diary",
                color = MoodColors.Text,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Короткий дневник, который помогает заметить, как сон и события дня связаны с настроением.",
                color = MoodColors.Muted,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            InfoCard(
                "Каждый день — пять быстрых оценок и несколько тегов контекста. Первые осторожные наблюдения появятся уже после 3–5 записей.",
                icon = "i",
                containerColor = MoodColors.InsightBlue,
            )
            Spacer(Modifier.height(10.dp))
            InfoCard(
                "Это не диагноз и не замена специалиста. Данные хранятся только на устройстве, без регистрации и аналитики.",
                icon = "✓",
                containerColor = MoodColors.SurfaceMuted,
            )
        }
    }
}

@Composable
private fun HomeScreen(
    entries: List<Entry>,
    onCheckIn: () -> Unit,
    onInsight: () -> Unit,
) {
    val todayEntry = entries.firstOrNull { it.date == LocalDate.now() }
    val insight = calculateInsight(entries)
    Scaffold(
        containerColor = MoodColors.Background,
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
                Spacer(Modifier.height(10.dp))
                MoodScaleStrip()
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
    onSave: (DraftEntry) -> Boolean,
    onBack: () -> Unit,
) {
    var mood by remember(entry) { mutableStateOf(entry?.mood) }
    var energy by remember(entry) { mutableStateOf(entry?.energy) }
    var anxiety by remember(entry) { mutableStateOf(entry?.anxiety) }
    var stress by remember(entry) { mutableStateOf(entry?.stress) }
    var sleep by remember(entry) { mutableStateOf(entry?.sleep) }
    var selectedTags by remember(entry) { mutableStateOf(entry?.tags?.toSet() ?: emptySet()) }
    val answers = listOf(mood, energy, anxiety, stress, sleep)
    val firstEmptyStep = answers.indexOfFirst { it == null }.let { if (it == -1) 0 else it }
    var carouselStep by remember(entry) { mutableStateOf(firstEmptyStep) }
    val canSave = answers.all { it != null }
    var saveError by remember { mutableStateOf(false) }
    fun answerForStep(step: Int): Int? = when (step) {
        0 -> mood
        1 -> energy
        2 -> anxiety
        3 -> stress
        4 -> sleep
        else -> null
    }

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
            Text(
                text = if (carouselStep < 5) "Шкала ${carouselStep + 1} из 5" else "Контекст дня",
                color = MoodColors.Muted,
                fontSize = 14.sp,
            )
            Card(colors = CardDefaults.cardColors(containerColor = MoodColors.Surface)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (carouselStep) {
                        0 -> RatingRow("Настроение", mood, ScaleKind.Mood) {
                            mood = it
                            carouselStep = 1
                        }
                        1 -> RatingRow("Энергия", energy, ScaleKind.Energy) {
                            energy = it
                            carouselStep = 2
                        }
                        2 -> RatingRow("Тревожность", anxiety, ScaleKind.Anxiety) {
                            anxiety = it
                            carouselStep = 3
                        }
                        3 -> RatingRow("Стресс", stress, ScaleKind.Stress) {
                            stress = it
                            carouselStep = 4
                        }
                        4 -> RatingRow("Сон", sleep, ScaleKind.Sleep) {
                            sleep = it
                            carouselStep = 5
                        }
                        else -> {
                            Text("Что повлияло на день?", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Теги необязательны. Выбери то, что поможет потом понять контекст.",
                                color = MoodColors.Muted,
                                fontSize = 14.sp,
                            )
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
                        }
                    }
                }
            }
            CarouselDots(activeStep = carouselStep)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = carouselStep > 0,
                    onClick = { carouselStep -= 1 },
                ) {
                    Text("Назад")
                }
                if (carouselStep < 5) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = answerForStep(carouselStep) != null,
                        onClick = {
                            carouselStep += 1
                        },
                    ) {
                        Text("Дальше")
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = canSave && carouselStep == 5,
                onClick = {
                    saveError = false
                    val ok = onSave(
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
                    if (!ok) saveError = true
                },
            ) {
                Text("Сохранить")
            }
            if (saveError) {
                Text(
                    "Не удалось сохранить запись. Проверьте свободное место и попробуйте снова.",
                    color = MoodColors.Error,
                    fontSize = 14.sp,
                )
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
            InfoCard(insight.message, icon = "✦", containerColor = MoodColors.InsightBlue)
            Text("Использовано записей: ${insight.entriesUsedCount}", color = MoodColors.Muted)
            insight.secondaryObservations.firstOrNull()?.let {
                InfoCard(secondaryObservationText(it), icon = "＋", containerColor = MoodColors.SurfaceMuted)
            }
            recommendation?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MoodColors.Recommendation)) {
                    Row(
                        Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.72f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("☾", color = MoodColors.AccentDark, fontSize = 20.sp)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Маленькое действие", fontWeight = FontWeight.SemiBold)
                            Text(it)
                        }
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
    showDemoDataTools: Boolean,
    onDemoData: () -> Unit,
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
            InfoCard(
                "Mood Diary не ставит диагнозы и не заменяет специалиста. Рекомендации — это бережные идеи для самонаблюдения.",
                icon = "i",
                containerColor = MoodColors.InsightBlue,
            )
            InfoCard(
                "Приложение работает офлайн: записи, оценки инсайтов и счётчики статистики хранятся только на устройстве.",
                icon = "✓",
                containerColor = MoodColors.SurfaceMuted,
            )
            if (showDemoDataTools) {
                Button(modifier = Modifier.fillMaxWidth(), onClick = onDemoData) {
                    Text("Заполнить тестовые записи")
                }
            }
            Button(modifier = Modifier.fillMaxWidth(), onClick = onExport) {
                Text("Экспорт статистики")
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

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DemoDataScreen(
    entries: List<Entry>,
    onSave: (List<DraftEntry>) -> Boolean,
    onDeleteToday: () -> Unit,
    onBack: () -> Unit,
) {
    var startDate by remember { mutableStateOf(LocalDate.now().minusDays(6)) }
    var endDate by remember { mutableStateOf(LocalDate.now()) }
    var preset by remember { mutableStateOf(DemoPreset.SleepMoodPattern) }
    var mood by remember { mutableStateOf(4) }
    var energy by remember { mutableStateOf(4) }
    var anxiety by remember { mutableStateOf(2) }
    var stress by remember { mutableStateOf(2) }
    var sleep by remember { mutableStateOf(4) }
    var selectedTags by remember { mutableStateOf(setOf("work", "rest")) }
    var saveError by remember { mutableStateOf(false) }
    var savedCount by remember { mutableStateOf<Int?>(null) }
    val dates = datesBetween(startDate, endDate)
    val isRangeTooLong = dates.size > MAX_DEMO_DAYS
    val drafts = if (isRangeTooLong) {
        emptyList()
    } else {
        buildDemoDrafts(
            dates = dates,
            preset = preset,
            manualMood = mood,
            manualEnergy = energy,
            manualAnxiety = anxiety,
            manualStress = stress,
            manualSleep = sleep,
            tags = selectedTags,
        )
    }
    val existingDates = entries.map { it.date }.toSet()
    val overwriteCount = drafts.count { it.date in existingDates }
    val todayEntryExists = LocalDate.now() in existingDates

    Scaffold(
        containerColor = MoodColors.Background,
        topBar = { TopBar("Тестовые записи", onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Заполнение для демонстрации", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            InfoCard(
                "Создаёт локальные тестовые записи за выбранные даты. Если запись за дату уже есть, она будет заменена новыми тестовыми значениями.",
                icon = "i",
                containerColor = MoodColors.SurfaceMuted,
            )
            DateRangePicker(
                startDate = startDate,
                endDate = endDate,
                onStartChange = {
                    startDate = it
                    if (it.isAfter(endDate)) endDate = it
                    savedCount = null
                },
                onEndChange = {
                    endDate = it
                    if (it.isBefore(startDate)) startDate = it
                    savedCount = null
                },
                onQuickRange = { days ->
                    endDate = LocalDate.now()
                    startDate = LocalDate.now().minusDays((days - 1).toLong())
                    savedCount = null
                },
            )
            Card(colors = CardDefaults.cardColors(containerColor = MoodColors.Surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Сценарий", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DemoPreset.values().forEach { item ->
                            ChoiceChip(
                                label = demoPresetLabel(item),
                                selected = preset == item,
                                onClick = {
                                    preset = item
                                    savedCount = null
                                },
                            )
                        }
                    }
                    Text(demoPresetDescription(preset), color = MoodColors.Muted, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
            if (preset == DemoPreset.Manual) {
                Card(colors = CardDefaults.cardColors(containerColor = MoodColors.Surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Значения для всех дат", fontWeight = FontWeight.SemiBold)
                        RatingRow("Настроение", mood, ScaleKind.Mood) {
                            mood = it
                            savedCount = null
                        }
                        RatingRow("Энергия", energy, ScaleKind.Energy) {
                            energy = it
                            savedCount = null
                        }
                        RatingRow("Тревожность", anxiety, ScaleKind.Anxiety) {
                            anxiety = it
                            savedCount = null
                        }
                        RatingRow("Стресс", stress, ScaleKind.Stress) {
                            stress = it
                            savedCount = null
                        }
                        RatingRow("Сон", sleep, ScaleKind.Sleep) {
                            sleep = it
                            savedCount = null
                        }
                    }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MoodColors.Surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Теги ко всем записям", fontWeight = FontWeight.SemiBold)
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
                                    savedCount = null
                                },
                            )
                        }
                    }
                }
            }
            DemoPreview(drafts = drafts, overwriteCount = overwriteCount, isRangeTooLong = isRangeTooLong)
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = drafts.isNotEmpty() && !isRangeTooLong,
                onClick = {
                    saveError = false
                    val ok = onSave(drafts)
                    if (ok) {
                        savedCount = drafts.size
                    } else {
                        saveError = true
                        savedCount = null
                    }
                },
            ) {
                Text("Сохранить ${drafts.size} ${recordWord(drafts.size)}")
            }
            savedCount?.let {
                Text("Готово: сохранено $it ${recordWord(it)}.", color = MoodColors.Accent)
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = todayEntryExists,
                onClick = {
                    onDeleteToday()
                    saveError = false
                    savedCount = null
                },
            ) {
                Text(if (todayEntryExists) "Удалить сегодняшнюю запись" else "Сегодняшней записи нет")
            }
            if (saveError) {
                Text(
                    "Не удалось сохранить тестовые записи. Проверьте свободное место и попробуйте снова.",
                    color = MoodColors.Error,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun DateRangePicker(
    startDate: LocalDate,
    endDate: LocalDate,
    onStartChange: (LocalDate) -> Unit,
    onEndChange: (LocalDate) -> Unit,
    onQuickRange: (Int) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MoodColors.Surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Даты", fontWeight = FontWeight.SemiBold)
            DateStepper("С", startDate, onStartChange)
            DateStepper("По", endDate, onEndChange)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(3, 5, 7, 14).forEach { days ->
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = { onQuickRange(days) }) {
                        Text("${days}д")
                    }
                }
            }
        }
    }
}

@Composable
private fun DateStepper(label: String, date: LocalDate, onChange: (LocalDate) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, modifier = Modifier.width(28.dp), color = MoodColors.Muted)
        OutlinedButton(onClick = { onChange(date.minusDays(1)) }) {
            Text("-")
        }
        Text(
            formatShortDate(date),
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = { onChange(date.plusDays(1)) }) {
            Text("+")
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) MoodColors.AccentSoft else MoodColors.Surface
    val border = if (selected) MoodColors.Accent else MoodColors.Border
    Text(
        text = label,
        modifier = Modifier
            .clip(PillShape)
            .border(1.dp, border, PillShape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = if (selected) MoodColors.AccentDark else MoodColors.Text,
        fontSize = 14.sp,
    )
}

@Composable
private fun DemoPreview(drafts: List<DraftEntry>, overwriteCount: Int, isRangeTooLong: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = MoodColors.Surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Предпросмотр", fontWeight = FontWeight.SemiBold)
            if (isRangeTooLong) {
                Text("Диапазон слишком большой. Для демо-заполнения выберите до $MAX_DEMO_DAYS дней.", color = MoodColors.Muted)
            } else {
                Text(
                    "${drafts.size} ${recordWord(drafts.size)}. Будет заменено существующих: $overwriteCount.",
                    color = MoodColors.Muted,
                    fontSize = 14.sp,
                )
                drafts.take(7).forEach { draft ->
                    DemoPreviewRow(draft)
                }
                if (drafts.size > 7) {
                    Text("И ещё ${drafts.size - 7} ${recordWord(drafts.size - 7)}.", color = MoodColors.Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun DemoPreviewRow(draft: DraftEntry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(formatShortDate(draft.date), modifier = Modifier.width(86.dp), fontSize = 13.sp)
        ScaleCircle(ScaleKind.Mood, draft.mood ?: 3, size = 22.dp)
        Text("Н ${draft.mood}", fontSize = 13.sp)
        ScaleCircle(ScaleKind.Sleep, draft.sleep ?: 3, size = 22.dp)
        Text("Сон ${draft.sleep}", fontSize = 13.sp)
        Text("Э ${draft.energy} / Тр ${draft.anxiety} / Ст ${draft.stress}", color = MoodColors.Muted, fontSize = 13.sp)
    }
}

private enum class ScaleKind {
    Mood,
    Energy,
    Anxiety,
    Stress,
    Sleep,
}

private data class ScaleOption(
    val icon: String,
    val label: String,
)

@Composable
private fun RatingRow(
    label: String,
    selected: Int?,
    kind: ScaleKind,
    enabled: Boolean = true,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontWeight = FontWeight.SemiBold)
            selected?.let {
                Text(scaleLabel(kind, it), color = MoodColors.Accent, fontSize = 13.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            valuesForScale(kind).forEach { value ->
                val option = scaleOption(kind, value)
                val color = when (kind) {
                    ScaleKind.Mood -> MoodColors.Mood[value] ?: MoodColors.Accent
                    ScaleKind.Sleep -> MoodColors.Sleep[value] ?: MoodColors.Accent
                    ScaleKind.Energy -> MoodColors.Energy[value] ?: MoodColors.Accent
                    ScaleKind.Anxiety -> MoodColors.Anxiety[value] ?: MoodColors.AnxietyLine
                    ScaleKind.Stress -> MoodColors.Stress[value] ?: MoodColors.StressLine
                }
                val selectedModifier = if (selected == value) {
                    Modifier.border(2.dp, MoodColors.Accent, CircleShape)
                } else {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.74f), CircleShape)
                }
                Column(
                    modifier = Modifier.width(64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .then(selectedModifier)
                            .clip(CircleShape)
                            .background(color.copy(alpha = if (enabled) 0.64f else 0.12f))
                            .clickable(enabled = enabled) { onSelect(value) },
                        contentAlignment = Alignment.Center,
                    ) {
                        ScaleGlyph(
                            kind = kind,
                            value = value,
                            modifier = Modifier.size(31.dp),
                            cutoutColor = color.copy(alpha = if (enabled) 0.64f else 0.12f),
                        )
                    }
                    Text(
                        option.label,
                        fontSize = 10.sp,
                        color = if (enabled) MoodColors.Muted else MoodColors.Muted.copy(alpha = 0.48f),
                        textAlign = TextAlign.Center,
                        lineHeight = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CarouselDots(activeStep: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        (0..5).forEach { step ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (step == activeStep) 10.dp else 7.dp)
                    .clip(CircleShape)
                    .background(if (step == activeStep) MoodColors.Accent else MoodColors.Border),
            )
        }
    }
}

@Composable
private fun TagChip(tag: Tag, selected: Boolean, onToggle: () -> Unit) {
    val background = if (selected) MoodColors.AccentSoft else MoodColors.Surface
    val border = if (selected) MoodColors.Accent else MoodColors.Border
    Text(
        text = tag.label,
        modifier = Modifier
            .clip(PillShape)
            .border(1.dp, border, PillShape)
            .background(background)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = if (selected) MoodColors.AccentDark else MoodColors.Text,
        fontSize = 14.sp,
    )
}

@Composable
private fun ProgressBlock(insight: Insight, entryCount: Int) {
    val target = when {
        entryCount < 3 -> 3
        entryCount < 5 && insight.status == InsightStatus.Waiting -> 5
        else -> return
    }
    val progress = entryCount / target.toFloat()
    val left = target - entryCount
    Card(colors = CardDefaults.cardColors(containerColor = MoodColors.Surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Прогресс до инсайта", fontWeight = FontWeight.SemiBold)
                Text("$entryCount/$target", color = MoodColors.Accent, fontWeight = FontWeight.SemiBold)
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text(
                if (entryCount < 3) {
                    "Нужно ещё $left ${recordWord(left)}, чтобы сравнить сон и настроение без поспешных выводов."
                } else {
                    "Хорошее начало. Ещё $left ${recordWord(left)} до первого инсайта."
                },
                color = MoodColors.Muted,
            )
        }
    }
}

@Composable
private fun InsightCard(insight: Insight, onOpen: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MoodColors.InsightBlue),
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.74f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("✦", color = MoodColors.Accent, fontSize = 21.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Инсайт", fontWeight = FontWeight.SemiBold)
                Text(insightPreviewText(insight))
                Text("Посмотреть детали →", color = MoodColors.AccentDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun MoodScaleStrip() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..5).forEach { value ->
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MoodColors.Mood[value] ?: MoodColors.Accent),
                contentAlignment = Alignment.Center,
            ) {
                MoodFaceGlyph(value = value, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun MiniChart(entries: List<Entry>) {
    var selectedRangeDays by remember { mutableStateOf(7) }
    val today = LocalDate.now()
    val days = (selectedRangeDays - 1 downTo 0).map { today.minusDays(it.toLong()) }
    val byDate = entries.associateBy { it.date }
    Card(colors = CardDefaults.cardColors(containerColor = MoodColors.Surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Статистика", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text("$selectedRangeDays дней", color = MoodColors.Muted, fontSize = 13.sp)
            }
            SegmentedRangeHeader(
                selectedDays = selectedRangeDays,
                onSelect = { selectedRangeDays = it },
            )
            if (entries.isEmpty()) {
                Text("Сделайте первую запись, чтобы увидеть график.", color = MoodColors.Muted)
            } else {
                MetricChartSection(
                    title = "Настроение",
                    kind = ScaleKind.Mood,
                    days = days,
                    values = days.map { byDate[it]?.mood },
                )
                MetricChartSection(
                    title = "Сон",
                    kind = ScaleKind.Sleep,
                    days = days,
                    values = days.map { byDate[it]?.sleep },
                )
                CombinedMetricsChartSection(days = days, byDate = byDate)
            }
        }
    }
}

@Composable
private fun SegmentedRangeHeader(selectedDays: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppCardShape)
            .border(1.dp, MoodColors.Border, AppCardShape)
            .background(MoodColors.Surface),
    ) {
        RangeSegment(label = "7 дней", value = 7, selectedDays = selectedDays, onSelect = onSelect)
        RangeSegment(label = "14 дней", value = 14, selectedDays = selectedDays, onSelect = onSelect)
    }
}

@Composable
private fun RowScope.RangeSegment(
    label: String,
    value: Int,
    selectedDays: Int,
    onSelect: (Int) -> Unit,
) {
    val selected = selectedDays == value
    Text(
        text = label,
        modifier = Modifier
            .weight(1f)
            .clip(AppCardShape)
            .background(if (selected) MoodColors.AccentSoft else Color.Transparent)
            .clickable { onSelect(value) }
            .padding(vertical = 9.dp),
        color = if (selected) MoodColors.AccentDark else MoodColors.Muted,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        fontSize = 13.sp,
    )
}

@Composable
private fun MetricChartSection(
    title: String,
    kind: ScaleKind,
    days: List<LocalDate>,
    values: List<Int?>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChartAxisIcons(kind = kind)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MetricLineChart(kind = kind, values = values)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    days.forEach { day ->
                        Text(
                            day.dayOfMonth.toString(),
                            modifier = Modifier.width(22.dp),
                            color = MoodColors.Muted,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartAxisIcons(kind: ScaleKind) {
    Column(
        modifier = Modifier
            .height(120.dp)
            .width(22.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        (5 downTo 1).forEach { value ->
            ScaleCircle(kind = kind, value = value, size = 18.dp)
        }
    }
}

@Composable
private fun MetricLineChart(kind: ScaleKind, values: List<Int?>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
    ) {
        val left = 6.dp.toPx()
        val right = size.width - 6.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        val chartHeight = bottom - top
        val chartWidth = right - left
        val lineColor = when (kind) {
            ScaleKind.Mood -> MoodColors.Mood[5] ?: MoodColors.Accent
            ScaleKind.Sleep -> MoodColors.Sleep[4] ?: MoodColors.Accent
            else -> scaleColor(kind, 4)
        }

        (1..5).forEach { level ->
            val y = bottom - (level - 1) / 4f * chartHeight
            drawLine(
                color = MoodColors.Border.copy(alpha = 0.72f),
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        val points = values.mapIndexedNotNull { index, value ->
            value?.let {
                val x = left + index / (values.size - 1).toFloat() * chartWidth
                val y = bottom - (it - 1) / 4f * chartHeight
                Offset(x, y) to it
            }
        }

        if (points.size >= 2) {
            val fill = Path().apply {
                moveTo(points.first().first.x, bottom)
                points.forEach { (point, _) -> lineTo(point.x, point.y) }
                lineTo(points.last().first.x, bottom)
                close()
            }
            drawPath(fill, color = lineColor.copy(alpha = if (kind == ScaleKind.Sleep) 0.10f else 0.06f))
        }

        var previous: Offset? = null
        values.forEachIndexed { index, value ->
            if (value == null) {
                previous = null
            } else {
                val point = Offset(
                    x = left + index / (values.size - 1).toFloat() * chartWidth,
                    y = bottom - (value - 1) / 4f * chartHeight,
                )
                previous?.let {
                    drawLine(
                        color = lineColor,
                        start = it,
                        end = point,
                        strokeWidth = 2.4.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                drawCircle(
                    color = scaleColor(kind, value),
                    radius = 4.2.dp.toPx(),
                    center = point,
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.dp.toPx(),
                    center = point,
                )
                previous = point
            }
        }
    }
}

@Composable
private fun CombinedMetricsChartSection(days: List<LocalDate>, byDate: Map<LocalDate, Entry>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Общий график", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        CombinedMetricsChart(days = days, byDate = byDate)
        CombinedChartLegend()
    }
}

@Composable
private fun CombinedMetricsChart(days: List<LocalDate>, byDate: Map<LocalDate, Entry>) {
    val series = listOf(
        CombinedSeries(
            label = "Настроение",
            color = MoodColors.Mood[5] ?: MoodColors.Accent,
            values = days.map { byDate[it]?.mood },
        ),
        CombinedSeries(
            label = "Сон",
            color = MoodColors.Sleep[4] ?: MoodColors.Accent,
            values = days.map { byDate[it]?.sleep },
            dash = floatArrayOf(8f, 6f),
        ),
        CombinedSeries(
            label = "Энергия",
            color = MoodColors.EnergyLine,
            values = days.map { byDate[it]?.energy },
        ),
        CombinedSeries(
            label = "Тревожность",
            color = MoodColors.AnxietyLine,
            values = days.map { byDate[it]?.anxiety },
            dash = floatArrayOf(2f, 5f),
        ),
        CombinedSeries(
            label = "Стресс",
            color = MoodColors.StressLine,
            values = days.map { byDate[it]?.stress },
            dash = floatArrayOf(8f, 4f, 2f, 4f),
        ),
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
    ) {
        val left = 28.dp.toPx()
        val right = size.width - 6.dp.toPx()
        val top = 10.dp.toPx()
        val bottom = size.height - 24.dp.toPx()
        val chartHeight = bottom - top
        val chartWidth = right - left

        (1..5).forEach { level ->
            val y = bottom - (level - 1) / 4f * chartHeight
            drawLine(
                color = MoodColors.Border.copy(alpha = 0.72f),
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        (1..5).forEach { level ->
            val y = bottom - (level - 1) / 4f * chartHeight
            drawContext.canvas.nativeCanvas.drawText(
                level.toString(),
                8.dp.toPx(),
                y + 4.dp.toPx(),
                android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(102, 106, 115)
                    textSize = 10.sp.toPx()
                    isAntiAlias = true
                },
            )
        }

        series.forEach { item ->
            val effect = item.dash?.let { dash ->
                PathEffect.dashPathEffect(dash.map { it.dp.toPx() }.toFloatArray())
            }
            var previous: Offset? = null
            item.values.forEachIndexed { index, value ->
                if (value == null) {
                    previous = null
                } else {
                    val point = Offset(
                        x = left + index / (item.values.size - 1).toFloat() * chartWidth,
                        y = bottom - (value - 1) / 4f * chartHeight,
                    )
                    previous?.let {
                        drawLine(
                            color = item.color,
                            start = it,
                            end = point,
                            strokeWidth = item.strokeWidth.dp.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = effect,
                        )
                    }
                    drawCircle(
                        color = item.color,
                        radius = 3.2.dp.toPx(),
                        center = point,
                    )
                    previous = point
                }
            }
        }

        days.forEachIndexed { index, day ->
            val x = left + index / (days.size - 1).toFloat() * chartWidth
            drawContext.canvas.nativeCanvas.drawText(
                day.dayOfMonth.toString(),
                x - 5.dp.toPx(),
                size.height - 4.dp.toPx(),
                android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(102, 106, 115)
                    textSize = 10.sp.toPx()
                    isAntiAlias = true
                },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun CombinedChartLegend() {
    val items = listOf(
        "● настроение" to (MoodColors.Mood[5] ?: MoodColors.Accent),
        "– – сон" to (MoodColors.Sleep[4] ?: MoodColors.Accent),
        "● энергия" to MoodColors.EnergyLine,
        ". . тревожность" to MoodColors.AnxietyLine,
        "-· стресс" to MoodColors.StressLine,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { (label, color) ->
            Text(label, color = color, fontSize = 12.sp)
        }
    }
}

private data class CombinedSeries(
    val label: String,
    val color: Color,
    val values: List<Int?>,
    val dash: FloatArray? = null,
    val strokeWidth: Float = 1.8f,
)

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FeedbackRow(onFeedback: (String) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 420.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(modifier = Modifier.fillMaxWidth(), onClick = { onFeedback("useful") }) {
                    FeedbackButtonText("Полезно")
                }
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { onFeedback("not_useful") }) {
                    FeedbackButtonText("Не\u00A0полезно")
                }
                TextButton(modifier = Modifier.fillMaxWidth(), onClick = { onFeedback("skipped") }) {
                    FeedbackButtonText("Пропустить")
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(modifier = Modifier.weight(1f), onClick = { onFeedback("useful") }) {
                    FeedbackButtonText("Полезно")
                }
                OutlinedButton(modifier = Modifier.weight(1f), onClick = { onFeedback("not_useful") }) {
                    FeedbackButtonText("Не\u00A0полезно")
                }
                TextButton(modifier = Modifier.weight(1f), onClick = { onFeedback("skipped") }) {
                    FeedbackButtonText("Пропустить")
                }
            }
        }
    }
}

@Composable
private fun FeedbackButtonText(text: String) {
    Text(text = text, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
}

@Composable
private fun HistoryItem(entry: Entry, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) MoodColors.AccentSoft else MoodColors.Surface),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ScaleCircle(ScaleKind.Mood, entry.mood, size = 30.dp)
            ScaleCircle(ScaleKind.Sleep, entry.sleep, size = 30.dp)
            Column(Modifier.weight(1f)) {
                Text(formatDate(entry.date), fontWeight = FontWeight.SemiBold)
                Text(
                    "Настроение: ${scaleLabel(ScaleKind.Mood, entry.mood)} · Сон: ${scaleLabel(ScaleKind.Sleep, entry.sleep)}",
                    color = MoodColors.Muted,
                    fontSize = 13.sp,
                )
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
            Text("Настроение: ${scaleLabel(ScaleKind.Mood, entry.mood)}")
            Text("Энергия: ${scaleLabel(ScaleKind.Energy, entry.energy)}")
            Text("Тревожность: ${scaleLabel(ScaleKind.Anxiety, entry.anxiety)}")
            Text("Стресс: ${scaleLabel(ScaleKind.Stress, entry.stress)}")
            Text("Сон: ${scaleLabel(ScaleKind.Sleep, entry.sleep)}")
            Text("Теги: ${entry.tags.mapNotNull { id -> Tags.firstOrNull { it.id == id }?.label }.ifEmpty { listOf("нет") }.joinToString(", ")}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onEdit) { Text("Редактировать") }
                OutlinedButton(onClick = onDelete) { Text("Удалить") }
            }
        }
    }
}

@Composable
private fun BottomNav(
    activeScreen: Screen,
    onHome: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    Surface(color = MoodColors.Surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BottomNavItem(
                icon = "⌂",
                selected = activeScreen == Screen.Home,
                onClick = onHome,
                modifier = Modifier.weight(1f),
            )
            BottomNavItem(
                icon = "▤",
                selected = activeScreen == Screen.History,
                onClick = onHistory,
                modifier = Modifier.weight(1f),
            )
            BottomNavItem(
                icon = "⚙",
                selected = activeScreen == Screen.Settings,
                onClick = onSettings,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) MoodColors.AccentDark else MoodColors.Muted
    Column(
        modifier = modifier
            .height(44.dp)
            .clip(AppCardShape)
            .background(if (selected) MoodColors.AccentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(icon, fontSize = 23.sp, color = contentColor, lineHeight = 23.sp)
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
private fun InfoCard(
    text: String,
    icon: String? = null,
    containerColor: Color = MoodColors.Surface,
) {
    Card(colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            icon?.let {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MoodColors.AccentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(it, color = MoodColors.AccentDark, fontSize = 18.sp)
                }
            }
            Text(
                text,
                modifier = Modifier.weight(1f),
                color = MoodColors.Text,
                lineHeight = 22.sp,
            )
        }
    }
}

@Composable
private fun ScaleCircle(kind: ScaleKind, value: Int, size: Dp = 30.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(scaleColor(kind, value)),
        contentAlignment = Alignment.Center,
    ) {
        ScaleGlyph(
            kind = kind,
            value = value,
            modifier = Modifier
                .fillMaxSize()
                .padding(if (size < 26.dp) 4.dp else 6.dp),
        )
    }
}

@Composable
private fun ScaleGlyph(
    kind: ScaleKind,
    value: Int,
    modifier: Modifier = Modifier,
    cutoutColor: Color = scaleColor(kind, value),
) {
    when (kind) {
        ScaleKind.Mood -> MoodFaceGlyph(value = value, modifier = modifier)
        ScaleKind.Energy -> BatteryGlyph(value = value, modifier = modifier)
        ScaleKind.Sleep -> SleepGlyph(value = value, modifier = modifier, cutoutColor = cutoutColor)
        ScaleKind.Anxiety -> AnxietyWeatherGlyph(value = value, modifier = modifier)
        ScaleKind.Stress -> StressHeadGlyph(value = value, modifier = modifier)
        else -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(scaleOption(kind, value).icon, fontSize = 16.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun MoodFaceGlyph(value: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val ink = Color(0xFF38404C).copy(alpha = 0.88f)
        val stroke = size.minDimension * 0.07f
        val eyeRadius = size.minDimension * 0.045f
        val leftEye = Offset(size.width * 0.34f, size.height * 0.36f)
        val rightEye = Offset(size.width * 0.66f, size.height * 0.36f)

        if (value == 2) {
            drawArc(
                color = ink,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(size.width * 0.25f, size.height * 0.28f),
                size = Size(size.width * 0.18f, size.height * 0.18f),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = ink,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(size.width * 0.57f, size.height * 0.28f),
                size = Size(size.width * 0.18f, size.height * 0.18f),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        } else {
            drawCircle(ink, radius = eyeRadius, center = leftEye)
            drawCircle(ink, radius = eyeRadius, center = rightEye)
        }

        when (value) {
            5 -> drawMoodArc(ink, stroke, top = 0.44f, start = 18f, sweep = 144f)
            4 -> drawMoodArc(ink, stroke, top = 0.47f, start = 28f, sweep = 124f)
            3 -> drawLine(
                color = ink,
                start = Offset(size.width * 0.38f, size.height * 0.64f),
                end = Offset(size.width * 0.62f, size.height * 0.64f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            2 -> drawLine(
                color = ink,
                start = Offset(size.width * 0.42f, size.height * 0.62f),
                end = Offset(size.width * 0.58f, size.height * 0.62f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            else -> drawMoodArc(ink, stroke, top = 0.58f, start = 198f, sweep = 144f)
        }
    }
}

private fun DrawScope.drawMoodArc(color: Color, stroke: Float, top: Float, start: Float, sweep: Float) {
    drawArc(
        color = color,
        startAngle = start,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(size.width * 0.32f, size.height * top),
        size = Size(size.width * 0.36f, size.height * 0.24f),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
}

@Composable
private fun BatteryGlyph(value: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val ink = Color(0xFF38404C).copy(alpha = 0.88f)
        val stroke = size.minDimension * 0.075f
        val bodyLeft = size.width * 0.12f
        val bodyTop = size.height * 0.31f
        val bodyWidth = size.width * 0.68f
        val bodyHeight = size.height * 0.38f
        val terminalWidth = size.width * 0.08f
        val terminalHeight = bodyHeight * 0.42f
        val corner = size.minDimension * 0.08f
        val fillRatio = ((value - 1) / 4f).coerceIn(0f, 1f)
        val innerPadding = stroke * 1.55f
        val innerWidth = (bodyWidth - innerPadding * 2f) * fillRatio

        drawRoundRect(
            color = ink.copy(alpha = 0.90f),
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = stroke),
        )
        drawRoundRect(
            color = ink.copy(alpha = 0.90f),
            topLeft = Offset(bodyLeft + bodyWidth + stroke * 0.30f, bodyTop + (bodyHeight - terminalHeight) / 2f),
            size = Size(terminalWidth, terminalHeight),
            cornerRadius = CornerRadius(corner * 0.55f, corner * 0.55f),
        )
        if (innerWidth > 0f) {
            drawRoundRect(
                color = ink.copy(alpha = 0.90f),
                topLeft = Offset(bodyLeft + innerPadding, bodyTop + innerPadding),
                size = Size(innerWidth, bodyHeight - innerPadding * 2f),
                cornerRadius = CornerRadius(corner * 0.55f, corner * 0.55f),
            )
        }
    }
}

@Composable
private fun SleepGlyph(
    value: Int,
    modifier: Modifier = Modifier,
    cutoutColor: Color = scaleColor(ScaleKind.Sleep, value),
) {
    Canvas(modifier = modifier) {
        when (value) {
            5 -> drawSunGlyph()
            4,
            3 -> drawCloudGlyph()
            2 -> drawMoonGlyph(cutoutColor, withStars = false)
            else -> drawMoonGlyph(cutoutColor, withStars = true)
        }
    }
}

private fun DrawScope.drawSunGlyph() {
    val white = Color.White.copy(alpha = 0.94f)
    val center = Offset(size.width * 0.5f, size.height * 0.5f)
    val radius = size.minDimension * 0.17f
    drawCircle(white, radius = radius, center = center)
    val inner = size.minDimension * 0.31f
    val outer = size.minDimension * 0.41f
    val stroke = size.minDimension * 0.055f
    listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f).forEach { angle ->
        val radians = Math.toRadians(angle.toDouble())
        val start = Offset(
            x = center.x + kotlin.math.cos(radians).toFloat() * inner,
            y = center.y + kotlin.math.sin(radians).toFloat() * inner,
        )
        val end = Offset(
            x = center.x + kotlin.math.cos(radians).toFloat() * outer,
            y = center.y + kotlin.math.sin(radians).toFloat() * outer,
        )
        drawLine(white, start, end, strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawCloudGlyph() {
    val white = Color.White.copy(alpha = 0.94f)
    drawRoundRect(
        color = white,
        topLeft = Offset(size.width * 0.24f, size.height * 0.51f),
        size = Size(size.width * 0.52f, size.height * 0.20f),
        cornerRadius = CornerRadius(size.minDimension * 0.10f, size.minDimension * 0.10f),
    )
    drawCircle(white, radius = size.minDimension * 0.15f, center = Offset(size.width * 0.39f, size.height * 0.51f))
    drawCircle(white, radius = size.minDimension * 0.19f, center = Offset(size.width * 0.55f, size.height * 0.46f))
    drawCircle(white, radius = size.minDimension * 0.13f, center = Offset(size.width * 0.68f, size.height * 0.53f))
}

private fun DrawScope.drawMoonGlyph(cutoutColor: Color, withStars: Boolean) {
    val white = Color.White.copy(alpha = 0.95f)
    drawCircle(white, radius = size.minDimension * 0.31f, center = Offset(size.width * 0.48f, size.height * 0.47f))
    drawCircle(cutoutColor, radius = size.minDimension * 0.30f, center = Offset(size.width * 0.61f, size.height * 0.39f))
    if (withStars) {
        drawStar(center = Offset(size.width * 0.77f, size.height * 0.31f), radius = size.minDimension * 0.08f, color = white)
        drawStar(center = Offset(size.width * 0.73f, size.height * 0.68f), radius = size.minDimension * 0.055f, color = white)
    }
}

private fun DrawScope.drawStar(center: Offset, radius: Float, color: Color) {
    drawLine(
        color = color,
        start = Offset(center.x - radius, center.y),
        end = Offset(center.x + radius, center.y),
        strokeWidth = radius * 0.28f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color,
        start = Offset(center.x, center.y - radius),
        end = Offset(center.x, center.y + radius),
        strokeWidth = radius * 0.28f,
        cap = StrokeCap.Round,
    )
}

@Composable
private fun AnxietyWeatherGlyph(value: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        when (value) {
            1 -> drawAnxietySun()
            2 -> drawPartlyCloudy()
            3 -> {
                drawCloudGlyph()
                drawRainDrops(count = 2)
            }
            4 -> {
                drawCloudGlyph()
                drawRainDrops(count = 4)
            }
            else -> {
                drawCloudGlyph()
                drawRainDrops(count = 3)
                drawLightningGlyph()
            }
        }
    }
}

private fun DrawScope.drawAnxietySun() {
    val white = Color.White.copy(alpha = 0.95f)
    val center = Offset(size.width * 0.5f, size.height * 0.5f)
    val radius = size.minDimension * 0.18f
    drawCircle(white, radius = radius, center = center)
    val inner = size.minDimension * 0.31f
    val outer = size.minDimension * 0.42f
    val stroke = size.minDimension * 0.055f
    listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f).forEach { angle ->
        val radians = Math.toRadians(angle.toDouble())
        drawLine(
            color = white,
            start = Offset(
                x = center.x + kotlin.math.cos(radians).toFloat() * inner,
                y = center.y + kotlin.math.sin(radians).toFloat() * inner,
            ),
            end = Offset(
                x = center.x + kotlin.math.cos(radians).toFloat() * outer,
                y = center.y + kotlin.math.sin(radians).toFloat() * outer,
            ),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawPartlyCloudy() {
    val white = Color.White.copy(alpha = 0.95f)
    val sunCenter = Offset(size.width * 0.36f, size.height * 0.38f)
    drawCircle(white, radius = size.minDimension * 0.13f, center = sunCenter)
    val rayInner = size.minDimension * 0.21f
    val rayOuter = size.minDimension * 0.28f
    val rayStroke = size.minDimension * 0.045f
    listOf(180f, 225f, 270f, 315f).forEach { angle ->
        val radians = Math.toRadians(angle.toDouble())
        drawLine(
            color = white,
            start = Offset(
                x = sunCenter.x + kotlin.math.cos(radians).toFloat() * rayInner,
                y = sunCenter.y + kotlin.math.sin(radians).toFloat() * rayInner,
            ),
            end = Offset(
                x = sunCenter.x + kotlin.math.cos(radians).toFloat() * rayOuter,
                y = sunCenter.y + kotlin.math.sin(radians).toFloat() * rayOuter,
            ),
            strokeWidth = rayStroke,
            cap = StrokeCap.Round,
        )
    }
    drawCloudGlyph()
}

private fun DrawScope.drawRainDrops(count: Int) {
    val white = Color.White.copy(alpha = 0.95f)
    val stroke = size.minDimension * 0.065f
    val positions = when (count) {
        2 -> listOf(0.40f, 0.60f)
        3 -> listOf(0.36f, 0.52f, 0.68f)
        else -> listOf(0.31f, 0.45f, 0.59f, 0.73f)
    }
    positions.forEachIndexed { index, x ->
        val top = if (index % 2 == 0) 0.68f else 0.63f
        drawLine(
            color = white,
            start = Offset(size.width * x, size.height * top),
            end = Offset(size.width * (x - 0.04f), size.height * (top + 0.13f)),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawLightningGlyph() {
    val white = Color.White.copy(alpha = 0.95f)
    val bolt = Path().apply {
        moveTo(size.width * 0.54f, size.height * 0.56f)
        lineTo(size.width * 0.43f, size.height * 0.79f)
        lineTo(size.width * 0.55f, size.height * 0.77f)
        lineTo(size.width * 0.47f, size.height * 0.98f)
        lineTo(size.width * 0.72f, size.height * 0.68f)
        lineTo(size.width * 0.59f, size.height * 0.70f)
        close()
    }
    drawPath(path = bolt, color = white)
}

@Composable
private fun StressHeadGlyph(value: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val ink = Color(0xFF38404C).copy(alpha = 0.88f)
        val stroke = size.minDimension * 0.075f
        val headCenter = Offset(size.width * 0.50f, size.height * 0.32f)
        val headRadius = size.minDimension * 0.15f

        drawCircle(
            color = ink,
            radius = headRadius,
            center = headCenter,
            style = Stroke(width = stroke),
        )

        val shoulders = Path().apply {
            moveTo(size.width * 0.23f, size.height * 0.78f)
            cubicTo(
                size.width * 0.25f,
                size.height * 0.61f,
                size.width * 0.34f,
                size.height * 0.54f,
                size.width * 0.42f,
                size.height * 0.50f,
            )
            moveTo(size.width * 0.58f, size.height * 0.50f)
            cubicTo(
                size.width * 0.66f,
                size.height * 0.54f,
                size.width * 0.75f,
                size.height * 0.61f,
                size.width * 0.77f,
                size.height * 0.78f,
            )
        }
        drawPath(
            path = shoulders,
            color = ink,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawLine(
            color = ink,
            start = Offset(size.width * 0.22f, size.height * 0.78f),
            end = Offset(size.width * 0.78f, size.height * 0.78f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )

        val stressMarks = listOf(
            listOf(
                Offset(size.width * 0.25f, size.height * 0.25f),
                Offset(size.width * 0.17f, size.height * 0.18f),
                Offset(size.width * 0.24f, size.height * 0.12f),
            ),
            listOf(
                Offset(size.width * 0.77f, size.height * 0.24f),
                Offset(size.width * 0.86f, size.height * 0.17f),
                Offset(size.width * 0.78f, size.height * 0.10f),
            ),
            listOf(
                Offset(size.width * 0.22f, size.height * 0.50f),
                Offset(size.width * 0.12f, size.height * 0.48f),
                Offset(size.width * 0.18f, size.height * 0.39f),
            ),
            listOf(
                Offset(size.width * 0.80f, size.height * 0.55f),
                Offset(size.width * 0.91f, size.height * 0.51f),
                Offset(size.width * 0.84f, size.height * 0.42f),
            ),
        )

        stressMarks.take((value - 1).coerceIn(0, stressMarks.size)).forEach { points ->
            drawZigzag(points = points, color = ink, stroke = stroke)
        }
    }
}

private fun DrawScope.drawZigzag(points: List<Offset>, color: Color, stroke: Float) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { point -> lineTo(point.x, point.y) }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
}

private fun scaleColor(kind: ScaleKind, value: Int): Color = when (kind) {
    ScaleKind.Mood -> MoodColors.Mood[value] ?: MoodColors.Accent
    ScaleKind.Sleep -> MoodColors.Sleep[value] ?: MoodColors.Accent
    ScaleKind.Energy -> MoodColors.Energy[value] ?: MoodColors.Accent
    ScaleKind.Anxiety -> MoodColors.Anxiety[value] ?: MoodColors.AnxietyLine
    ScaleKind.Stress -> MoodColors.Stress[value] ?: MoodColors.StressLine
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

private fun datesBetween(startDate: LocalDate, endDate: LocalDate): List<LocalDate> {
    val first = if (startDate.isBefore(endDate)) startDate else endDate
    val last = if (startDate.isBefore(endDate)) endDate else startDate
    val dates = mutableListOf<LocalDate>()
    var cursor = first
    while (!cursor.isAfter(last) && dates.size <= MAX_DEMO_DAYS) {
        dates += cursor
        cursor = cursor.plusDays(1)
    }
    return dates
}

private fun buildDemoDrafts(
    dates: List<LocalDate>,
    preset: DemoPreset,
    manualMood: Int,
    manualEnergy: Int,
    manualAnxiety: Int,
    manualStress: Int,
    manualSleep: Int,
    tags: Set<String>,
): List<DraftEntry> = dates.mapIndexed { index, date ->
    val values = when (preset) {
        DemoPreset.SleepMoodPattern -> {
            val sleepPattern = listOf(2, 2, 3, 4, 5, 4, 2)
            val sleep = sleepPattern[index % sleepPattern.size]
            DemoValues(
                mood = when (sleep) {
                    5 -> 5
                    4 -> 4
                    3 -> 3
                    else -> 2
                },
                energy = when (sleep) {
                    5 -> 5
                    4 -> 4
                    3 -> 3
                    else -> 2
                },
                anxiety = when (sleep) {
                    5 -> 1
                    4 -> 2
                    3 -> 3
                    else -> 4
                },
                stress = when (sleep) {
                    5 -> 1
                    4 -> 2
                    3 -> 3
                    else -> 4
                },
                sleep = sleep,
            )
        }
        DemoPreset.StressfulPeriod -> {
            val stress = listOf(3, 4, 5, 5, 4, 3, 2)[index % 7]
            DemoValues(
                mood = (6 - stress).coerceIn(1, 5),
                energy = (6 - stress).coerceIn(1, 5),
                anxiety = stress,
                stress = stress,
                sleep = (6 - stress).coerceIn(1, 5),
            )
        }
        DemoPreset.StableGood -> DemoValues(
            mood = if (index % 5 == 0) 4 else 5,
            energy = if (index % 4 == 0) 4 else 5,
            anxiety = if (index % 6 == 0) 2 else 1,
            stress = if (index % 4 == 0) 2 else 1,
            sleep = if (index % 3 == 0) 4 else 5,
        )
        DemoPreset.Manual -> DemoValues(
            mood = manualMood,
            energy = manualEnergy,
            anxiety = manualAnxiety,
            stress = manualStress,
            sleep = manualSleep,
        )
    }
    DraftEntry(
        date = date,
        mood = values.mood,
        energy = values.energy,
        anxiety = values.anxiety,
        stress = values.stress,
        sleep = values.sleep,
        tags = tags,
    )
}

private data class DemoValues(
    val mood: Int,
    val energy: Int,
    val anxiety: Int,
    val stress: Int,
    val sleep: Int,
)

private fun demoPresetLabel(preset: DemoPreset): String = when (preset) {
    DemoPreset.SleepMoodPattern -> "Сон -> настроение"
    DemoPreset.StressfulPeriod -> "Напряжённая неделя"
    DemoPreset.StableGood -> "Хороший ритм"
    DemoPreset.Manual -> "Вручную"
}

private fun demoPresetDescription(preset: DemoPreset): String = when (preset) {
    DemoPreset.SleepMoodPattern ->
        "Создаёт контраст: после плохого сна настроение, энергия и спокойствие ниже. Удобно для показа первого инсайта."
    DemoPreset.StressfulPeriod ->
        "Показывает период перегруза: выше стресс и тревожность, ниже сон, энергия и настроение."
    DemoPreset.StableGood ->
        "Заполняет спокойный стабильный период с хорошим сном и настроением."
    DemoPreset.Manual ->
        "Использует одинаковые значения шкал для всех выбранных дат."
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

private fun insightPreviewText(insight: Insight): String = when (insight.status) {
    InsightStatus.NotEnoughData ->
        "Пока собираем основу: настроение, сон и контекст дня. Инсайт появится после 3 записей."
    InsightStatus.Waiting ->
        "Первые записи уже есть. Первый инсайт появится после 5 записей."
    InsightStatus.Found ->
        "Есть осторожное наблюдение по твоим последним записям. Открой, чтобы увидеть объяснение и маленькое действие."
    InsightStatus.NoClearPattern ->
        "Явной связи между сном и настроением пока не видно. Открой, чтобы понять, что именно проверялось."
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

private fun scaleOption(kind: ScaleKind, value: Int): ScaleOption = when (kind) {
    ScaleKind.Mood -> when (value) {
        5 -> ScaleOption("☺", "очень хорошо")
        4 -> ScaleOption("◡", "хорошо")
        3 -> ScaleOption("–", "спокойно")
        2 -> ScaleOption("⌣", "грустное")
        else -> ScaleOption("⌢", "тяжёлое")
    }
    ScaleKind.Energy -> when (value) {
        5 -> ScaleOption("", "полный заряд")
        4 -> ScaleOption("", "хороший заряд")
        3 -> ScaleOption("", "средне")
        2 -> ScaleOption("", "мало сил")
        else -> ScaleOption("", "нет сил")
    }
    ScaleKind.Anxiety -> when (value) {
        5 -> ScaleOption("", "максимальная")
        4 -> ScaleOption("", "сильная")
        3 -> ScaleOption("", "заметная")
        2 -> ScaleOption("", "лёгкая")
        else -> ScaleOption("", "спокойно")
    }
    ScaleKind.Stress -> when (value) {
        5 -> ScaleOption("", "сильный")
        4 -> ScaleOption("", "перегруз")
        3 -> ScaleOption("", "заметный")
        2 -> ScaleOption("", "лёгкий")
        else -> ScaleOption("", "нет стресса")
    }
    ScaleKind.Sleep -> when (value) {
        5 -> ScaleOption("☼", "отличный")
        4 -> ScaleOption("☁", "хороший")
        3 -> ScaleOption("☁", "средний")
        2 -> ScaleOption("☾", "плохой")
        else -> ScaleOption("☽", "очень плохой")
    }
}

private fun valuesForScale(kind: ScaleKind): IntProgression = when (kind) {
    ScaleKind.Sleep,
    ScaleKind.Anxiety,
    ScaleKind.Stress -> 5 downTo 1
    else -> 1..5
}

private fun scaleLabel(kind: ScaleKind, value: Int): String = scaleOption(kind, value).label

private fun recordWord(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "записей"
        mod10 == 1 -> "запись"
        mod10 in 2..4 -> "записи"
        else -> "записей"
    }
}

private fun formatDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru")))

private fun formatShortDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.forLanguageTag("ru")))

@Preview(showBackground = true)
@Composable
private fun OnboardingPreview() {
    MoodDiaryTheme {
        OnboardingScreen(onStart = {})
    }
}
