# VK Comment Bot

Java-бот для сообщества VK с `Long Poll API`. Он получает события в реальном времени, ведёт учёт в `SQLite`, отвечает на первый комментарий пользователя под конкурсным постом и после сообщения в ЛС выдаёт номер подарка из базы.

Текущий сценарий:

- бот слушает `wall_reply_new` через `Long Poll API`;
- первый комментарий пользователя по акции засчитывается;
- бот отвечает под этим комментарием от лица сообщества;
- в ответе даёт ссылку вида `vk.com/write-236069574?...`;
- когда пользователь пишет в сообщения сообщества, бот ловит `message_new`;
- если пользователь ещё не подписан на сообщество, бот присылает клавиатуру с кнопками `Подписаться` и `Я подписался`;
- если комментарий был принят и подписка подтверждена, бот назначает следующий свободный подарок из `SQLite` и отправляет номер в ЛС;
- если включена интеграция с `Google Sheets`, бот автоматически обновляет строку участника в таблице;
- повторные комментарии и повторные сообщения не дают новые подарки;
- если VK запрещает гиперссылки в комментарии, бот автоматически переключается на fallback-текст без прямой ссылки.

## Что хранится в SQLite

- пользователь: `user_id`, имя, фамилия, `screen_name`, ссылка на профиль, аватар;
- первый принятый комментарий: пост, арт-стол, текст, дата;
- ответ бота и ссылка;
- выданный номер подарка и наименование подарка;
- все входящие комментарии как журнал событий;
- входящие сообщения пользователя как отдельный журнал;
- каталог подарков и их статусы `AVAILABLE / ASSIGNED / ISSUED`;
- статус обработки: `ACCEPTED`, `DUPLICATE_USER`, `IGNORED_NON_USER`, `ERROR`.

## Переменные окружения

Обязательные:

- `VK_GROUP_ID` — ID тестовой или боевой группы, которая шлёт callback
- `VK_ACCESS_TOKEN` — ключ доступа сообщества
- `VK_WRITE_LINK_BASE` — базовая ссылка для диалога, например `https://vk.com/write-236069574`

Рекомендуемые:

- `VK_EVENT_SOURCE` — `LONG_POLL`, `CALLBACK` или `BOTH`; по умолчанию `LONG_POLL`
- `VK_CALLBACK_SECRET` — секрет callback, если включён в настройках VK
- `VK_CONFIRMATION_CODE` — строка подтверждения из Callback API, нужна только для `CALLBACK`/`BOTH`
- `VK_LONG_POLL_WAIT_SECONDS` — длительность long poll, обычно `25`
- `VK_TRACKED_POSTS` — карта постов и названий столов в формате `123=Бурятский стол,124=Русский стол`
- `VK_REPLY_TEMPLATE` — шаблон первого ответа
- `VK_DUPLICATE_TEMPLATE` — шаблон ответа на повторный комментарий
- `VK_GIFT_ISSUED_TEMPLATE` — шаблон сообщения при первой выдаче подарка
- `VK_GIFT_REPEAT_TEMPLATE` — шаблон при повторном сообщении пользователя
- `VK_COMMENT_REQUIRED_TEMPLATE` — что писать, если человек написал боту без комментария
- `VK_GIFTS_EXHAUSTED_TEMPLATE` — что писать, если подарки кончились
- `VK_SUBSCRIPTION_REQUIRED_TEMPLATE` — сообщение, если пользователь ещё не подписан на сообщество
- `VK_REPLY_ON_DUPLICATE` — `true/false`, отвечать ли на дубли
- `VK_ENABLE_HYPERLINK_FALLBACK` — `true/false`, пробовать fallback без прямой ссылки при ошибке VK `222`
- `VK_PUBLIC_GROUP_ID` — ID сообщества для fallback-упоминания; по умолчанию используется `VK_GROUP_ID`
- `VK_SEED_GIFTS_COUNT` — сколько подарков автоматически создать при первом запуске
- `SQLITE_PATH` — путь к БД, по умолчанию `/data/vk-bot.db`
- `SERVER_PORT` — порт приложения, по умолчанию `8080`

Для клавиатуры подписки бот сам формирует:

- кнопку `Подписаться`, которая открывает `https://vk.com/club<groupId>`
- кнопку `Я подписался`, которая отправляет событие обратно боту

Для Google Sheets:

- `GOOGLE_SHEETS_ENABLED` — `true/false`, включить синхронизацию с таблицей
- `GOOGLE_SHEETS_SPREADSHEET_ID` — ID таблицы из URL
- `GOOGLE_SHEETS_SHEET_NAME` — имя листа внутри таблицы, по умолчанию `Голоса`
- `GOOGLE_SHEETS_CREDENTIALS_PATH` — путь до JSON ключа сервисного аккаунта
- `GOOGLE_APPLICATION_CREDENTIALS` — стандартная переменная Google, можно использовать вместо `GOOGLE_SHEETS_CREDENTIALS_PATH`
- `GOOGLE_SHEETS_APPLICATION_NAME` — произвольное имя приложения для логов
- `GOOGLE_SHEETS_API_BASE_URL` — базовый URL Sheets API, по умолчанию `https://sheets.googleapis.com`

Шаблоны поддерживают плейсхолдеры:

- `{link}`
- `{artTable}`
- `{postId}`
- `{profileUrl}`
- `{groupMention}`
- `{giftNumber}`
- `{giftName}`

## Быстрый запуск локально

```bash
mvn spring-boot:run \
  -DVK_GROUP_ID=123456789 \
  -DVK_ACCESS_TOKEN=vk1.a.xxxxx \
  -DVK_CONFIRMATION_CODE=abcdef \
  -DVK_WRITE_LINK_BASE=https://vk.com/write-236069574
```

Или через env:

```bash
export VK_GROUP_ID=123456789
export VK_ACCESS_TOKEN=vk1.a.xxxxx
export VK_EVENT_SOURCE=LONG_POLL
export VK_WRITE_LINK_BASE=https://vk.com/write-236069574
mvn spring-boot:run
```

## Docker

Сборка:

```bash
docker build -t vk-comment-bot .
```

Запуск:

```bash
docker run --rm \
  -p 8080:8080 \
  -v "$(pwd)/data:/data" \
  -v "$(pwd)/google/service-account.json:/run/secrets/google-sheets.json:ro" \
  -e VK_EVENT_SOURCE=LONG_POLL \
  -e VK_GROUP_ID=123456789 \
  -e VK_ACCESS_TOKEN=vk1.a.xxxxx \
  -e VK_WRITE_LINK_BASE=https://vk.com/write-236069574 \
  -e VK_TRACKED_POSTS='101=Арт-стол Бурятии,102=Арт-стол Тывы' \
  -e GOOGLE_SHEETS_ENABLED=true \
  -e GOOGLE_SHEETS_SPREADSHEET_ID=1AbCdEfGhIjKlMnOpQrStUvWxYz \
  -e GOOGLE_SHEETS_SHEET_NAME=Голоса \
  -e GOOGLE_SHEETS_CREDENTIALS_PATH=/run/secrets/google-sheets.json \
  vk-comment-bot
```

## Google Sheets

При включённой интеграции бот:

- создаёт лист, если его ещё нет;
- создаёт или восстанавливает строку заголовков;
- делает backfill уже существующих участников из `SQLite` при старте;
- обновляет одну строку на пользователя по `VK User ID`.

В таблицу записываются колонки:

- `VK User ID`
- `Имя / аккаунт`
- `Ссылка на профиль`
- `Пост`
- `Арт-стол`
- `Дата и время комментария`
- `Комментарий`
- `Номер подарка`
- `Подарок`
- `Статус подарка`
- `Дата выдачи подарка`
- `Ссылка в ЛС`
- `Режим ответа`

### Как настроить Google Sheets

1. Откройте [Google Cloud Console](https://console.cloud.google.com/).
2. Создайте новый проект или выберите существующий.
3. Включите [Google Sheets API](https://console.cloud.google.com/apis/library/sheets.googleapis.com).
4. Создайте сервисный аккаунт в разделе `IAM & Admin -> Service Accounts`.
5. Для сервисного аккаунта создайте JSON key и скачайте файл.
6. Создайте Google-таблицу и скопируйте её ID из URL.
7. Откройте файл JSON и возьмите значение `client_email`.
8. Поделитесь таблицей с этим `client_email` как минимум с правом `Editor`.
9. Смонтируйте JSON файл в контейнер и передайте путь через `GOOGLE_SHEETS_CREDENTIALS_PATH`.
10. Передайте `GOOGLE_SHEETS_ENABLED=true`, `GOOGLE_SHEETS_SPREADSHEET_ID` и при желании `GOOGLE_SHEETS_SHEET_NAME`.

Пример URL таблицы:

```text
https://docs.google.com/spreadsheets/d/1AbCdEfGhIjKlMnOpQrStUvWxYz/edit#gid=0
```

Здесь `1AbCdEfGhIjKlMnOpQrStUvWxYz` — это `GOOGLE_SHEETS_SPREADSHEET_ID`.

## Настройка Long Poll API в VK

1. Включите `Long Poll API` в тестовой группе.
2. Установите версию API `5.199`.
3. В типах событий включите как минимум `wall_reply_new` и `message_new`.
4. Убедитесь, что у ключа сообщества есть достаточные права. Для `groups.getLongPollServer` официальная документация VK отдельно указывает, что ключ сообщества должен включать `manage`.
5. Включите сообщения сообщества, чтобы пользователь мог написать боту.
6. Для работы кнопок убедитесь, что сообщения сообщества и клавиатура бота доступны в диалоге сообщества.

## Настройка Callback API

Если захотите оставить и webhook:

1. Включите Callback API.
2. Укажите URL: `https://ваш-домен/vk/callback`.
3. Скопируйте строку подтверждения в `VK_CONFIRMATION_CODE`.
4. При использовании секрета перенесите его в `VK_CALLBACK_SECRET`.

## Полезные endpoints

- `POST /vk/callback` — webhook для VK
- `GET /health` — простая проверка, что сервис жив

## Примечание по ссылкам в комментариях

Официальная документация VK для `wall.createComment` описывает ошибку `222 Hyperlinks are forbidden`. Поэтому в проекте есть fallback-режим: если прямая ссылка `vk.com/write-...` будет отклонена, бот попробует опубликовать текст без этой ссылки и с упоминанием сообщества.

## Тесты

```bash
mvn test
```
