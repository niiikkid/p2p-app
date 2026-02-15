# P2P Bridge (Android)

Android-приложение для сбора входящих SMS и push-уведомлений, сохранения их в локальную историю и отправки на внешний сервер с ретраями при сбоях.

## Технологический стек
- Kotlin 2.0.0, JVM 17, minSdk 26, targetSdk 34, AGP 8.5.1
- Jetpack Compose (BOM 2024.06.00), Material3
- Hilt 2.51.1 (включая интеграцию с Worker)
- WorkManager 2.9.0
- Room 2.6.1 (KSP), DataStore Preferences
- OkHttp 4.12.0

## Требования
- Android Studio (актуальная версия под AGP 8.5.1)
- JDK 17
- Устройство/эмулятор Android 8.0+ (API 26+)

[P2P процессинг платформа/скрипт](https://github.com/niiikkid/p2p.processing)
