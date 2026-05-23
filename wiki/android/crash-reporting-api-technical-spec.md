# Техническое задание: Crash/Error Reporting API

> Sources: AI assistant conversation, 2026-05-23; User product request, 2026-05-23
> Raw: [Переписка: механизм отправки логов при падении](../../raw/android/2026-05-23-android-crash-error-reporting.md); [Запрос: ТЗ и API для crash/error reporting](../../raw/android/2026-05-23-crash-reporting-api-technical-spec-request.md)

> Updated: 2026-05-23

## Overview

Нужно реализовать надёжный механизм сбора, локального хранения и отложенной отправки crash/error reports из Android-приложения на серверный API сайта. Клиент не должен пытаться делать HTTP-запрос в момент фатального падения: сначала событие сохраняется локально, затем `WorkManager` отправляет одну или несколько записей на endpoint `POST /api/app/error-reports/batch`. Сервер принимает пачку отчётов, дедуплицирует их по `client_report_id`, сохраняет в БД и возвращает результат по каждому элементу.

## Цель

Дать владельцу сайта диагностическую информацию о падениях, ANR, неожиданных завершениях процесса, non-fatal ошибках и важных HTTP/логических ошибках приложения.

Главный пользовательский результат: если приложение «сломалось», после следующего успешного старта или появления сети сервер получает структурированный отчёт с контекстом устройства, версии приложения, stacktrace и breadcrumbs.

## Не цели

- Не гарантировать отправку «при любой ситуации»: Android не даёт такой гарантии при `SIGKILL`, force-stop, выключении устройства, полном OOM до записи и отсутствии сети.
- Не отправлять секреты, токены, пароли, cookies, полные Authorization headers, персональные данные без необходимости.
- Не заменять Sentry/Crashlytics полностью, если позже потребуется внешний fallback.
- Не блокировать основной пользовательский сценарий из-за ошибки в диагностике.

## Участники

| Компонент | Ответственность |
|---|---|
| `ErrorReporter` | Единая точка записи fatal/non-fatal ошибок |
| `CrashStore` | Быстрое локальное сохранение отчётов |
| Room `error_reports` | Основная очередь unsent reports |
| File fallback | Минимальный резерв для фатального креша |
| `AppCrashHandler` | `Thread.UncaughtExceptionHandler` для fatal JVM crashes |
| `BreadcrumbLogger` | Кольцевой буфер последних событий |
| `ApplicationExitInfoCollector` | Сбор причин завершения процесса на API 30+ |
| `SendErrorReportsWorker` | Отправка пачки отчётов с retry |
| `ErrorReportsApi` | OkHttp-клиент для server endpoint |
| Backend endpoint | Приём, валидация, дедупликация и хранение отчётов |

## Основной поток

```text
Ошибка / креш / ApplicationExitInfo / non-fatal
  -> ErrorReporter
  -> CrashStore.save(...)
  -> Room error_reports(sent = false)
  -> enqueueUniqueWork("send_error_reports", KEEP)
  -> SendErrorReportsWorker
  -> POST /api/app/error-reports/batch
  -> markSent(...) для accepted элементов
```

При каждом старте приложения:

```text
Application.onCreate()
  -> установить AppCrashHandler
  -> собрать ApplicationExitInfo на API 30+
  -> импортировать file fallback, если есть
  -> enqueueUniqueWork("send_error_reports", KEEP)
```

## Endpoint

### Метод

`POST /api/app/error-reports/batch`

### Назначение

Принимает пачку crash/error reports от одного устройства. Batch-формат выбран как основной, потому что `WorkManager` может накопить несколько записей без сети, а серверу проще обработать их одним запросом.

### Заголовки

| Header | Обязателен | Значение |
|---|---:|---|
| `Accept` | да | `application/json` |
| `Content-Type` | да | `application/json` |
| `Access-Token` | да | Текущий токен приложения, как в существующем API |
| `Idempotency-Key` | желательно | UUID запроса batch, например `batch_id` |

### Request body

```json
{
  "schema_version": 1,
  "batch_id": "0d7b0e33-6d8a-4e86-8b96-80c6f51f92df",
  "sent_at": "2026-05-23T11:15:00Z",
  "device": {
    "android_id": "abc123",
    "manufacturer": "Google",
    "brand": "google",
    "model": "Pixel 8",
    "android_version": "14",
    "sdk_int": 34
  },
  "app": {
    "package_name": "com.android.autopay",
    "version_name": "1.0.0",
    "version_code": 1,
    "build_type": "release"
  },
  "reports": [
    {
      "client_report_id": "d4f9d081-66a1-49f2-b5e2-5d42f3d1acfb",
      "occurred_at": "2026-05-23T11:10:32Z",
      "type": "fatal",
      "severity": "error",
      "source": "uncaught_exception_handler",
      "message": "java.lang.IllegalStateException: Example failure",
      "exception_class": "java.lang.IllegalStateException",
      "stacktrace": "java.lang.IllegalStateException: Example failure\n  at ...",
      "thread": "main",
      "screen": "SettingsScreen",
      "process_importance": "foreground",
      "is_foreground": true,
      "user_id": null,
      "session_id": "c1307cc8-2453-42f5-9482-e53d69937352",
      "breadcrumbs": [
        {
          "timestamp": "2026-05-23T11:10:10Z",
          "category": "screen",
          "level": "info",
          "message": "SettingsScreen opened",
          "data": {}
        },
        {
          "timestamp": "2026-05-23T11:10:20Z",
          "category": "api",
          "level": "warning",
          "message": "POST /api/app/device/connect -> 500",
          "data": {
            "duration_ms": 820,
            "request_id": "srv-req-123"
          }
        }
      ],
      "http_context": {
        "method": "POST",
        "path": "/api/app/device/connect",
        "status_code": 500,
        "duration_ms": 820,
        "request_id": "srv-req-123"
      },
      "exit_info": null,
      "extra": {
        "notification_listener_enabled": true
      }
    }
  ]
}
```

## Поля верхнего уровня

| Поле | Тип | Обязательность | Описание |
|---|---|---:|---|
| `schema_version` | integer | да | Версия контракта. Начать с `1` |
| `batch_id` | string UUID | да | Уникальный ID пачки для идемпотентности |
| `sent_at` | ISO-8601 string | да | Время отправки batch с устройства |
| `device` | object | да | Общие данные устройства |
| `app` | object | да | Общие данные приложения |
| `reports` | array | да | Список отчётов, максимум 50 |

## Поля `device`

| Поле | Тип | Обязательность | Описание |
|---|---|---:|---|
| `android_id` | string | да | Идентификатор устройства, уже используется в connect |
| `manufacturer` | string | нет | Производитель |
| `brand` | string | нет | Бренд |
| `model` | string | нет | Модель |
| `android_version` | string | нет | Версия Android |
| `sdk_int` | integer | нет | SDK level |

## Поля `app`

| Поле | Тип | Обязательность | Описание |
|---|---|---:|---|
| `package_name` | string | да | Package приложения |
| `version_name` | string | нет | Версия для человека |
| `version_code` | integer | нет | Код версии |
| `build_type` | string | нет | `debug`, `release` и т.п. |

## Поля одного report

| Поле | Тип | Обязательность | Описание |
|---|---|---:|---|
| `client_report_id` | string UUID | да | Стабильный ID события на клиенте |
| `occurred_at` | ISO-8601 string | да | Когда ошибка произошла |
| `type` | enum | да | `fatal`, `non_fatal`, `anr`, `process_exit`, `http_error`, `logic_error` |
| `severity` | enum | да | `debug`, `info`, `warning`, `error`, `critical` |
| `source` | string | да | Источник: `uncaught_exception_handler`, `coroutine`, `manual`, `application_exit_info`, `okhttp_interceptor` |
| `message` | string | да | Краткое описание |
| `exception_class` | string | нет | Класс исключения |
| `stacktrace` | string | нет | Stacktrace, если есть |
| `thread` | string | нет | Имя thread |
| `screen` | string | нет | Текущий экран/сценарий |
| `process_importance` | string | нет | `foreground`, `background`, `service`, `unknown` |
| `is_foreground` | boolean | нет | Было ли приложение в foreground |
| `user_id` | string/null | нет | Только если безопасно и нужно |
| `session_id` | string UUID | нет | ID сессии приложения |
| `breadcrumbs` | array | нет | Последние 50-200 событий |
| `http_context` | object/null | нет | Контекст HTTP-ошибки без секретов |
| `exit_info` | object/null | нет | Данные `ApplicationExitInfo` |
| `extra` | object | нет | Ограниченные дополнительные поля |

## Поля `breadcrumb`

| Поле | Тип | Обязательность | Описание |
|---|---|---:|---|
| `timestamp` | ISO-8601 string | да | Время события |
| `category` | string | да | `screen`, `api`, `notification`, `permission`, `lifecycle`, `state` |
| `level` | enum | да | `debug`, `info`, `warning`, `error` |
| `message` | string | да | Короткое описание |
| `data` | object | нет | Маленький JSON без секретов |

## Поля `http_context`

| Поле | Тип | Обязательность | Описание |
|---|---|---:|---|
| `method` | string | да | HTTP method |
| `path` | string | да | Только path без query с sensitive data |
| `status_code` | integer | нет | Код ответа |
| `duration_ms` | integer | нет | Длительность |
| `request_id` | string | нет | Серверный request id, если есть |

Запрещено отправлять `Authorization`, cookies, access token, refresh token, пароли, OTP, полный URL с приватными query-параметрами.

## Поля `exit_info`

Используется только на API 30+.

```json
{
  "reason": "ANR",
  "reason_code": 6,
  "importance": 100,
  "timestamp": "2026-05-23T11:09:00Z",
  "description": "Input dispatching timed out",
  "pss_kb": 120000,
  "rss_kb": 180000,
  "trace": "optional shortened trace text"
}
```

Если trace большой, клиент должен ограничить размер или не отправлять его.

## Response body

### Успешный ответ

HTTP `202 Accepted`

```json
{
  "accepted": true,
  "server_batch_id": "9a420982-baa2-41a8-a96f-0cf00ff0b430",
  "results": [
    {
      "client_report_id": "d4f9d081-66a1-49f2-b5e2-5d42f3d1acfb",
      "status": "accepted",
      "server_report_id": "7e2d8dc1-6d29-47a4-a443-8ff87be29f34"
    }
  ]
}
```

### Частичный успех

HTTP `207 Multi-Status` или `202 Accepted` с `status = rejected` у отдельных элементов. Для простоты клиента предпочтительнее всегда возвращать `202`, если batch синтаксически валиден.

```json
{
  "accepted": true,
  "server_batch_id": "9a420982-baa2-41a8-a96f-0cf00ff0b430",
  "results": [
    {
      "client_report_id": "ok-id",
      "status": "accepted",
      "server_report_id": "server-id"
    },
    {
      "client_report_id": "bad-id",
      "status": "rejected",
      "error_code": "payload_too_large",
      "message": "Report is too large"
    }
  ]
}
```

## HTTP статусы

| Статус | Когда | Поведение клиента |
|---|---|---|
| `202` | Batch принят, см. `results` | `accepted` пометить sent, `rejected` пометить failed/drop |
| `400` | Невалидный JSON/schema | Не retry бесконечно; пометить batch failed |
| `401` | Неверный `Access-Token` | Retry позже только если токен может обновиться |
| `413` | Batch слишком большой | Уменьшить batch/report, rejected не слать повторно в том же виде |
| `422` | Семантическая ошибка данных | Не retry без изменения payload |
| `429` | Rate limit | Retry с backoff |
| `500/502/503/504` | Ошибка сервера | Retry через WorkManager |

## Лимиты

| Лимит | Значение |
|---|---:|
| Reports в одном batch | 50 |
| Размер одного report | до 256 KB |
| Размер batch | до 1 MB |
| Breadcrumbs на report | 100 |
| Длина `message` | 8 KB |
| Длина `stacktrace` | 128 KB |
| Длина `extra` | 32 KB |

Если данные превышают лимит, клиент должен обрезать безопасно: сначала breadcrumbs, затем extra, затем stacktrace.

## Клиентский интерфейс

Минимальный интерфейс отправки:

```kotlin
interface ErrorReportsApi {
    suspend fun sendErrorReports(
        token: String,
        request: ErrorReportsBatchRequest
    ): Result<ErrorReportsBatchResponse>
}
```

Модель request:

```kotlin
data class ErrorReportsBatchRequest(
    val schemaVersion: Int,
    val batchId: String,
    val sentAt: String,
    val device: ErrorReportDevicePayload,
    val app: ErrorReportAppPayload,
    val reports: List<ErrorReportPayload>
)
```

Модель report:

```kotlin
data class ErrorReportPayload(
    val clientReportId: String,
    val occurredAt: String,
    val type: String,
    val severity: String,
    val source: String,
    val message: String,
    val exceptionClass: String?,
    val stacktrace: String?,
    val thread: String?,
    val screen: String?,
    val processImportance: String?,
    val isForeground: Boolean?,
    val userId: String?,
    val sessionId: String?,
    val breadcrumbs: List<ErrorReportBreadcrumbPayload>,
    val httpContext: ErrorReportHttpContextPayload?,
    val exitInfo: ErrorReportExitInfoPayload?,
    val extra: Map<String, Any?>
)
```

Если проект продолжит использовать `org.json.JSONObject` вместо сериализатора, эти модели можно держать как domain/data DTO, а в `ErrorReportsApi` вручную маппить их в snake_case JSON по контракту.

## Серверная обработка

Сервер должен:

1. Проверить `Access-Token`.
2. Проверить `schema_version`.
3. Проверить размер batch и каждого report.
4. Для каждого `client_report_id` выполнить idempotent upsert или ignore duplicate.
5. Сохранить raw payload для диагностики, если это допустимо политикой хранения.
6. Нормализовать ключевые поля в отдельные колонки для поиска: type, severity, app_version, android_version, model, occurred_at.
7. Вернуть результат по каждому report.

Рекомендуемая уникальность: `(android_id, client_report_id)` или `(device_id, client_report_id)`.

## Хранение на сервере

Минимальные таблицы:

```text
error_report_batches
  id
  batch_id
  device_id/android_id
  received_at
  app_version
  schema_version
  raw_payload

error_reports
  id
  client_report_id
  batch_id
  android_id
  type
  severity
  source
  message
  exception_class
  stacktrace
  screen
  app_version
  android_version
  device_model
  occurred_at
  received_at
  raw_payload
```

Опционально отдельно хранить `breadcrumbs`, если нужен поиск/аналитика по ним.

## Безопасность и приватность

- Все запросы только по HTTPS.
- `Access-Token` не логировать на клиенте и сервере.
- В OkHttp interceptor исключить sensitive headers.
- Query-параметры URL либо не отправлять, либо whitelist-only.
- `extra` принимать только как bounded JSON object.
- Ограничить размер строк на клиенте и сервере.
- Добавить rate limit по токену, устройству и IP.
- На сервере иметь задачу очистки старых raw payload, если хранение ограничено.

## Retry и идемпотентность

Клиент:

- `202`: accepted reports пометить `sent = true`.
- `400`, `413`, `422`: не retry бесконечно, пометить rejected/failed с причиной.
- `429`, `5xx`, IOException: `Result.retry()`.
- Batch отправлять не больше 50 reports.
- Использовать exponential backoff.

Сервер:

- Повторный `client_report_id` не создаёт дубль.
- Повторный `batch_id` возвращает совместимый результат или обрабатывается идемпотентно.

## Acceptance Criteria

- При non-fatal ошибке запись появляется в локальной очереди и позже отправляется на API.
- При fatal JVM crash handler сохраняет минимальный отчёт и передаёт управление предыдущему handler.
- При старте приложения unsent reports отправляются через WorkManager.
- При отсутствии сети reports остаются локально и отправляются после восстановления сети.
- При `202` accepted элементы помечаются как sent.
- При `5xx` worker возвращает retry.
- При `400/422` клиент не зацикливается на вечной отправке битого payload.
- Payload не содержит access token, password, cookies, Authorization header.
- На API 30+ собирается `ApplicationExitInfo` и сохраняется как `process_exit`/`anr`, если есть новые причины завершения.

## MVP

1. Room `error_reports`.
2. `ErrorReporter`.
3. `UncaughtExceptionHandler`.
4. `SendErrorReportsWorker`.
5. Endpoint `POST /api/app/error-reports/batch`.
6. JSON body со schema v1.
7. Дедупликация по `client_report_id`.
8. Ограничения размера и safe retry.

## See Also

- [Надёжная отправка ошибок и крешей на свой API](crash-error-reporting-architecture.md)
- [Техническое задание: Device Connect Snapshot](device-connect-snapshot-technical-spec.md)
