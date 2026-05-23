# Надёжная отправка ошибок и крешей на свой API

> Sources: AI assistant conversation, 2026-05-23
> Raw: [Переписка: механизм отправки логов при падении](../../raw/android/2026-05-23-android-crash-error-reporting.md)

> Updated: 2026-05-23

## Overview

На Android нельзя гарантировать отправку лога «при любой ситуации» (SIGKILL, force-stop, нет сети, OOM до записи). Рабочая стратегия — **сначала durable write локально**, **отправка позже** через WorkManager и при старте приложения. Единая точка входа — `ErrorReporter`; для p2p-app стек уже включает Room, WorkManager, OkHttp, Hilt, minSdk 26.

## Ограничения «абсолютной» надёжности

Код в crash handler не выполнится, если процесс убит без возможности записи, устройство выключено, приложение принудительно остановлено или память закончилась до flush. Цель — закрыть максимум реальных сценариев многослойной схемой, а не HTTP в момент падения.

## Архитектура по слоям

### 1. Локальный journal (обязательно первым шагом)

Никогда не слать на API в момент ошибки. Синхронно сохранить событие:

| Поле | Назначение |
|------|------------|
| id, timestamp, type, severity | Идентификация и классификация |
| message, stacktrace, thread | Суть ошибки |
| screen, userId | Контекст UI/пользователя |
| appVersion, androidVersion, device | Окружение |
| breadcrumbs | Цепочка событий до падения |
| sent | Флаг отправки |

- **Room**: таблица `error_reports` — основное хранилище очереди.
- **Файл**: дублировать критический минимум для жёстких крешей, когда Room может не успеть.

Принцип: **durable write → потом отправка**.

### 2. UncaughtExceptionHandler (фатальные JVM-креши)

Регистрировать в `Application.onCreate()`, делегировать предыдущему handler в `finally`.

Ограничения внутри handler:

- Без DI, сети, корутин, тяжёлого JSON/Room.
- Только максимально простой `saveBlocking()` в `CrashStore`.
- Оборачивать сохранение в try/catch, чтобы handler сам не упал.

### 3. Корутины: non-fatal

- `CoroutineExceptionHandler` → `errorReporter.reportNonFatal`.
- `CoroutineExceptionHandler` не ловит всё в `viewModelScope.launch`; дополнительно — `ViewModel.safeLaunch` с try/catch и rethrow после репорта.

### 4. WorkManager — отправка с retry

После сохранения — `OneTimeWorkRequest` + повторная постановка при каждом старте.

- Constraints: `NetworkType.CONNECTED`.
- Backoff: exponential (например 30 с).
- `enqueueUniqueWork("send_crash_reports", KEEP, request)` — без дублирования воркеров.
- Worker через Hilt: читает unsent (limit 50), шлёт на API, `markSent`; при ошибке — `Result.retry()`.

### 5. ApplicationExitInfo (API 30+)

При старте на Android 11+:

`ActivityManager.getHistoricalProcessExitReasons(packageName, 0, 5)` — ANR, low memory kill, crash, native crash, когда custom handler не сработал. Сохранять reason, importance, timestamp, description, trace stream при наличии. При minSdk 26 — только на API 30+.

### 6. Breadcrumbs

Кольцевой буфер 50–200 событий: экраны, клики, API start/response code, foreground/background, notifications, permissions, важные state transitions. Прикладывать к отчёту при любой ошибке.

## Дополнительные источники ошибок

| Тип | Подход |
|-----|--------|
| Handled / non-fatal | `errorReporter.reportNonFatal` в catch |
| HTTP | OkHttp interceptor: path (без секретов), method, code, duration, request id, safe body |
| ANR | Vitals / Crashlytics / Sentry / ApplicationExitInfo; свой watchdog — осторожно |
| Native (NDK) | Отдельный сбор, если появится C/C++ |

**Не логировать**: токены, пароли, cookies, `Authorization`.

## Готовые SDK (опционально поверх своего API)

| Вариант | Сильные стороны |
|---------|-----------------|
| **Sentry** | crashes, non-fatal, breadcrumbs, performance, tags, offline, self-hosted |
| **Firebase Crashlytics** | быстрое подключение, fatal + non-fatal с отложенной отправкой |
| **Свой API** | полный контроль; разумно оставить Sentry/Crashlytics как fallback |

## Рекомендуемый поток для p2p-app

```
App code → ErrorReporter
  → save local journal + breadcrumbs
  → enqueue WorkManager
  → (optional) Sentry/Crashlytics
  → Your API

Application.onCreate()
  → unsent from Room/file
  → ApplicationExitInfo (API 30+)
  → enqueue SendCrashReportsWorker
```

## Минимальный чеклист внедрения

1. `ErrorReporter` — единая точка.
2. `UncaughtExceptionHandler` + простой `CrashStore`.
3. `CoroutineExceptionHandler` + `safeLaunch`.
4. Room/file очередь с `sent=false`.
5. `SendCrashReportsWorker` + стартовая постановка.
6. Breadcrumbs.
7. `ApplicationExitInfo` на API 30+.
8. Crashlytics или Sentry как резервный канал.

**Ключевая мысль**: в момент падения — только быстрая запись; HTTP и тяжёлая логика — после рестарта или в Worker.

## See Also

- [Техническое задание: Crash/Error Reporting API](crash-reporting-api-technical-spec.md)
