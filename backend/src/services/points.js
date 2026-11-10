'use strict';
/**
 * Karma. El cliente NUNCA envía puntos: el servidor los otorga según reglas fijas (domain/karma.js)
 * y con un tope diario por tipo de acción, así que un script no puede "granjear" karma.
 */
const { sql } = require('../db');
const { RULES } = require('../domain/karma');
const { uuid } = require('../lib/crypto');

const COUNT = sql`SELECT COUNT(*) AS c FROM karma_events WHERE user_id = :uid AND kind = :kind AND created_at > :since`;
const INSERT = sql`INSERT OR IGNORE INTO karma_events(user_id, kind, points, created_at, ref) VALUES (:uid, :kind, :points, :now, :ref)`;
const FIND = sql`SELECT id, points FROM karma_events WHERE user_id = :uid AND kind = :kind AND ref = :ref`;
const DEL = sql`DELETE FROM karma_events WHERE id = :id`;
const SUB = sql`UPDATE users SET karma = MAX(0, karma - :points) WHERE id = :uid`;
const ADD = sql`UPDATE users SET karma = karma + :points WHERE id = :uid`;

function createPoints({ db, now }) {
  return {
    /** @returns {number} puntos otorgados (0 si se alcanzó el tope diario) */
    award(userId, kind, ref = null) {
      const rule = RULES[kind];
      if (!rule) throw new Error(`Regla de karma desconocida: ${kind}`);
      const t = now();
      const { c } = db.get(COUNT, { uid: userId, kind, since: t - 24 * 3600 * 1000 });
      if (c >= rule.dailyCap) return 0;
      // Con ref, el mismo (usuario, tipo, ref) solo paga una vez: cambiar de voto no vuelve a dar puntos.
      if (db.run(INSERT, { uid: userId, kind, points: rule.points, now: t, ref }).changes !== 1) return 0;
      db.run(ADD, { uid: userId, points: rule.points });
      return rule.points;
    },
    /** Quita los puntos de una acción (p. ej. una oferta eliminada por moderación). */
    revoke(userId, kind, ref) {
      const e = db.get(FIND, { uid: userId, kind, ref });
      if (!e) return 0;
      db.run(DEL, { id: e.id });
      db.run(SUB, { uid: userId, points: e.points });
      return e.points;
    },
    newId: uuid,
  };
}

module.exports = { createPoints };
