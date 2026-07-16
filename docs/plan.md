# plan.md — Технический план Mood Diary MLP

> **Зачем нужен:** `plan.md` отвечает на вопрос «КАК» реализуем то, что описано в спеке.
> Он переводит продуктовые истории в технические решения: стек, архитектуру, модель данных.
> Место в цикле: `spec.md` (ЧТО) → **`plan.md` (КАК)** → `tasks.md` → код.
> План читает спеку и конституцию и не должен им противоречить.

---

## 0. Мета
- **Команда:** Mood Diary
- **Гипотеза / продукт:** см. `hypothesis.md`
- **Основано на:** `spec.md` v0.2 · `constitution.md`
- **Версия / дата:** v1.0 / 2026-07-16

---

## 1. Стек и обоснование

| Компонент | Решение | Почему |
|---|---|---|
| Платформа | Android, minSdk 29 (Android 10) | spec §10: целевой минимум прототипа |
| Язык | Kotlin | constitution §2: зафиксирован |
| UI | Jetpack Compose | constitution §2: рабочее предположение; декларативный UI упрощает динамические состояния экранов (§6.3 spec) |
| Локальное хранилище | Room (поверх SQLite) | spec §3 и §10: P0 полностью офлайн, данные не покидают устройство → Python + PostgreSQL откладываются (см. §6 Компромиссы) |
| DI | Hilt | стандартная Jetpack DI; снижает boilerplate ViewModel/Repository |
| Реактивность | Kotlin Coroutines + Flow | `Room → Flow → ViewModel → Compose State` обеспечивает автоматический пересчёт инсайта при любом изменении данных (spec §4.3: «инсайт пересчитывается после создания, редактирования или удаления записи») |
| Навигация | Navigation Compose | type-safe routes, интеграция с back stack |
| Графики | Vico | MIT-лицензия, Compose-native, нативно поддерживает `null`-пробелы в сериях (spec US-7: «дни без записи — пропуски, а не нули») |
| Настройки | SharedPreferences (DataStore) | хранение флага «дисклеймер показан» и прочих non-entry настроек |

Все компоненты бесплатны. Платных внешних API — нет. Аналитических SDK — нет.

---

## 2. Архитектура

Приложение — **single-module Android**, без бэкенда в P0. Три слоя по Android App Architecture Guide:

**Data layer**
- `MoodDatabase` (Room): таблицы `entries`, `tags`, `entry_tags`, `insight_feedback`, `app_events`
- `EntryDao` / `InsightFeedbackDao` / `AppEventDao`: запросы + Flow-подписки
- `EntryRepository`: upsert записи, наблюдение за последними N записями, удаление
- `InsightFeedbackRepository`: сохранение оценки
- `AppEventRepository`: запись события `insight_screen_opened`, счётчик для экспорта

**Domain layer** (чистый Kotlin, без Android-зависимостей — легко тестировать)
- `CalculateInsightUseCase`: вся формальная логика spec §5 → возвращает `Insight`
- `GetRecommendationUseCase`: выбирает шаблон из §5.5 по `Insight.subtype` → возвращает строку

**UI layer**
- ViewModel подписывается на `Flow` из Repository, запускает UseCase, отдаёт `UiState` через `StateFlow`
- Compose-экраны подписываются на `UiState` через `collectAsStateWithLifecycle()`

**Поток данных при изменении записи:**
```
CheckInScreen
  → CheckInViewModel.save()
    → EntryRepository.upsert()
      → Room эмитит новый список
        → HomeViewModel пересчитывает инсайт
          → HomeScreen перерисовывается
```

---

## 3. Модель данных

### 3.1 Room-таблицы

**entries**

| Поле | Тип Room | Описание |
|---|---|---|
| id | `INTEGER PK AUTOINCREMENT` | |
| date | `TEXT` (ISO-8601, `YYYY-MM-DD`) | локальная дата устройства; `UNIQUE INDEX` — одна запись в день |
| mood | `INTEGER` (1–5) | |
| energy | `INTEGER` (1–5) | |
| anxiety | `INTEGER` (1–5) | |
| stress | `INTEGER` (1–5) | |
| sleep | `INTEGER` (1–5) | |
| comment | `TEXT NULL` | P1; в P0 всегда `null` |
| created_at | `INTEGER` (epoch ms) | |
| updated_at | `INTEGER` (epoch ms) | |

Upsert по `date` (`OnConflictStrategy.REPLACE`) реализует правило spec §4.1: «повторный check-in = редактирование».

**tags** (pre-seeded при первом запуске, в P0 пользователь не изменяет)

| Поле | Тип |
|---|---|
| id | `TEXT PK` ('work', 'deadline', …) |
| label_ru | `TEXT` |
| category | `TEXT` |

12 записей из словаря spec §4.2.

**entry_tags** (many-to-many)

| Поле | Тип |
|---|---|
| entry_id | `INTEGER FK → entries.id ON DELETE CASCADE` |
| tag_id | `TEXT FK → tags.id` |

Составной PK `(entry_id, tag_id)`.

**insight_feedback**

| Поле | Тип |
|---|---|
| id | `INTEGER PK AUTOINCREMENT` |
| insight_type | `TEXT` (всегда `'sleep_mood'` в P0) |
| shown_at | `INTEGER` (epoch ms) |
| value | `TEXT` ('useful' / 'not_useful' / 'skipped') |

**app_events** (счётчики для измерения критерия успеха — только команда, не передаётся третьим лицам)

| Поле | Тип |
|---|---|
| id | `INTEGER PK AUTOINCREMENT` |
| event_type | `TEXT` |
| occurred_at | `INTEGER` (epoch ms) |

Единственное событие в P0: `insight_screen_opened`. Записывается в `AppEventDao` при каждом открытии `InsightScreen`. Счётчик не дедуплицируется — нужна частота, а не уникальность. При «Удалить все данные» — таблица очищается вместе с остальными.

### 3.2 Domain-модели (Kotlin data classes)

| Класс | Соответствие spec | Примечание |
|---|---|---|
| `Entry` | §4.1 | включает `List<Tag>`, маппится из Room-entity |
| `Tag` | §4.2 | |
| `Insight` | §4.3 | `status`, `subtype`, `message`, `confidenceLabel`, `entriesUsedCount`, `secondaryObservations` |
| `SecondaryObservation` | §5.3.1 | `scale`, `direction` |
| `Recommendation` | §4.4 | строка из шаблона §5.5 |
| `InsightFeedback` | §4.5 | |

`Insight` и `Recommendation` — вычисляемые, не хранятся в Room (пересчитываются в памяти при каждом изменении `entries`).

### 3.3 Структура каталогов

```
app/src/main/
  data/
    db/          MoodDatabase, EntryDao, InsightFeedbackDao, AppEventDao
    model/       EntryEntity, TagEntity, EntryTagCrossRef, InsightFeedbackEntity, AppEventEntity
    repository/  EntryRepository, InsightFeedbackRepository, AppEventRepository
  domain/
    model/       Entry, Tag, Insight, Recommendation, InsightFeedback, SecondaryObservation
    usecase/     CalculateInsightUseCase, GetRecommendationUseCase, ExportStatsUseCase
  ui/
    home/        HomeScreen, HomeViewModel
    checkin/     CheckInScreen, CheckInViewModel
    history/     HistoryScreen, HistoryViewModel
    settings/    SettingsScreen, SettingsViewModel
    onboarding/  OnboardingScreen
    theme/       Color (палитра §10.1), Theme, Type
    components/  MiniChart, ProgressBlock, TagChip, InsightCard,
                 RecommendationCard, FeedbackRow
  di/            AppModule (Hilt)
  navigation/    NavGraph, Route
```

---

## 4. Экраны / эндпоинты

В P0 бэкенд-эндпоинты отсутствуют — только Android-экраны.

### US-0 · Первый запуск → `OnboardingScreen`
- Показывается при `entries.isEmpty() AND !prefs.disclaimerShown`
- Содержит: краткое описание продукта, «не диагноз и не замена специалисту», «данные хранятся только на устройстве»
- Единственная кнопка «Начать» → `CheckInScreen` (без доп. шагов настройки)
- Не запрашивает разрешения ОС
- Дисклеймер повторно доступен через `SettingsScreen`

### US-1 · Быстрый check-in → `CheckInScreen`
- Один экран: 5 `RatingRow`-компонентов (иконка + текстовая метка из палитры §10.1) + `TagGrid` (чипы из словаря §4.2)
- Кнопка «Сохранить» активна, как только выбраны все 5 оценок (теги необязательны)
- `CheckInViewModel.save()`: определяет `LocalDate.now()` устройства → upsert в Room
- После сохранения → `HomeScreen` с актуальным `UiState`
- Счёт действий happy path: 5 тапов (оценки) + 1 тап «Сохранить» = **6 ≤ 7** (US-1)
- При повторном открытии за тот же день — поля предзаполнены из существующей записи

### US-2 · Инсайт → `InsightCard` на `HomeScreen` + `InsightScreen`
- `HomeViewModel.uiState` содержит `Insight`, полученный от `CalculateInsightUseCase(last7entries)`
- `InsightCard`: краткий текст статуса, `confidenceLabel` при `found`; тап → `InsightScreen`
- `InsightScreen`: полный текст шаблона §5.4, вторичное наблюдение (если есть), счётчик «использовано N записей»
- Покрываются все статусы: `not_enough_data`, `waiting`, `found`, `no_clear_pattern` (все подтипы)
- Все примеры из §5.3 покрыты unit-тестами `CalculateInsightUseCaseTest`

### US-3 · Рекомендация → `RecommendationCard` на `InsightScreen`
- `GetRecommendationUseCase(insight.subtype)` возвращает первый шаблон нужного пула (A / B / C) из §5.5
- Карточка отображается только при `insight.status == found`; при `waiting`, `not_enough_data`, `no_clear_pattern` — скрыта
- Цвет карточки: светло-бежевый / светло-голубой / светло-лавандовый (§10.1)

### US-4 · Прогресс → `ProgressBlock` на `HomeScreen`
- 0–2 записи: `LinearProgressIndicator(current/3f)` + текст `not_enough_data` из §5.4 (с подстановкой n)
- 3–4 записи без явной связи (`status == waiting`): `LinearProgressIndicator(current/5f)` + текст `waiting`
- 5+ записей: `ProgressBlock` не отображается, основное место занимает `InsightCard`
- Формулировки нейтральные; слова «провал», «стрик» — запрещены

### US-5 · История → `HistoryScreen`
- `LazyColumn` с запросом Room: `ORDER BY date DESC LIMIT 14`
- `HistoryItem`: дата · цветная точка mood · цветная точка sleep · чипы тегов (spec §10.1: точка, не заливка карточки)
- Тап → `HistoryDetailSheet` (bottom sheet): все поля записи; кнопки «Редактировать» → `CheckInScreen(entryId)`, «Удалить»
- Удаление: confirmation dialog → `entryRepository.delete(id)` → `ON DELETE CASCADE` удаляет `entry_tags` → Flow пересчитывает историю, график и инсайт автоматически
- Пустое состояние: «Записей пока нет. Сделайте первую запись» + кнопка → `CheckInScreen`

### US-6 · Оценка инсайта → `FeedbackRow` на `InsightScreen`
- Три кнопки: «Полезно» / «Не полезно» / «Пропустить»
- Отображается только при `insight.status == found`
- После выбора «Полезно» / «Не полезно»: кнопки → `gone`, показывается текст-подтверждение из §5.4 (кратко, без продолжения)
- «Пропустить»: строка просто скрывается, без подтверждения
- `InsightFeedbackRepository.save(...)` — сохраняется только `value`, без личных текстов

### US-7 · График 7 дней → `MiniChart` на `HomeScreen`
- Vico `CartesianChartHost`, ось X — последние 7 локальных дат, ось Y — 1..5
- Пять `LineSpec` по данным из `palette.md`:

| Серия | Цвет HEX | Стиль | Толщина |
|---|---|---|---|
| mood | #8EB69B | сплошная | 2.5dp |
| sleep | #84BDE8 | пунктир `– – –` | 2dp |
| energy | #C8A84B | сплошная | 1.5dp |
| anxiety | #C0A8B8 | пунктир `. . .` | 1.5dp |
| stress | #9890C4 | штрих-пунктир `-·-` | 1.5dp |

- Пропущенные дни: `null` в `ChartEntryModel` → Vico отображает разрыв; null только если за день нет записи целиком (все 5 шкал обязательны, пустых значений внутри записи нет)
- Легенда под графиком: 5 иконок с короткими подписями (цвет + стиль, не только цвет — доступность)
- 0 записей: `EmptyChartPlaceholder` («Сделайте первую запись, чтобы увидеть график»)
- 1 запись: одиночные точки по всем 5 сериям
- Высота графика ≤ 180dp (увеличена с исходных 120dp ради читаемости 5 серий) + легенда ≤ 32dp

### US-8 · Доверие и удаление → `SettingsScreen` + `DeleteAllConfirmationDialog`
- `SettingsScreen`: полный дисклеймер текстом, кнопка «Удалить все данные приложения»
- `DeleteAllConfirmationDialog`: явное подтверждение, деструктивная кнопка
- По подтверждению: `entryRepository.deleteAll()` + `insightFeedbackRepository.deleteAll()` + `appEventRepository.deleteAll()` + `DataStore.clear()` → навигация в `OnboardingScreen` (back stack очищается)
- Проверка: в `build.gradle` нет зависимостей сторонних аналитических SDK; нет сетевых вызовов в `Repository`

### Метрики успеха · Экспорт статистики → кнопка в `SettingsScreen`

Механизм измерения критерия успеха (hypothesis.md §5) без бэкенда:

- Кнопка «Экспорт статистики для команды» в нижней части `SettingsScreen`
- `ExportStatsUseCase` читает Room и формирует JSON — **только анонимные счётчики, никаких mood-данных**:

```json
{
  "exportedAt": "2026-07-16T10:00:00Z",
  "appVersion": "1.0.0",
  "metrics": {
    "totalEntries": 12,
    "entriesLast14Days": 7,
    "reached5PlusEntries": true,
    "insightScreenOpenedCount": 4,
    "insightFeedback": {
      "useful": 2,
      "not_useful": 0,
      "skipped": 2
    }
  }
}
```

- Файл передаётся через `ShareCompat.IntentBuilder` (стандартный Android Share) — команда собирает файлы от тестировщиков вручную
- Покрывает обе метрики гипотезы: retention (≥5 записей за 14 дней) и ценность инсайтов (открытие + «полезно»)
- Добавляет `AppEventDao` + `AppEventRepository`; `ExportStatsUseCase` в domain layer

---

## 5. Внешние зависимости

| Библиотека | Версия | Лицензия | Назначение |
|---|---|---|---|
| Room | 2.6.x | Apache 2.0 | Локальная БД + Flow |
| Hilt | 2.51.x | Apache 2.0 | Dependency injection |
| Navigation Compose | 2.7.x | Apache 2.0 | Навигация между экранами |
| Vico | 2.x | MIT | Линейный график (MiniChart) |
| Kotlin Coroutines | 1.9.x | Apache 2.0 | Async, Flow, StateFlow |
| DataStore Preferences | 1.1.x | Apache 2.0 | Флаг «дисклеймер показан» |
| JUnit 4 + Kotlin Test | — | Apache 2.0 | Unit-тесты UseCase |
| kotlinx-datetime | 0.6.x | Apache 2.0 | Безопасная работа с `LocalDate` |

Все бесплатны. Лимитов нет — всё локально. Платных API — нет. Аналитических SDK — нет.

---

## 6. Компромиссы и что вынесено за скоуп

### Компромисс 1: Python-бэкенд и PostgreSQL откладываются до post-MVP

**Отклонение от constitution §2.** Constitution упоминает Python-бэкенд, но допускает «локальное хранение на устройстве для MVP». Spec §3 и §10 явно требуют P0 без интернета, без аккаунта и без облачной синхронизации; данные P0 не покидают устройство. Реализация бэкенда в P0 нарушает privacy-first, добавляет зависимость от сети и выходит за 4-сессионный бюджет.

**Решение:** P0 — полностью локальное Android-приложение (Room/SQLite). Python + PostgreSQL — в архитектурном отступлении; реализуются в post-MVP при необходимости облачной синхронизации. Команда уведомляется.

### Вне скоупа P0 (согласно spec §3 и constitution §1)

| Функция | История | Причина |
|---|---|---|
| Короткий комментарий | US-9 (P1) | spec явно P1; не нужен для инсайта |
| Недельный обзор | US-10 (P1) | spec явно P1 |
| Авто-теги из комментария | US-11 (P1) | зависит от US-9 |
| Python backend | — | см. Компромисс 1 |
| Push-уведомления | — | spec §3 «Вне скоупа» |
| Кризисная логика | — | spec §9, §12: явно не в P0 |
| Пагинация истории > 14 записей | — | spec §5 |
| Мультиязычность | — | spec §10 |

---

## 7. Риски реализации

| Риск | Вероятность | Митигация |
|---|---|---|
| Алгоритм инсайта: 7 ветвей + вторичные наблюдения сложны в реализации | Высокая | `CalculateInsightUseCase` — чистый Kotlin без Android-зависимостей; реализовать и покрыть JUnit-тестами на **все примеры §5.3** до написания UI |
| Timezone-баги: «одна запись в день» при переходе через полночь | Средняя | `kotlinx-datetime LocalDate.now(TimeZone.currentSystemDefault())` — дата фиксируется однократно при создании и не пересчитывается (spec §4.1) |
| Vico: пробелы в 7-дневном графике не поддержаны в нужной версии | Средняя | Проверить в Session 1; fallback — Custom Canvas с `Path.moveTo/lineTo` (≤ 50 строк) |
| 20-секундный лимит check-in нарушен из-за UI-friction | Средняя | Прокатать happy path в эмуляторе в Session 1; если > 7 действий — убрать лишние шаги (например, убрать явный экран подтверждения) |
| Реактивный пересчёт: удаление записи не обновляет InsightCard | Низкая | Flow-цепочка `Room → Repository → ViewModel` гарантирует пересчёт автоматически; покрывается интеграционным тестом с in-memory Room |
| Переполнение back stack при навигации check-in → home → check-in | Низкая | `popUpTo` в NavGraph при переходе из `CheckInScreen` после сохранения |

---

## Проверка покрытия

| История | Чем закрыта в плане |
|---|---|
| **US-0** Первый запуск | §4 → `OnboardingScreen`; флаг `disclaimerShown` в DataStore |
| **US-1** Быстрый check-in | §4 → `CheckInScreen` + `CheckInViewModel`; §3.1 → upsert по `date` |
| **US-2** Первый инсайт сон ↔ настроение | §4 → `InsightCard` / `InsightScreen`; §3.2 → `CalculateInsightUseCase` (§5 spec) |
| **US-3** Одна мягкая рекомендация | §4 → `RecommendationCard`; §3.2 → `GetRecommendationUseCase` (§5.5 spec) |
| **US-4** Прогресс до первого инсайта | §4 → `ProgressBlock` на `HomeScreen`; состояния `not_enough_data` / `waiting` |
| **US-5** История последних записей | §4 → `HistoryScreen` + `HistoryDetailSheet`; §3.1 → `LIMIT 14 ORDER BY date DESC` |
| **US-6** Оценка полезности инсайта | §4 → `FeedbackRow` на `InsightScreen`; §3.1 → таблица `insight_feedback` |
| **US-7** Компактный график 7 дней | §4 → `MiniChart`; §5 → Vico; палитра §10.1 |
| **US-8** Доверие, дисклеймер, удаление данных | §4 → `SettingsScreen` + `DeleteAllConfirmationDialog`; §3.1 → `deleteAll()` + DataStore clear |
| **US-STAT** Экспорт статистики (метрики успеха) | §4 → «Экспорт статистики»; §3.1 → `app_events`; §3.2 → `ExportStatsUseCase` |
| **US-9** Короткий комментарий (P1) | Явно вне скоупа P0 — §6; поле `comment` зарезервировано в схеме (`NULL`) |
| **US-10** Недельный обзор (P1) | Явно вне скоупа P0 — §6 |
| **US-11** Авто-теги из комментария (P1) | Явно вне скоупа P0 — §6 |

Все P0-истории (US-0 — US-8) покрыты. P1-истории (US-9, US-10, US-11) явно вынесены в §6 с обоснованием.
