# Smart Feeder Cloud

Полный локальный проект для управления Arduino/ESP32-кормушкой:
- backend: **Java + Kora**
- DB: **PostgreSQL**
- web admin UI: **HTML/CSS/JS + Bootstrap**
- device simulator: **Node.js**
- запуск: **одной командой через docker compose**

Главная особенность: проверка подписи устройства сделана **переключаемой глобальным флагом** и по умолчанию **выключена**.

## Что реализовано

- Регистрация/логин/логаут пользователя (cookie session, HttpOnly).
- Управление устройствами:
  - добавление устройства,
  - просмотр списка и карточки устройства,
  - rotate secret (показывается один раз).
- Профили и расписание:
  - создание/обновление/удаление профилей,
  - выбор активного профиля,
  - замена расписания профиля.
- Команды:
  - `FEED_NOW`,
  - `SET_PROFILE` через выбор активного профиля.
- `POST /api/device/poll`:
  - heartbeat/status/log/ack,
  - выдача до 10 pending команд,
  - подтверждение ack.
- Защита:
  - bcrypt для паролей,
  - device secret: `secret_hash` + `encrypted_secret` (AES-GCM),
  - rate limit на poll,
  - replay protection (nonce) при включенной подписи.
- OpenAPI + Swagger UI (`/swagger-ui`).
- Unit тесты (JUnit) для HMAC, replay/feature-flag, crypto, session cookie.

## Структура

```text
backend/      Kora backend + migrations + OpenAPI
frontend/     статический web UI
simulator/    эмулятор устройства
nginx/        reverse proxy + static hosting
docker-compose.yml
package.json
```

## Быстрый старт

1. Подготовить env:

```bash
cp .env.example .env
```

2. Запустить всё:

```bash
npm run up
```

3. Открыть:
- UI: [http://localhost:8080](http://localhost:8080)
- Swagger: [http://localhost:8080/swagger-ui](http://localhost:8080/swagger-ui)

4. Seed данные при первом старте:
- user: `demo@smartfeeder.local`
- password: `demo12345`
- device: `feeder-001`
- secret: печатается в лог backend один раз (`Seed device secret...`).

## Управление

```bash
npm run logs   # смотреть логи
npm run down   # остановить и удалить volumes
npm run sim    # локально запустить эмулятор (если backend уже запущен)
```

## Подпись устройства (global toggle)

Флаг:

```env
DEVICE_AUTH_SIGNATURE_ENABLED=false
```

### Поведение

- `false` (default):
  - HMAC-подпись и nonce replay проверки **пропускаются**.
  - остаются проверка существования device и rate-limit.

- `true`:
  - обязательны `X-Device-Id`, `X-Nonce`, `X-Sign`.
  - включается replay protection через `device_nonce`.

## Пример device poll (signature OFF)

```bash
curl -X POST http://localhost:8080/api/device/poll \
  -H 'Content-Type: application/json' \
  -d '{
    "deviceId":"feeder-001",
    "ts":1700000000,
    "status":{"fw":"1.0.3","uptimeSec":12345,"rssi":-60,"error":null,"lastFeedTs":1699999000},
    "log":[{"ts":1699998000,"type":"AUTO_FEED","msg":"portion=1200 profile=adult","meta":{}}],
    "ack":[]
  }'
```

## Пример device poll (signature ON)

1. Включить подпись в `.env` и перезапустить `npm run up`.
2. Использовать секрет устройства.

```bash
BODY='{"ack":[],"deviceId":"feeder-001","log":[],"status":{"error":null,"fw":"1.0.3","lastFeedTs":1699999000,"rssi":-60,"uptimeSec":12345},"ts":1700000000}'
NONCE="nonce-$(date +%s)"
SIGN=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "<DEVICE_SECRET>" -hex | sed 's/^.* //')

curl -X POST http://localhost:8080/api/device/poll \
  -H 'Content-Type: application/json' \
  -H "X-Device-Id: feeder-001" \
  -H "X-Nonce: $NONCE" \
  -H "X-Sign: $SIGN" \
  -d "$BODY"
```

Примечание: для HMAC body должен совпадать байт-в-байт.

## Эмулятор устройства

Локально:

```bash
SIM_POLL_URL=http://localhost:8080/api/device/poll \
SIM_DEVICE_ID=feeder-001 \
SIM_SIGNATURE_ENABLED=false \
npm run sim
```

Через docker compose профиль:

```bash
docker compose --profile sim up --build
```

## API

Основные endpoint'ы:
- `POST /api/device/poll`
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `GET /api/admin/devices`
- `POST /api/admin/devices`
- `GET /api/admin/devices/{deviceId}`
- `POST /api/admin/devices/{deviceId}/rotate-secret`
- `POST /api/admin/devices/{deviceId}/profiles`
- `PATCH /api/admin/profiles/{profileId}`
- `DELETE /api/admin/profiles/{profileId}`
- `PUT /api/admin/profiles/{profileId}/schedule`
- `POST /api/admin/devices/{deviceId}/active-profile`
- `POST /api/admin/devices/{deviceId}/feed-now`
- `GET /api/admin/devices/{deviceId}/logs`

## Тесты backend

```bash
cd backend
./gradlew test
```

Если wrapper не установлен, используйте локальный `gradle test` или запуск в Docker build.
