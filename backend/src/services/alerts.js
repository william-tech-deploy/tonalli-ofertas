'use strict';
/**
 * Alertas de precio. Cada consulta filtra por user_id: una persona jamás ve ni toca las alertas de otra
 * (control de acceso a nivel de objeto, OWASP API1). Si el id no es suyo, la respuesta es "no encontrada",
 * igual que si no existiera.
 */
const { sql } = require('../db');
const { uuid } = require('../lib/crypto');
const { E } = require('../lib/errors');
const { normText } = require('../lib/text');
const { MAX_ALERTS } = require('../domain/karma');
const { iso } = require('./dto');

const INSERT = sql`INSERT INTO alerts(id, user_id, keyword, keyword_norm, max_price_cents, active, created_at) VALUES (:id, :uid, :kw, :kwn, :max, 1, :now)`;
const LIST = sql`SELECT id, keyword, max_price_cents, active, created_at FROM alerts WHERE user_id = :uid ORDER BY created_at DESC, rowid DESC`;
const COUNT = sql`SELECT COUNT(*) AS c FROM alerts WHERE user_id = :uid`;
const GET_ONE = sql`SELECT id, keyword, max_price_cents, active, created_at FROM alerts WHERE id = :id AND user_id = :uid`;
const UPDATE = sql`UPDATE alerts SET active = COALESCE(:active, active), max_price_cents = COALESCE(:max, max_price_cents) WHERE id = :id AND user_id = :uid`;
const DELETE = sql`DELETE FROM alerts WHERE id = :id AND user_id = :uid`;
const ACTIVE_OF_USER = sql`SELECT id, keyword_norm, max_price_cents FROM alerts WHERE user_id = :uid AND active = 1`;
const MATCHES_FOR_OFFER = sql`SELECT a.id AS alert_id, a.user_id FROM alerts a
  WHERE a.active = 1 AND a.max_price_cents >= :price AND instr(:title_norm, a.keyword_norm) > 0
    AND a.user_id IS NOT :author
    AND NOT EXISTS (SELECT 1 FROM alert_hits h WHERE h.alert_id = a.id AND h.offer_id = :offer)
  ORDER BY a.created_at ASC, a.id ASC LIMIT 1000`;
const OFFERS_FOR_ALERT = sql`SELECT o.id, o.title, o.price_now_cents FROM offers o
  WHERE o.status = 'visible' AND o.author_id IS NOT :uid AND o.price_now_cents <= :max AND instr(o.title_norm, :kwn) > 0
    AND NOT EXISTS (SELECT 1 FROM alert_hits h WHERE h.alert_id = :alert AND h.offer_id = o.id)
  ORDER BY o.created_at DESC LIMIT 20`;
const HIT = sql`INSERT OR IGNORE INTO alert_hits(alert_id, offer_id) VALUES (:alert, :offer)`;

const dto = (r) => ({ id: r.id, keyword: r.keyword, max_price_cents: r.max_price_cents, active: Boolean(r.active), created_at: iso(r.created_at) });

function createAlerts({ db, now, notify }) {
  const notFound = () => E.notFound('alert_not_found', 'Alerta no encontrada');
  return {
    list(user) { return db.all(LIST, { uid: user.id }).map(dto); },

    create(user, { keyword, max_price_cents: max }) {
      return db.tx(() => {
        if (db.get(COUNT, { uid: user.id }).c >= MAX_ALERTS) throw E.conflict('alert_limit', `Máximo ${MAX_ALERTS} alertas por cuenta`);
        // La palabra clave normalizada debe tener al menos 2 letras/números: una vacía (p. ej. solo acentos sueltos)
        // coincidiría con TODAS las ofertas.
        const kwn = normText(keyword);
        if (kwn.replace(/[^\p{L}\p{N}]/gu, '').length < 2) throw E.badRequest('invalid_keyword', 'La palabra clave debe tener al menos 2 letras o números');
        const id = uuid();
        db.run(INSERT, { id, uid: user.id, kw: keyword, kwn, max, now: now() });
        return dto(db.get(GET_ONE, { id, uid: user.id }));
      });
    },

    update(user, id, { active, max_price_cents: max }) {
      return db.tx(() => {
        const res = db.run(UPDATE, { id, uid: user.id, active: active === undefined ? null : Number(active), max: max ?? null });
        if (res.changes !== 1) throw notFound();
        return dto(db.get(GET_ONE, { id, uid: user.id }));
      });
    },

    remove(user, id) {
      if (db.run(DELETE, { id, uid: user.id }).changes !== 1) throw notFound();
    },

    /** Al publicarse o aprobarse una oferta: avisa a quien tenga una alerta que coincida. */
    matchOffer(offer) {
      const rows = db.all(MATCHES_FOR_OFFER, { price: offer.price_now_cents, title_norm: offer.title_norm, author: offer.author_id ?? null, offer: offer.id });
      const notified = new Set();
      for (const r of rows) {
        db.run(HIT, { alert: r.alert_id, offer: offer.id });
        if (notified.has(r.user_id)) continue;
        notified.add(r.user_id);
        notify.send(r.user_id, 'alert', { offer_id: offer.id, title: offer.title, price_cents: offer.price_now_cents });
      }
      return notified.size;
    },

    /** "Probar ahora": busca coincidencias entre las ofertas ya publicadas, solo para las alertas de ESTE usuario. */
    check(user) {
      return db.tx(() => {
        let created = 0;
        for (const a of db.all(ACTIVE_OF_USER, { uid: user.id })) {
          for (const o of db.all(OFFERS_FOR_ALERT, { uid: user.id, max: a.max_price_cents, kwn: a.keyword_norm, alert: a.id })) {
            if (created >= 20) return created;
            db.run(HIT, { alert: a.id, offer: o.id });
            notify.send(user.id, 'alert', { offer_id: o.id, title: o.title, price_cents: o.price_now_cents });
            created++;
          }
        }
        return created;
      });
    },
  };
}

module.exports = { createAlerts };
