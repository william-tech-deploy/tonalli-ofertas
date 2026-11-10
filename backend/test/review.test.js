'use strict';
/** Una prueba por cada hallazgo confirmado de la revisión de seguridad independiente (C1–C12, P1, P4). */
const test = require('node:test');
const assert = require('node:assert/strict');
const { start } = require('./helper');
const { analyzeUrl } = require('../src/domain/url');
const { loadConfig } = require('../src/config');
const { PEPPER } = require('./helper');

const OFFER = { category_id: 'super', title: 'Leche entera 1L', store: 'Super Selectos', price_old_cents: 200, price_now_cents: 150, link_url: 'https://www.superselectos.com/leche' };
const ENV = { NEW_ACCOUNT_HOURS: '0', GLOBAL_RATE_PER_MIN: '100000' };
const karmaOf = (h, email) => h.db.db.prepare('SELECT karma FROM users WHERE email=?').get(email).karma;
const hunter = (h, n) => h.signup(n, { mode: 'cazador' });

test('C1 reportes de cuentas sin reputación no ocultan una oferta', async (t) => {
  const h = await start(ENV); t.after(h.stop);
  const a = await hunter(h, 1);
  const o = (await h.call('POST', '/v1/offers', { token: a.access_token, body: OFFER })).json;
  for (let i = 2; i < 6; i++) { const r = await h.signup(i); await h.call('POST', `/v1/offers/${o.id}/reports`, { token: r.access_token, body: { reason: 'spam' } }); }
  const v = await h.signup(9);
  assert.equal((await h.call('GET', `/v1/offers/${o.id}`, { token: v.access_token })).status, 200);
});

test('C2 una sola cuenta no puede sembrar un historial para falsear el veredicto', async (t) => {
  const h = await start(ENV); t.after(h.stop);
  const a = await hunter(h, 1);
  const ean = '7501055300075';
  const ins = h.db.db.prepare('INSERT INTO price_points(product_key, store_norm, price_cents, observed_at, user_id) VALUES (?,?,?,?,?)');
  h.db.db.prepare("UPDATE users SET id=id").run();
  const uid = h.db.db.prepare('SELECT id FROM users WHERE email=?').get(a.email).id;
  for (const d of [40, 35, 30, 25, 20, 15]) ins.run(`ean:${ean}`, 'super selectos', 900, h.clock.t - d * 86400_000, uid); // todo del mismo autor
  const o = await h.call('POST', '/v1/offers', { token: a.access_token, body: { ...OFFER, ean, price_old_cents: 900, price_now_cents: 700 } });
  assert.equal(o.status, 201, JSON.stringify(o.json));
  const d = await h.call('GET', `/v1/offers/${o.json.id}`, { token: a.access_token });
  assert.equal(d.json.verdict.kind, 'none');
});

test('C3 karma: el voto paga una sola vez por oferta, comentar lo propio no paga, y eliminar la oferta quita sus puntos', async (t) => {
  const h = await start(ENV); t.after(h.stop);
  const a = await hunter(h, 1); const b = await hunter(h, 2);
  const o = (await h.call('POST', '/v1/offers', { token: a.access_token, body: OFFER })).json;
  for (let i = 0; i < 5; i++) {
    await h.call('PUT', `/v1/offers/${o.id}/vote`, { token: b.access_token, body: { value: 1 } });
    await h.call('PUT', `/v1/offers/${o.id}/vote`, { token: b.access_token, body: { value: 0 } });
  }
  assert.equal(karmaOf(h, b.email), 1);
  const k0 = karmaOf(h, a.email);
  for (let i = 0; i < 4; i++) await h.call('POST', `/v1/offers/${o.id}/comments`, { token: a.access_token, body: { body: 'comentario ' + i } });
  assert.equal(karmaOf(h, a.email), k0);
  const m = await h.signup(3);
  h.db.db.prepare("UPDATE users SET role='moderador' WHERE email=?").run(m.email);
  await h.call('POST', `/v1/mod/offers/${o.id}/remove`, { token: m.access_token });
  assert.equal(karmaOf(h, a.email), 0);
});

test('C4 eliminar ofertas no devuelve cupo del tope diario', async (t) => {
  const h = await start(ENV); t.after(h.stop);
  const a = await hunter(h, 1); const m = await h.signup(3);
  h.db.db.prepare("UPDATE users SET role='moderador' WHERE email=?").run(m.email);
  for (let i = 0; i < 5; i++) {
    const r = await h.call('POST', '/v1/offers', { token: a.access_token, body: { ...OFFER, title: 'Oferta ' + i } });
    await h.call('POST', `/v1/mod/offers/${r.json.id}/remove`, { token: m.access_token });
  }
  assert.equal((await h.call('POST', '/v1/offers', { token: a.access_token, body: { ...OFFER, title: 'Oferta extra' } })).status, 429);
});

test('C5 un login que empezó antes de un cambio de contraseña no crea sesión', async (t) => {
  const h = await start(ENV); t.after(h.stop);
  const u = await h.signup(1);
  const user = h.db.db.prepare('SELECT id, password_changed_at AS p FROM users WHERE email=?').get(u.email);
  h.clock.t += 1000;
  h.db.db.prepare('UPDATE users SET password_changed_at=? WHERE id=?').run(h.clock.t, user.id); // el reset ocurre "durante" el login
  assert.throws(() => h.app.services.sessions.create(user.id, { passwordChangedAt: user.p }), (e) => e.status === 401);
  h.db.db.prepare("UPDATE users SET status='suspended' WHERE id=?").run(user.id);
  assert.throws(() => h.app.services.sessions.create(user.id, {}), (e) => e.status === 401);
});

test('C6 registro: el código HTTP no revela si el correo ya existe', async (t) => {
  const h = await start(ENV); t.after(h.stop);
  await h.signup(1); // user_1 / user1@ejemplo.test
  const reg = (email, handle) => h.call('POST', '/v1/auth/register', { body: { email, handle, password: h.PASS } });
  const a = await reg('user1@ejemplo.test', 'user_1');      // correo existente + nombre ocupado
  const b = await reg('nuevo@ejemplo.test', 'user_1');      // correo nuevo + nombre ocupado
  assert.equal(a.status, b.status);
  assert.deepEqual(a.json.error?.code, b.json.error?.code);
});

test('C7 no se puede bombardear un buzón ajeno y las cuentas sin verificar se purgan a las 48 h', async (t) => {
  const h = await start(ENV); t.after(h.stop);
  for (let i = 0; i < 10; i++) await h.call('POST', '/v1/auth/register', { body: { email: 'victima@ejemplo.test', handle: 'atk_' + i, password: h.PASS } });
  assert.ok(h.app.mailer.sent.filter((m) => m.to === 'victima@ejemplo.test').length <= 3);
  h.clock.t += 49 * 3600_000;
  const r = h.app.services.sessions.purgeExpired();
  assert.ok(r.unverified >= 1);
  assert.equal(h.db.db.prepare('SELECT COUNT(*) c FROM users').get().c, 0);
});

test('C8 alertas: palabra clave vacía tras normalizar se rechaza y exigen correo verificado', async (t) => {
  const h = await start(ENV); t.after(h.stop);
  const v = await h.signup(1); const nv = await h.signup(2, { verify: false });
  assert.equal((await h.call('POST', '/v1/alerts', { token: v.access_token, body: { keyword: '́́', max_price_cents: 100 } })).status, 400);
  assert.equal((await h.call('POST', '/v1/alerts', { token: nv.access_token, body: { keyword: 'leche', max_price_cents: 100 } })).status, 403);
});

test('C9 enlaces: puntos finales dobles y subdominios de acortadores ya no pasan', () => {
  for (const u of ['https://evil.xyz../', 'https://localhost../', 'https://x.internal../', 'https://tinyurl.com../', 'https://www.bit.ly/x', 'https://sub.t.me/x', 'https://a..b.com/']) {
    assert.equal(analyzeUrl(u).ok, false, u);
  }
  assert.equal(analyzeUrl('https://www.superselectos.com/x').ok, true);
});

test('C10 cuentas nuevas no pueden votar ni comentar todavía', async (t) => {
  const h = await start({ ...ENV, NEW_ACCOUNT_HOURS: '24' }); t.after(h.stop);
  const n = await h.signup(1, { mode: 'cazador' });
  const id = '00000000-0000-4000-8000-000000000000';
  assert.equal((await h.call('PUT', `/v1/offers/${id}/vote`, { token: n.access_token, body: { value: 1 } })).json.error.code, 'account_too_new');
  assert.equal((await h.call('POST', `/v1/offers/${id}/comments`, { token: n.access_token, body: { body: 'hola' } })).json.error.code, 'account_too_new');
});

test('C11 un atacante no puede bloquear a la víctima desde otra IP (bloqueo por cuenta+IP)', async (t) => {
  const h = await start(ENV); t.after(h.stop);
  const u = await h.signup(1);
  const { createAuth } = require('../src/services/auth');
  void createAuth;
  const auth = h.app.services.auth;
  for (let i = 0; i < 5; i++) await auth.login({ email: u.email, password: 'incorrecta incorrecta 1' }, '203.0.113.9').catch(() => {});
  await assert.rejects(auth.login({ email: u.email, password: h.PASS }, '203.0.113.9'), (e) => e.status === 429); // el atacante sí queda frenado
  const ok = await auth.login({ email: u.email, password: h.PASS }, '198.51.100.7');                              // la víctima, desde su IP, entra
  assert.match(ok.access_token, /^bat_/);
});

test('P1 sin NODE_ENV explícito el servidor no arranca', () => {
  assert.throws(() => loadConfig({ PASSWORD_PEPPER: PEPPER }), /NODE_ENV/);
});

test('P4 las peticiones OPTIONS también consumen el límite global', async (t) => {
  const h = await start({ GLOBAL_RATE_PER_MIN: '10' }); t.after(h.stop);
  let last;
  for (let i = 0; i < 14; i++) last = await h.call('OPTIONS', '/v1/offers', { headers: { origin: 'https://tonalli.example', 'access-control-request-method': 'GET' } });
  assert.equal(last.status, 429);
});
