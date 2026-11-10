'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { start } = require('./helper');
const { makeJpeg } = require('./jpegfixture');

const OFFER = { category_id: 'super', title: 'Leche entera 1L', store: 'Super Selectos', price_old_cents: 200, price_now_cents: 150, link_url: 'https://www.superselectos.com/leche' };
const ENV = { NEW_ACCOUNT_HOURS: '0', GLOBAL_RATE_PER_MIN: '100000' };

async function hunter(h, n) { return h.signup(n, { mode: 'cazador' }); }
async function makeMod(h, user) { h.db.db.prepare("UPDATE users SET role='moderador' WHERE email=?").run(user.email); }

test('crear oferta: se ve en la lista, se calcula la temperatura y el autor gana karma', async (t) => {
  const h = await start(ENV); t.after(h.stop);
  const a = await hunter(h, 1);
  const c = await h.call('POST', '/v1/offers', { token: a.access_token, body: OFFER });
  assert.equal(c.status, 201, JSON.stringify(c.json));
  const list = await h.call('GET', '/v1/offers?sort=new', { token: a.access_token });
  assert.equal(list.json.items.length, 1);
  assert.equal(list.json.items[0].title, 'Leche entera 1L');
  assert.equal((await h.call('GET', '/v1/me', { token: a.access_token })).json.karma, 15);
});

test('validaciones de oferta: precio nuevo < anterior, enlace peligroso, EAN inválido, sin evidencia, categoría inexistente', async (t) => {
  const h = await start(ENV); t.after(h.stop);
  const a = await hunter(h, 1);
  const post = (over) => h.call('POST', '/v1/offers', { token: a.access_token, body: { ...OFFER, ...over } });
  assert.ok((await post({ price_now_cents: 300 })).status >= 400);
  for (const link_url of ['http://tienda.com/x', 'https://bit.ly/abc', 'https://127.0.0.1/x', 'https://localhost/x', 'https://user:pw@tienda.com/', 'javascript:alert(1)', 'https://xn--pple-43d.com/']) {
    const r = await post({ link_url });
    assert.ok(r.status >= 400 && r.status < 500, `${link_url} → ${r.status}`);
  }
  assert.ok((await post({ link_url: undefined, ean: '1234567890123' })).status >= 400); // sin evidencia + EAN con dígito malo
  assert.ok((await post({ category_id: 'no-existe' })).status >= 400);
  assert.equal((await post({ title: '<img src=x onerror=alert(1)>Oferta' })).status < 500, true);
});

test('foto: se aceptan JPEG válidos, se les quita EXIF/GPS y lo pegado al final; se sirve como imagen aislada', async (t) => {
  const h = await start(ENV); t.after(h.stop);
  const a = await hunter(h, 1);
  const jpg = makeJpeg();
  const r = await h.call('POST', '/v1/offers', { token: a.access_token, body: { ...OFFER, link_url: undefined, photo: jpg.toString('base64') } });
  assert.equal(r.status, 201, JSON.stringify(r.json));
  const p = await h.call('GET', `/v1/offers/${r.json.id}/photo`, { token: a.access_token });
  assert.equal(p.status, 200);
  assert.equal(p.headers['content-type'], 'image/jpeg');
  assert.match(p.headers['content-security-policy'], /sandbox/);
  const s = p.buf.toString('latin1');
  assert.ok(!s.includes('GPS') && !s.includes('SECRETO') && !s.includes('script') && !s.includes('comentario') && !s.includes('PK'));
  assert.deepEqual([...p.buf.subarray(0, 2)], [0xff, 0xd8]);
  assert.deepEqual([...p.buf.subarray(-2)], [0xff, 0xd9]);
  // archivos que no son JPEG (PNG, HTML, ZIP, texto) o de dimensiones absurdas se rechazan
  for (const bad of [Buffer.from('<html><script>alert(1)</script></html>'.padEnd(200, ' ')), Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47]), Buffer.alloc(200)]), makeJpeg({ width: 60000, height: 60000 })]) {
    const b = await h.call('POST', '/v1/offers', { token: a.access_token, body: { ...OFFER, link_url: undefined, photo: bad.toString('base64') } });
    assert.ok(b.status >= 400 && b.status < 500, `bad photo → ${b.status}`);
  }
});

test('votos: peso por nivel, no votar la propia oferta, cambiar de voto no duplica', async (t) => {
  const h = await start(ENV); t.after(h.stop);
  const a = await hunter(h, 1); const b = await hunter(h, 2);
  const o = (await h.call('POST', '/v1/offers', { token: a.access_token, body: OFFER })).json;
  assert.equal((await h.call('PUT', `/v1/offers/${o.id}/vote`, { token: a.access_token, body: { value: 1 } })).status, 403);
  const v1 = await h.call('PUT', `/v1/offers/${o.id}/vote`, { token: b.access_token, body: { value: 1 } });
  assert.equal(v1.status, 200);
  const t1 = v1.json.temperature;
  const v2 = await h.call('PUT', `/v1/offers/${o.id}/vote`, { token: b.access_token, body: { value: 1 } });
  assert.equal(v2.json.temperature, t1);
  const v3 = await h.call('PUT', `/v1/offers/${o.id}/vote`, { token: b.access_token, body: { value: -1 } });
  assert.ok(v3.json.temperature < t1);
  assert.equal((await h.call('PUT', `/v1/offers/${o.id}/vote`, { token: b.access_token, body: { value: 5 } })).status, 400);
  assert.equal((await h.call('PUT', `/v1/offers/${o.id}/vote`, { token: b.access_token, body: { value: '1' } })).status, 400);
});

test('reportes: 3 reportes de cuentas con antigüedad ocultan la oferta; cuentas nuevas no cuentan', async (t) => {
  const h = await start({ ...ENV, NEW_ACCOUNT_HOURS: '24' }); t.after(h.stop);
  const author = await h.signup(1, { mode: 'cazador' });
  h.clock.t += 25 * 3600_000;
  const o = await h.call('POST', '/v1/offers', { token: (await h.call('POST', '/v1/auth/login', { body: { email: author.email, password: h.PASS } })).json.access_token, body: OFFER });
  assert.equal(o.status, 201, JSON.stringify(o.json));
  // 3 cuentas NUEVAS reportan: no cuentan
  const fresh = [];
  for (let i = 10; i < 13; i++) fresh.push(await h.signup(i));
  for (const f of fresh) assert.equal((await h.call('POST', `/v1/offers/${o.json.id}/reports`, { token: f.access_token, body: { reason: 'spam' } })).status, 202);
  const viewer = await h.signup(30);
  assert.equal((await h.call('GET', `/v1/offers/${o.json.id}`, { token: viewer.access_token })).status, 200);
  // 3 cuentas con antigüedad reportan: se oculta
  const olds = [];
  for (let i = 20; i < 23; i++) olds.push(await h.signup(i));
  h.clock.t += 25 * 3600_000;
  for (const f of olds) {
    h.db.db.prepare('UPDATE users SET karma=50 WHERE email=?').run(f.email); // los reportes solo cuentan con reputación mínima
    const tk = (await h.call('POST', '/v1/auth/login', { body: { email: f.email, password: h.PASS } })).json.access_token;
    assert.equal((await h.call('POST', `/v1/offers/${o.json.id}/reports`, { token: tk, body: { reason: 'fake' } })).status, 202);
  }
  const vt = (await h.call('POST', '/v1/auth/login', { body: { email: viewer.email, password: h.PASS } })).json.access_token;
  assert.equal((await h.call('GET', `/v1/offers/${o.json.id}`, { token: vt })).status, 404);
  assert.equal((await h.call('GET', '/v1/offers?sort=new', { token: vt })).json.items.length, 0);
});

test('moderación: cola, aprobar/restaurar/quitar; moderador no actúa sobre su propia oferta; todo auditado', async (t) => {
  const h = await start({ ...ENV, REQUIRE_APPROVAL: 'true' }); t.after(h.stop);
  const a = await hunter(h, 1);
  const m = await h.signup(2); await makeMod(h, m);
  const o = (await h.call('POST', '/v1/offers', { token: a.access_token, body: OFFER })).json;
  const other = await h.signup(3);
  assert.equal((await h.call('GET', `/v1/offers/${o.id}`, { token: other.access_token })).status, 404); // pendiente: invisible
  assert.equal((await h.call('GET', `/v1/offers/${o.id}`, { token: a.access_token })).status, 200);     // el autor la ve
  const q = await h.call('GET', '/v1/mod/queue', { token: m.access_token });
  assert.equal(q.status, 200);
  assert.equal(q.json.pending.length, 1);
  assert.equal((await h.call('POST', `/v1/mod/offers/${o.id}/approve`, { token: other.access_token })).status, 403);
  assert.equal((await h.call('POST', `/v1/mod/offers/${o.id}/approve`, { token: m.access_token })).status, 200);
  assert.equal((await h.call('GET', `/v1/offers/${o.id}`, { token: other.access_token })).status, 200);
  assert.equal((await h.call('POST', `/v1/mod/offers/${o.id}/remove`, { token: m.access_token })).status, 200);
  assert.equal((await h.call('GET', `/v1/offers/${o.id}`, { token: other.access_token })).status, 404);
  const log = h.db.db.prepare("SELECT event FROM audit_log WHERE event LIKE 'mod_%' OR event LIKE 'offer_%'").all();
  assert.ok(log.length >= 2, JSON.stringify(log));
  // moderador no modera lo propio
  const mo = (await h.call('PATCH', '/v1/me', { token: m.access_token, body: { mode: 'cazador' } }), (await h.call('POST', '/v1/offers', { token: m.access_token, body: OFFER })).json);
  assert.equal((await h.call('POST', `/v1/mod/offers/${mo.id}/approve`, { token: m.access_token })).status, 403);
});

test('audit_log es de solo anexar: UPDATE y DELETE reciente fallan en la propia base', async (t) => {
  const h = await start(ENV); t.after(h.stop);
  await h.signup(1);
  assert.throws(() => h.db.db.exec("UPDATE audit_log SET event='x'"), /solo anexar/);
  assert.throws(() => h.db.db.exec('DELETE FROM audit_log'), /retención/);
});

test('alertas: coinciden con ofertas nuevas por palabra clave y precio máximo; no duplican', async (t) => {
  const h = await start(ENV); t.after(h.stop);
  const a = await hunter(h, 1); const b = await h.signup(2);
  assert.equal((await h.call('POST', '/v1/alerts', { token: b.access_token, body: { keyword: 'leche', max_price_cents: 160 } })).status, 201);
  assert.equal((await h.call('POST', '/v1/alerts', { token: b.access_token, body: { keyword: 'leche', max_price_cents: 100 } })).status, 201);
  await h.call('POST', '/v1/offers', { token: a.access_token, body: OFFER });
  const n = await h.call('GET', '/v1/notifications', { token: b.access_token });
  assert.equal(n.status, 200);
  assert.equal(n.json.items.filter((x) => x.kind === 'alert').length, 1);
  assert.equal(n.json.unread, 1);
  assert.equal((await h.call('POST', '/v1/notifications/read', { token: b.access_token })).json.updated, 1);
  assert.equal((await h.call('GET', '/v1/notifications', { token: b.access_token })).json.unread, 0);
});

test('propuestas de categoría: exigen karma 150 y al llegar a la meta se crea la categoría', async (t) => {
  const h = await start(ENV); t.after(h.stop);
  const a = await hunter(h, 1);
  assert.ok((await h.call('POST', '/v1/category-proposals', { token: a.access_token, body: { name: 'Mascotas' } })).status === 403);
  h.db.db.prepare('UPDATE users SET karma=200 WHERE email=?').run(a.email);
  const p = await h.call('POST', '/v1/category-proposals', { token: a.access_token, body: { name: 'Mascotas' } });
  assert.equal(p.status, 201, JSON.stringify(p.json));
  assert.ok((await h.call('POST', '/v1/category-proposals', { token: a.access_token, body: { name: 'mascotas' } })).status >= 400); // duplicado
  // simula 499 votos y el voto 500 real
  h.db.db.prepare('UPDATE category_proposals SET votes=499').run();
  const b = await h.signup(2);
  assert.equal((await h.call('PUT', `/v1/category-proposals/${p.json.id}/vote`, { token: b.access_token })).status, 200);
  const cats = await h.call('GET', '/v1/categories', { token: b.access_token });
  assert.ok(cats.json.items.some((c) => c.name === 'Mascotas'));
});

test('historial de precios y veredicto: oferta con precio inflado se marca como falsa', async (t) => {
  const h = await start(ENV); t.after(h.stop);
  const a = await hunter(h, 1);
  const ean = '7501055300075';
  // precios habituales hace 20-40 días
  const now = h.clock.t;
  for (const [d, c] of [[40, 100], [35, 102], [30, 98], [25, 100], [20, 101], [15, 100]]) {
    h.db.db.prepare('INSERT INTO price_points(product_key, store_norm, price_cents, observed_at) VALUES (?,?,?,?)').run(`ean:${ean}`, 'super selectos', c, now - d * 86400_000);
  }
  const o = await h.call('POST', '/v1/offers', { token: a.access_token, body: { ...OFFER, ean, price_old_cents: 200, price_now_cents: 99 } });
  assert.equal(o.status, 201, JSON.stringify(o.json));
  const d = await h.call('GET', `/v1/offers/${o.json.id}`, { token: a.access_token });
  assert.equal(d.json.verdict.kind, 'fake');
  const ph = await h.call('GET', `/v1/offers/${o.json.id}/price-history`, { token: a.access_token });
  assert.equal(ph.status, 200);
  const byEan = await h.call('GET', `/v1/products/${ean}`, { token: a.access_token });
  assert.equal(byEan.status, 200);
});

test('tope diario de publicaciones (5) y paginación por cursor sin duplicados', async (t) => {
  const h = await start(ENV); t.after(h.stop);
  const a = await hunter(h, 1);
  let last;
  for (let i = 0; i < 6; i++) last = await h.call('POST', '/v1/offers', { token: a.access_token, body: { ...OFFER, title: `Oferta número ${i}` } });
  assert.equal(last.status, 429);
  const seen = new Set(); let cursor = null; let pages = 0;
  do {
    const r = await h.call('GET', `/v1/offers?sort=new&limit=2${cursor ? '&cursor=' + cursor : ''}`, { token: a.access_token });
    assert.equal(r.status, 200);
    for (const it of r.json.items) { assert.ok(!seen.has(it.id)); seen.add(it.id); }
    cursor = r.json.next_cursor; pages++;
  } while (cursor && pages < 10);
  assert.equal(seen.size, 5);
});
