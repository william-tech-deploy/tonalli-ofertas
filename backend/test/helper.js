'use strict';
/** Utilidades de prueba: app real en un puerto efímero, BD en memoria, reloj controlable y correo en memoria. */
const http = require('node:http');
const { loadConfig } = require('../src/config');
const { createApp } = require('../src/app');
const { createLogger } = require('../src/lib/logger');

const PEPPER = 'q7Rk2mZp9XvL4nBt8WcY1hGdS6fJ3aEu5oIy0TrN';
const PASS = 'correcto caballo bateria grapa 2026';

async function start(extraEnv = {}, startTime = 1_800_000_000_000) {
  const env = {
    NODE_ENV: 'test', PASSWORD_PEPPER: PEPPER, DATABASE_PATH: ':memory:', MAIL_DRIVER: 'memory', SCRYPT_LN: '10',
    CORS_ORIGINS: 'https://appassets.androidplatform.net,https://tonalli.example', PUBLIC_BASE_URL: 'https://tonalli.example',
    NEW_ACCOUNT_HOURS: '24', ...extraEnv,
  };
  const clock = { t: startTime };
  const now = () => clock.t;
  const app = createApp(loadConfig(env), { now, logger: createLogger('error') });
  const server = http.createServer(app.handler);
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const port = server.address().port;

  function call(method, path, { body, token, headers = {}, raw, ip } = {}) {
    return new Promise((resolve, reject) => {
      const data = raw !== undefined ? raw : body !== undefined ? JSON.stringify(body) : null;
      const h = { ...headers };
      if (data !== null) { h['content-length'] = Buffer.byteLength(data); if (!h['content-type']) h['content-type'] = 'application/json'; }
      if (token) h.authorization = `Bearer ${token}`;
      const req = http.request({ host: '127.0.0.1', port, method, path, headers: h }, (res) => {
        const chunks = [];
        res.on('data', (c) => chunks.push(c));
        res.on('end', () => {
          const buf = Buffer.concat(chunks);
          let json = null;
          try { json = JSON.parse(buf.toString('utf8')); } catch { /* no JSON */ }
          resolve({ status: res.statusCode, headers: res.headers, json, buf });
        });
      });
      req.on('error', reject);
      if (data !== null) req.write(data);
      req.end();
    });
  }

  const lastToken = (to, kind) => {
    const m = [...app.mailer.sent].reverse().find((x) => x.to === to && x.text.includes(`#/${kind}/`));
    if (!m) return null;
    return /#\/[a-z]+\/(bot_[A-Za-z0-9_-]{43})/.exec(m.text)?.[1] ?? null;
  };

  async function signup(n, { verify = true, mode = null, age = true } = {}) {
    const email = `user${n}@ejemplo.test`;
    const r = await call('POST', '/v1/auth/register', { body: { email, handle: `user_${n}`, password: PASS } });
    if (r.status !== 202) throw new Error(`register ${r.status} ${JSON.stringify(r.json)}`);
    if (verify) {
      const v = await call('POST', '/v1/auth/verify-email', { body: { token: lastToken(email, 'verify') } });
      if (v.status !== 204) throw new Error(`verify ${v.status} ${JSON.stringify(v.json)}`);
    }
    const l = await call('POST', '/v1/auth/login', { body: { email, password: PASS, device_name: 'test' } });
    if (l.status !== 200) throw new Error(`login ${l.status} ${JSON.stringify(l.json)}`);
    const out = { email, ...l.json };
    if (mode) await call('PATCH', '/v1/me', { token: out.access_token, body: { mode } });
    return out;
  }

  async function stop() { await new Promise((r) => server.close(r)); await app.close(); }
  return { app, call, clock, signup, lastToken, stop, db: app.db, PASS };
}

module.exports = { start, PASS, PEPPER };
