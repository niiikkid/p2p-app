# Переписка: механизм отправки логов при падении Android-приложения

> Source: user-provided AI conversation (p2p-app project)
> Collected: 2026-05-23
> Published: Unknown

## Вопрос пользователя

Я хочу сделать вот что: бывает, приложение крошится, ломается, появляется какая-то ошибка и так далее. И я хочу какой-то супернадёжный, суперкрутой механизм, который в любом случае, при любой ситуации, если приложение сломается, отправит мне на API, на мой сайт, какой-то лог, какую-то распечатку, какую-то информацию по ошибке, чтобы я мог это отслеживать. Какие есть механизмы реализации данной штуки?

## Ответ AI

Абсолютного "при любой ситуации" на Android не существует: если процесс убили SIGKILL, устройство выключилось, нет сети, приложение force-stopped, кончилась память до записи лога — код уже не выполнится. Но можно сделать очень надёжную многоуровневую систему.

### Лучший вариант архитектуры

#### 1. Локальный crash/error journal

Сначала никогда не отправлять лог напрямую в момент ошибки. Сначала синхронно сохранить событие локально:

* Room таблица error_reports
* поля: id, timestamp, type, severity, message, stacktrace, thread, screen, userId, appVersion, androidVersion, device, breadcrumbs, sent=false
* можно дублировать критический минимум в файл, потому что Room тоже может не успеть при жёстком креше

Идея: сначала durable write, потом отправка.

---

#### 2. Глобальный UncaughtExceptionHandler

В Application.onCreate() ставишь обработчик:

```kotlin
class AppCrashHandler(
    private val previous: Thread.UncaughtExceptionHandler?,
    private val crashStore: CrashStore
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            crashStore.saveBlocking(
                CrashReport.fromThrowable(thread, throwable)
            )
        } catch (_: Throwable) {
            // не даём crash handler самому упасть
        } finally {
            previous?.uncaughtException(thread, throwable)
                ?: kotlin.system.exitProcess(10)
        }
    }
}
```

Важно: внутри crash handler нельзя делать сложный DI, сеть, корутины, JSON-библиотеки, тяжёлый Room-код. Лучше иметь максимально простой saveBlocking().

---

#### 3. CoroutineExceptionHandler

Для корутин:

```kotlin
val appCoroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    errorReporter.reportNonFatal(throwable)
}
```

Но он ловит не всё. Ошибки внутри viewModelScope.launch {} лучше ещё оборачивать в свой helper:

```kotlin
fun ViewModel.safeLaunch(
    block: suspend CoroutineScope.() -> Unit
) = viewModelScope.launch {
    try {
        block()
    } catch (t: Throwable) {
        errorReporter.reportNonFatal(t)
        throw t
    }
}
```

---

#### 4. WorkManager для отправки

После сохранения ошибки запускаешь OneTimeWorkRequest, а также запускаешь отправку при старте приложения.

WorkManager подходит для надёжных отложенных задач с constraints/retry, когда сеть появится позже.

```kotlin
val request = OneTimeWorkRequestBuilder<SendCrashReportsWorker>()
    .setConstraints(
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    )
    .setBackoffCriteria(
        BackoffPolicy.EXPONENTIAL,
        30.seconds
    )
    .build()
workManager.enqueueUniqueWork(
    "send_crash_reports",
    ExistingWorkPolicy.KEEP,
    request
)
```

Worker:

```kotlin
@HiltWorker
class SendCrashReportsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val crashRepository: CrashRepository,
    private val api: CrashApi
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val reports = crashRepository.getUnsent(limit = 50)
        for (report in reports) {
            try {
                api.sendCrashReport(report)
                crashRepository.markSent(report.id)
            } catch (_: Throwable) {
                return Result.retry()
            }
        }
        return Result.success()
    }
}
```

---

#### 5. ApplicationExitInfo для смертей процесса

Начиная с Android 11 / API 30 можно читать причины предыдущих завершений процесса через ApplicationExitInfo. Это полезно для случаев вроде ANR, low memory kill, crash, native crash. У тебя minSdk 26, значит нужно использовать только на API 30+.

При старте приложения:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    val activityManager = getSystemService(ActivityManager::class.java)
    val exits = activityManager.getHistoricalProcessExitReasons(packageName, 0, 5)
    exits.forEach { exit ->
        // сохранить reason, importance, timestamp, description, traceInputStream если есть
    }
}
```

Это закрывает часть ситуаций, где твой UncaughtExceptionHandler не сработал.

---

#### 6. Breadcrumbs

Очень полезная штука: храни последние 50–200 событий перед крешем:

* экран открыт
* кнопка нажата
* API request начался
* API response code
* foreground/background
* notification event
* permission result
* важные state transitions

Например:

```kotlin
logger.breadcrumb("screen", "SettingsScreen opened")
logger.breadcrumb("api", "GET /profile -> 500")
logger.breadcrumb("notification", "NotificationListenerService connected")
```

При ошибке прикладываешь breadcrumbs к crash report.

---

### Что ещё ловить

**Handled/non-fatal errors**

Не только креши:

```kotlin
try {
    api.call()
} catch (t: Throwable) {
    errorReporter.reportNonFatal(t)
}
```

**HTTP ошибки**

В OkHttp interceptor можно логировать:

* URL path без sensitive данных
* method
* response code
* duration
* request id
* server error body, если безопасно

Не логируй токены, пароли, cookies, Authorization header.

**ANR**

Свой ANR watchdog можно сделать, но лучше осторожно. Надёжнее использовать:

* Google Play Console Android Vitals
* Crashlytics / Sentry / Bugsnag
* ApplicationExitInfo после рестарта

**Native crashes**

Если нет C/C++ — неважно. Если появится NDK — нужен отдельный сбор нативных crash reports.

---

### Готовые решения

Я бы не писал всё полностью сам, а сделал так:

**Вариант А — Sentry**

Очень хороший вариант для твоего стэка:

* crashes
* non-fatal exceptions
* breadcrumbs
* performance traces
* custom tags
* offline cache
* release/environment
* user context

Плюс можешь настроить self-hosted Sentry.

**Вариант B — Firebase Crashlytics**

Проще всего подключить. Crashlytics автоматически собирает fatal events, умеет non-fatal exceptions, custom logs и custom keys. Non-fatal события сохраняются на устройстве и отправляются позже, например при следующем запуске.

**Вариант C — свой механизм**

Нормально, если тебе нужно именно отправлять на свой API. Но я бы всё равно оставил Crashlytics/Sentry как fallback.

---

### Рекомендованная схема

```
App code
  ↓
ErrorReporter interface
  ↓
1. save to local journal
2. add breadcrumbs
3. enqueue WorkManager
4. optionally send to Sentry/Crashlytics
  ↓
Your API
```

И отдельно при каждом старте:

```
Application.onCreate()
  ↓
read unsent reports from Room/file
  ↓
read ApplicationExitInfo on API 30+
  ↓
enqueue SendCrashReportsWorker
```

---

### Минимальный набор

1. ErrorReporter как единая точка логирования ошибок.
2. UncaughtExceptionHandler для фатальных JVM crashes.
3. CoroutineExceptionHandler + safeLaunch.
4. Room/file очередь unsent reports.
5. WorkManager retry sender.
6. Breadcrumbs.
7. ApplicationExitInfo на API 30+.
8. Crashlytics или Sentry как резервный внешний канал.

Главная мысль: в момент падения только быстро сохранить, а отправлять уже после рестарта или через WorkManager. Это будет гораздо надёжнее, чем пытаться делать HTTP-запрос прямо в crash handler.
