'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { start, PASS } = require('./helper');

test('toda respuesta lleva cabeceras de seguridad y X-Request-Id', async (t) => {
  const h = await start(); t.after(h.stop);
  const r = await h.call('GET', '/health');
  assert.equal(r.headers['x-content-type-options'], 'nosniff');
  assert.equal(r.headers['referrer-policy'], 'no-referrer');
  assert.match(r.headers['content-security-policy'], /default-src 'none'/);
  assert.match(r.headers['cache-control'], /no-store/);
  assert.ok(r.headers['x-request-id']);
  assert.equal(r.headers['x-powered-by'], undefined);
  const e = await h.call('GET', '/nope');
  assert.equal(e.headers['x-content-type-options'], 'nosniff');
});

test('CORS: solo orígenes de la lista blanca, nunca comodín', async (t) => {
  const h = await start(); t.after(h.stop);
  const ok = await h.call('OPTIONS', '/v1/offers', { headers: { origin: 'https://appassets.androidplatform.net', 'access-control-request-method': 'GET' } });
  assert.equal(ok.status, 204);
  assert.equal(ok.headers['access-control-allow-origin'], 'https://appassets.androidplatform.net');
  const bad = await h.call('GET', '/health', { headers: { origin: 'https://evil.example' } });
  assert.equal(bad.headers['access-control-allow-origin'], undefined);
  const nul = await h.call('GET', '/health', { headers: { origin: 'null' } });
  assert.equal(nul.headers['access-control-allow-origin'], undefined);
});

test('rutas sin sesión → 401 uniforme; token malformado/falso → 401', async (t) => {
  const h = await start(); t.after(h.stop);
  for (const p of ['/v1/me', '/v1/offers', '/v1/alerts', '/v1/notifications', '/v1/mod/queue']) {
    assert.equal((await h.call('GET', p)).status, 401, p);
  }
  assert.equal((await h.call('GET', '/v1/me', { token: 'bat_' + 'A'.repeat(43) })).status, 401);
  assert.equal((await h.call('GET', '/v1/me', { token: "' OR 1=1 --" })).status, 401);
  assert.equal((await h.call('GET', '/v1/me', { headers: { authorization: 'Basic abc' } })).status, 401);
});

test('inyección SQL y XSS como datos: se tratan como texto o se rechazan, nunca 500', async (t) => {
  const h = await start(); t.after(h.stop);
  const u = await h.signup(1, { mode: 'cazador' });
  const payloads = ["'; DROP TABLE users;--", '" OR ""="', '<script>alert(1)</script>', '${7*7}', '\u0000', 'a'.repeat(5000)];
  for (const p of payloads) {
    const r1 = await h.call('POST', '/v1/auth/login', { body: { email: p, password: p } });
    assert.ok(r1.status >= 400 && r1.status < 500, `login ${r1.status}`);
    const r2 = await h.call('GET', '/v1/offers?q=' + encodeURIComponent(p), { token: u.access_token });
    assert.ok(r2.status < 500, `q ${r2.status}`);
    const r3 = await h.call('POST', '/v1/alerts', { token: u.access_token, body: { keyword: p, max_price_cents: 100 } });
    assert.ok(r3.status < 500, `alert ${r3.status}`);
  }
  const still = await h.call('GET', '/v1/me', { token: u.access_token });
  assert.equal(still.status, 200); // la tabla users sigue ahí
});

test('cuerpo: JSON inválido 400, tipo erróneo 415, demasiado grande 413, campos extra rechazados', async (t) => {
  const h = await start(); t.after(h.stop);
  assert.equal((await h.call('POST', '/v1/auth/login', { raw: '{no es json' })).status, 400);
  assert.equal((await h.call('POST', '/v1/auth/login', { raw: 'a=b', headers: { 'content-type': 'application/x-www-form-urlencoded' } })).status, 415);
  assert.equal((await h.call('POST', '/v1/auth/login', { raw: JSON.stringify({ email: 'a@b.co', password: 'x'.repeat(4000) }) })).status, 413);
  const extra = await h.call('POST', '/v1/auth/register', { body: { email: 'z@ejemplo.test', handle: 'zeta_1', password: PASS, role: 'admin' } });
  assert.equal(extra.status, 400); // mass assignment
  assert.equal((await h.call('POST', '/v1/auth/login', { raw: '[]' })).status, 400);
  assert.equal((await h.call('POST', '/v1/auth/login', { raw: '{"email":"a@b.co","email":"c@d.co","password":"x"}' })).status < 500, true);
  // __proto__ no contamina nada
  const p = await h.call('POST', '/v1/auth/login', { raw: '{"__proto__":{"admin":true},"email":"a@b.co","password":"x"}' });
  assert.ok(p.status >= 400 && p.status < 500);
  assert.equal({}.admin, undefined);
});

test('parámetros de URL: repetidos, desconocidos y fuera de rango se rechazan', async (t) => {
  const h = await start(); t.after(h.stop);
  const u = await h.signup(1);
  assert.equal((await h.call('GET', '/v1/offers?limit=5&limit=6', { token: u.access_token })).status, 400);
  assert.equal((await h.call('GET', '/v1/offers?admin=1', { token: u.access_token })).status, 400);
  assert.equal((await h.call('GET', '/v1/offers?limit=9999', { token: u.access_token })).status, 400);
  assert.equal((await h.call('GET', '/v1/offers?limit=-1', { token: u.access_token })).status, 400);
  assert.equal((await h.call('GET', '/v1/offers?sort=hot%27', { token: u.access_token })).status, 400);
  assert.equal((await h.call('GET', '/v1/offers?cursor=!!!', { token: u.access_token })).status, 400);
  assert.equal((await h.call('GET', '/v1/me?x=1', { token: u.access_token })).status, 400);
});

test('rutas: trucos de path, 404, 405 y métodos exóticos', async (t) => {
  const h = await start(); t.after(h.stop);
  for (const p of ['/v1/../etc/passwd', '/v1/%2e%2e/me', '//v1/me', '/v1//me', '/v1/me/', '/%00', '/v1/offers/' + 'a'.repeat(3000)]) {
    const r = await h.call('GET', p);
    assert.ok([400, 401, 404, 414].includes(r.status), `${p.slice(0, 30)} → ${r.status}`);
  }
  assert.equal((await h.call('DELETE', '/v1/categories')).status, 405);
  assert.ok((await h.call('DELETE', '/v1/categories')).headers.allow);
  assert.equal((await h.call('TRACE', '/health')).status, 405);
  assert.equal((await h.call('GET', '/health', { raw: 'x', headers: { 'content-type': 'text/plain' } })).status, 400);
});

test('IDs con formato inválido no llegan a la base (404/400)', async (t) => {
  const h = await start(); t.after(h.stop);
  const u = await h.signup(1);
  for (const id of ['1', 'abc', "1' OR '1'='1", 'x'.repeat(36)]) {
    const r = await h.call('GET', '/v1/offers/' + encodeURIComponent(id), { token: u.access_token });
    assert.ok([400, 404].includes(r.status), `${id} → ${r.status}`);
  }
});

test('límite de peticiones: el exceso recibe 429 con Retry-After', async (t) => {
  const h = await start({ GLOBAL_RATE_PER_MIN: '10' }); t.after(h.stop);
  let last;
  for (let i = 0; i < 14; i++) last = await h.call('GET', '/health');
  assert.equal(last.status, 429);
  assert.ok(last.headers['retry-after']);
  h.clock.t += 121_000; // dos ventanas: la deslizante pondera la anterior
  assert.equal((await h.call('GET', '/health')).status, 200);
});

test('registro limitado por IP (20/hora)', async (t) => {
  const h = await start({ GLOBAL_RATE_PER_MIN: '1000' }); t.after(h.stop);
  let s;
  for (let i = 0; i < 22; i++) s = (await h.call('POST', '/v1/auth/register', { body: { email: `r${i}@ejemplo.test`, handle: `rr_${i}`, password: PASS } })).status;
  assert.equal(s, 429);
});

test('autorización: correo sin verificar no vota; rastreador no publica; cuenta nueva no publica', async (t) => {
  const h = await start(); t.after(h.stop);
  const un = await h.signup(1, { verify: false });
  assert.equal((await h.call('POST', '/v1/offers/00000000-0000-4000-8000-000000000000/reports', { token: un.access_token, body: { reason: 'spam' } })).status, 403);
  const ras = await h.signup(2);
  const mk = { category_id: 'supermercado', title: 'Leche 1L', store: 'Super', price_old_cents: 200, price_now_cents: 100 };
  const r = await h.call('POST', '/v1/offers', { token: ras.access_token, body: mk });
  assert.equal(r.status, 403);
  assert.equal(r.json.error.code, 'mode_required');
  await h.call('PATCH', '/v1/me', { token: ras.access_token, body: { mode: 'cazador' } });
  const young = await h.call('POST', '/v1/offers', { token: ras.access_token, body: mk });
  assert.equal(young.status, 403);
  assert.equal(young.json.error.code, 'account_too_new');
});

test('no se puede auto-ascender: PATCH /v1/me rechaza role y la ruta de moderación exige rol', async (t) => {
  const h = await start(); t.after(h.stop);
  const u = await h.signup(1);
  assert.equal((await h.call('PATCH', '/v1/me', { token: u.access_token, body: { role: 'admin' } })).status, 400);
  assert.equal((await h.call('GET', '/v1/mod/queue', { token: u.access_token })).status, 403);
  assert.equal((await h.call('PATCH', '/v1/me', { token: u.access_token, body: { mode: 'admin' } })).status, 400);
});

test('BOLA: no se pueden ver ni tocar alertas ni sesiones de otra persona', async (t) => {
  const h = await start(); t.after(h.stop);
  const a = await h.signup(1); const b = await h.signup(2);
  const al = await h.call('POST', '/v1/alerts', { token: a.access_token, body: { keyword: 'leche', max_price_cents: 500 } });
  assert.equal(al.status, 201);
  const id = al.json.id;
  assert.equal((await h.call('PATCH', `/v1/alerts/${id}`, { token: b.access_token, body: { active: false } })).status, 404);
  assert.equal((await h.call('DELETE', `/v1/alerts/${id}`, { token: b.access_token })).status, 404);
  assert.equal((await h.call('GET', '/v1/alerts', { token: b.access_token })).json.items.length, 0);
  const sess = (await h.call('GET', '/v1/me/sessions', { token: a.access_token })).json.items[0].id;
  assert.equal((await h.call('DELETE', `/v1/me/sessions/${sess}`, { token: b.access_token })).status, 404);
  assert.equal((await h.call('GET', '/v1/me', { token: a.access_token })).status, 200);
});

test('las respuestas nunca filtran hashes, correos ajenos ni campos internos', async (t) => {
  const h = await start(); t.after(h.stop);
  const a = await h.signup(1);
  const txt = JSON.stringify((await h.call('GET', '/v1/me', { token: a.access_token })).json) + JSON.stringify((await h.call('GET', '/v1/me/sessions', { token: a.access_token })).json);
  assert.doesNotMatch(txt, /scrypt|password|token_hash|\$scrypt/i);
});
