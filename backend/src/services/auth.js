'use strict';
/**
 * Cuentas y credenciales: registro, verificación de correo, inicio de sesión, restablecimiento y
 * cambio de contraseña, borrado de cuenta.
 *
 * Principios (OWASP ASVS V2/V3, Authentication Cheat Sheet, NIST SP 800-63B):
 *  - Respuestas IDÉNTICAS exista o no la cuenta (anti-enumeración): registro, login y "olvidé mi contraseña".
 *  - Mismo coste de CPU exista o no la cuenta (siempre se calcula un hash) para que el tiempo no delate nada.
 *  - Freno a fuerza bruta por cuenta Y por IP, con contadores que sobreviven a reinicios.
 *  - Tokens de un solo uso, con vencimiento corto y guardados solo como hash.
 *  - Todo cambio de credenciales cierra las demás sesiones y avisa por correo.
 */
const { sql } = require('../db');
const { sha256, randomToken, TOKEN_RE, uuid } = require('../lib/crypto');
const { E } = require('../lib/errors');
const { checkPasswordPolicy, isBreached } = require('../lib/password');
const { ipKey } = require('../lib/ratelimit');
const { selfDto } = require('./dto');

const MIN = 60_000;
const HOUR = 3_600_000;
const LOGIN_WINDOW = 15 * MIN;
const LOGIN_PAIR_MAX = 5;   // fallos de UNA IP contra UNA cuenta: otra IP (la persona real) no queda bloqueada por el atacante
const LOGIN_ACCT_MAX = 20;  // tope global por cuenta (ataques distribuidos)
const LOGIN_IP_MAX = 30;
const PW_WINDOW = 15 * MIN;
const PW_MAX = 5;
const VERIFY_TTL = 24 * HOUR;
const RESET_TTL = 30 * MIN;
// Nombres que podrían usarse para hacerse pasar por el equipo de la app.
const RESERVED = ['admin', 'administrador', 'moderador', 'moderator', 'soporte', 'support', 'staff', 'root', 'system', 'sistema', 'bajo', 'tonalli', 'security', 'seguridad', 'ayuda', 'help'];

const USER_BY_EMAIL = sql`SELECT id, handle, email, password_hash, email_verified_at, mode, role, lang, karma, status, created_at, password_changed_at FROM users WHERE email = :email`;
const USER_BY_ID = sql`SELECT id, handle, email, password_hash, email_verified_at, mode, role, lang, karma, status, created_at FROM users WHERE id = :id`;
const HANDLE_TAKEN = sql`SELECT id FROM users WHERE handle = :handle`;
const INSERT_USER = sql`INSERT INTO users(id, email, handle, password_hash, lang, created_at, password_changed_at) VALUES (:id, :email, :handle, :hash, :lang, :now, :now)`;
const SET_PASSWORD = sql`UPDATE users SET password_hash = :hash, password_changed_at = :now WHERE id = :id`;
const REHASH = sql`UPDATE users SET password_hash = :hash WHERE id = :id`;
const SET_VERIFIED = sql`UPDATE users SET email_verified_at = COALESCE(email_verified_at, :now) WHERE id = :uid`;
const DELETE_USER = sql`DELETE FROM users WHERE id = :id`;
const ONCE_PEEK = sql`SELECT t.user_id, u.email, u.handle, u.lang FROM one_time_tokens t JOIN users u ON u.id = t.user_id
  WHERE t.token_hash = :h AND t.purpose = :p AND t.used_at IS NULL AND t.expires_at > :now`;
const ONCE_USE = sql`UPDATE one_time_tokens SET used_at = :now WHERE token_hash = :h AND purpose = :p AND used_at IS NULL AND expires_at > :now`;
const ONCE_KILL = sql`UPDATE one_time_tokens SET used_at = :now WHERE user_id = :uid AND purpose = :p AND used_at IS NULL`;
const ONCE_INSERT = sql`INSERT INTO one_time_tokens(token_hash, user_id, purpose, expires_at, created_at) VALUES (:h, :uid, :p, :exp, :now)`;

function isUnique(err, column) {
  return Boolean(err) && err.code === 'ERR_SQLITE_ERROR' && /UNIQUE constraint failed/.test(String(err.message)) && String(err.message).includes(column);
}

function createAuth({ db, config, now, passwords, sessions, mailer, throttle, audit, bg }) {
  let dummyPromise = null;
  /** Hash "señuelo": se compara contra él cuando el correo no existe, para gastar el mismo tiempo. */
  const dummyHash = () => (dummyPromise ??= passwords.hash(randomToken('dummy_')));
  dummyHash().catch(() => { dummyPromise = null; }); // se calcula al arrancar: el primer login de un correo inexistente no tarda más

  const weak = (reason) => E.badRequest('weak_password', 'La contraseña no cumple la política de seguridad', { reason });

  async function assertAcceptablePassword(password, identity) {
    const reason = checkPasswordPolicy(password, identity);
    if (reason) throw weak(reason);
    if (config.breachCheck && (await isBreached(password))) {
      throw E.badRequest('breached_password', 'Esa contraseña apareció en filtraciones públicas. Elige otra');
    }
  }

  function issueOnce(userId, purpose, ttlMs) {
    const t = now();
    db.run(ONCE_KILL, { uid: userId, p: purpose, now: t }); // invalida los anteriores
    const token = randomToken('bot_');
    db.run(ONCE_INSERT, { h: sha256(token), uid: userId, p: purpose, exp: t + ttlMs, now: t });
    return token;
  }

  const acctKey = (email) => 'acct:' + sha256(email).slice(0, 32);

  return {
    async register({ email, handle, password, lang }, ip) {
      if (!config.registrationEnabled) throw E.forbidden('registration_closed', 'El registro está cerrado por ahora');
      const lower = handle.toLowerCase();
      if (RESERVED.some((r) => lower.startsWith(r))) throw E.badRequest('handle_reserved', 'Ese nombre de usuario no está disponible');
      await assertAcceptablePassword(password, { email, handle });
      const hash = await passwords.hash(password); // SIEMPRE se calcula: mismo tiempo exista o no el correo

      // Límite por DESTINATARIO: nadie puede usar la API para llenar de correos el buzón de un tercero.
      const mk = 'regmail:' + sha256(email).slice(0, 32);
      const ms = throttle.check(mk, 3, HOUR);
      throttle.fail(mk, HOUR);
      if (ms.blocked) return; // misma respuesta 202 de siempre: no se revela nada

      const id = uuid();
      const t = now();
      let created = false;
      let exists = false;
      try {
        db.tx(() => {
          // El nombre de usuario se comprueba ANTES que el correo y se responde igual a un correo ya registrado
          // que a uno nuevo con ese nombre ocupado: así el código HTTP no revela si el correo existe.
          if (db.get(HANDLE_TAKEN, { handle })) throw E.conflict('handle_taken', 'Ese nombre de usuario ya está en uso');
          if (db.get(USER_BY_EMAIL, { email })) { exists = true; return; }
          db.run(INSERT_USER, { id, email, handle, hash, lang, now: t });
          created = true;
        });
      } catch (err) {
        if (isUnique(err, 'users.email')) exists = true; // carrera entre dos registros simultáneos
        else if (isUnique(err, 'users.handle')) throw E.conflict('handle_taken', 'Ese nombre de usuario ya está en uso');
        else throw err;
      }

      if (created) {
        const token = issueOnce(id, 'verify_email', VERIFY_TTL);
        bg(mailer.send({ to: email, template: 'verify', lang, handle, token }));
        audit.log('register', { userId: id, ip });
      } else if (exists) {
        bg(mailer.send({ to: email, template: 'exists', lang }));
        audit.log('register_existing_email', { ip });
      }
      // La respuesta es la misma en ambos casos: no revela si el correo ya tenía cuenta.
    },

    verifyEmail(token, ip) {
      const t = now();
      const h = sha256(token);
      const ok = db.tx(() => {
        const row = db.get(ONCE_PEEK, { h, p: 'verify_email', now: t });
        if (!row) return null;
        if (db.run(ONCE_USE, { h, p: 'verify_email', now: t }).changes !== 1) return null;
        db.run(SET_VERIFIED, { uid: row.user_id, now: t });
        return row.user_id;
      });
      if (!ok) throw E.badRequest('invalid_token', 'El enlace no es válido o venció');
      audit.log('email_verified', { userId: ok, ip });
    },

    resendVerification(user, ip) {
      if (user.email_verified_at) return;
      const k = 'resend:' + user.id;
      const s = throttle.check(k, 3, HOUR);
      if (s.blocked) throw E.tooMany(s.retryAfterSec);
      throttle.fail(k, HOUR);
      const token = issueOnce(user.id, 'verify_email', VERIFY_TTL);
      bg(mailer.send({ to: user.email, template: 'verify', lang: user.lang, handle: user.handle, token }));
      audit.log('verification_resent', { userId: user.id, ip });
    },

    async login({ email, password, device_name: deviceName }, ip) {
      const kA = acctKey(email);
      const kI = 'ip:' + ipKey(ip);
      const kP = kA + ':' + ipKey(ip);
      for (const [key, max] of [[kP, LOGIN_PAIR_MAX], [kA, LOGIN_ACCT_MAX], [kI, LOGIN_IP_MAX]]) {
        const s = throttle.check(key, max, LOGIN_WINDOW);
        if (s.blocked) { audit.log('login_throttled', { ip }); throw E.tooMany(s.retryAfterSec); }
      }
      const user = db.get(USER_BY_EMAIL, { email });
      const result = await passwords.verify(password, user ? user.password_hash : await dummyHash());
      if (!user || !result.ok) {
        throttle.fail(kP, LOGIN_WINDOW);
        throttle.fail(kA, LOGIN_WINDOW);
        throttle.fail(kI, LOGIN_WINDOW);
        audit.log('login_fail', { userId: user ? user.id : null, ip });
        // Mismo mensaje y mismo código para "no existe" y "contraseña incorrecta".
        throw E.unauthorized('invalid_credentials', 'Correo o contraseña incorrectos');
      }
      if (user.status !== 'active') {
        audit.log('login_suspended', { userId: user.id, ip });
        throw E.forbidden('account_suspended', 'La cuenta está suspendida');
      }
      throttle.clear(kA);
      throttle.clear(kP);
      if (result.needsRehash) db.run(REHASH, { hash: await passwords.hash(password), id: user.id });
      const tokens = sessions.create(user.id, { deviceName, ip, passwordChangedAt: user.password_changed_at });
      audit.log('login_ok', { userId: user.id, ip });
      return { ...tokens, user: selfDto(user) };
    },

    refresh(refreshToken, ip) {
      const { session_id: sessionId, ...tokens } = sessions.rotate(refreshToken, ip);
      return { ...tokens };
    },

    logout(sessionId, userId, ip) {
      sessions.revoke(sessionId);
      audit.log('logout', { userId, ip });
    },

    logoutAll(userId, ip) {
      const n = sessions.revokeAll(userId);
      audit.log('logout_all', { userId, ip, detail: { sessions: n } });
    },

    /** Siempre responde igual (el handler devuelve 202), exista o no el correo. */
    forgotPassword({ email }, ip) {
      const k = 'forgot:' + sha256(email).slice(0, 32);
      const s = throttle.check(k, 3, HOUR);
      throttle.fail(k, HOUR);
      if (s.blocked) return;
      const user = db.get(USER_BY_EMAIL, { email });
      audit.log('password_reset_requested', { userId: user ? user.id : null, ip });
      if (!user || user.status !== 'active') return;
      const token = issueOnce(user.id, 'reset_password', RESET_TTL);
      bg(mailer.send({ to: user.email, template: 'reset', lang: user.lang, handle: user.handle, token }));
    },

    async resetPassword({ token, new_password: newPassword }, ip) {
      const h = sha256(token);
      const row = db.get(ONCE_PEEK, { h, p: 'reset_password', now: now() });
      if (!row) throw E.badRequest('invalid_token', 'El enlace no es válido o venció');
      await assertAcceptablePassword(newPassword, { email: row.email, handle: row.handle });
      const hash = await passwords.hash(newPassword);
      const done = db.tx(() => {
        const t = now();
        // Consumo atómico: si dos personas usan el mismo enlace a la vez, solo una lo logra.
        if (db.run(ONCE_USE, { h, p: 'reset_password', now: t }).changes !== 1) return false;
        db.run(SET_PASSWORD, { hash, id: row.user_id, now: t });
        db.run(SET_VERIFIED, { uid: row.user_id, now: t }); // abrir el enlace prueba que controla el correo
        db.run(ONCE_KILL, { uid: row.user_id, p: 'reset_password', now: t });
        sessions.revokeAll(row.user_id);
        return true;
      });
      if (!done) throw E.badRequest('invalid_token', 'El enlace no es válido o venció');
      audit.log('password_reset', { userId: row.user_id, ip });
      bg(mailer.send({ to: row.email, template: 'changed', lang: row.lang }));
    },

    async changePassword(user, sessionId, { current_password: current, new_password: next }, ip) {
      const k = 'pw:' + user.id;
      const s = throttle.check(k, PW_MAX, PW_WINDOW);
      if (s.blocked) throw E.tooMany(s.retryAfterSec);
      const row = db.get(USER_BY_ID, { id: user.id });
      const result = await passwords.verify(current, row.password_hash);
      if (!result.ok) {
        throttle.fail(k, PW_WINDOW);
        audit.log('password_change_fail', { userId: user.id, ip });
        throw E.forbidden('invalid_password', 'La contraseña actual es incorrecta');
      }
      throttle.clear(k);
      if (current === next) throw E.badRequest('same_password', 'La contraseña nueva debe ser distinta');
      await assertAcceptablePassword(next, { email: row.email, handle: row.handle });
      const hash = await passwords.hash(next);
      db.tx(() => {
        db.run(SET_PASSWORD, { hash, id: user.id, now: now() });
        sessions.revokeAll(user.id, sessionId); // se cierran las demás sesiones; esta sigue abierta
      });
      audit.log('password_changed', { userId: user.id, ip });
      bg(mailer.send({ to: row.email, template: 'changed', lang: row.lang }));
    },

    async deleteAccount(user, { password }, ip) {
      const k = 'pw:' + user.id;
      const s = throttle.check(k, PW_MAX, PW_WINDOW);
      if (s.blocked) throw E.tooMany(s.retryAfterSec);
      const row = db.get(USER_BY_ID, { id: user.id });
      const result = await passwords.verify(password, row.password_hash);
      if (!result.ok) {
        throttle.fail(k, PW_WINDOW);
        audit.log('account_delete_fail', { userId: user.id, ip });
        throw E.forbidden('invalid_password', 'La contraseña es incorrecta');
      }
      throttle.clear(k);
      // Las ofertas publicadas quedan sin autor (ON DELETE SET NULL); sesiones, votos, alertas y avisos se borran en cascada.
      db.run(DELETE_USER, { id: user.id });
      audit.log('account_deleted', { userId: user.id, ip });
    },
  };
}

module.exports = { createAuth, TOKEN_RE };
