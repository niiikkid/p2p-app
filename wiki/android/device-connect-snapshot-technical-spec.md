# Техническое задание: Device Connect Snapshot

> Sources: Internal server API contract, 2026-05-23; AI assistant conversation, 2026-05-23; Local implementation summary, 2026-05-23
> Raw: [Device Connect Snapshot API Contract](../../raw/android/2026-05-23-device-connect-snapshot-api-contract.md); [Device Connect Snapshot Implementation Notes](../../raw/android/2026-05-23-device-connect-snapshot-implementation-notes.md)

> Updated: 2026-05-23

## Overview

При каждом подключении устройства приложение должно отправлять на существующий endpoint `POST /api/app/device/connect` дополнительное необязательное поле `device_connect_snapshot`. Поле содержит строку с JSON-снимком доступной информации об устройстве и окружении приложения. Сбор снимка должен быть best-effort: если часть данных недоступна или при сборе возникает исключение, приложение пропускает проблемное поле/секцию и продолжает connect без падения.

## Цель фичи

Фича нужна для серверной отладки проблем на конкретных устройствах: версия Android, модель, сборка ОС, ABI, версия приложения, состояние разрешений и системных интеграций. Снимок не должен влиять на бизнес-логику подключения: он дополняет текущий connect, но не становится обязательным условием успешного подключения.

## Изменение API

Существующий запрос:

`POST /api/app/device/connect`

Остаётся без изменений по URL, методу и заголовкам. Заголовок `Access-Token` сохраняется в текущем виде. В body добавляется одно поле:

| Параметр | Значение |
|---|---|
| Поле | `device_connect_snapshot` |
| Тип | `string` |
| Обязательность | необязательное |
| Формат значения | строка, содержащая JSON-объект |
| Когда отправлять | при каждом `connect` и повторном connect |
| Где не отправлять | `device/ping` |
| Максимальный размер | 1 048 576 символов |
| Ответ API | без изменений, поле не возвращается |

Текущие поля `android_id`, `device_model`, `android_version`, `manufacturer`, `brand` остаются как есть. Старые APK без `device_connect_snapshot` продолжают работать.

## Поведение сервера

Если поле отсутствует или равно пустой строке, сервер обрабатывает connect как раньше и не обновляет сохранённый снимок устройства. Если поле присутствует и содержит непустую строку, сервер сохраняет или перезаписывает снимок при каждом connect, включая повторный connect с тем же `android_id`.

Если значение превышает 1 048 576 символов, сервер возвращает `422`. Клиент должен избегать отправки слишком большого снимка, чтобы не ломать пользовательский сценарий подключения.

## Формат request body

Пример тела запроса после изменения:

```json
{
  "android_id": "...",
  "device_model": "...",
  "android_version": "...",
  "manufacturer": "...",
  "brand": "...",
  "device_connect_snapshot": "{\"schema_version\":1,\"sdk\":34,\"model\":\"...\"}"
}
```

Важно: `device_connect_snapshot` — именно строка с сериализованным JSON, а не вложенный JSON-объект. Сервер сохраняет строку как пришла, без нормализации структуры.

## Требования к клиентской реализации

Клиент должен сформировать JSON-снимок в отдельном сборщике, например `DeviceConnectSnapshotCollector`, и передать результат в существующий connect payload. Сборщик должен возвращать `String?`: непустую строку при успешной сериализации хотя бы части данных и `null` или пустую строку, если снимок собрать не удалось.

Сбор снимка не должен изменять поведение `ConnectDeviceUseCase`: успешность connect определяется ответом API на сам запрос, а не полнотой диагностических данных. Если snapshot не собрался, запрос должен уйти без поля или с пустым значением согласно выбранной реализации клиента.

Рекомендуемый поток:

```text
ConnectDeviceUseCase(token)
  -> NotificationRepository.connect(token)
    -> собрать legacy поля как раньше
    -> best-effort собрать device_connect_snapshot
    -> NotificationApi.connect(token, payload)
```

## Best-Effort и отказоустойчивость

Главное инвариантное требование: приложение не должно падать при сборе диагностической информации.

Сбор каждой секции или потенциально рискованного поля должен быть изолирован через `try/catch` или `runCatching`. Если поле недоступно, возвращает `null`, требует отсутствующего permission или падает из-за OEM-особенности, оно пропускается. Исключение внутри одной секции не должно отменять весь connect.

Минимальные правила безопасности:

- Не использовать `!!` при сборе значений.
- Не обращаться к массивам через `[0]` без проверки `getOrNull(0)`.
- Не выполнять permission-зависимые чтения без проверки разрешения.
- Не запрашивать новые runtime permissions во время connect.
- Не выполнять тяжёлые операции на main thread.
- Не отправлять токены, SMS-тексты, пароли, cookies, access headers и другие секреты.
- Ограничивать итоговый размер строки до серверного лимита с запасом.

Если итоговая строка снимка превышает лимит, клиент должен пропустить `device_connect_snapshot` или отправить укороченную версию с признаком truncation. Предпочтительный вариант для первого внедрения — пропустить поле, чтобы не получить `422` и не сломать подключение.

## Рекомендуемая структура snapshot JSON

Структура внутри строки не является серверным контрактом, но должна быть стабильной для людей и будущей аналитики. Рекомендуется включить версию схемы:

```json
{
  "schema_version": 1,
  "collected_at": 1716470400000,
  "app": {
    "package_name": "com.android.autopay",
    "version_name": "1.0",
    "version_code": 1
  },
  "android": {
    "sdk_int": 34,
    "release": "14",
    "security_patch": "2024-06-01",
    "incremental": "...",
    "codename": "REL"
  },
  "device": {
    "manufacturer": "...",
    "brand": "...",
    "model": "...",
    "device": "...",
    "product": "...",
    "board": "...",
    "hardware": "...",
    "display": "...",
    "fingerprint": "..."
  },
  "runtime": {
    "supported_abis": ["arm64-v8a"],
    "available_processors": 8,
    "locale": "ru_RU",
    "timezone": "Asia/Bangkok"
  },
  "capabilities": {
    "notification_listener_enabled": true,
    "sms_receive_permission_granted": true,
    "sms_read_permission_granted": true,
    "post_notifications_permission_granted": true,
    "battery_optimization_ignored": false
  }
}
```

Секции можно расширять без изменения серверного API, пока итоговый результат остаётся строкой в поле `device_connect_snapshot`.

## Минимальный состав данных для первого релиза

Для первого внедрения достаточно отправлять данные, которые уже доступны приложению без новых runtime permissions:

| Секция | Поля |
|---|---|
| `app` | package name, version name, version code |
| `android` | SDK/API level, release, security patch, incremental, codename |
| `device` | manufacturer, brand, model, device, product, board, hardware, display, fingerprint |
| `runtime` | supported ABIs, processors, locale, timezone |
| `capabilities` | granted/denied для уже объявленных permissions, notification listener enabled, battery optimization ignored |

Списки SMS sender'ов, тексты сообщений, содержимое уведомлений и полный список установленных приложений не входят в минимальный snapshot для connect. Эти данные либо чувствительные, либо permission/visibility-зависимые, либо могут резко увеличить размер payload.

## Интеграция в текущий код

В текущем приложении connect собирается в `NotificationRepositoryImpl.connect()` и отправляется через `NotificationApi.connect()`. Изменение должно быть узким:

1. Расширить payload модель `NotificationApi.DeviceInfoPayload` необязательным полем `deviceConnectSnapshot: String?`.
2. Добавить сборщик snapshot в data-слой, например `data/datasource/device/DeviceConnectSnapshotCollector.kt`.
3. В `NotificationRepositoryImpl.connect()` собрать snapshot через `runCatching`, не влияя на legacy поля.
4. В `NotificationApi.connect()` добавлять `device_connect_snapshot` только если строка не `null` и не пустая.
5. Не менять `ApiEndpoints.getConnectUrl()`, `ping()`, `sendNotification()` и contract ответа.

## Статус реализации

Фича реализована в клиенте как узкое расширение существующего connect flow. Добавлен `DeviceConnectSnapshotCollector` в `data/datasource/device`, который формирует JSON-строку со схемой `schema_version = 1`, временем сбора `collected_at` и секциями `app`, `android`, `device`, `runtime`, `capabilities`.

Реализация сохраняет best-effort-инвариант: секции добавляются через изолированные `runCatching`, итоговая строка возвращается только если она непустая и не превышает лимит `1_048_576` символов. Ошибка collector не должна менять результат connect, потому что `NotificationRepositoryImpl.connect()` получает snapshot через отдельный `runCatching`.

`NotificationApi.DeviceInfoPayload` расширен nullable-полем `deviceConnectSnapshot: String?`. `NotificationApi.connect()` продолжает отправлять legacy-поля и добавляет `device_connect_snapshot` только при непустом значении. `ping()`, `sendNotification()`, URL connect, заголовок `Access-Token` и contract ответа не изменялись.

IDE lints по изменённым файлам ошибок не показали. Gradle build, unit tests, instrumented tests и lint-задачи не запускались, потому что правила проекта запрещают запуск сборки и тестов без явного запроса пользователя.

Псевдокод:

```kotlin
val snapshot: String? = runCatching {
    deviceConnectSnapshotCollector.collect()
}.getOrNull()?.takeIf { it.isNotBlank() && it.length <= DEVICE_CONNECT_SNAPSHOT_LIMIT }

val payload = DeviceInfoPayload(
    androidId = androidId.orEmpty(),
    deviceModel = Build.MODEL,
    androidVersion = Build.VERSION.RELEASE,
    manufacturer = Build.MANUFACTURER,
    brand = Build.BRAND,
    deviceConnectSnapshot = snapshot
)
```

## Обработка ошибок

Если сбор отдельного поля упал, поле не попадает в snapshot. Если сбор всей секции упал, секция не попадает в snapshot. Если сериализация JSON упала, `device_connect_snapshot` не отправляется. Если сервер вернул `422` из-за размера, connect считается неуспешным по текущим правилам API-ошибок; чтобы избежать этого сценария, клиент обязан проверять размер до отправки.

Ошибки сборщика не должны попадать пользователю как ошибка подключения. Логировать их можно только локально и без чувствительных данных.

## Неизменяемые части

В рамках этой фичи нельзя менять:

- URL `POST /api/app/device/connect`;
- заголовок `Access-Token`;
- существующие legacy поля connect body;
- `GET /api/app/device/ping`;
- ответ API;
- логику сохранения `isConnected` после успешного connect.

## Критерии приёмки

Фича считается реализованной, если:

1. При connect клиент отправляет `device_connect_snapshot`, когда snapshot успешно собран и не превышает лимит.
2. Старые обязательные поля connect body сохраняются без переименования и изменения смысла.
3. Ping-запрос не содержит snapshot.
4. Если любая часть сборщика падает, приложение не падает, connect всё равно выполняется.
5. Если snapshot невозможно собрать, connect выполняется без нового поля или с пустым значением.
6. Итоговая строка не превышает 1 048 576 символов.
7. Snapshot не содержит токены, SMS-тексты, содержимое уведомлений, пароли или cookies.
8. Повторный connect отправляет актуальный snapshot заново.

## Тест-план

Рекомендуемые проверки без запуска приложения на боевом сервере:

- Unit test для collector: исключение в одной секции не ломает сбор остальных секций.
- Unit test для collector: пустые/null значения пропускаются.
- Unit test для API serialization: `device_connect_snapshot` добавляется только при непустой строке.
- Unit test для лимита: слишком длинный snapshot не отправляется.
- Проверка regression: legacy JSON поля остаются прежними.
- Проверка regression: `ping()` не изменён.

## See Also

- [Надёжная отправка ошибок и крешей на свой API](crash-error-reporting-architecture.md)
