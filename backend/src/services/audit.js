'use strict';
/**
 * Bitácora de seguridad (tabla audit_log, de solo anexar: la propia base de datos impide editarla).
 * Registra QUIÉN hizo QUÉ y DESDE DÓNDE en eventos sensibles (inicios de sesión, cambios de
 * contraseña, acciones de moderación). Nunca se guardan contraseñas, tokens ni correos en "detail".
 */
const { sql } = require('../db');

const INSERT = sql`INSERT INTO audit_log(ts, event, user_id, ip, detail) VALUES (:ts, :event, :user_id, :ip, :detail)`;

function createAudit({ db, now }) {
  return {
    log(event, { userId = null, ip = null, detail = null } = {}) {
      db.run(INSERT, {
        ts: now(),
        event: String(event).slice(0, 60),
        user_id: userId,
        ip: ip ? String(ip).slice(0, 45) : null,
        detail: detail ? JSON.stringify(detail).slice(0, 500) : null,
      });
    },
  };
}

module.exports = { createAudit };
