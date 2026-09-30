// ==UserScript==
// @name         Roblox Images Fix (RF)
// @namespace    https://github.com/tigrantigran386-ship-it/roblox-images-fix
// @version      1.0.0
// @description  Чинит загрузку иконок, обложек и аватарок на сайте Roblox в РФ: подменяет заблокированный tr.rbxcdn.com на CloudFront-зеркало + резервный прокси.
// @author       tigrantigran386-ship-it
// @match        *://*.roblox.com/*
// @match        *://roblox.com/*
// @icon         https://www.roblox.com/favicon.ico
// @run-at       document-start
// @grant        GM_xmlhttpRequest
// @connect      wsrv.nl
// @connect      images.weserv.nl
// @connect      dns.google
// @connect      cloudflare-dns.com
// @license      MIT
// @supportURL   https://github.com/tigrantigran386-ship-it/roblox-images-fix/issues
// @downloadURL  https://raw.githubusercontent.com/tigrantigran386-ship-it/roblox-images-fix/main/userscript/roblox-images-fix.user.js
// @updateURL    https://raw.githubusercontent.com/tigrantigran386-ship-it/roblox-images-fix/main/userscript/roblox-images-fix.user.js
// ==/UserScript==

/*
 * ─────────────────────────────────────────────────────────────────────────────
 *  КАК ЭТО РАБОТАЕТ
 *
 *  В РФ не резолвится (блокируется на уровне DNS/TSPU) домен tr.rbxcdn.com —
 *  через него Roblox отдаёт ВСЕ картинки: иконки игр, обложки, аватарки.
 *  Сам сайт и игровой клиент при этом работают.
 *
 *  tr.rbxcdn.com — это Amazon CloudFront. У каждой CloudFront-дистрибуции
 *  есть «родное» имя вида dXXXX.cloudfront.net, которое отдаёт ТО ЖЕ САМОЕ
 *  содержимое по тем же путям. DNS-имя rbxcdn.com блокируется, а имя
 *  cloudfront.net — нет. Плюс CSP сайта Roblox разрешает картинки
 *  с *.cloudfront.net — значит подмена домена полностью легальна для браузера.
 *
 *  Стратегия скрипта:
 *   1. Основной путь: перезаписываем хост tr.rbxcdn.com -> d77muyc5iodv8.cloudfront.net
 *      (путь не трогаем). Быстро, напрямую, без прокси.
 *   2. Автодиагностика: если Roblox сменит CloudFront-дистрибуцию, скрипт сам
 *      узнает новое имя через DNS-over-HTTPS (Cloudflare/Google).
 *   3. Резерв: если и зеркало не отвечает — качаем картинку через прокси
 *      wsrv.nl / images.weserv.nl и подставляем как data:URI
 *      (data: разрешён CSP сайта, GM_xmlhttpRequest обходит страницу).
 * ─────────────────────────────────────────────────────────────────────────────
 */

(function () {
  'use strict';

  // ═════════════════════════ НАСТРОЙКИ ═════════════════════════

  const CONFIG = {
    debug: true, // писать логи в консоль (F12)

    // Карта «заблокированный домен -> CloudFront-зеркало».
    // Проверено 30.09.2026. ЕслиRoblox сменит дистрибуции — см. autoDiscover.
    mirrorMap: {
      'tr.rbxcdn.com': 'd77muyc5iodv8.cloudfront.net',
      't0.rbxcdn.com': 'djm1c8bbf58td.cloudfront.net',
      't1.rbxcdn.com': 'dy9nmzn7lz0hh.cloudfront.net',
      't5.rbxcdn.com': 'd1cn2tk5nesoa7.cloudfront.net',
    },

    // Автоопределение зеркал через DNS-over-HTTPS (self-healing).
    // traws/t0aws/... — «облачная» половина CDN Roblox, их CNAME всегда
    // указывает на актуальную cloudfront-дистрибуцию.
    autoDiscover: true,
    discoverTargets: [
      ['traws.rbxcdn.com', 'tr.rbxcdn.com'],
      ['t0aws.rbxcdn.com', 't0.rbxcdn.com'],
      ['t1aws.rbxcdn.com', 't1.rbxcdn.com'],
      ['t5aws.rbxcdn.com', 't5.rbxcdn.com'],
    ],
    dohEndpoints: [
      'https://cloudflare-dns.com/dns-query?name=%H&type=A',
      'https://dns.google/resolve?name=%H&type=A',
    ],

    // Резервные публичные прокси (когда и зеркало не помогло).
    fallbackProxies: [
      'https://wsrv.nl/?url=',
      'https://images.weserv.nl/?url=',
    ],
  };

  // ═════════════════════════ /НАСТРОЙКИ ═════════════════════════

  const VERSION = GM_info.script.version;
  const stats = { mirrored: 0, proxied: 0, failed: 0 };
  const log = (...a) => CONFIG.debug && console.log('%c[Roblox Images Fix]', 'color:#00b06f;font-weight:bold', ...a);

  // ── Утилиты URL ──────────────────────────────────────────────

  function mappedHost(hostname) {
    return Object.prototype.hasOwnProperty.call(CONFIG.mirrorMap, hostname)
      ? CONFIG.mirrorMap[hostname]
      : null;
  }

  // Пытаемся перезаписать URL на зеркало. Возвращает новый URL или null.
  function mapUrl(u) {
    if (typeof u !== 'string' || !u) return null;
    let url;
    try {
      url = new URL(u, location.href);
    } catch (e) { return null; }
    if (url.protocol !== 'https:' && url.protocol !== 'http:') return null;
    if (url.hostname.endsWith('.cloudfront.net')) return null; // уже зеркало
    const mirror = mappedHost(url.hostname);
    if (!mirror) return null;
    url.hostname = mirror;
    return url.href;
  }

  // Является ли хост «нашим подопечным» (rbxcdn / legacy-эндпоинты roblox.com)
  function isOurHost(hostname) {
    if (hostname === 'rbxcdn.com' || hostname.endsWith('.rbxcdn.com')) return true;
    if (hostname === 'roblox.com' || hostname.endsWith('.roblox.com')) {
      return true; // ошибки ловим точечно по типу URL (см. onErrorImg)
    }
    return false;
  }

  function isLegacyThumbUrl(url) {
    // старые эндпоинты картинок на roblox.com, которые редиректят в rbxcdn
    return /\/(asset-thumbnail|avatar-thumbnail|headshot-thumbnail|Thumbs)\//i.test(url.pathname)
      || /\.ashx$/i.test(url.pathname);
  }

  // ── Перезапись одного <img> ──────────────────────────────────

  function mirrorImg(img, origUrl) {
    const mapped = mapUrl(origUrl);
    if (!mapped) return false;
    img.dataset.rbxFixOrig = origUrl;
    img.dataset.rbxFixState = 'mirrored';
    try { img.srcset = ''; img.removeAttribute('sizes'); } catch (e) { /* noop */ }
    img.src = mapped; // попадёт в наш пропатченный сеттер, но mapped уже готов
    stats.mirrored++;
    log('зеркализировано:', origUrl.slice(0, 90), '->', mapped.slice(0, 90));
    return true;
  }

  // Резервный путь: качаем через прокси и подставляем data:URI
  function proxyLoad(img, origUrl, proxyIdx) {
    if (proxyIdx >= CONFIG.fallbackProxies.length) {
      img.dataset.rbxFixState = 'failed';
      stats.failed++;
      log('не удалось починить:', origUrl);
      return;
    }
    img.dataset.rbxFixOrig = origUrl;
    img.dataset.rbxFixState = 'proxied';
    const target = CONFIG.fallbackProxies[proxyIdx] + encodeURIComponent(origUrl);
    log('резервный прокси #' + (proxyIdx + 1) + ':', origUrl.slice(0, 90));
    if (typeof GM_xmlhttpRequest !== 'function') {
      img.dataset.rbxFixState = 'failed';
      return;
    }
    GM_xmlhttpRequest({
      method: 'GET',
      url: target,
      responseType: 'blob',
      timeout: 20000,
      onload(r) {
        const blob = r.response;
        if (r.status === 200 && blob && (!blob.type || /^image\//i.test(blob.type))) {
          const reader = new FileReader();
          reader.onload = () => {
            try { img.srcset = ''; } catch (e) { /* noop */ }
            img.src = reader.result; // data: — разрешён CSP сайта
            stats.proxied++;
            log('починено через прокси:', origUrl.slice(0, 90));
          };
          reader.onerror = () => proxyLoad(img, origUrl, proxyIdx + 1);
          reader.readAsDataURL(blob);
        } else {
          proxyLoad(img, origUrl, proxyIdx + 1);
        }
      },
      onerror: () => proxyLoad(img, origUrl, proxyIdx + 1),
      ontimeout: () => proxyLoad(img, origUrl, proxyIdx + 1),
    });
  }

  function onErrorImg(img) {
    if (!(img instanceof HTMLImageElement)) return;
    const cur = img.currentSrc || img.src || '';
    if (!cur || /^(data|blob):/i.test(cur)) return;
    const state = img.dataset.rbxFixState || '';

    let orig = cur;
    try { orig = new URL(cur, location.href).href; } catch (e) { /* noop */ }

    // 1) Мы ещё не трогали эту картинку — пробуем зеркало сразу.
    if (state === '') {
      const u = safeUrl(orig);
      if (!u) return;
      if (mappedHost(u.hostname)) { mirrorImg(img, orig); return; }
      if (u.hostname.endsWith('.rbxcdn.com')) {
        // неизвестный rbxcdn-хост: зеркала нет — сразу в резервный прокси
        proxyLoad(img, orig, 0);
        return;
      }
      if (isOurHost(u.hostname) && isLegacyThumbUrl(u)) { proxyLoad(img, orig, 0); return; }
      return; // не наша проблема
    }

    // 2) Зеркало не сработало — уходим в прокси.
    if (state === 'mirrored') {
      const saved = img.dataset.rbxFixOrig || orig;
      proxyLoad(img, saved, 0);
    }
  }

  function safeUrl(u) {
    try { return new URL(u, location.href); } catch (e) { return null; }
  }

  // ── Ловим присвоение img.src ДО вставки в DOM (без «мигания» битых картинок) ──

  const srcDesc = Object.getOwnPropertyDescriptor(HTMLImageElement.prototype, 'src');
  if (srcDesc && srcDesc.set) {
    Object.defineProperty(HTMLImageElement.prototype, 'src', {
      configurable: true,
      enumerable: srcDesc.enumerable,
      get() { return srcDesc.get.call(this); },
      set(v) {
        try {
          // Сайт может менять src у одной и той же картинки много раз
          // (карусели, ленивые списки) — переписываем КАЖДЫЙ раз.
          if (typeof v === 'string' && v && !/^(data|blob):/i.test(v)) {
            const mapped = mapUrl(v);
            if (mapped) {
              this.dataset.rbxFixOrig = v;
              this.dataset.rbxFixState = 'mirrored';
              try { this.srcset = ''; } catch (e) { /* noop */ }
              srcDesc.set.call(this, mapped);
              stats.mirrored++;
              log('зеркализировано (setter):', v.slice(0, 90));
              return;
            }
          }
        } catch (e) { /* noop */ }
        srcDesc.set.call(this, v);
      },
    });
  }

  // Аналогично для setAttribute('src', ...)
  const rawSetAttr = Element.prototype.setAttribute;
  Element.prototype.setAttribute = function (name, value) {
    try {
      if (this instanceof HTMLImageElement
        && typeof name === 'string'
        && name.toLowerCase() === 'src'
        && typeof value === 'string'
        && !/^(data|blob):/i.test(value)) {
        const mapped = mapUrl(value);
        if (mapped) {
          this.dataset.rbxFixOrig = value;
          this.dataset.rbxFixState = 'mirrored';
          return rawSetAttr.call(this, 'src', mapped);
        }
      }
    } catch (e) { /* noop */ }
    return rawSetAttr.call(this, name, value);
  };

  // ── srcset: переписываем каждый кандидат ─────────────────────

  function fixSrcset(el) {
    const raw = el.getAttribute('srcset');
    if (!raw) return;
    let changed = false;
    const parts = raw.split(',').map((part) => {
      const chunk = part.trim();
      if (!chunk) return chunk;
      const sp = chunk.split(/\s+/);
      const mapped = mapUrl(sp[0]);
      if (mapped) { changed = true; sp[0] = mapped; }
      return sp.join(' ');
    });
    if (changed) {
      el.setAttribute('srcset', parts.join(', '));
      log('переписан srcset у', el.tagName.toLowerCase());
    }
  }

  // ── inline style="background-image:url(...)" ─────────────────

  function fixInlineBg(el) {
    const style = el.getAttribute('style');
    if (!style || style.indexOf('url(') === -1) return;
    let changed = false;
    const next = style.replace(/url\(\s*(['"]?)([^'")]+)\1\s*\)/g, (m, q, u) => {
      const mapped = mapUrl(u.trim());
      if (mapped) { changed = true; return 'url(' + (q || '') + mapped + (q || '') + ')'; }
      return m;
    });
    if (changed) el.setAttribute('style', next);
  }

  // ── Обход элементов ──────────────────────────────────────────

  function handleNode(node) {
    if (!node || node.nodeType !== 1) return;
    if (node instanceof HTMLImageElement) {
      if (!node.dataset.rbxFixState) {
        const mapped = mapUrl(node.currentSrc || node.src || '');
        if (mapped) mirrorImg(node, node.currentSrc || node.src);
      }
      fixSrcset(node);
    } else if (node instanceof HTMLSourceElement) {
      fixSrcset(node);
    }
    if (node.hasAttribute && node.hasAttribute('style')) fixInlineBg(node);
    if (node.querySelectorAll) {
      node.querySelectorAll('img,source').forEach((el) => {
        if (el instanceof HTMLImageElement && !el.dataset.rbxFixState) {
          const s = el.currentSrc || el.src || '';
          if (mapUrl(s)) mirrorImg(el, s);
        }
        fixSrcset(el);
      });
      node.querySelectorAll('[style]').forEach(fixInlineBg);
    }
  }

  function sweep() {
    if (document.documentElement) handleNode(document.documentElement);
  }

  // ── MutationObserver — страховка для SPA ─────────────────────

  const mo = new MutationObserver((muts) => {
    for (const m of muts) {
      if (m.type === 'attributes') {
        const t = m.target;
        if (t instanceof HTMLImageElement || t instanceof HTMLSourceElement) {
          if (m.attributeName === 'srcset') fixSrcset(t);
          else if (t instanceof HTMLImageElement && !t.dataset.rbxFixState) {
            const s = t.currentSrc || t.src || '';
            if (mapUrl(s)) mirrorImg(t, s);
          }
        } else if (m.attributeName === 'style') {
          fixInlineBg(t);
        }
      } else {
        m.addedNodes.forEach(handleNode);
      }
    }
  });

  // ── Ошибки загрузки картинок — последний рубеж ───────────────

  window.addEventListener('error', (e) => {
    if (e.target instanceof HTMLImageElement) onErrorImg(e.target);
  }, true);

  // ── Автоопределение зеркал через DoH (self-healing) ──────────

  function dohQuery(endpoint, name) {
    return new Promise((resolve) => {
      const url = endpoint.replace('%H', encodeURIComponent(name));
      GM_xmlhttpRequest({
        method: 'GET',
        url,
        headers: { Accept: 'application/dns-json' },
        timeout: 8000,
        onload(r) {
          try { resolve(JSON.parse(r.responseText)); } catch (e) { resolve(null); }
        },
        onerror: () => resolve(null),
        ontimeout: () => resolve(null),
      });
    });
  }

  async function discoverMirrors() {
    for (const [target, srcHost] of CONFIG.discoverTargets) {
      if (!mappedHost(srcHost)) continue; // пользователь сам убрал — не лезем
      for (const ep of CONFIG.dohEndpoints) {
        let cur = target;
        let found = null;
        for (let hop = 0; hop < 6 && !found; hop++) {
          const d = await dohQuery(ep, cur);
          if (!d || !Array.isArray(d.Answer)) break;
          const cname = d.Answer.find((a) => a.type === 5);
          if (!cname) break;
          const next = String(cname.data).replace(/\.$/, '');
          if (next.endsWith('.cloudfront.net')) found = next;
          else cur = next;
        }
        if (found) {
          if (CONFIG.mirrorMap[srcHost] !== found) {
            log('обновлено зеркало:', srcHost, '->', found);
            CONFIG.mirrorMap[srcHost] = found;
            // если текущее зеркало умерло — перепрoбуем отложенные ошибки заново
            document.querySelectorAll('img[data-rbx-fix-state="failed"]').forEach((img) => {
              const orig = img.dataset.rbxFixOrig;
              delete img.dataset.rbxFixState;
              if (orig) mirrorImg(img, orig);
            });
          }
          break;
        }
      }
    }
  }

  // ── Старт ────────────────────────────────────────────────────

  if (CONFIG.debug) {
    console.log(
      '%c[Roblox Images Fix]%c v' + VERSION + ' активен. Статистика: window.__robloxImagesFix.stats',
      'color:#00b06f;font-weight:bold', 'color:inherit'
    );
  }
  window.__robloxImagesFix = { version: VERSION, stats, config: CONFIG };

  mo.observe(document.documentElement || document, {
    childList: true,
    subtree: true,
    attributes: true,
    attributeFilter: ['src', 'srcset', 'style'],
  });

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', sweep);
  } else {
    sweep();
  }
  window.addEventListener('load', sweep);
  setTimeout(sweep, 3000); // страховка для ленивых загрузок

  if (CONFIG.autoDiscover && typeof GM_xmlhttpRequest === 'function') {
    setTimeout(discoverMirrors, 1000);
  }
})();
