# AutoPay (p2p-app) — Техническая документация

## Назначение
Приложение Android собирает входящие SMS и push-уведомления, сохраняет их в локальную историю и отправляет на внешний сервер с ретраями при сбоях. Пользователь указывает токен доступа. Приложение работает устойчиво в фоне, используя foreground-сервис и WorkManager.

## Технологический стек
- **Kotlin/JVM/SDK**: Kotlin 2.0.0, JVM 17, minSdk 26, targetSdk 34, AGP 8.5.1
- **UI**: Jetpack Compose (BOM 2024.06.00), Material3
- **DI**: Hilt 2.51.1 (+ Hilt Worker)
- **Фоновая работа**: WorkManager 2.9.0
- **Хранилище**: Room 2.6.1 (KSP), DataStore Preferences
- **Сеть**: OkHttp 4.12.0
- **Manifest**: usesCleartextTraffic=true, NotificationListenerService

## Архитектура (высокий уровень)
- **Точка входа**: `com.android.autopay.MainActivity`
- **Сбор событий**:
  - Push: `com.android.autopay.data.PushNotificationHandlerService` (`NotificationListenerService`)
  - SMS: `com.android.autopay.data.SmsReceiver` (`BroadcastReceiver` на `SMS_RECEIVED_ACTION`)
  - Автозапуск: `com.android.autopay.data.BootBroadcastReceiver` стартует сервис после `BOOT_COMPLETED`
- **Ретраи**: `com.android.autopay.data.NotificationRetryWorker` — периодическая отправка неотправленных
- **Данные**:
  - Репозиторий: `com.android.autopay.data.repositories.NotificationRepository`
  - Room: `NotificationDatabase`, `NotificationHistoryDao`, `UnsentNotificationDao`
  - DataStore: `com.android.autopay.data.DataStoreManager`
- **DI**:
  - `com.android.autopay.di.App` — `@HiltAndroidApp`, конфигурирует HiltWorkerFactory для WorkManager
  - `com.android.autopay.di.AppModule` — провайдинг `AppDispatchers`, базы/DAO
- **UI**:
  - `com.android.autopay.presentation.MainViewModel`, `com.android.autopay.presentation.MainScreen`

## Последовательность работы (end-to-end)
1. Запуск `MainActivity`:
   - Запрашиваются разрешения: `RECEIVE_SMS`, доступ к слушателю уведомлений, отключение оптимизации батареи.
   - Стартует foreground-сервис `PushNotificationHandlerService`.
   - Планируется `NotificationRetryWorker` каждые 15 минут при наличии сети.
   - Отрисовывается Compose-экран `MainScreen`.
2. Сбор push-уведомлений (`PushNotificationHandlerService`):
   - В `onStartCommand` создаётся канал уведомлений и запускается foreground.
   - В `onNotificationPosted`:
     - Игнорируются уведомления из дефолтного SMS-пакета и собственного пакета приложения.
     - Извлекается текст `android.text`, формируется доменная модель `Notification(type=PUSH)` с `idempotencyKey=UUID`.
     - Событие пишется в историю (Room) и пытается отправиться на сервер; при неуспехе — кладётся в очередь ретраев (Room) c новым `Idempotency-Key`.
3. Сбор SMS (`SmsReceiver`):
   - На `SMS_RECEIVED_ACTION` склеиваются multipart-SMS (`Telephony.Sms.Intents.getMessagesFromIntent`) в одно сообщение.
   - Формируется `Notification(type=SMS)` с `idempotencyKey=UUID`.
   - Сохраняется в историю и отправляется на сервер; при неуспехе — очередь ретраев с новым ключом.
4. Ретраи (`NotificationRetryWorker`):
   - Каждые 15 минут (при сети) загружает все неотправленные из `UnsentNotificationDao`.
   - Параллельно отправляет их. Успешные — удаляет; при частичных удачах — `Result.retry()` для новой попытки.
5. Автозапуск после перезагрузки (`BootBroadcastReceiver`):
   - По `BOOT_COMPLETED` запускает `PushNotificationHandlerService` в foreground.

## Сетевой протокол
- **Endpoint**: фиксированный `DEFAULT_URL` из `data/utils/Constants.kt` (`https://nikkid.ru/api/app/sms`). Поле `url` в `SettingsData` пока не используется в `NotificationRepository` (см. раздел Несоответствия/улучшения).
- **Заголовки**:
  - `Accept: application/json`
  - `Idempotency-Key: <UUID из уведомления>`
  - `Access-Token: <token из DataStore>`
- **Тело JSON**:
  - `sender`, `message`, `timestamp`, `type`
- **Идемпотентность**:
  - При первичной неудаче отправки сохраняем запись в очередь с новым `Idempotency-Key` для следующих попыток.

## Слой данных
- **Модели**:
  - `Notification(sender, message, timestamp, type, idempotencyKey)`
  - `NotificationType`: `sms`, `push`
  - Транспортные: `Sms`, `Push`
- **Room**:
  - `NotificationDatabase` (version=1, destructive migrations)
  - Таблицы: `notification_history`, `unsent_notifications`
  - DAO:
    - `NotificationHistoryDao.upsert()`, `observeAll(): Flow<List<...>>`
    - `UnsentNotificationDao.upsert()`, `getAll()`, `delete()`
  - Мапперы: `Notification <-> HistoryNotificationDBO`, `Notification <-> UnsentNotificationDBO`
- **DataStore**:
  - Ключи: `token` (строка). `url` в схеме есть, но фактически не сохраняется/не читается — используется `DEFAULT_URL`.

## Репозиторий (`NotificationRepository`)
- `sendToServer(notification)`:
  - Читает `token` из `DataStore`.
  - Формирует заголовки и JSON, делает `POST` на `DEFAULT_URL` с OkHttp.
  - Успех -> `Result.success(Unit)`; иначе `Result.failure(Exception(code))`.
- `saveToHistory(notification)` — запись в историю.
- `observeHistory()` — поток для UI.
- Очередь ретраев: `saveForRetry()`, `getForRetry()`, `deleteForRetry()`.

## DI и фоновые диспетчеры
- `App` — включает Hilt, интегрирует HiltWorkerFactory в WorkManager.
- `AppModule` — провайдит `AppDispatchers`, `NotificationDatabase`, `NotificationHistoryDao`, `UnsentNotificationDao`.
- `AppDispatchers` — набор корутин-диспетчеров (Default/IO/Main/Unconfined).

## Презентационный слой
- `MainViewModel`:
  - Загружает `token` из `DataStore`, управляет `isSavePossible`, `isConnected`.
  - Подписывается на `observeHistory()` и отражает статистику: всего получено, последние 5.
  - Собирает информацию об устройстве для диагностического блока.
  - Интенты: `ChangeToken`, `Save`.
- `MainScreen` (Compose):
  - Экран настроек/статуса: редактирование токена, индикатор подключения, блок сведения об устройстве, статистика и последние уведомления (или полный лог).

## Разрешения и поведение (Manifest)
- Permissions: `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `RECEIVE_SMS`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- Components:
  - `MainActivity` (LAUNCHER)
  - `PushNotificationHandlerService` (`NotificationListenerService`, exported, foregroundServiceType=specialUse)
  - `SmsReceiver` (`BROADCAST_SMS`, priority=5)
  - `BootBroadcastReceiver` (на `BOOT_COMPLETED`)
- Network: `usesCleartextTraffic=true` (для dev), рекомендуется HTTPS в проде.

## Потоки данных (диаграмма последовательности — словесно)
- Push/SMS -> Парсинг -> `Notification` -> Room (история) -> Попытка отправки -> Успех (ничего) | Ошибка -> Room (очередь) -> WorkManager (периодически) -> Отправка -> Успех -> Удаление из очереди | Ошибка -> повтор позже.

## Краевые случаи и устойчивость
- Сервис перезапускается системой (`START_STICKY`), `onListenerDisconnected()` запрашивает `requestRebind(...)`.
- Очистка уведомлений: `NotificationManager.cancelAll()` для системного трея самого сервиса.
- Обработка multipart SMS: соединение всех частей в один текст.
- Параллельные ретраи: отправка пачки с `async/awaitAll`, корректная обработка частичных успехов.

## Несоответствия/улучшения
1. `SettingsData.url` не используется при отправке: `NotificationRepository.sendToServer()` всегда бьёт на `DEFAULT_URL`. Улучшение: читать `settings.url` из DataStore и использовать его.
2. `DataStoreManager.saveSettings()` не сохраняет `url`. Улучшение: добавить ключ `URL_KEY` и чтение/запись `url`.
3. Безопасность: `usesCleartextTraffic=true`. В прод-сборках включать только HTTPS и выключать флаг.
4. Ошибки сети различать по типам/телу ответа; логировать причину.
5. Rate limiting/Backoff: можно использовать `WorkRequest` с экспоненциальным backoff для on-demand ретраев.
6. Персистентные ключи идемпотентности: сейчас на ретрай генерируется новый ключ — это ок, но следует учитывать логику бэка (возможно, нужен сохранённый ключ).
7. Улучшить контроль доступности: `MainViewModel` помечает `isConnected=true` после сохранения токена — лучше валидировать токен пробным пингом.

## Как собрать и запустить
- Сборка: `./gradlew :app:assembleDebug`
- Первый запуск: предоставить `RECEIVE_SMS`, включить Notification Listener, отключить оптимизацию батареи.
- В UI: ввести токен, нажать «Сохранить». Приложение начнёт сбор и отправку событий.

## Ключевые файлы
- `app/src/main/AndroidManifest.xml`
- `com.android.autopay.MainActivity`
- `com.android.autopay.data.PushNotificationHandlerService`
- `com.android.autopay.data.SmsReceiver`
- `com.android.autopay.data.BootBroadcastReceiver`
- `com.android.autopay.data.NotificationRetryWorker`
- `com.android.autopay.data.repositories.NotificationRepository`
- `com.android.autopay.data.local.db.*`, `data/local/models.*`, `data/local.mappers.*`
- `com.android.autopay.data.DataStoreManager`
- `com.android.autopay.presentation.MainViewModel`, `presentation.MainScreen`
- `com.android.autopay.di.App`, `di.AppModule`
