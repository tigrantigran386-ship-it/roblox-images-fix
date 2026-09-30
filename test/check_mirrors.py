#!/usr/bin/env python3
"""
Roblox Images Fix (RF) — проверка здоровья зеркал без сторонних зависимостей.

Что делает:
  1. Через DoH (Cloudflare → фолбэк Google) резолвит актуальные CloudFront-имена
     для tr/t0/t1/t5.rbxcdn.com (по цепочкам *aws.rbxcdn.com).
  2. Сверяет их с EXPECTED ниже и с mirrorMap юзерскрипта.
  3. Скачивает тестовую картинку через каждое зеркало: HTTP 200 + валидная сигнатура.

Выход: отчёт в Markdown (stdout или --out FILE). Код возврата: 0 = всё живо, 1 = проблемы.
"""

import argparse
import json
import re
import sys
import urllib.request

DOH_ENDPOINTS = [
    "https://cloudflare-dns.com/dns-query?name=%s&type=A",
    "https://dns.google/resolve?name=%s&type=A",
]

# domain -> ожидаемое зеркало (должно совпадать с mirrorMap юзерскрипта и rules.json)
EXPECTED = {
    "tr.rbxcdn.com": "d77muyc5iodv8.cloudfront.net",
    "t0.rbxcdn.com": "djm1c8bbf58td.cloudfront.net",
    "t1.rbxcdn.com": "dy9nmzn7lz0hh.cloudfront.net",
    "t5.rbxcdn.com": "d1cn2tk5nesoa7.cloudfront.net",
}

USERSCRIPT = "userscript/roblox-images-fix.user.js"
UA = {"User-Agent": "roblox-images-fix-healthcheck/1.0"}


def doh_resolve(name):
    """CNAME-цепочка через DoH. Возвращает (последнее_имя, [IP])."""
    cur = name
    for _ in range(6):
        data = None
        for ep in DOH_ENDPOINTS:
            try:
                req = urllib.request.Request(ep % cur, headers={"Accept": "application/dns-json", **UA})
                with urllib.request.urlopen(req, timeout=10) as r:
                    data = json.load(r)
                break
            except Exception:
                continue
        if not data or not data.get("Answer"):
            return cur, []
        cnames = [a["data"].rstrip(".") for a in data["Answer"] if a["type"] == 5]
        if cnames:
            cur = cnames[0]
            continue
        return cur, [a["data"] for a in data["Answer"] if a["type"] == 1]
    return cur, []


def current_mirrors_from_userscript():
    """Вытаскивает mirrorMap из кода юзерскрипта (единый источник правды)."""
    try:
        src = open(USERSCRIPT, encoding="utf-8").read()
    except OSError:
        return {}
    block = re.search(r"mirrorMap:\s*\{(.*?)\}", src, re.S)
    if not block:
        return {}
    return dict(re.findall(r"'([\w.]+)':\s*'([\w.-]+)'", block.group(1)))


def distribution_alive(mirror):
    """Жива ли дистрибуция CloudFront вообще: живая отвечает любым HTTP-статусом
    (хоть 403 на корень), мёртвая/переименованная — не отвечает (DNS/соединение)."""
    try:
        req = urllib.request.Request(f"https://{mirror}/", headers=UA)
        with urllib.request.urlopen(req, timeout=10) as r:
            return True, f"HTTP {r.status} (дистрибуция отвечает)"
    except urllib.error.HTTPError as e:
        return True, f"HTTP {e.code} (дистрибуция отвечает)"
    except Exception as e:
        return False, f"нет ответа: {e}"


def test_mirror(mirror):
    """Скачивает реальную картинку через зеркало. Возвращает (ok, подробности)."""
    try:
        req = urllib.request.Request(
            "https://thumbnails.roblox.com/v1/places/gameicons?placeIds=920587237&size=512x512&format=Png",
            headers=UA,
        )
        with urllib.request.urlopen(req, timeout=10) as r:
            image_url = json.load(r)["data"][0]["imageUrl"]
    except Exception as e:
        return False, f"thumbnails API недоступен: {e}"

    path = image_url.split("tr.rbxcdn.com", 1)[-1]
    url = f"https://{mirror}{path}"
    try:
        req = urllib.request.Request(url, headers=UA)
        with urllib.request.urlopen(req, timeout=15) as r:
            ct = r.headers.get("Content-Type", "")
            body = r.read(8)
            magic_ok = body[:4] == b"\x89PNG" or body[:3] == b"\xff\xd8\xff"
            if r.status == 200 and ct.lower().startswith("image/") and magic_ok:
                return True, f"HTTP 200, {ct}"
            return False, f"HTTP {r.status}, {ct}"
    except Exception as e:
        return False, str(e)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", help="куда писать отчёт (по умолчанию stdout)")
    args = ap.parse_args()

    script_map = current_mirrors_from_userscript()
    lines = ["# 🩺 Mirror health report", ""]
    problems = 0

    for host, expected in EXPECTED.items():
        label = host.split(".")[0]              # tr / t0 / t1 / t5
        cname, ips = doh_resolve(f"{label}aws.rbxcdn.com")
        lines.append(f"## `{host}`")
        lines.append(f"- mirrorMap юзерскрипта: `{script_map.get(host, '❌ не найдено')}`")
        lines.append(f"- ожидаемое зеркало:     `{expected}`")
        lines.append(f"- актуальная DoH-цепочка *aws → `{cname}` ({len(ips)} IP)")

        if script_map.get(host) != expected:
            lines.append("- ⚠️ mirrorMap юзерскрипта не совпадает с EXPECTED — синхронизируй файлы")
            problems += 1
        if cname.endswith(".cloudfront.net") and cname != expected:
            lines.append(f"- 🔴 Roblox СМЕНИЛ дистрибуцию: замени `{expected}` → `{cname}`")
            problems += 1

        if host == "tr.rbxcdn.com":
            ok, info = test_mirror(expected)
            note = "тест картинкой"
        else:
            ok, info = distribution_alive(expected)
            note = "проверка живости дистрибуции (свой контент без публичного семпла)"
        lines.append(f"- {note} через `{expected}`: {'✅ ' + info if ok else '🔴 ' + info}")
        if not ok:
            problems += 1
        lines.append("")

    verdict = "✅ Все зеркала живы" if problems == 0 else f"🔴 Проблем: {problems} — нужны обновления"
    lines.insert(2, f"**Итог: {verdict}**")
    lines.insert(3, "")
    report = "\n".join(lines)
    print(report)

    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            f.write(report + "\n")

    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
