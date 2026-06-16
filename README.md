# VK Comment Bot

Java-бот для `Callback API` ВКонтакте. Он принимает события о новых комментариях под постами, сохраняет их в `SQLite` и отвечает на первый комментарий пользователя от лица сообщества с призывом написать в сообщения сообщества.

Текущий сценарий:

- бот слушает `wall_reply_new`;
- первый комментарий пользователя в рамках акции засчитывается;
- бот отвечает под этим комментарием;
- в ответе даёт ссылку вида `vk.com/write-236069574?...`;
- повторные комментарии того же пользователя сохраняются в БД, но не получают новый первичный ответ;
- если VK запрещает гиперссылки в комментарии, бот может автоматически переключиться на fallback-текст без прямой ссылки.

## Что хранится в SQLite

- пользователь: `user_id`, имя, фамилия, `screen_name`, ссылка на профиль, аватар;
- первый принятый комментарий: пост, арт-стол, текст, дата;
- ответ бота и ссылка;
- все входящие комментарии как журнал событий;
- статус обработки: `ACCEPTED`, `DUPLICATE_USER`, `IGNORED_NON_USER`, `ERROR`.

## Переменные окружения

Обязательные:

- `VK_GROUP_ID` — ID тестовой или боевой группы, которая шлёт callback
- `VK_ACCESS_TOKEN` — ключ доступа сообщества
- `VK_CONFIRMATION_CODE` — строка подтверждения из Callback API
- `VK_WRITE_LINK_BASE` — базовая ссылка для диалога, например `https://vk.com/write-236069574`

Рекомендуемые:

- `VK_CALLBACK_SECRET` — секрет callback, если включён в настройках VK
- `VK_TRACKED_POSTS` — карта постов и названий столов в формате `123=Бурятский стол,124=Русский стол`
- `VK_REPLY_TEMPLATE` — шаблон первого ответа
- `VK_DUPLICATE_TEMPLATE` — шаблон ответа на повторный комментарий
- `VK_REPLY_ON_DUPLICATE` — `true/false`, отвечать ли на дубли
- `VK_ENABLE_HYPERLINK_FALLBACK` — `true/false`, пробовать fallback без прямой ссылки при ошибке VK `222`
- `VK_PUBLIC_GROUP_ID` — ID сообщества для fallback-упоминания; по умолчанию используется `VK_GROUP_ID`
- `SQLITE_PATH` — путь к БД, по умолчанию `/data/vk-bot.db`
- `SERVER_PORT` — порт приложения, по умолчанию `8080`

Шаблоны поддерживают плейсхолдеры:

- `{link}`
- `{artTable}`
- `{postId}`
- `{profileUrl}`
- `{groupMention}`

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
export VK_CONFIRMATION_CODE=abcdef
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
  -e VK_GROUP_ID=123456789 \
  -e VK_ACCESS_TOKEN=vk1.a.xxxxx \
  -e VK_CONFIRMATION_CODE=abcdef \
  -e VK_CALLBACK_SECRET=super-secret \
  -e VK_WRITE_LINK_BASE=https://vk.com/write-236069574 \
  -e VK_TRACKED_POSTS='101=Арт-стол Бурятии,102=Арт-стол Тывы' \
  vk-comment-bot
```

## Настройка Callback API в VK

1. Включите Callback API в тестовой группе.
2. Укажите URL: `https://ваш-домен/vk/callback`.
3. Скопируйте строку подтверждения в `VK_CONFIRMATION_CODE`.
4. При использовании секрета перенесите его в `VK_CALLBACK_SECRET`.
5. Включите событие `wall_reply_new`.

## Полезные endpoints

- `POST /vk/callback` — webhook для VK
- `GET /health` — простая проверка, что сервис жив

## Примечание по ссылкам в комментариях

Официальная документация VK для `wall.createComment` описывает ошибку `222 Hyperlinks are forbidden`. Поэтому в проекте есть fallback-режим: если прямая ссылка `vk.com/write-...` будет отклонена, бот попробует опубликовать текст без этой ссылки и с упоминанием сообщества.

## Тесты

```bash
mvn test
```

