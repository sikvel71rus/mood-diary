# tasks.md — Список задач реализации Mood Diary MLP

> **Место в цикле:** `spec.md` (ЧТО) → `plan.md` (КАК) → **`tasks.md` (ЧТО ДЕЛАТЬ)** → код.
> Версия / дата: v1.0 / 2026-07-16. Основано на: `spec.md` v0.2, `plan.md` v1.0.

---

## Раздел 1. Каркас и данные

### T-1 · Инициализировать Android-проект

**Зачем / связь:** Каркас (plan §1, §3.3); закрывает нефункциональные требования spec §10 (платформа Android, minSdk 29, офлайн, нет аналитических SDK).

**Что сделать:**
Создать Android-проект (Kotlin, minSdk 29). Добавить в `build.gradle` зависимости из таблицы plan §5: Compose BOM, Hilt + kapt/ksp, Room, Vico 2.x, Navigation Compose, DataStore Preferences, kotlinx-datetime, JUnit 4 + Kotlin Test. Создать директорную структуру строго по plan §3.3 (`data/`, `domain/`, `ui/`, `di/`, `navigation/`).

**Готово, когда:** `./gradlew assembleDebug` завершается без ошибок; все зависимости в `build.gradle` соответствуют таблице plan §5; в проекте нет Firebase, Amplitude, Adjust или любых аналитических SDK.

**Зависит от:** —

---

### T-2 · Определить Room-схему (сущности и база)

**Зачем / связь:** Каркас (plan §3.1); фундамент для всех P0-историй без исключения.

**Что сделать:**
Создать Room-сущности: `EntryEntity`, `TagEntity`, `EntryTagCrossRef`, `InsightFeedbackEntity`, `AppEventEntity` — строго по полям plan §3.1. Создать `MoodDatabase` (Room, версия 1, все пять сущностей). Добавить `UNIQUE INDEX` на `entries.date` (одна запись в день, spec §4.1). Поле `comment` в `EntryEntity` — `TEXT NULL`, резервируется для P1.

**Готово, когда:** `MoodDatabase` компилируется; Room генерирует `schema.json`; `EntryEntity` содержит поле `date TEXT` с `UNIQUE` constraint.

**Зависит от:** T-1

---

### T-3 · Создать DAO, Repository и seeding тегов

**Зачем / связь:** Каркас (plan §2 Data layer); нужен для всех историй работы с записями и аналитикой.

**Что сделать:**
Реализовать `EntryDao`: upsert с `OnConflictStrategy.REPLACE`, `Flow`-запрос последних N записей (для инсайта), запрос последних 14 (для истории), `delete(id)`, `deleteAll()`. Реализовать `InsightFeedbackDao` (insert, aggregate count по `value`) и `AppEventDao` (insert, count по `event_type`). Реализовать `EntryRepository`, `InsightFeedbackRepository`, `AppEventRepository`. Добавить pre-seeding 12 тегов из spec §4.2 через `RoomDatabase.Callback` при первом открытии базы.

**Готово, когда:** Unit-тест с in-memory Room: сохранить запись → Flow эмитирует её; удалить запись → `entry_tags` удалены каскадом; после первого открытия база содержит ровно 12 тегов.

**Зависит от:** T-2

---

### T-4 · Определить domain-модели (Kotlin data classes)

**Зачем / связь:** Каркас (plan §3.2); нужен UseCase-слою, не содержит Android-зависимостей.

**Что сделать:**
Создать data classes: `Entry` (включает `List<Tag>`, все поля spec §4.1 кроме `comment`), `Tag` (id, labelRu, category), `Insight` (status: enum, subtype: enum, message: String, confidenceLabel: String?, entriesUsedCount: Int, secondaryObservations: List<SecondaryObservation>), `SecondaryObservation` (scale, direction), `Recommendation` (message: String), `InsightFeedback`. Добавить mapper-функции `EntryEntity + List<TagEntity> → Entry`.

**Готово, когда:** Все классы компилируются без Android-зависимостей; mapper корректно собирает `Entry` из пары entity + теги.

**Зависит от:** T-2

---

### T-5 · Настроить Material3 тему и цветовую палитру

**Зачем / связь:** Каркас (plan §3.3 `ui/theme`); закрывает нефункциональные требования spec §10 и §10.1; нужен каждому экрану и компоненту.

**Что сделать:**
Создать `Color.kt` с именованными константами для всех цветов из spec §10.1 и `palette.md`: для mood (MoodSage, MoodAqua, MoodSand, MoodLavender, MoodDeepIndigo), для sleep (SleepSky, SleepBlue, SleepSteelBlue, SleepSlate, SleepMidnight), для серий графика (EnergyGold `#C8A84B`, AnxietyMauve `#C0A8B8`, StressViolet `#9890C4`). Создать `Theme.kt` (`MoodDiaryTheme`). Убедиться: красный (#FF…) отсутствует среди семантических цветов интерфейса.

**Готово, когда:** Тема применяется в `MainActivity`; все именованные hex-константы совпадают со значениями spec §10.1; красный не присутствует ни в одном `ColorScheme`-слоте.

**Зависит от:** T-1

---

### T-6 · Настроить Navigation graph

**Зачем / связь:** Каркас (plan §3.3 `navigation/`); связывает все P0-экраны, фиксирует back stack.

**Что сделать:**
Создать sealed class `Route` (Onboarding, Home, CheckIn с опциональным `entryId`, History, Insight, Settings). Создать `NavGraph` с `NavHost`: зарегистрировать все маршруты как stub-composable (`Text("Screen name")`). Настроить `popUpTo` при переходе `CheckInScreen → HomeScreen` (spec §9: не накапливать back stack). Предусмотреть очистку back stack до `Onboarding` при удалении всех данных (нужен в T-21).

**Готово, когда:** Приложение запускается; навигация между stub-экранами работает; переход CheckIn → Home не накапливает back stack (кнопка «назад» из Home не возвращает в CheckIn).

**Зависит от:** T-1, T-5

---

## Раздел 2. P0-истории

### T-7 · Реализовать OnboardingScreen (US-0)

**Зачем / связь:** US-0 (spec §7.1); первый запуск без барьеров и запросов разрешений.

**Что сделать:**
`OnboardingScreen`: три смысловых блока (краткое описание продукта; «не диагноз и не замена специалисту»; «данные хранятся только на устройстве») + единственная кнопка «Начать» → `CheckIn`. При нажатии записать в DataStore флаг `disclaimerShown = true`. `HomeScreen` (stub): если записей нет И `disclaimerShown == false` → перенаправить на `OnboardingScreen`. Дисклеймер повторно доступен из `SettingsScreen` (заглушка сейчас, контент подключается в T-21).

**Готово, когда:** Первый запуск без записей → `OnboardingScreen`; кнопка «Начать» → `CheckIn`; второй запуск (`disclaimerShown = true`) → `HomeScreen`; приложение не запрашивает разрешений ОС до первого check-in.

**Зависит от:** T-5, T-6

---

### T-8 · Реализовать компоненты RatingRow и TagChip (US-1)

**Зачем / связь:** US-1 (spec §7.1, §10.1); строительные блоки check-in; закрывает визуальный язык — иконка + метка + безопасная палитра.

**Что сделать:**
`RatingRow(label, scale, selected, onSelect)`: горизонтальная строка из 5 кликабельных кругов; каждый круг — иконка из spec §10.1 + текстовая метка под кругом; выбранный уровень — тонкая обводка Accent Color + лёгкое масштабирование; яркость не меняется; красные рамки и анимации ошибки запрещены (spec §10.1).
`TagChip(tag, selected, onToggle)`: chip с состояниями selected/unselected из словаря тегов.

**Готово, когда:** `@Preview` в Android Studio показывает все 5 уровней для mood и sleep с корректными иконками и метками из spec §10.1; выбор уровня меняет состояние; красный цвет не используется ни в каком состоянии.

**Зависит от:** T-5

---

### T-9 · Реализовать CheckInScreen + CheckInViewModel (US-1)

**Зачем / связь:** US-1 (spec §7.1); создание и редактирование записи; главный продуктовый экран.

**Что сделать:**
`CheckInScreen`: 5 `RatingRow` (mood, energy, anxiety, stress, sleep) + `TagGrid` (12 чипов из словаря) + кнопка «Сохранить» (активна когда все 5 оценок выбраны; теги необязательны). `CheckInViewModel`: при открытии с `entryId` — предзаполнить поля из Room; без `entryId` — новая запись за `LocalDate.now(TimeZone.currentSystemDefault())`; `save()` → `EntryRepository.upsert()` → `navigate(Route.Home, popUpTo = Route.Home)`. Проверить: 5 тапов (оценки) + 1 тап «Сохранить» = **6 действий ≤ 7** (spec US-1).

**Готово, когда:** Запись сохраняется в Room; повторный check-in за тот же день предзаполняет форму; отсутствие тегов не блокирует сохранение; после save() — HomeScreen; happy path = 6 обязательных действий.

**Зависит от:** T-3, T-4, T-6, T-8

---

### T-10 · Реализовать CalculateInsightUseCase — основной алгоритм (US-2)

**Зачем / связь:** US-2 (spec §5.1–5.3); ядро продуктовой ценности; plan §7 помечает как риск высокой вероятности — реализовать до написания UI.

**Что сделать:**
`CalculateInsightUseCase.invoke(entries: List<Entry>): Insight`. Логика строго по spec §5:
- взять последние 7 по `date` (или меньше, если записей < 7);
- < 3 записей → `not_enough_data`;
- определить `low_sleep` (sleep ≤ 2) и `high_sleep` (sleep ≥ 4);
- проверить `found/contrast` (есть обе группы, avg(mood|high) − avg(mood|low) ≥ 1.0; для 3–4 записей дополнительный критерий явного контраста);
- проверить `found/stable_low` и `found/stable_high` (ветки spec §5.2 «стабильного состояния»);
- 3–4 записи без явной связи → `waiting`;
- 5+ без связи → `no_clear_pattern` (no_polarity / no_correlation / intermediate);
- заполнить `message` из шаблонов §5.4 (с подстановкой n); `confidenceLabel` по правилу spec §4.3.
Поле `secondaryObservations` пока возвращать как пустой список (заполнит T-12).

**Готово, когда:** Класс компилируется; для каждого из 8+ примеров spec §5.3 UseCase возвращает ожидаемый `status` и `subtype` — проверяется в T-11.

**Зависит от:** T-4

---

### T-11 · JUnit-тесты CalculateInsightUseCase (US-2)

**Зачем / связь:** US-2, plan §7 («реализовать и покрыть JUnit-тестами до написания UI»); обязательно по DoD spec §11.

**Что сделать:**
Написать тесты в `domain/usecase/CalculateInsightUseCaseTest`: все примеры из spec §5.3 (не менее 10 кейсов), включая:
- сон `5 4 5 4 5`, mood `3 4 3 4 3` → `no_clear_pattern / no_polarity`;
- сон `5 5 5`, mood `1 5 3` → `waiting`;
- сон `2 5 2 4 5`, mood `2 4 3 4 5` → `found / contrast`;
- сон `2 1 2 1 2`, mood `2 1 3 2 1` → `found / stable_low`;
- сон `5 4 5 4 5`, mood `4 5 4 4 5` → `found / stable_high`;
- сон `3 3 3 3 3`, mood `3 2 4 3 3` → `no_clear_pattern`;
- сон `5 4 5 4 5`, mood `2 3 2 3 2` → `no_clear_pattern`;
- ровно 2 записи → `not_enough_data`; ровно 3 с явной связью → `found`;
- `no_clear_pattern / no_correlation` (есть обе группы, разница < 1.0).

**Готово, когда:** `./gradlew test` проходит без ошибок; все перечисленные кейсы дают ожидаемый `status + subtype`; покрыты все 6 ветвей алгоритма.

**Зависит от:** T-10

---

### T-12 · Добавить вторичные наблюдения в CalculateInsightUseCase (US-2)

**Зачем / связь:** US-2 (spec §5.3.1); дополнительная ценность инсайта — одно наблюдение по energy/anxiety/stress.

**Что сделать:**
Расширить `CalculateInsightUseCase`: для каждой шкалы (energy, anxiety, stress) рассчитать паттерн по тем же записям по правилам §5.3.1; выбрать одно наблюдение с наибольшей абсолютной разницей средних; приоритет при равенстве: energy > anxiety > stress; при `found/stable_high` — вторичные наблюдения не рассчитываются; при `not_enough_data` — тоже. Добавить соответствующие тест-кейсы.

**Готово, когда:** При 5 записях с явной связью и `avg(energy|high_sleep) − avg(energy|low_sleep) ≥ 1.0` → `secondaryObservations` содержит один элемент `{scale=energy, direction=better_sleep_higher}`; при `found/stable_high` → `secondaryObservations` пустой; тесты проходят.

**Зависит от:** T-11

---

### T-13 · Реализовать GetRecommendationUseCase (US-3)

**Зачем / связь:** US-3 (spec §5.5, §7.3); одна мягкая рекомендация только при найденном паттерне.

**Что сделать:**
`GetRecommendationUseCase.invoke(insight: Insight): String?`. Логика: `found/contrast` → первый шаблон Пула A из spec §5.5; `found/stable_low` → первый шаблон Пула B; `found/stable_high` → первый шаблон Пула C; все остальные статусы → `null`. Текст шаблонов не изменять (spec §5.5, строгие формулировки).

**Готово, когда:** Unit-тест: для каждого из трёх found-подтипов возвращает непустую строку из соответствующего пула; для `waiting`, `not_enough_data`, `no_clear_pattern` возвращает `null`.

**Зависит от:** T-4

---

### T-14 · Реализовать ProgressBlock composable (US-4)

**Зачем / связь:** US-4 (spec §7.4); прогресс до первого инсайта без давления; нужен для сборки HomeScreen (T-20).

**Что сделать:**
`ProgressBlock(insight: Insight, entryCount: Int)`. 0–2 записи: `LinearProgressIndicator(entryCount / 3f)` + текст шаблона `not_enough_data` из §5.4 с подстановкой n = (3 − entryCount). 3–4 записи + `status == waiting`: `LinearProgressIndicator(entryCount / 5f)` + текст шаблона `waiting` из §5.4 с n = (5 − entryCount). При 5+ записях или `status ∉ {not_enough_data, waiting}`: компонент не отображается (`return`). Слова «провал», «стрик», «потерян» — запрещены (spec US-4).

**Готово, когда:** При entryCount=1 — прогресс 1/3, текст «Ещё 2 записи…»; при entryCount=4 + status=waiting — прогресс 4/5, текст «Осталась 1 запись до 5»; при entryCount=5 — компонент невидим.

**Зависит от:** T-4, T-5

---

### T-15 · Реализовать MiniChart composable (US-7)

**Зачем / связь:** US-7 (spec §7.7); компактный график 5 серий за 7 дней; нужен для сборки HomeScreen (T-20).

**Что сделать:**
`MiniChart(entries: Map<LocalDate, Entry?>, last7Days: List<LocalDate>)`. Vico `CartesianChartHost`, ось X — 7 дат, ось Y — 1..5. Пять `LineSpec` по таблице plan §4:

| Серия | Цвет | Стиль | Толщина |
|---|---|---|---|
| mood | #8EB69B | сплошная | 2.5dp |
| sleep | #84BDE8 | пунктир | 2dp |
| energy | #C8A84B | сплошная | 1.5dp |
| anxiety | #C0A8B8 | пунктир тонкий | 1.5dp |
| stress | #9890C4 | штрих-пунктир | 1.5dp |

`null` для дат без записи → Vico показывает разрыв (spec US-7). Легенда под графиком: 5 строк «цвет + стиль + метка» (доступность: не только цвет). Высота графика ≤ 180dp, легенда ≤ 32dp. 0 записей → `EmptyChartPlaceholder`. 1 запись → одиночные точки по всем сериям.

**Готово, когда:** С тремя записями за разные дни видны разрывы в нужных позициях; 0 записей — пустое состояние без краша; легенда содержит 5 различимых элементов (цвет + стиль); красный цвет в `LineSpec` отсутствует.

**Зависит от:** T-5

---

### T-16 · Реализовать InsightCard + InsightScreen (US-2, US-3)

**Зачем / связь:** US-2, US-3 (spec §7.2, §7.3, §6.1); главный UI инсайта, рекомендации и вторичных наблюдений.

**Что сделать:**
`InsightCard` (на HomeScreen): краткий статус-текст, `confidenceLabel` при `status == found`; тап → `InsightScreen`.
`InsightScreen`: полный текст шаблона из §5.4 (все статусы и подтипы), одна строка вторичного наблюдения из шаблонов §5.4 (если `secondaryObservations` непуст), «использовано N записей».
`RecommendationCard` (на `InsightScreen`): отображается только при `status == found`; текст из `GetRecommendationUseCase`; цвет карточки — светло-бежевый / светло-голубой / светло-лавандовый (spec §10.1); без диагнозов и категоричных выводов.

**Готово, когда:** При `found` → `InsightCard` показывает статус + `confidenceLabel`; `InsightScreen` — полный текст + вторичное наблюдение (если есть) + `RecommendationCard`; при `not_enough_data` — только шаблон, рекомендации нет; при `no_clear_pattern` — шаблон без рекомендации.

**Зависит от:** T-12, T-13, T-5, T-6

---

### T-17 · Реализовать FeedbackRow + логирование AppEvent (US-6)

**Зачем / связь:** US-6 (spec §7.6, §4.5, §4.6); оценка полезности + аналитика для проверки гипотезы.

**Что сделать:**
`FeedbackRow` на `InsightScreen`: три кнопки «Полезно» / «Не полезно» / «Пропустить»; отображается только при `status == found`. После «Полезно» / «Не полезно»: кнопки скрываются, показывается текст-подтверждение из §5.4 (без навязывания продолжения). «Пропустить»: `FeedbackRow` скрывается без подтверждения. `InsightFeedbackRepository.save(value)` — сохраняет только enum-значение, без личных текстов. При открытии `InsightScreen`: `AppEventRepository.logEvent("insight_screen_opened")` — записывает в `app_events` (дедупликация не применяется).

**Готово, когда:** Оценка «полезно» записывается в `insight_feedback` без mood-данных; `app_events` содержит строку при каждом открытии `InsightScreen`; «Пропустить» не показывает подтверждение; при `not_enough_data` `FeedbackRow` не отображается.

**Зависит от:** T-3, T-16

---

### T-18 · Реализовать HistoryScreen (US-5)

**Зачем / связь:** US-5 (spec §7.5); просмотр динамики, доверие к сохранению данных.

**Что сделать:**
`HistoryScreen`: `LazyColumn`, запрос Room `ORDER BY date DESC LIMIT 14` через `Flow`. `HistoryItem`: дата + небольшая цветная точка mood (цвет по уровню из spec §10.1) + небольшая цветная точка sleep + чипы тегов (не заливка карточки — spec §10.1). Пустое состояние: «Записей пока нет. Сделайте первую запись» + кнопка → `CheckIn`. `HistoryViewModel` подписан на `Flow<List<Entry>>` из `EntryRepository`.

**Готово, когда:** 14 записей отображаются от новых к старым; при 0 записей — пустое состояние; точки mood и sleep различимы без заливки всей карточки; пагинация и поиск отсутствуют (spec US-5).

**Зависит от:** T-3, T-4, T-5, T-6

---

### T-19 · Реализовать HistoryDetailSheet — просмотр, редактирование, удаление (US-5)

**Зачем / связь:** US-5 (spec §7.5, §4.1, §9); детали записи, каскадное удаление с пересчётом.

**Что сделать:**
Bottom sheet (или отдельный экран) при тапе на `HistoryItem`: показывает все 5 шкал + теги. Кнопка «Редактировать» → `CheckInScreen(entryId)`. Кнопка «Удалить» → `AlertDialog` подтверждения → `EntryRepository.delete(id)` → `ON DELETE CASCADE` удаляет `entry_tags` → `Flow` автоматически обновляет `HistoryScreen`, `MiniChart` и `Insight` через `HomeViewModel`. После удаления: если `entryCount < 3`, статус инсайта возвращается в `not_enough_data` (автоматически через Flow-цепочку).

**Готово, когда:** Удалённая запись исчезает из истории и не участвует в инсайте; при падении количества ниже 3 — `InsightCard` переходит в `not_enough_data`, `ProgressBlock` показывает актуальный прогресс; «Редактировать» открывает `CheckInScreen` с предзаполненными полями.

**Зависит от:** T-9, T-18

---

### T-20 · Собрать HomeScreen + HomeViewModel (US-2, US-3, US-4, US-7)

**Зачем / связь:** US-2, US-3, US-4, US-7 (spec §6.1, §6.3); главный экран — объединяет все компоненты и реактивный пересчёт.

**Что сделать:**
`HomeViewModel`: подписан на `EntryRepository.observeLast7()` (Flow); при каждом изменении запускает `CalculateInsightUseCase` + `GetRecommendationUseCase`; формирует `HomeUiState {insight, entryCount, last7DaysMap, showProgress}`.
`HomeScreen`:
- CTA «Отметить состояние» / «Редактировать запись сегодня» (определяется по `LocalDate.now()`) → `CheckIn`;
- `ProgressBlock` (если `showProgress`);
- `InsightCard` (тап → `InsightScreen`);
- `MiniChart`.
Состояния §6.3 spec: первый запуск (0 записей + disclaimerShown=true) — приветственный экран; 0–2 записи — только ProgressBlock; 3–4 без явной связи — ProgressBlock + InsightCard (waiting); 5+ — InsightCard (found / no_clear_pattern).

**Готово, когда:** После сохранения записи `HomeScreen` обновляется автоматически без явной навигации; при 5 записях с явной связью — `InsightCard` показывает `found`; `ProgressBlock` отсутствует при 5+ записях; `MiniChart` показывает актуальные данные.

**Зависит от:** T-12, T-13, T-14, T-15, T-16

---

### T-21 · Реализовать SettingsScreen + удаление всех данных (US-8)

**Зачем / связь:** US-8 (spec §7.8, §6.1); доверие пользователя, дисклеймер повторно, полный контроль над данными.

**Что сделать:**
`SettingsScreen`: полный текст дисклеймера (не диагноз, не замена специалисту), privacy-first объяснение (данные только на устройстве), кнопка «Удалить все данные приложения».
`DeleteAllConfirmationDialog`: деструктивная кнопка подтверждения.
По подтверждению: `EntryRepository.deleteAll()` + `InsightFeedbackRepository.deleteAll()` + `AppEventRepository.deleteAll()` + `DataStore.clear()` → `navController.navigate(Route.Onboarding) { popUpTo(Route.Home) { inclusive = true } }`.

**Готово, когда:** После «Удалить всё» → `OnboardingScreen` без записей, `disclaimerShown = false`; записи, feedback и app_events в Room отсутствуют; без нажатия кнопки подтверждения удаление не происходит; дисклеймер доступен из Settings без входа в check-in.

**Зависит от:** T-3, T-7, T-5, T-6

---

### T-22 · Реализовать ExportStatsUseCase + кнопку экспорта (US-STAT)

**Зачем / связь:** US-STAT (spec §7, §4.6); измерение критерия успеха гипотезы без передачи mood-данных.

**Что сделать:**
`ExportStatsUseCase`: читает Room (`EntryDao`, `AppEventDao`, `InsightFeedbackDao`); формирует JSON строго по структуре plan §4:
```json
{
  "exportedAt": "...",
  "appVersion": "...",
  "metrics": {
    "totalEntries": N,
    "entriesLast14Days": N,
    "reached5PlusEntries": bool,
    "insightScreenOpenedCount": N,
    "insightFeedback": {"useful": N, "not_useful": N, "skipped": N}
  }
}
```
Никаких mood/sleep/anxiety/stress/energy значений, тегов, дат записей, текстов в JSON.
Кнопка «Экспорт статистики для команды» в `SettingsScreen` → вызвать `ExportStatsUseCase` → записать во временный файл → `ShareCompat.IntentBuilder` (стандартный Android Share).
Проверить: при «Удалить все данные» (T-21) `app_events` сбрасываются вместе с остальным.

**Готово, когда:** После 5 записей и открытия `InsightScreen` JSON содержит `totalEntries=5`, `reached5PlusEntries=true`, `insightScreenOpenedCount ≥ 1`; JSON не содержит полей `mood`/`sleep`/`anxiety`/`stress`/`energy`/`tags`; Share Intent открывается.

**Зависит от:** T-3, T-4, T-21

---

## Раздел 3. Проверка и сдача

### T-23 · Прогон главного сценария (happy path)

**Зачем / связь:** Definition of Done spec §11; проверка сквозного сценария от первого запуска до инсайта.

**Что сделать:**
В эмуляторе или на устройстве пройти сценарий spec §8 целиком:
1. Первый запуск → `OnboardingScreen` → «Начать»;
2. Создать 5 записей с разными `sleep` (минимум 1 `low_sleep ≤ 2` и 1 `high_sleep ≥ 4`) и соответствующим контрастом `mood`;
3. После 3-й записи убедиться, что `ProgressBlock` или `InsightCard (waiting)` отображается;
4. После 5-й записи убедиться, что `InsightCard (found/contrast)` отображается;
5. Открыть `InsightScreen` → убедиться в наличии рекомендации → оценить «Полезно» → увидеть подтверждение;
6. Открыть `HistoryScreen` → убедиться в наличии 5 записей в обратном порядке;
7. Убедиться, что `MiniChart` показывает данные и легенду;
8. Замерить время 3 попытки check-in: ≥ 2 из 3 ≤ 20 секунд.

**Готово, когда:** Сценарий проходит от начала до конца без крашей и потери данных; check-in timing ≤ 20 с в 2/3 попытках; `InsightCard` показывает `found`; рекомендация отображается; история, MiniChart и инсайт согласованы.

**Зависит от:** T-7 – T-22

---

### T-24 · Проверка крайних случаев (spec §9)

**Зачем / связь:** Definition of Done spec §11, §9; обязательная проверка всех edge cases.

**Что сделать:**
Проверить каждый пункт spec §9:
1. Повторный check-in за тот же день → форма открывается предзаполненной (не новая запись);
2. Удалить одну запись из 5 → инсайт пересчитался (если осталось < 3 → `not_enough_data` + прогресс);
3. Удалить все данные → `OnboardingScreen` без записей;
4. Редактирование старой записи → история, MiniChart и инсайт пересчитались;
5. Пустая история → пустое состояние с предложением сделать первую запись;
6. Все примеры spec §5.3 дают правильный `status/subtype` (дополнительно к T-11 — проверить через UseCase или ручными тестовыми данными);
7. Проверить, что при `no_clear_pattern` основная рекомендация не отображается.

**Готово, когда:** Каждый из перечисленных сценариев проверен; нет критических крашей; ошибка сохранения (если воспроизводима) не приводит к потере введённых данных.

**Зависит от:** T-23

---

### T-25 · Сборка APK и проверка офлайн-работы

**Зачем / связь:** Definition of Done spec §11, spec §10 (P0 офлайн, без аналитических SDK); финальная проверка перед дистрибуцией тестировщикам.

**Что сделать:**
Собрать debug APK (`./gradlew assembleDebug`). Установить на устройство/эмулятор в режиме «в самолёте» (airplane mode). Пройти happy path spec §8. Проверить `build.gradle`: нет Firebase, Analytics, Crashlytics, Adjust, Amplitude, Mixpanel или любых сторонних аналитических SDK. Проверить `AndroidManifest.xml`: разрешение `INTERNET` отсутствует или не задействовано в P0-кодовых путях. Передать APK тестировщикам вручную (без публикации в Store).

**Готово, когда:** APK установлен и запускается; все P0-сценарии работают в airplane mode; в `build.gradle` нет сторонних аналитических SDK; `INTERNET` permission отсутствует или не используется в P0; APK передан команде для ручного тестирования.

**Зависит от:** T-24

---

## Проверка покрытия

| История | Задачи |
|---|---|
| **US-0** Первый запуск | T-7 (OnboardingScreen, DataStore флаг, дисклеймер) |
| **US-1** Быстрый daily check-in | T-8 (RatingRow, TagChip), T-9 (CheckInScreen + ViewModel, upsert, ≤ 6 действий) |
| **US-2** Первый инсайт сон ↔ настроение | T-10 (алгоритм), T-11 (JUnit-тесты), T-12 (вторичные наблюдения), T-16 (InsightCard + InsightScreen), T-20 (HomeViewModel, реактивный пересчёт) |
| **US-3** Одна мягкая рекомендация | T-13 (GetRecommendationUseCase, шаблоны §5.5), T-16 (RecommendationCard), T-20 |
| **US-4** Прогресс до первого инсайта | T-14 (ProgressBlock, форматы x/3 и x/5), T-20 (HomeScreen, состояния §6.3) |
| **US-5** История последних записей | T-18 (HistoryScreen, LIMIT 14), T-19 (HistoryDetailSheet, редактирование, удаление, каскадный пересчёт) |
| **US-6** Оценка полезности инсайта | T-17 (FeedbackRow, InsightFeedbackRepository, AppEvent-логирование) |
| **US-7** Компактный график за 7 дней | T-15 (MiniChart, Vico, 5 серий, пробелы, легенда), T-20 (HomeScreen) |
| **US-8** Доверие, дисклеймер, удаление данных | T-21 (SettingsScreen, DeleteAllConfirmationDialog, deleteAll + DataStore.clear) |
| **US-STAT** Экспорт статистики | T-22 (ExportStatsUseCase, JSON только счётчики, ShareCompat) |
| **US-9** Короткий комментарий (P1) | Вне скоупа P0 (plan §6); поле `comment NULL` зарезервировано в T-2 |
| **US-10** Недельный обзор (P1) | Вне скоупа P0 (plan §6) |
| **US-11** Авто-теги из комментария (P1) | Вне скоупа P0 (plan §6) |

**Все P0-истории (US-0 – US-8, US-STAT) покрыты задачами. P1-истории явно вне скоупа.**

**Задачи без привязки к истории (инфраструктурные) — явно в Разделе 1:**
T-1 (проект), T-2 (схема Room), T-3 (DAO/Repository/seeding), T-4 (domain-модели), T-5 (тема/палитра), T-6 (навигация) — все относятся к plan §1–§3.3 и необходимы как каркас для всех P0-историй.

**Задачи проверки и сдачи — явно в Разделе 3:**
T-23 (happy path), T-24 (edge cases), T-25 (APK + офлайн) — относятся к DoD spec §11.
