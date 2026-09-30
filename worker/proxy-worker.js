// ─────────────────────────────────────────────────────────────────────────────
//  Roblox Images Fix (RF) — собственный резервный прокси на Cloudflare Workers
//
//  Зачем: публичный прокси wsrv.nl может иногда тормозить или лимитировать.
//  Свой воркер = полный контроль. Юзерскрипт работает и без этого файла
//  (это ОПЦИОНАЛЬНЫЙ резерв), но если хотите независимость:
//
//    1. Зарегистрируйтесь на https://workers.cloudflare.com (бесплатно).
//    2. Create Worker -> вставьте этот код -> Deploy.
//    3. В userscript/roblox-images-fix.user.js в CONFIG добавьте
//       адрес своего воркера первым в список fallbackProxies:
//         fallbackProxies: [
//           'https://roblox-fix.ваш-поддомен.workers.dev/?url=',
//           'https://wsrv.nl/?url=',
//           'https://images.weserv.nl/?url=',
//         ],
//
//  Воркер отвечает на запросы вида:  https://<воркер>/?url=<encodeURIComponent(url)>
//  В целях безопасности проксирует ТОЛЬКО домены Roblox (см. ALLOWED ниже),
//  чтобы никто не смог использовать ваш воркер как открытый прокси.
// ─────────────────────────────────────────────────────────────────────────────

const ALLOWED = [
  /(^|\.)rbxcdn\.com$/i,
  /(^|\.)roblox\.com$/i,
  /(^|\.)robloxlabs\.com$/i,
];

export default {
  async fetch(request) {
    const url = new URL(request.url);

    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: corsHeaders() });
    }
    if (request.method !== 'GET') {
      return new Response('Method Not Allowed', { status: 405 });
    }

    const target = url.searchParams.get('url');
    if (!target || !/^https?:\/\//i.test(target)) {
      return new Response('Usage: ?url=<encoded image url>', {
        status: 400,
        headers: corsHeaders(),
      });
    }

    let targetUrl;
    try {
      targetUrl = new URL(target);
    } catch (e) {
      return new Response('Bad url parameter', { status: 400, headers: corsHeaders() });
    }

    if (!ALLOWED.some((re) => re.test(targetUrl.hostname))) {
      return new Response('Forbidden host', { status: 403, headers: corsHeaders() });
    }

    const upstream = await fetch(targetUrl.href, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) roblox-images-fix/1.0',
        Accept: 'image/avif,image/webp,image/png,image/*,*/*;q=0.8',
      },
      cf: {
        cacheEverything: true,
        cacheTtl: 86400, // картинки Roblox неизменяемые (хеш в пути)
      },
    });

    const headers = new Headers();
    const ct = upstream.headers.get('content-type');
    if (ct) headers.set('Content-Type', ct);
    headers.set('Cache-Control', 'public, max-age=86400');
    headers.set('Access-Control-Allow-Origin', '*');
    headers.set('X-Roblox-Fix-Proxy', '1');

    return new Response(upstream.body, {
      status: upstream.status,
      headers,
    });
  },
};

function corsHeaders() {
  return {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, OPTIONS',
  };
}
