'use strict';
/**
 * Moderación. El rol "moderador" o "admin" NO se puede obtener por la API (solo con scripts/make-moderator.js
 * en el servidor): así ningún error de validación puede convertir a alguien en moderador.
 * Toda acción deja huella en la bitácora de auditoría (quién, qué oferta, cuándo).
 */
const { sql } = require('../db');
const { E } = require('../lib/errors');
const { iso } = require('./dto');

const PENDING = sql`SELECT o.id, o.title, o.store, o.category_id, o.price_now_cents, o.price_old_cents, o.created_at, u.handle AS author
  FROM offers o LEFT JOIN users u ON u.id = o.author_id WHERE o.status = 'pending' ORDER BY o.created_at ASC LIMIT 50`;
const REPORTED = sql`SELECT o.id, o.title, o.store, o.status, COUNT(r.id) AS open_reports, GROUP_CONCAT(DISTINCT r.reason) AS reasons, MAX(r.created_at) AS last_report
  FROM offers o JOIN reports r ON r.offer_id = o.id AND r.status = 'open'
  WHERE o.status IN ('visible', 'hidden') GROUP BY o.id ORDER BY open_reports DESC, last_report DESC LIMIT 50`;
const FULL = sql`SELECT id, author_id, title, title_norm, store_norm, product_key, price_now_cents, created_at, status FROM offers WHERE id = :id`;
const SET_STATUS = sql`UPDATE offers SET status = :to, visible_at = COALESCE(:vis, visible_at) WHERE id = :id AND status = :from`;
const SET_REMOVED = sql`UPDATE offers SET status = 'removed' WHERE id = :id AND status <> 'removed'`;
const CLOSE_REPORTS = sql`UPDATE reports SET status = :status, resolved_at = :t, resolved_by = :mod WHERE offer_id = :id AND status = 'open'`;

function createModeration({ db, now, offers, notify, audit, points }) {
  function load(mod, id) {
    const o = db.get(FULL, { id });
    if (!o) throw E.notFound('offer_not_found', 'Oferta no encontrada');
    if (o.author_id && o.author_id === mod.id) throw E.forbidden('own_offer', 'No puedes moderar tu propia oferta');
    return o;
  }
  const result = (id, status) => ({ id, status });

  return {
    queue() {
      return {
        pending: db.all(PENDING).map((r) => ({ id: r.id, title: r.title, store: r.store, category_id: r.category_id, price_now_cents: r.price_now_cents, price_old_cents: r.price_old_cents, author: r.author ?? null, created_at: iso(r.created_at) })),
        reported: db.all(REPORTED).map((r) => ({ id: r.id, title: r.title, store: r.store, status: r.status, open_reports: r.open_reports, reasons: String(r.reasons || '').split(',').filter(Boolean) })),
      };
    },

    approve(mod, id, ip) {
      return db.tx(() => {
        const o = load(mod, id);
        if (db.run(SET_STATUS, { id, from: 'pending', to: 'visible', vis: now() }).changes !== 1) throw E.conflict('not_pending', 'La oferta no está pendiente');
        offers.onVisible(o);
        audit.log('mod_approve', { userId: mod.id, ip, detail: { offer: id } });
        return result(id, 'visible');
      });
    },

    restore(mod, id, ip) {
      return db.tx(() => {
        load(mod, id);
        if (db.run(SET_STATUS, { id, from: 'hidden', to: 'visible', vis: null }).changes !== 1) throw E.conflict('not_hidden', 'La oferta no está oculta');
        db.run(CLOSE_REPORTS, { id, status: 'dismissed', t: now(), mod: mod.id });
        audit.log('mod_restore', { userId: mod.id, ip, detail: { offer: id } });
        return result(id, 'visible');
      });
    },

    remove(mod, id, ip) {
      return db.tx(() => {
        const o = load(mod, id);
        if (db.run(SET_REMOVED, { id }).changes !== 1) throw E.conflict('already_removed', 'La oferta ya fue eliminada');
        db.run(CLOSE_REPORTS, { id, status: 'upheld', t: now(), mod: mod.id });
        if (o.author_id) points.revoke(o.author_id, 'publish', id); // una oferta eliminada no deja karma
        notify.send(o.author_id, 'hidden', { offer_id: id, title: o.title });
        audit.log('mod_remove', { userId: mod.id, ip, detail: { offer: id } });
        return result(id, 'removed');
      });
    },
  };
}

module.exports = { createModeration };
