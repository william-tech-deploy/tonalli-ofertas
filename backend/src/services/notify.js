'use strict';
/**
 * Notificaciones dentro de la app. "params" es JSON con datos mínimos (ids y textos cortos);
 * el cliente los escapa al mostrarlos. Se conservan solo las últimas 200 por usuario para que
 * una cuenta no pueda llenar la base de datos generando avisos.
 */
const { sql } = require('../db');
const { uuid } = require('../lib/crypto');

const INSERT = sql`INSERT INTO notifications(id, user_id, kind, params, created_at) VALUES (:id, :uid, :kind, :params, :now)`;
const PRUNE = sql`DELETE FROM notifications WHERE user_id = :uid AND id NOT IN
  (SELECT id FROM notifications WHERE user_id = :uid ORDER BY created_at DESC, rowid DESC LIMIT 200)`;

function shorten(value) {
  if (typeof value === 'string') return value.slice(0, 80);
  return value;
}

function createNotify({ db, now }) {
  return {
    send(userId, kind, params = {}) {
      if (!userId) return;
      const clean = {};
      for (const [k, v] of Object.entries(params)) clean[k] = shorten(v);
      db.run(INSERT, { id: uuid(), uid: userId, kind, params: JSON.stringify(clean).slice(0, 1000), now: now() });
      db.run(PRUNE, { uid: userId });
    },
  };
}

module.exports = { createNotify };
