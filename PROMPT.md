Создай полный проект “Smart Feeder Cloud” (сервер + веб-интерфейс) для управления Arduino/ESP32-кормушкой по схеме: клиент (устройство) раз в минуту делает HTTPS запрос на сервер (“poll”), отправляет статус и логи, а сервер отвечает командами (“что делать”), плюс веб-панель в браузере позволяет менять расписание/профили и видеть историю кормлений/онлайн-статус.

Требования к стеку и структуре:

Используй KORA.md для создания бекэнда на фреймворке Kora

База: PostgreSQL (docker-compose).

Frontend: HTML + JS + CSS + Bootstrap(по желанию)

Авторизация в веб-панели: простая (email+password)ю
Должна быть возможность регистрации пользователя.
Должна быть возможность добавить новое устройство к себе в профиль.

Сделай красивый UI.

Всё должно запускаться локально одной командой (через docker-compose + npm scripts).

Добавь README с пошаговым запуском и тестовыми примерами запросов.

Основные сущности и БД (минимум):

User (админ панели)

id, email, passwordHash, createdAt

Device

id (строка типа "feeder-001"), name, secretHash (хэш секрета устройства, сам секрет в базе не хранить в открытом виде),

lastSeenAt, lastStatusJson (jsonb), firmwareVersion, createdAt

Profile

id, deviceId, name (kitten/adult/diet/...), defaultPortionMs, createdAt

ScheduleEvent

id, profileId, hh (0-23), mm (0-59), portionMs

CommandQueue

id, deviceId, commandType (FEED_NOW, SET_PROFILE, SET_SCHEDULE, SET_DEFAULT_PORTION, REBOOT, PING),

payloadJson (jsonb),

status (PENDING, SENT, ACKED, FAILED),

createdAt, sentAt, ackedAt

FeedLog

id, deviceId, ts (timestamp), type (AUTO_FEED/MANUAL_FEED/ERROR/BOOT/PROFILE_CHANGED/SCHEDULE_UPDATED),

message, metaJson (jsonb)

API для устройства (device API):
A) POST /api/device/poll
Назначение: устройство раз в minute отправляет heartbeat/status/logs и получает команды/конфиг.
Аутентификация устройства:

В заголовках: X-Device-Id, X-Nonce, X-Sign

Подпись X-Sign = HMAC-SHA256(body, device_secret) в hex.

Сервер должен:

найти device по X-Device-Id

проверить nonce на повтор (хранить последние N nonce или timestamp окна, чтобы защищаться от replay)

проверить HMAC подпись (секрет сравнивать по хэшу? можно хранить секрет в env и хэш в БД; но для MVP допустимо хранить secretHash + отдельное поле encryptedSecret; выбери безопасный практичный вариант и объясни в README)

обновить lastSeenAt и lastStatusJson

принять logs (массив) и записать в FeedLog

принять ack команд (если устройство прислало ackIds) и пометить ACKED

выдать ответ:
{
"serverTime": <unix>,
"intervalSec": 60,
"commands": [ ... до 10 штук ... ],
"config": { "activeProfile": "...", "profiles": [...], "schedule": [...] }
}

Пометить выданные команды как SENT с sentAt.

Формат тела device poll (JSON):
{
"deviceId": "feeder-001",
"ts": 1700000000,
"status": {
"fw": "1.0.3",
"uptimeSec": 12345,
"rssi": -60,
"error": null,
"lastFeedTs": 1699999000
},
"log": [
{"ts":1699998000,"type":"AUTO_FEED","msg":"portion=1200 profile=adult","meta":{}}
],
"ack": ["cmd-uuid-1","cmd-uuid-2"]
}

B) POST /api/device/register (опционально)
Можно сделать регистрацию устройства через панель админа: генерируется deviceId + secret, secret показывается один раз, дальше хранится только хэш.
Если register делаешь — защити его админской авторизацией (не для устройства).

Веб-панель (admin UI):

Страница Dashboard:

список устройств: имя, deviceId, ONLINE/OFFLINE (online если lastSeenAt > now-2min), lastSeenAt, RSSI, fw

кнопка открыть устройство

Страница Device:

текущий статус (последний JSON красиво отформатировать)

активный профиль (селектор)

профили (создать/удалить/переименовать, defaultPortionMs)

расписание активного профиля: таблица событий (hh:mm, portionMs), добавить/удалить, сохранить

кнопка “Feed now” с выбором порции (по умолчанию defaultPortionMs)

лог событий (последние 200): фильтр по типу, поиск, пагинация

Страница Security:

показать warning если устройство давно не в сети

кнопка “Rotate secret” (генерит новый secret, показывает один раз)

Команды (server->device):

FEED_NOW payload: { "portionMs": number }

SET_PROFILE payload: { "profileName": string }

SET_SCHEDULE payload: { "profileName": string, "events": [{hh,mm,portionMs}] }

SET_DEFAULT_PORTION payload: { "profileName": string, "defaultPortionMs": number }
Команды должны иметь уникальный id (UUID).

Валидация и безопасность:

Валидация входящих JSON (zod).

Rate limiting на device poll.

Защита от replay (nonce + окно времени).

Пароли пользователей хранить через bcrypt.

Все секреты/ключи через env.

Пиши аккуратные ошибки, без утечек.

DevOps:

docker-compose: postgres + app + nginx(frontend + backend)

создать тестовое устройство feeder-001 и секрет (вывести секрет в консоль при seed)
создать профили (kitten/adult/diet) и расписания по умолчанию

Сделать возможность эмуляции устройств, чтобы они делали все как будто настоящие устройства.

Дополнительно (желательно):

Swagger/OpenAPI для device API и admin API.

Примеры curl для /api/device/poll (подпись можно посчитать маленьким скриптом).

Unit-тесты хотя бы для проверки HMAC и replay защиты (jest).

Вывод:
Сгенерируй весь репозиторий целиком: структура папок, весь код, конфиги (tsconfig, eslint), prisma schema, migrations/seed, docker-compose, README. Проект должен быть готов к запуску локально.

Важно: устройство является “клиентом”, оно не принимает входящие подключения. Сервер — интернет-сайт. Устройство каждые 60 секунд поллит сервер, чтобы:

сообщить что оно онлайн и передать статус

отдать логи выдачи еды

получить команды (в т.ч. “насыпать сейчас”)

получить обновлённые профили/расписание/порции.
:::