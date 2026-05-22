# Device Connect Snapshot Implementation Notes

> Source: Local implementation summary from Cursor coding session
> Collected: 2026-05-23
> Published: Unknown

## Реализованные изменения

Фича `Device Connect Snapshot` реализована в Android-клиенте как узкое расширение существующего connect flow.

Добавлен `DeviceConnectSnapshotCollector` в `app/src/main/java/com/android/autopay/data/datasource/device/DeviceConnectSnapshotCollector.kt`. Сборщик формирует строку с JSON-снимком устройства и окружения приложения. Снимок содержит `schema_version`, `collected_at` и секции `app`, `android`, `device`, `runtime`, `capabilities`.

Сбор реализован best-effort: каждая секция добавляется через `runCatching`; если секция или поле не собираются, они пропускаются. Итоговая строка возвращается только если она непустая и не превышает лимит `1_048_576` символов.

`NotificationApi.DeviceInfoPayload` расширен nullable-полем `deviceConnectSnapshot: String?`.

`NotificationApi.connect()` продолжает отправлять прежние поля `android_id`, `device_model`, `android_version`, `manufacturer`, `brand` и добавляет `device_connect_snapshot` только если значение непустое.

`NotificationRepositoryImpl.connect()` собирает legacy-поля как раньше и дополнительно получает snapshot через `DeviceConnectSnapshotCollector`. Сбор обёрнут в `runCatching`, поэтому ошибка snapshot collector не меняет результат connect.

`ping()`, `sendNotification()`, endpoint URL, заголовок `Access-Token` и ответ API не изменялись.

## Проверка

IDE lints по изменённым файлам ошибок не показали.

Gradle build, unit tests, instrumented tests и lint-задачи не запускались, потому что правила проекта запрещают запуск сборки и тестов без явного запроса пользователя.
