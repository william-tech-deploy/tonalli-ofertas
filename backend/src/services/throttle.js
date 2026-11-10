'use strict';
/**
 * Contadores PERSISTENTES de intentos fallidos (tabla auth_throttle). A diferencia del limitador en
 * memoria, sobreviven a un reinicio del servidor: reiniciar no sirve para reiniciar el contador de
 * un ataque de fuerza bruta. Las claves nunca contienen datos personales en claro (se usan hashes).
 *
 * Es un FRENO (throttling, NIST 800-63B §5.2.2), no un bloqueo permanente: pasada la ventana
 * el contador vuelve a cero. Así un atacante no puede dejar a una persona fuera de su cuenta para siempre.
 */
const { sql } = require('../db');

const GET = sql`SELECT count, window_start FROM auth_throttle WHERE key = :key`;
const BUMP = sql`INSERT INTO auth_throttle(key, count, window_start) VALUES (:key, 1, :now)
  ON CONFLICT(key) DO UPDATE SET
    count = CASE WHEN :now - window_start >= :win THEN 1 ELSE count + 1 END,
    window_start = CASE WHEN :now - window_start >= :win THEN :now ELSE window_start END`;
const CLEAR = sql`DELETE FROM auth_throttle WHERE key = :key`;
const PURGE = sql`DELETE FROM auth_throttle WHERE :now - window_start >= :maxage`;

function createThrottle({ db, now }) {
  return {
    /** ¿Ya se alcanzó el máximo de fallos dentro de la ventana? */
    check(key, max, windowMs) {
      const t = now();
      const row = db.get(GET, { key });
      if (!row || t - row.window_start >= windowMs || row.count < max) return { blocked: false, retryAfterSec: 0 };
      return { blocked: true, retryAfterSec: Math.ceil((row.window_start + windowMs - t) / 1000) };
    },
    /** Cuenta un fallo (o un intento, según el uso). */
    fail(key, windowMs) { db.run(BUMP, { key, now: now(), win: windowMs }); },
    clear(key) { db.run(CLEAR, { key }); },
    purge(maxAgeMs = 24 * 3600 * 1000) { return db.run(PURGE, { now: now(), maxage: maxAgeMs }).changes; },
  };
}

module.exports = { createThrottle };
