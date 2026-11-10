'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { start, PASS } = require('./helper');

test('registro → verificación → login → /me', async (t) => {
  const h = await start(); t.after(h.stop);
  const u = await h.signup(1);
  assert.match(u.access_token, /^bat_/);
  assert.match(u.refresh_token, /^brt_/);
  const me = await h.call('GET', '/v1/me', { token: u.access_token });
  assert.equal(me.status, 200);
  assert.equal(me.json.handle, 'user_1');
  assert.equal(me.json.role, 'user');
  assert.equal('password_hash' in me.json, false);
});

test('anti-enumeración: registrar un correo existente responde igual', async (t) => {
  const h = await start(); t.after(h.stop);
  await h.signup(1);
  const a = await h.call('POST', '/v1/auth/register', { body: { email: 'user1@ejemplo.test', handle: 'otro_nombre', password: PASS } });
  const b = await h.call('POST', '/v1/auth/register', { body: { email: 'nuevo@ejemplo.test', handle: 'otro_nombre2', password: PASS } });
  assert.equal(a.status, 202); assert.equal(b.status, 202);
  assert.deepEqual(a.json, b.json);
});

test('login: mismo error para correo inexistente y contraseña errónea', async (t) => {
  const h = await start(); t.after(h.stop);
  await h.signup(1);
  const a = await h.call('POST', '/v1/auth/login', { body: { email: 'user1@ejemplo.test', password: 'incorrecta incorrecta 1' } });
  const b = await h.call('POST', '/v1/auth/login', { body: { email: 'nadie@ejemplo.test', password: 'incorrecta incorrecta 1' } });
  assert.equal(a.status, 401); assert.equal(b.status, 401);
  assert.equal(a.json.error.code, b.json.error.code);
  assert.equal(a.json.error.message, b.json.error.message);
});

test('contraseñas débiles y demasiado cortas se rechazan', async (t) => {
  const h = await start(); t.after(h.stop);
  for (const p of ['corta', 'passwordpassword', '12345678901234567890', 'a'.repeat(129)]) {
    const r = await h.call('POST', '/v1/auth/register', { body: { email: 'd@ejemplo.test', handle: 'debil', password: p } });
    assert.ok([400, 422].includes(r.status), `${p.slice(0, 10)} → ${r.status}`);
  }
});

test('bloqueo por fuerza bruta tras 5 fallos (cuenta) incluso con la clave correcta', async (t) => {
  const h = await start(); t.after(h.stop);
  await h.signup(1);
  for (let i = 0; i < 5; i++) await h.call('POST', '/v1/auth/login', { body: { email: 'user1@ejemplo.test', password: 'incorrecta incorrecta 1' } });
  const r = await h.call('POST', '/v1/auth/login', { body: { email: 'user1@ejemplo.test', password: PASS } });
  assert.equal(r.status, 429);
  assert.ok(r.headers['retry-after']);
  h.clock.t += 16 * 60_000;
  const ok = await h.call('POST', '/v1/auth/login', { body: { email: 'user1@ejemplo.test', password: PASS } });
  assert.equal(ok.status, 200);
});

test('el access token vence a los 15 min y deja de servir', async (t) => {
  const h = await start(); t.after(h.stop);
  const u = await h.signup(1);
  h.clock.t += 16 * 60_000;
  assert.equal((await h.call('GET', '/v1/me', { token: u.access_token })).status, 401);
});

test('refresh rota el token; reutilizar uno viejo revoca TODA la sesión', async (t) => {
  const h = await start(); t.after(h.stop);
  const u = await h.signup(1);
  const r1 = await h.call('POST', '/v1/auth/refresh', { body: { refresh_token: u.refresh_token } });
  assert.equal(r1.status, 200);
  assert.notEqual(r1.json.refresh_token, u.refresh_token);
  h.clock.t += 60_000; // fuera de la tolerancia de 10 s
  const reuse = await h.call('POST', '/v1/auth/refresh', { body: { refresh_token: u.refresh_token } });
  assert.equal(reuse.status, 401);
  // el token nuevo y el access nuevo también quedaron revocados
  assert.equal((await h.call('POST', '/v1/auth/refresh', { body: { refresh_token: r1.json.refresh_token } })).status, 401);
  assert.equal((await h.call('GET', '/v1/me', { token: r1.json.access_token })).status, 401);
});

test('logout revoca el access token al instante', async (t) => {
  const h = await start(); t.after(h.stop);
  const u = await h.signup(1);
  assert.equal((await h.call('POST', '/v1/auth/logout', { token: u.access_token })).status, 204);
  assert.equal((await h.call('GET', '/v1/me', { token: u.access_token })).status, 401);
});

test('cambiar contraseña: exige la actual y cierra las demás sesiones', async (t) => {
  const h = await start(); t.after(h.stop);
  const a = await h.signup(1);
  const l2 = await h.call('POST', '/v1/auth/login', { body: { email: a.email, password: PASS } });
  const bad = await h.call('POST', '/v1/auth/change-password', { token: a.access_token, body: { current_password: 'mala mala mala mala 1', new_password: 'otra frase larga y distinta 77' } });
  assert.equal(bad.status, 403); // 403 y no 401: el cliente no debe confundirlo con sesión vencida
  const ok = await h.call('POST', '/v1/auth/change-password', { token: a.access_token, body: { current_password: PASS, new_password: 'otra frase larga y distinta 77' } });
  assert.equal(ok.status, 204);
  assert.equal((await h.call('GET', '/v1/me', { token: l2.json.access_token })).status, 401);
  assert.equal((await h.call('GET', '/v1/me', { token: a.access_token })).status, 200);
  assert.equal((await h.call('POST', '/v1/auth/login', { body: { email: a.email, password: 'otra frase larga y distinta 77' } })).status, 200);
});

test('recuperar contraseña: token de un solo uso', async (t) => {
  const h = await start(); t.after(h.stop);
  const a = await h.signup(1);
  assert.equal((await h.call('POST', '/v1/auth/forgot-password', { body: { email: a.email } })).status, 202);
  assert.equal((await h.call('POST', '/v1/auth/forgot-password', { body: { email: 'nadie@ejemplo.test' } })).status, 202);
  const tk = h.lastToken(a.email, 'reset');
  assert.ok(tk);
  const np = 'frase nueva muy larga para pruebas 9';
  assert.equal((await h.call('POST', '/v1/auth/reset-password', { body: { token: tk, new_password: np } })).status, 204);
  assert.notEqual((await h.call('POST', '/v1/auth/reset-password', { body: { token: tk, new_password: np + 'x' } })).status, 204);
  assert.equal((await h.call('GET', '/v1/me', { token: a.access_token })).status, 401);
  assert.equal((await h.call('POST', '/v1/auth/login', { body: { email: a.email, password: np } })).status, 200);
});

test('borrar cuenta pide contraseña y elimina los datos', async (t) => {
  const h = await start(); t.after(h.stop);
  const a = await h.signup(1);
  assert.notEqual((await h.call('DELETE', '/v1/me', { token: a.access_token, body: { password: 'mala mala mala mala 1' } })).status, 204);
  assert.equal((await h.call('DELETE', '/v1/me', { token: a.access_token, body: { password: PASS } })).status, 204);
  assert.equal((await h.call('GET', '/v1/me', { token: a.access_token })).status, 401);
  assert.equal((await h.call('POST', '/v1/auth/login', { body: { email: a.email, password: PASS } })).status, 401);
});
