# Device Connect Snapshot API Contract

> Source: User-provided server API change summary in Cursor conversation
> Collected: 2026-05-23
> Published: Unknown

## Что добавить в приложение (кратко)

**Один новый необязательный параметр** в уже существующий запрос `POST /api/app/device/connect`:

| | |
|---|---|
| **Поле** | `device_connect_snapshot` |
| **Тип** | строка (`string`) |
| **Обязательность** | нет — старый APK без поля работает как раньше |
| **Когда слать** | при каждом `connect` (не на `ping`) |
| **Что класть** | одна строка с JSON, как вы собрали данные об устройстве (`JSON.stringify(...)`), без изменений на сервере |
| **Лимит** | до 1 048 576 символов; больше — ответ **422** |
| **Ответ API** | без изменений — этого поля в ответе **нет** |

## Что не трогать

- URL, метод, заголовок `Access-Token`
- Поля `android_id`, `device_model`, `android_version`, `manufacturer`, `brand` — как были
- `device/ping` — без изменений

## Поведение сервера (важно для логики клиента)

- **Не прислали** поле / пустая строка → connect как раньше, снимок на сервере не обновляется
- **Прислали** непустую строку → снимок сохраняется/перезаписывается при каждом connect (в т.ч. повторный connect с тем же `android_id`)

## Пример — только добавка к текущему body

```json
{
  "android_id": "...",
  "device_model": "...",
  "android_version": "...",
  "manufacturer": "...",
  "brand": "...",
  "device_connect_snapshot": "{\"sdk\":34,\"model\":\"...\"}"
}
```

Итого для мобильной команды: **добавить одно опциональное поле в connect; всё остальное без изменений.**
