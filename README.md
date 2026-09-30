# 🖼️ Roblox Images Fix (RF)

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Mirror health](https://img.shields.io/badge/зеркала-автопроверка%20раз%20в%206ч-brightgreen)](.github/workflows/mirror-health.yml)
[![Userscript](https://img.shields.io/badge/Сайт-userscript-blue)](userscript/roblox-images-fix.user.js)
[![Extension](https://img.shields.io/badge/Сайт-расширение%20Chrome-orange)](extension/)
[![Windows](https://img.shields.io/badge/Клиент%20Win-автофикс%20hosts-0078d7)](windows/)
[![Android](https://img.shields.io/badge/Android-приложение%20(APK)-3ddc84)](android-app/)
[![APK](https://img.shields.io/badge/APK-сборка%20в%20Actions-blueviolet)](.github/workflows/build-apk.yml)

**Возвращает загрузку иконок, обложек и аватарок на сайте Roblox в России** — там, где сейчас вместо картинок серые заглушки: на главной странице, в каталоге, в профилях, в списках друзей.

> Русский | [README in English (soon)](#)

---

## ❓ Что происходит

С конца июля 2026 у пользователей из РФ **не загружаются изображения Roblox**: иконки игр, обложки, аватарки — ни на сайте, ни в клиенте, ни в Studio. Причина:

- все картинки Roblox отдаются через домен **`tr.rbxcdn.com`** (Amazon CloudFront);
- в РФ этот домен **не резолвится / блокируется** на уровне провайдеров (TSPU/DPI);
- сам сайт roblox.com и игровой клиент при этом продолжают работать.

Обычные советы «поменяй DNS на 8.8.8.8» часто не помогают: провайдеры перехватывают DNS-запросы, а рабочие IP CloudFront постоянно меняются (поэтому и рецепты с файлом `hosts` живут недолго).

## 💡 Как это работает

Ключевое наблюдение: `tr.rbxcdn.com` — это **Amazon CloudFront**, а у каждой CloudFront-дистрибуции есть «родное» имя вида `dXXXX.cloudfront.net`, которое отдаёт **тот же контент по тем же путям**. Домен `rbxcdn.com` блокируется, а `cloudfront.net` — нет.

Более того, **CSP самого сайта Roblox разрешает картинки с `*.cloudfront.net`**, так что подмена домена не встречает никаких блокировок в браузере.

```
❌ Было:   https://tr.rbxcdn.com/180DAY-abc123.../512/512/Image/Png/noFilter   → блокируется в РФ
✅ Стало:  https://d77muyc5iodv8.cloudfront.net/180DAY-abc123.../512/512/Image/Png/noFilter
                                    ↑ тот же файл, байт-в-байт, но домен не заблокирован
```

Скрипт делает три вещи:

1. **Перезаписывает домен** `tr.rbxcdn.com` (и t0/t1/t5) на CloudFront-зеркало ещё до загрузки картинки — без «мигания» битых изображений.
2. **Самонастраивается**: если Roblox сменит CloudFront-дистрибуцию, скрипт сам узнает новое имя через DNS-over-HTTPS (Cloudflare/Google) и продолжит работать.
3. **Резерв на крайний случай**: если и зеркало вдруг не ответит, картинка докачивается через публичный прокси (wsrv.nl) и подставляется как `data:URI` — это тоже разрешено CSP сайта.

## 📦 Установка

### Вариант 1 — Юзерскрипт (рекомендуется, Chrome / Yandex / Edge / Firefox / Opera)

1. Установите расширение **Tampermonkey** (или Violentmonkey) — см. подробную [INSTALL.md](INSTALL.md).
2. Откройте [userscript/roblox-images-fix.user.js](userscript/roblox-images-fix.user.js) → кнопка **Raw** → Tampermonkey предложит установить.
3. Обновите страницу Roblox (`Ctrl+F5`). Готово — в консоли (F12) появится зелёный лог `[Roblox Images Fix]`.

### Вариант 2 — Расширение (Chrome / Edge / Yandex Browser, работает даже быстрее)

1. Скачайте папку [`extension/`](extension/) (Code → Download ZIP → распакуйте).
2. Откройте `chrome://extensions` → включите **Режим разработчика** → **Загрузить распакованное** → выберите папку `extension`.
3. Обновите страницу Roblox. Расширение работает без единой строчки исполняемого JS — просто перенаправляет запросы правилом.

### Вариант 3 — Свой прокси (для продвинутых)

Не хотите зависеть от публичных прокси — разверните свой на Cloudflare Workers за 5 минут: инструкция внутри [`worker/proxy-worker.js`](worker/proxy-worker.js).

📱 **Телефон:** на Android помогает браузер Firefox + Tampermonkey, либо браузер Kiwi + это расширение. На iOS — браузер с поддержкой юзерскриптов (например, Userscripts для Safari).

## 🖥 А как же КЛИЕНТ Roblox на ПК и приложение на телефоне?

Юзерскрипт живёт в браузере, а клиент качает картинки сам, мимо браузера. Для них в проекте есть отдельные инструменты:

| Где | Инструмент | Как чинит |
|---|---|---|
| **Windows** (клиент Roblox + Studio) | [`windows/RobloxCDNFix.bat`](windows/) — интерактивный фикс | Сам находит через DoH **живые IP** `tr.rbxcdn.com` (проверяя каждый реальным TLS-соединением), прописывает в `hosts`, ставит задачу планировщика: IP обновляются каждые 6 ч и при входе в систему — фикс не «умирает» через неделю, как ручные гайды |
| **Android** (приложение Roblox) | 🏆 **[`android-app/`](android-app/) — наше собственное приложение (APK)** | Одна кнопка ВКЛ/ВЫКЛ: локальный точечный туннель перехватывает DNS-запросы rbxcdn.com и отвечает IP официального CloudFront-зеркала. Без root, без настроек, весь остальной трафик идёт напрямую. APK собирается автоматически в GitHub Actions |
| **Android** (альтернатива без установки APP) | [`android/README.md`](android/) — гайд + ADB-скрипты | Диагностика типа блокировки + Private DNS (`dns.opendns.com` / `dns.quad9.net`) в 2 тапа или одной ADB-командой |

> Честно: если оператор блокирует не DNS, а само соединение (IP/SNI) — DNS-способы на телефоне не помогут, нужен VPN. В [android-гайде](android/) есть шаг «определи свой случай за 30 секунд».

## ✅ Проверено

Механизм проверен на реальных данных (30.09.2026):

| Тест | Результат |
|---|---|
| Иконка игры через зеркало `d77muyc5iodv8.cloudfront.net` | **HTTP 200**, PNG 512×512 |
| Аватарка: `tr.rbxcdn.com` vs зеркало | **файлы байт-в-байт идентичны** (15 129 байт) |
| CSP сайта roblox.com | `img-src` содержит `*.cloudfront.net` → подмена разрешена |
| Автотесты (jsdom, папка `test/`) | **16/16 сценариев** ✅ — `npm install && npm test` |
| GitHub Action «Mirror health» | каждые 6 ч сам проверяет зеркала и открывает Issue, если Roblox их сменил |

## ⚙️ Настройка

Все настройки — в начале файла скрипта (блок `CONFIG`):

- `mirrorMap` — карта «заблокированный домен → зеркало» (добавьте свои хосты, если появится новый);
- `autoDiscover` — автообновление зеркал через DoH (`true` по умолчанию);
- `fallbackProxies` — резервные прокси (добавьте адрес своего воркера первым);
- `debug` — логи в консоли.

## ❔ FAQ

**Чинит ли это картинки внутри игрового клиента (не на сайте)?**
Да. **Windows** — [`windows/RobloxCDNFix.bat`](windows/): прописывает проверенный живой IP в `hosts` и **сам обновляет его по расписанию**. **Android** — [`android-app/`](android-app/): собственное приложение с одной кнопкой (точечный туннель → CloudFront-зеркало), APK собирается прямо в GitHub Actions. Это НЕ мод Roblox — античит и баны не грозят.

**Это безопасно? Аккаунт не забанят?**
Да. Скрипт лишь подменяет домен, с которого браузер качает **те же самые официальные картинки** Roblox. Никаких сторонних модификаций клиента, никакой передачи ваших данных. Вы даже можете проверить код целиком — его тут ~350 строк.

**Почему бы просто не VPN?**
Можно и VPN — но это скорость/трафик всей системы ради картинок. Здесь же через прокси идут только изображения, всё остальное — напрямую.

**Перестало работать спустя время?**
Значит, Roblox сменил CloudFront-дистрибуцию. Юзерскрипт починится сам через DoH (`autoDiscover`). Для расширения обновите `rules.json` (или просто поставьте юзерскрипт). Проверить актуальное имя: `nslookup traws.rbxcdn.com 9.9.9.9` → смотрите CNAME вида `dXXXX.cloudfront.net`.

**Картинки грузятся, но медленно?**
Попробуйте поставить свой прокси на Cloudflare Workers первым в `fallbackProxies`, либо отключите `debug`.

## 📁 Структура проекта

```
roblox-images-fix/
├── README.md                        ← вы здесь
├── INSTALL.md                       ← подробная установка для новичков
├── PUBLISH.md                       ← как выложить это на GitHub
├── LICENSE                          ← MIT
├── userscript/
│   └── roblox-images-fix.user.js    ← юзерскрипт: сайт (основной вариант)
├── extension/                       ← расширение Chrome/Edge/Яндекс (MV3): сайт
│   ├── manifest.json
│   ├── rules.json
│   └── icons/
├── windows/                         ← КЛИЕНТ Roblox + Studio на Windows
│   ├── RobloxCDNFix.bat             ← запуск двойным кликом (сам просит админа)
│   └── RobloxCDNFix.ps1             ← сам фикс: DoH-поиск IP + hosts + планировщик
├── android-app/                     ← 🏆 СОБСТВЕННОЕ ПРИЛОЖЕНИЕ для Android (APK)
│   ├── README.md                    ← как работает + как собрать/установить
│   ├── app/src/main/java/…          ← Java: VpnService-туннель, DNS-перехват, зеркала
│   └── gradle…                      ← проект Gradle (сборка в один клик, в т.ч. в CI)
├── android/                         ← альтернатива без установки APP
│   ├── README.md                    ← гайд: диагностика + Private DNS + RethinkDNS
│   ├── set-private-dns.bat          ← одной командой через ADB (Windows)
│   └── set-private-dns.sh           ← …или с Linux/macOS
├── test/                            ← автотесты (jsdom, 16 сценариев) + health-check зеркал
├── .github/workflows/               ← «Mirror health» (проверка зеркал раз в 6 ч) + «Build APK»
└── worker/
    └── proxy-worker.js              ← опциональный свой прокси (Cloudflare Workers)
```

## 🤝 Участие

Нашли новый заблокированный хост? Он перестал зеркалироваться? Открывайте [Issue](../../issues) или присылайте PR — карта `mirrorMap` пополняется за минуту.

## 📄 Лицензия

MIT — делайте что хотите, но автор не даёт гарантий. Roblox является товарищем Roblox Corporation; проект не связан с Roblox Corporation.
