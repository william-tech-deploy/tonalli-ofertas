'use strict';
/**
 * Sesiones y tokens.
 *
 *  - Tokens OPACOS y aleatorios de 256 bits (no JWT): se pueden revocar al instante, no tienen
 *    algoritmo que confundir ("alg:none"), ni claves que filtrar. En la base de datos solo vive su SHA-256.
 *  - Token de acceso: vida corta (15 min por defecto).
 *  - Token de refresco: de UN SOLO USO y rotativo. Si alguien presenta uno que ya se usó (después de una
 *    ventana de gracia de 10 s por reintentos legítimos), se asume robo y se REVOCA TODA LA SESIÓN.
 *  - La sesión tiene vida máxima absoluta (90 días) aunque se refresque sin parar.
 *  - Máximo de sesiones activas por usuario: al crear una más, se cierra la más antigua.
 */
const { sql } = require('../db');
const { randomToken, TOKEN_RE, sha256, uuid } = require('../lib/crypto');
const { E } = require('../lib/errors');

const DAY = 86_400_000;
const REUSE_LEEWAY_MS = 10_000;

const USER_STATE = sql`SELECT status, password_changed_at FROM users WHERE id = :uid`;
const PURGE_UNVERIFIED = sql`DELETE FROM users WHERE email_verified_at IS NULL AND created_at < :cut`;
const INS_SESSION = sql`INSERT INTO sessions(id, user_id, created_at, expires_at, device_name, last_ip) VALUES (:id, :uid, :now, :exp, :device, :ip)`;
const INS_ACCESS = sql`INSERT INTO access_tokens(token_hash, session_id, user_id, expires_at) VALUES (:h, :sid, :uid, :exp)`;
const INS_REFRESH = sql`INSERT INTO refresh_tokens(token_hash, session_id, expires_at) VALUES (:h, :sid, :exp)`;
const ACTIVE = sql`SELECT id FROM sessions WHERE user_id = :uid AND revoked_at IS NULL AND expires_at > :now ORDER BY created_at ASC, rowid ASC`;
const ACTIVE_ANY = sql`SELECT id FROM sessions WHERE user_id = :uid AND revoked_at IS NULL`;
const REVOKE = sql`UPDATE sessions SET revoked_at = :now WHERE id = :id AND revoked_at IS NULL`;
const DEL_ACCESS = sql`DELETE FROM access_tokens WHERE session_id = :id`;
const DEL_REFRESH = sql`DELETE FROM refresh_tokens WHERE session_id = :id`;
const OWN_SESSION = sql`SELECT id FROM sessions WHERE id = :id AND user_id = :uid AND revoked_at IS NULL`;
const LIST = sql`SELECT id, created_at, device_name FROM sessions WHERE user_id = :uid AND revoked_at IS NULL AND expires_at > :now ORDER BY created_at DESC, rowid DESC`;
const AUTH_LOOKUP = sql`SELECT u.id, u.handle, u.email, u.mode, u.role, u.lang, u.karma, u.email_verified_at, u.created_at, a.session_id
  FROM access_tokens a
  JOIN sessions s ON s.id = a.session_id
  JOIN users u ON u.id = a.user_id
  WHERE a.token_hash = :h AND a.expires_at > :now AND s.revoked_at IS NULL AND s.expires_at > :now AND u.status = 'active'`;
const REFRESH_LOOKUP = sql`SELECT r.session_id, r.used_at, r.expires_at, s.user_id, s.revoked_at, s.expires_at AS session_expires, u.status
  FROM refresh_tokens r
  JOIN sessions s ON s.id = r.session_id
  JOIN users u ON u.id = s.user_id
  WHERE r.token_hash = :h`;
const MARK_USED = sql`UPDATE refresh_tokens SET used_at = :now WHERE token_hash = :h AND used_at IS NULL`;
const PURGE_ACCESS = sql`DELETE FROM access_tokens WHERE expires_at < :now`;
const PURGE_REFRESH = sql`DELETE FROM refresh_tokens WHERE expires_at < :now`;
const PURGE_SESSIONS = sql`DELETE FROM sessions WHERE expires_at < :cut OR (revoked_at IS NOT NULL AND revoked_at < :cut)`;
const PURGE_ONCE = sql`DELETE FROM one_time_tokens WHERE created_at < :cut`;

function createSessions({ db, config, now, audit }) {
  function revokeInner(id, t) {
    db.run(REVOKE, { id, now: t });
    db.run(DEL_ACCESS, { id });
    db.run(DEL_REFRESH, { id });
  }

  function issue(sessionId, userId, t, sessionExpires) {
    const access = randomToken('bat_');
    const refresh = randomToken('brt_');
    const accessExp = t + config.accessTtlSec * 1000;
    const refreshExp = Math.min(t + config.refreshTtlDays * DAY, sessionExpires);
    db.run(INS_ACCESS, { h: sha256(access), sid: sessionId, uid: userId, exp: accessExp });
    db.run(INS_REFRESH, { h: sha256(refresh), sid: sessionId, exp: refreshExp });
    return {
      token_type: 'Bearer',
      access_token: access,
      access_expires_in: config.accessTtlSec,
      refresh_token: refresh,
      refresh_expires_at: new Date(refreshExp).toISOString(),
    };
  }

  return {
    /** Crea una sesión nueva (inicio de sesión). */
    create(userId, { deviceName = null, ip = null, passwordChangedAt = null } = {}) {
      const t = now();
      return db.tx(() => {
        // Se revalida DENTRO de la transacción: si entre la comprobación de la contraseña y este punto
        // hubo un cambio/restablecimiento de contraseña o una suspensión, no se crea la sesión (carrera de login).
        const st = db.get(USER_STATE, { uid: userId });
        if (!st || st.status !== 'active' || (passwordChangedAt !== null && st.password_changed_at !== passwordChangedAt)) {
          throw E.unauthorized('invalid_credentials', 'Correo o contraseña incorrectos');
        }
        const active = db.all(ACTIVE, { uid: userId, now: t });
        const excess = active.length - (config.maxSessionsPerUser - 1);
        for (let i = 0; i < excess; i++) revokeInner(active[i].id, t);
        const id = uuid();
        const exp = t + config.sessionMaxDays * DAY;
        db.run(INS_SESSION, { id, uid: userId, now: t, exp, device: deviceName ? String(deviceName).slice(0, 60) : null, ip: ip ? String(ip).slice(0, 45) : null });
        return { session_id: id, ...issue(id, userId, t, exp) };
      });
    },

    /** Cambia un token de refresco por un par nuevo. Detecta reutilización (robo). */
    rotate(token, ip) {
      const invalid = () => E.unauthorized('invalid_refresh_token', 'Sesión inválida o vencida');
      if (typeof token !== 'string' || !TOKEN_RE.refresh.test(token)) throw invalid();
      const h = sha256(token);
      const t = now();
      // OJO: dentro de tx no se puede lanzar para "registrar y fallar": el ROLLBACK borraría la revocación.
      // Por eso la transacción DEVUELVE el resultado y el error se lanza después del COMMIT.
      const out = db.tx(() => {
        const row = db.get(REFRESH_LOOKUP, { h });
        if (!row) return { fail: 'unknown' };
        if (row.revoked_at || row.session_expires <= t || row.status !== 'active') return { fail: 'revoked', row };
        if (row.used_at) {
          if (t - row.used_at <= REUSE_LEEWAY_MS) return { fail: 'retry', row }; // doble envío legítimo del mismo cliente
          revokeInner(row.session_id, t);
          return { fail: 'reuse', row };
        }
        if (row.expires_at <= t) return { fail: 'expired', row };
        if (db.run(MARK_USED, { now: t, h }).changes !== 1) return { fail: 'retry', row };
        return { ok: { session_id: row.session_id, ...issue(row.session_id, row.user_id, t, row.session_expires) }, row };
      });
      if (out.ok) return out.ok;
      if (out.fail === 'reuse') audit.log('refresh_reuse_detected', { userId: out.row.user_id, ip, detail: { session: out.row.session_id } });
      if (out.fail === 'retry') throw E.unauthorized('refresh_retry', 'Vuelve a intentarlo con el token más reciente');
      throw invalid();
    },

    /** Devuelve el usuario dueño de un token de acceso válido, o null. */
    authenticate(token) {
      if (typeof token !== 'string' || !TOKEN_RE.access.test(token)) return null;
      return db.get(AUTH_LOOKUP, { h: sha256(token), now: now() }) || null;
    },

    revoke(sessionId) { db.tx(() => revokeInner(sessionId, now())); },

    /** Cierra todas las sesiones de un usuario (menos, opcionalmente, una). */
    revokeAll(userId, exceptId = null) {
      const t = now();
      return db.tx(() => {
        let n = 0;
        for (const r of db.all(ACTIVE_ANY, { uid: userId })) {
          if (r.id === exceptId) continue;
          revokeInner(r.id, t);
          n++;
        }
        return n;
      });
    },

    /** Cierra UNA sesión, pero solo si es del usuario indicado. */
    revokeOwn(userId, sessionId) {
      return db.tx(() => {
        if (!db.get(OWN_SESSION, { id: sessionId, uid: userId })) return false;
        revokeInner(sessionId, now());
        return true;
      });
    },

    list(userId) { return db.all(LIST, { uid: userId, now: now() }); },

    purgeExpired() {
      const t = now();
      const cut = t - 7 * DAY;
      return {
        access: db.run(PURGE_ACCESS, { now: t }).changes,
        refresh: db.run(PURGE_REFRESH, { now: t }).changes,
        sessions: db.run(PURGE_SESSIONS, { cut }).changes,
        one_time: db.run(PURGE_ONCE, { cut }).changes,
        // Cuentas que nunca confirmaron su correo en 48 h: libera correos y nombres que alguien pudo "okupar".
        unverified: db.run(PURGE_UNVERIFIED, { cut: t - 2 * DAY }).changes,
      };
    },
  };
}

module.exports = { createSessions, REUSE_LEEWAY_MS };
