const fs = require('fs');
const { JSDOM } = require('jsdom');

const code = fs.readFileSync('/home/user/roblox-images-fix/userscript/roblox-images-fix.user.js', 'utf8');

const html = `<!DOCTYPE html><html><head></head><body>
<img id="existing" src="https://tr.rbxcdn.com/180DAY-abc123/512/512/Image/Png/noFilter">
<img id="srcsetImg" src="https://www.roblox.com/asset-thumbnail/image?id=777"
     srcset="https://t0.rbxcdn.com/xx1 1x, https://t1.rbxcdn.com/yy2 2x">
<div id="bg" style="background-image:url('https://tr.rbxcdn.com/180DAY-xyz/768/768/Image/Png/noFilter')"></div>
<img id="foreign" src="https://example.com/pic.png">
<img id="dataImg" src="data:image/png;base64,AAAA">
</body></html>`;

const dom = new JSDOM(html, {
  url: 'https://www.roblox.com/discover',
  runScripts: 'outside-only',
  pretendToBeVisual: true,
});
const w = dom.window;

// стабы Tampermonkey
w.GM_info = { script: { version: '1.0.0-test' } };
w.GM_xmlhttpRequest = () => { /* в тесте резервный прокси не нужен */ };

w.eval(code);

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const $ = (id) => w.document.getElementById(id);
let pass = 0, fail = 0;
function check(name, cond, extra = '') {
  if (cond) { pass++; console.log('  ✅', name); }
  else { fail++; console.log('  ❌', name, extra); }
}

async function main() {
  // даём jsdom завершить жизненный цикл документа: DOMContentLoaded → sweep()
  await sleep(200);
  console.log('readyState:', w.document.readyState);

  console.log('\n— Тест 1: существующая картинка tr.rbxcdn.com → зеркало');
  const a = $('existing');
  check('src переписан на d77muyc5iodv8.cloudfront.net',
    a.src.startsWith('https://d77muyc5iodv8.cloudfront.net/180DAY-abc123/512/512/Image/Png/noFilter'),
    'got: ' + a.src);
  check('маркер rbxFixState=mirrored', a.dataset.rbxFixState === 'mirrored');
  check('оригинал сохранён', (a.dataset.rbxFixOrig || '').includes('tr.rbxcdn.com'));

  console.log('\n— Тест 2: srcset переписан, src на roblox.com не тронут');
  const b = $('srcsetImg');
  check('src остался roblox.com', b.src.startsWith('https://www.roblox.com/asset-thumbnail/image'));
  check('srcset: t0 → cf-зеркало', b.srcset.includes('https://djm1c8bbf58td.cloudfront.net/xx1 1x'), 'got: ' + b.srcset);
  check('srcset: t1 → cf-зеркало', b.srcset.includes('https://dy9nmzn7lz0hh.cloudfront.net/yy2 2x'), 'got: ' + b.srcset);

  console.log('\n— Тест 3: inline background-image переписан');
  const c = $('bg');
  check('style содержит зеркало', c.getAttribute('style').includes('d77muyc5iodv8.cloudfront.net'),
    'got: ' + c.getAttribute('style'));

  console.log('\n— Тест 4: чужие и data: URL не трогаем');
  check('example.com не изменён', $('foreign').src === 'https://example.com/pic.png');
  check('data: не изменён', $('dataImg').src.startsWith('data:image/png'));

  console.log('\n— Тест 5: динамическое создание img + свойство .src');
  const n = w.document.createElement('img');
  n.src = 'https://tr.rbxcdn.com/180DAY-new/150/150/Image/Png/noFilter';
  check('новый img сразу на зеркале', n.src.startsWith('https://d77muyc5iodv8.cloudfront.net/180DAY-new'), 'got: ' + n.src);

  console.log('\n— Тест 6: setAttribute("src")');
  const n2 = w.document.createElement('img');
  n2.setAttribute('src', 'https://t5.rbxcdn.com/aa/1.png');
  check('setAttribute переписан на d1cn2tk5nesoa7',
    n2.getAttribute('src').startsWith('https://d1cn2tk5nesoa7.cloudfront.net/aa/1.png'),
    'got: ' + n2.getAttribute('src'));

  console.log('\n— Тест 7: карусель — повторная смена src у того же элемента');
  n2.setAttribute('src', 'https://tr.rbxcdn.com/180DAY-second/2.png');
  check('второй URL тоже переписан', n2.getAttribute('src').startsWith('https://d77muyc5iodv8.cloudfront.net/180DAY-second'),
    'got: ' + n2.getAttribute('src'));
  n2.src = 'https://tr.rbxcdn.com/180DAY-third/3.png';
  check('третий URL (свойство) тоже переписан', n2.src.startsWith('https://d77muyc5iodv8.cloudfront.net/180DAY-third'),
    'got: ' + n2.src);

  console.log('\n— Тест 8: idempotentность (cloudfront → не трогаем)');
  n2.src = 'https://d77muyc5iodv8.cloudfront.net/already.png';
  check('остался как есть', n2.src === 'https://d77muyc5iodv8.cloudfront.net/already.png');

  console.log('\n— Тест 9: вставка нового узла ловится MutationObserver');
  const holder = w.document.createElement('div');
  holder.innerHTML = '<img id="viaObserver" src="https://tr.rbxcdn.com/180DAY-mo/9.png">';
  w.document.body.appendChild(holder);
  await sleep(100); // MutationObserver срабатывает асинхронно (микротаск)
  const mo = w.document.getElementById('viaObserver');
  check('img из addedNodes переписан', mo.src.startsWith('https://d77muyc5iodv8.cloudfront.net/180DAY-mo'),
    'got: ' + mo.src);

  console.log('\n— Тест 10: статистика');
  const st = w.__robloxImagesFix.stats;
  check('stats.mirrored > 0', st.mirrored >= 4, JSON.stringify(st));
  console.log('   stats:', JSON.stringify(st));

  console.log(`\n${'═'.repeat(50)}\nИТОГ: ${pass} ✅ / ${fail} ❌`);
  process.exit(fail ? 1 : 0);
}

main().catch((e) => { console.error(e); process.exit(1); });
