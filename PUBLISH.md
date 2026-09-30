# 🚀 Как выложить проект на GitHub

Пошагово, от нуля до публичного репозитория. Займёт ~10 минут.

---

## Шаг 0. Замените плейсхолдеры

В файлах проекта имя `ВАШ_НИК` — это заглушка под ваш аккаунт GitHub. Замените её:

**Вручную** (файлы: `README.md`, `INSTALL.md`, `PUBLISH.md`, `userscript/roblox-images-fix.user.js`)
или **одной командой** в терминале из папки проекта:

```bash
# Windows (PowerShell):
Get-ChildItem -Recurse -Include *.md,*.user.js | ForEach-Object {
  (Get-Content $_ | Raw -ErrorAction SilentlyContinue) -replace 'ВАШ_НИК', 'ваш-ник' | Set-Content $_
}
```
```bash
# Linux / macOS / Git Bash:
grep -rl 'ВАШ_НИК' . | xargs sed -i 's/ВАШ_НИК/ваш-ник/g'
```

> `ваш-ник` — ваш логин на GitHub, латиницей. Например `ivangames123`.

## Шаг 1. Аккаунт и новый репозиторий

1. Зарегистрируйтесь на https://github.com (если ещё нет).
2. Нажмите **+** (справа вверху) → **New repository**.
3. Repository name: `roblox-images-fix`.
4. Description: `Чинит загрузку иконок/обложек/аватарок Roblox в РФ — юзерскрипт + расширение Chrome`.
5. Выберите **Public**. Галочку «Add a README» НЕ ставьте (у нас уже есть).
6. **Create repository**.

## Шаг 2. Загрузите файлы

### Способ А — через сайт (без программ, самый простой)

На странице пустого репозитория нажмите ссылку **uploading an existing file**,
перетащите туда ВСЕ файлы и папки проекта (README.md, LICENSE, INSTALL.md, PUBLISH.md,
папки `userscript/`, `extension/`, `windows/`, `android/`, `test/`, `worker/`, `.github/`) и нажмите **Commit changes**. Готово! 🎉

### Способ Б — через git (правильный, на будущее)

```bash
cd путь/до/roblox-images-fix

git init
git add .
git commit -m "Roblox Images Fix v1.0.0: юзерскрипт + расширение"
git branch -M main
git remote add origin https://github.com/ваш-ник/roblox-images-fix.git
git push -u origin main
```

При первом `push` GitHub попросит войти — в браузере откроется окно авторизации.

## Шаг 3. Проверьте установку «в один клик»

Откройте в браузере с Tampermonkey:
`https://github.com/ваш-ник/roblox-images-fix/raw/main/userscript/roblox-images-fix.user.js`

Tampermonkey должен перехватить файл и предложить установку — так скрипт будут ставить ваши пользователи. Ссылку для них удобно добавить в шапку README:

```markdown
![Install](https://img.shields.io/badge/Установить-юзерскрипт-brightgreen)
(https://github.com/ваш-ник/roblox-images-fix/raw/main/userscript/roblox-images-fix.user.js)
```

## Шаг 4. Собери APK для Android

Вкладка **Actions** → **Build APK** → **Run workflow** → через ~5 минут скачай
артефакт `RobloxImagesFix-apk` (внутри готовый `RobloxImagesFix.apk`).
Сделаешь релиз с тегом `v1.0.0` — APK приложится к релизу автоматически.
Подробнее: [android-app/README.md](android-app/README.md).

## Шаг 5. (Опционально) Релиз с расширением в ZIP

1. **Releases → Create a new release** → тег `v1.0.0`.
2. Прикрепите `extension.zip` (заархивируйте содержимое папки `extension`).
3. Publish release (APK от Actions подхватится сам).

## Как обновлять потом

1. Поправили код → поднимите версию:
   - в `userscript/roblox-images-fix.user.js`: строка `@version 1.0.1`;
   - в `extension/manifest.json`: `"version": "1.0.1"`;
   - в `android-app/app/build.gradle`: `versionCode` +1 и `versionName "1.0.1"`.
2. `git add . && git commit -m "v1.0.1: ..." && git push`.
3. У Tampermonkey пользователей скрипт обновится сам (`@downloadURL` уже прописан).

## Идеи для развития репозитория

- Экран «до/после» в README (скриншоты главной страницы Roblox).
- Публикация расширения в Chrome Web Store (разовая плата $5).
> ✅ Автопроверка зеркал через GitHub Actions уже настроена: см. `.github/workflows/mirror-health.yml` — раз в 6 часов проверяет зеркала и открывает Issue, если Roblox их сменил.
