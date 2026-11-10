'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { priceVerdict } = require('../src/domain/verdict');
const { analyzeUrl } = require('../src/domain/url');
const { isValidEAN } = require('../src/domain/ean');
const { levelOf, voteWeight } = require('../src/domain/karma');
const { object, string, int, parse, optional } = require('../src/lib/schema');
const { checkPasswordPolicy } = require('../src/lib/password');
const { sanitizeJpeg, InvalidImage } = require('../src/lib/jpeg');
const { ipKey } = require('../src/lib/ratelimit');
const { Database } = require('../src/db');
const { makeJpeg } = require('./jpegfixture');

const hist = (arr) => arr.map(([daysAgo, cents]) => ({ daysAgo, cents }));
const BASE = hist([[40, 100], [35, 100], [30, 100], [25, 100], [20, 100], [15, 100]]);

test('veredicto de precio', () => {
  assert.equal(priceVerdict([], 200, 100).kind, 'none');
  assert.equal(priceVerdict(hist([[30, 100]]), 200, 80).kind, 'none');
  assert.equal(priceVerdict(BASE, 103, 90).kind, 'real');            // 10 % real
  assert.equal(priceVerdict(BASE, 200, 99).kind, 'fake');            // "antes" inflado
  assert.equal(priceVerdict(BASE, 100, 99).kind, 'mild');            // rebaja ínfima y sin exageración
  assert.equal(priceVerdict(BASE, 120, 99).kind, 'fake');            // anuncia 17 %, real 1 %
  assert.equal(priceVerdict(BASE, 103, 95).kind, 'mild');            // 5 %: ni real ni falsa
  assert.equal(priceVerdict(hist([[5, 100], [4, 100], [3, 100], [2, 100], [1, 100]]), 150, 80).kind, 'none'); // todo reciente
  const outlier = hist([[40, 100], [35, 100], [30, 100], [25, 100], [20, 100000]]);
  assert.equal(priceVerdict(outlier, 130, 90).base_cents, 100);       // la mediana resiste atípicos
});

test('enlaces: solo https público normal', () => {
  assert.equal(analyzeUrl('https://www.tienda.com/oferta?id=1').ok, true);
  assert.equal(analyzeUrl('tienda.com/oferta').ok, true);
  const bad = { 'http://tienda.com': 'http', 'https://bit.ly/x': 'short', 'https://10.0.0.1/': 'ip', 'https://0x7f000001/': 'ip', 'https://2130706433/': 'ip', 'https://[::1]/': 'ip',
    'https://localhost/': 'internal', 'https://intranet.corp/': 'internal', 'https://a:b@tienda.com/': 'creds', 'https://tienda.com:8080/': 'port', 'https://xn--pple-43d.com/': 'punycode',
    'https://oferta.xyz/': 'tld', 'https://tienda.com/a b': 'invalid', 'javascript:alert(1)': 'invalid', 'ftp://x.com/': 'http' };
  for (const [u, flag] of Object.entries(bad)) {
    const r = analyzeUrl(u);
    assert.equal(r.ok, false, u);
    assert.ok(r.flags.includes(flag) || r.flags.includes('invalid'), `${u} → ${r.flags}`);
  }
});

test('EAN/UPC: dígito verificador', () => {
  assert.equal(isValidEAN('4006381333931'), true);
  assert.equal(isValidEAN('4006381333932'), false);
  assert.equal(isValidEAN('036000291452'), true); // UPC-A
  assert.equal(isValidEAN('abc'), false);
  assert.equal(isValidEAN(''), false);
});

test('karma: niveles y peso de voto', () => {
  assert.equal(levelOf(0), 0); assert.equal(levelOf(49), 0); assert.equal(levelOf(50), 1); assert.equal(levelOf(150), 2); assert.equal(levelOf(99999), 3);
  assert.ok(voteWeight(0) < voteWeight(150));
});

test('esquema estricto: rechaza campos extra, tipos erróneos y recorta/normaliza texto', () => {
  const S = object({ a: string({ min: 1, max: 5 }), n: int({ min: 1, max: 10 }), o: optional(string({ max: 3 }), 'z') });
  assert.deepEqual(parse(S, { a: 'hola', n: 3 }), { a: 'hola', n: 3, o: 'z' });
  for (const bad of [{ a: 'hola', n: 3, x: 1 }, { a: 'demasiado', n: 3 }, { a: 'a', n: '3' }, { a: 'a', n: 3.5 }, { a: 'a', n: 11 }, { a: 1, n: 1 }, { n: 1 }, null, [], 'x']) {
    assert.throws(() => parse(S, bad), undefined, JSON.stringify(bad));
  }
  assert.throws(() => parse(S, JSON.parse('{"a":"a","n":1,"__proto__":{"x":1}}')));
  assert.equal(parse(S, { a: 'a\u0000b', n: 1 }).a, 'ab'); // los caracteres de control se eliminan
});

test('política de contraseñas (NIST 800-63B)', () => {
  const ctx = { email: 'ana.lopez@x.com', handle: 'analopez' };
  assert.equal(checkPasswordPolicy('corta', ctx), 'too_short');
  assert.equal(checkPasswordPolicy('x'.repeat(129), ctx), 'too_long');
  assert.equal(checkPasswordPolicy('aaaaaaaaaaaaaaaaaaaa', ctx), 'too_repetitive');
  assert.equal(checkPasswordPolicy('passwordpassword', ctx), 'common_password');
  assert.equal(checkPasswordPolicy('mi clave analopez 2026 larga', ctx), 'contains_identity');
  assert.equal(checkPasswordPolicy('correcto caballo bateria grapa', ctx), null);       // sin reglas de composición
  assert.equal(checkPasswordPolicy('contraseña con ñ y acentos áéí', ctx), null);        // Unicode permitido
});

test('JPEG: se limpia, se valida y se rechaza lo raro', () => {
  const out = sanitizeJpeg(makeJpeg());
  const s = out.bytes.toString('latin1');
  assert.ok(!/GPS|SECRETO|script|comentario|PK/.test(s));
  assert.equal(out.width, 8);
  assert.throws(() => sanitizeJpeg(Buffer.alloc(200)), InvalidImage);
  assert.throws(() => sanitizeJpeg(makeJpeg({ width: 5000, height: 5000 })), InvalidImage);
  assert.throws(() => sanitizeJpeg(makeJpeg().subarray(0, 120)), InvalidImage);
  assert.throws(() => sanitizeJpeg(Buffer.concat([Buffer.from([0xff, 0xd8]), Buffer.alloc(400, 0xff)])), InvalidImage);
});

test('rate limit: IPv6 se agrupa por /64 y IPv4 mapeada se normaliza', () => {
  assert.equal(ipKey('2001:db8:1:2:aaaa:bbbb:cccc:dddd'), ipKey('2001:db8:1:2::1'));
  assert.notEqual(ipKey('2001:db8:1:3::1'), ipKey('2001:db8:1:2::1'));
  assert.equal(ipKey('::ffff:1.2.3.4'), '1.2.3.4');
  assert.equal(ipKey('basura'), 'unknown');
});

test('migraciones: una ya aplicada y alterada se detecta', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'mig-'));
  fs.writeFileSync(path.join(dir, '001_a.sql'), 'CREATE TABLE t(x INTEGER) STRICT;');
  const f = path.join(dir, 'b.db');
  const db = new Database(f); db.migrate(dir); db.close();
  fs.writeFileSync(path.join(dir, '001_a.sql'), 'CREATE TABLE t(x INTEGER, y TEXT) STRICT;');
  const db2 = new Database(f);
  assert.throws(() => db2.migrate(dir), /modificada/);
  db2.close();
  fs.rmSync(dir, { recursive: true, force: true });
});

test('sql``: prohíbe interpolar y db rechaza texto crudo', () => {
  const { sql } = require('../src/db');
  const evil = "x'; DROP TABLE users;--";
  assert.throws(() => sql`SELECT ${evil}`);
  const db = new Database(':memory:');
  assert.throws(() => db.get({ text: 'SELECT 1' }), TypeError);
  assert.throws(() => db.get('SELECT 1'), TypeError);
  db.close();
});

test('pepper distinto al de la base → el servidor se niega a arrancar', () => {
  const { loadConfig } = require('../src/config');
  const { createApp } = require('../src/app');
  const { createLogger } = require('../src/lib/logger');
  const db = new Database(':memory:');
  const env = (p) => loadConfig({ NODE_ENV: 'test', PASSWORD_PEPPER: p, MAIL_DRIVER: 'memory', SCRYPT_LN: '10', DATABASE_PATH: ':memory:' });
  const a = createApp(env('q7Rk2mZp9XvL4nBt8WcY1hGdS6fJ3aEu5oIy0TrN'), { db, logger: createLogger('error') });
  assert.throws(() => createApp(env('Zz9Yy8Xx7Ww6Vv5Uu4Tt3Ss2Rr1Qq0Pp9Oo8Nn7'), { db, logger: createLogger('error') }), /PEPPER/);
  void a;
});

test('configuración: falla cerrado en producción insegura', () => {
  const { loadConfig } = require('../src/config');
  const good = { NODE_ENV: 'production', PASSWORD_PEPPER: 'q7Rk2mZp9XvL4nBt8WcY1hGdS6fJ3aEu5oIy0TrN', CORS_ORIGINS: 'https://tonalli.example', PUBLIC_BASE_URL: 'https://tonalli.example', MAIL_DRIVER: 'resend', RESEND_API_KEY: 'k', TRUST_PROXY_HOPS: '1' };
  assert.doesNotThrow(() => loadConfig(good));
  assert.throws(() => loadConfig({ ...good, PASSWORD_PEPPER: 'corto' }));
  assert.throws(() => loadConfig({ ...good, PASSWORD_PEPPER: 'a'.repeat(40) }));
  assert.throws(() => loadConfig({ ...good, CORS_ORIGINS: '*' }));
  assert.throws(() => loadConfig({ ...good, PUBLIC_BASE_URL: 'http://tonalli.example' }));
  assert.throws(() => loadConfig({ ...good, MAIL_DRIVER: 'console' }));
  assert.throws(() => loadConfig({ ...good, SCRYPT_LN: '12' }));
  assert.throws(() => loadConfig({ ...good, NODE_ENV: 'staging' }));
});

test('scripts: migrate, make-moderator, backup y check-security funcionan', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'tonalli-'));
  const env = { ...process.env, NODE_ENV: 'test', PASSWORD_PEPPER: 'q7Rk2mZp9XvL4nBt8WcY1hGdS6fJ3aEu5oIy0TrN', DATABASE_PATH: path.join(dir, 'x', 'b.db'), MAIL_DRIVER: 'memory' };
  const run = (script, ...args) => spawnSync(process.execPath, [path.join(__dirname, '..', 'scripts', script), ...args], { env, encoding: 'utf8' });
  assert.equal(run('migrate.js').status, 0);
  assert.equal(run('make-moderator.js', 'nadie').status, 1);
  const db = new Database(env.DATABASE_PATH);
  db.db.prepare("INSERT INTO users(id,email,handle,password_hash,created_at,password_changed_at) VALUES (?,?,?,?,?,?)").run('11111111-1111-4111-8111-111111111111', 'a@b.co', 'ana_1', 'h', 1, 1);
  db.close();
  assert.equal(run('make-moderator.js', 'ana_1').status, 0);
  const db2 = new Database(env.DATABASE_PATH);
  assert.equal(db2.db.prepare("SELECT role FROM users WHERE handle='ana_1'").get().role, 'moderador');
  assert.equal(db2.db.prepare("SELECT COUNT(*) c FROM audit_log WHERE event='role_changed_cli'").get().c, 1);
  db2.close();
  const b = run('backup.js'); assert.equal(b.status, 0, b.stderr);
  const files = fs.readdirSync(path.join(dir, 'x', 'backups'));
  assert.equal(files.length, 1);
  assert.equal(fs.statSync(path.join(dir, 'x', 'backups', files[0])).mode & 0o077, 0); // solo el dueño
  assert.equal(run('check-security.js').status, 0);
  fs.rmSync(dir, { recursive: true, force: true });
});
