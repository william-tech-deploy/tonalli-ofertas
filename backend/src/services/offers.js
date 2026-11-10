'use strict';
/**
 * Ofertas: listado, detalle, publicación, votos, comentarios, reportes, historial de precios,
 * fotos y búsqueda por código de barras.
 *
 * Reglas que se aplican AQUÍ (servidor) y que el cliente no puede saltarse:
 *  - Precios en centavos enteros; el actual debe ser menor que el anterior.
 *  - Evidencia obligatoria (enlace seguro o foto saneada). El enlace nunca se abre (no hay SSRF).
 *  - Tope de ofertas por cuenta cada 24 h y detección de duplicados.
 *  - Un voto por persona y oferta; no se vota la propia; el peso del voto depende del nivel.
 *  - Reportes: solo cuentan los de cuentas con antigüedad; con 3 reportes la oferta se oculta sola.
 *  - Control de acceso por objeto: una oferta no publicada solo la ve su autor o un moderador.
 */
const { sql } = require('../db');
const { uuid } = require('../lib/crypto');
const { E } = require('../lib/errors');
const { normText } = require('../lib/text');
const { analyzeUrl } = require('../domain/url');
const { isValidEAN, toEAN13 } = require('../domain/ean');
const { priceVerdict } = require('../domain/verdict');
const { voteWeight, DAILY_OFFER_CAP, AUTOHIDE_REPORTS, REPORTER_MIN_KARMA } = require('../domain/karma');
const { sanitizeJpeg, InvalidImage } = require('../lib/jpeg');
const { encodeCursor, decodeCursor } = require('../lib/cursor');
const { iso } = require('./dto');

const DAY = 86_400_000;
const HOUR = 3_600_000;
const HISTORY_DAYS = 56;

const CATEGORIES = sql`SELECT id, name, is_base FROM categories ORDER BY is_base DESC, created_at ASC, id ASC`;
const CATEGORY = sql`SELECT id FROM categories WHERE id = :id`;

const GET_ONE = sql`SELECT o.id, o.author_id, o.category_id, o.title, o.store, o.product_key, o.store_norm, o.ean, o.price_now_cents, o.price_old_cents,
    o.link_url, o.photo_id, o.status, o.up, o.down, o.temperature, o.created_at, o.visible_at, u.handle AS author_handle, v.value AS my_vote
  FROM offers o LEFT JOIN users u ON u.id = o.author_id LEFT JOIN votes v ON v.offer_id = o.id AND v.user_id = :me
  WHERE o.id = :id`;

const LIST_HOT = sql`SELECT o.id, o.author_id, o.category_id, o.title, o.store, o.product_key, o.store_norm, o.ean, o.price_now_cents, o.price_old_cents,
    o.link_url, o.photo_id, o.status, o.up, o.down, o.temperature, o.created_at, o.visible_at, u.handle AS author_handle, v.value AS my_vote
  FROM offers o LEFT JOIN users u ON u.id = o.author_id LEFT JOIN votes v ON v.offer_id = o.id AND v.user_id = :me
  WHERE o.status = 'visible'
    AND (:cat IS NULL OR o.category_id = :cat)
    AND (:q IS NULL OR instr(o.title_norm, :q) > 0 OR instr(o.store_norm, :q) > 0)
    AND (:ct IS NULL OR (o.temperature, o.created_at, o.id) < (:ct, :cc, :ci))
  ORDER BY o.temperature DESC, o.created_at DESC, o.id DESC
  LIMIT :lim`;

const LIST_NEW = sql`SELECT o.id, o.author_id, o.category_id, o.title, o.store, o.product_key, o.store_norm, o.ean, o.price_now_cents, o.price_old_cents,
    o.link_url, o.photo_id, o.status, o.up, o.down, o.temperature, o.created_at, o.visible_at, u.handle AS author_handle, v.value AS my_vote
  FROM offers o LEFT JOIN users u ON u.id = o.author_id LEFT JOIN votes v ON v.offer_id = o.id AND v.user_id = :me
  WHERE o.status = 'visible'
    AND (:cat IS NULL OR o.category_id = :cat)
    AND (:q IS NULL OR instr(o.title_norm, :q) > 0 OR instr(o.store_norm, :q) > 0)
    AND (:cc IS NULL OR (o.created_at, o.id) < (:cc, :ci))
  ORDER BY o.created_at DESC, o.id DESC
  LIMIT :lim`;

const BY_EAN = sql`SELECT o.id, o.author_id, o.category_id, o.title, o.store, o.product_key, o.store_norm, o.ean, o.price_now_cents, o.price_old_cents,
    o.link_url, o.photo_id, o.status, o.up, o.down, o.temperature, o.created_at, o.visible_at, u.handle AS author_handle, v.value AS my_vote
  FROM offers o LEFT JOIN users u ON u.id = o.author_id LEFT JOIN votes v ON v.offer_id = o.id AND v.user_id = :me
  WHERE o.status = 'visible' AND o.ean = :ean
  ORDER BY o.price_now_cents ASC, o.created_at DESC, o.id DESC
  LIMIT 50`;

const HISTORY = sql`SELECT pp.price_cents AS cents, pp.observed_at, pp.user_id, pp.offer_id
  FROM price_points pp LEFT JOIN offers o2 ON o2.id = pp.offer_id
  WHERE pp.product_key = :pk AND pp.store_norm = :sn AND pp.observed_at <= :at AND pp.observed_at >= :from
    AND (pp.offer_id IS NULL OR o2.status = 'visible')
  ORDER BY pp.observed_at ASC LIMIT 200`;
const PP_RECENT = sql`SELECT 1 AS x FROM price_points WHERE user_id = :uid AND product_key = :pk AND store_norm = :sn AND observed_at > :since LIMIT 1`;
const PP_INSERT = sql`INSERT INTO price_points(product_key, store_norm, price_cents, observed_at, user_id, offer_id) VALUES (:pk, :sn, :price, :at, :uid, :offer)`;

const COUNT_DAILY = sql`SELECT COUNT(*) AS c, MIN(created_at) AS oldest FROM offers WHERE author_id = :uid AND created_at > :since`;
const DUPLICATE = sql`SELECT 1 AS x FROM offers WHERE author_id = :uid AND product_key = :pk AND store_norm = :sn AND price_now_cents = :price AND created_at > :since AND status <> 'removed' LIMIT 1`;
const INSERT_OFFER = sql`INSERT INTO offers(id, author_id, category_id, title, title_norm, store, store_norm, product_key, ean, price_now_cents, price_old_cents,
    link_url, photo_id, status, up, down, temperature, created_at, visible_at)
  VALUES (:id, :uid, :cat, :title, :tn, :store, :sn, :pk, :ean, :pnow, :pold, :link, NULL, :status, 0, 0, 0, :t, :vis)`;
const INSERT_PHOTO = sql`INSERT INTO photos(id, offer_id, owner_id, data, width, height, created_at) VALUES (:id, :offer, :uid, :data, :w, :h, :t)`;
const SET_PHOTO = sql`UPDATE offers SET photo_id = :pid WHERE id = :id`;
const PHOTO = sql`SELECT p.data, o.author_id, o.status FROM offers o JOIN photos p ON p.id = o.photo_id WHERE o.id = :id`;

const OFFER_MIN = sql`SELECT id, author_id, title, status FROM offers WHERE id = :id`;
const VOTE_GET = sql`SELECT value FROM votes WHERE offer_id = :offer AND user_id = :uid`;
const VOTE_UPSERT = sql`INSERT INTO votes(offer_id, user_id, value, weight, created_at) VALUES (:offer, :uid, :value, :weight, :t)
  ON CONFLICT(offer_id, user_id) DO UPDATE SET value = excluded.value, weight = excluded.weight, created_at = excluded.created_at`;
const VOTE_DEL = sql`DELETE FROM votes WHERE offer_id = :offer AND user_id = :uid`;
const VOTE_SUM = sql`SELECT COALESCE(SUM(CASE WHEN value = 1 THEN weight END), 0) AS up, COALESCE(SUM(CASE WHEN value = -1 THEN weight END), 0) AS down FROM votes WHERE offer_id = :offer`;
const SET_TEMP = sql`UPDATE offers SET up = :up, down = :down, temperature = :temp WHERE id = :offer`;

const COMMENT_INSERT = sql`INSERT INTO comments(id, offer_id, user_id, body, created_at) VALUES (:id, :offer, :uid, :body, :t)`;
const COMMENT_LIST = sql`SELECT c.id, c.body, c.created_at, u.handle FROM comments c LEFT JOIN users u ON u.id = c.user_id
  WHERE c.offer_id = :offer AND (:cc IS NULL OR (c.created_at, c.id) > (:cc, :ci))
  ORDER BY c.created_at ASC, c.id ASC LIMIT :lim`;

const REPORT_INSERT = sql`INSERT OR IGNORE INTO reports(id, offer_id, reporter_id, reason, detail, status, created_at) VALUES (:id, :offer, :uid, :reason, :detail, 'open', :t)`;
const REPORT_COUNTED = sql`SELECT COUNT(*) AS c FROM reports r JOIN users u ON u.id = r.reporter_id
  WHERE r.offer_id = :offer AND r.status = 'open' AND u.created_at <= r.created_at - :minage AND u.karma >= :minkarma`;
const HIDE = sql`UPDATE offers SET status = 'hidden' WHERE id = :id AND status = 'visible'`;

const dayAgo = (t) => t - DAY;
const round1 = (n) => Math.round(n * 10) / 10;
const isMod = (u) => u.role === 'moderador' || u.role === 'admin';

function createOffers({ db, config, now, points, notify, alerts, audit }) {
  const notFound = () => E.notFound('offer_not_found', 'Oferta no encontrada');

  /** Veredicto de rebaja calculado con los precios que la comunidad observó para ese producto y tienda. */
  function verdictOf(row) {
    const at = row.created_at;
    const rows = db.all(HISTORY, { pk: row.product_key, sn: row.store_norm, at, from: at - HISTORY_DAYS * DAY });
    // El historial que se MUESTRA incluye todo; el que se usa para JUZGAR excluye lo que el propio autor pudo sembrar
    // (sus precios y el de esta oferta) y exige al menos 3 fuentes distintas con más de 10 días: una sola cuenta no basta.
    const toPoint = (r) => ({ daysAgo: (at - r.observed_at) / DAY, cents: r.cents });
    const history = rows.map(toPoint);
    const usable = rows.filter((r) => r.offer_id !== row.id && (r.user_id === null || r.user_id !== row.author_id));
    const sources = new Set(usable.filter((r) => (at - r.observed_at) / DAY > 10).map((r) => r.user_id ?? `o:${r.offer_id ?? r.observed_at}`));
    const verdict = sources.size >= 3 ? priceVerdict(usable.map(toPoint), row.price_old_cents, row.price_now_cents) : { kind: 'none' };
    return { history, verdict };
  }

  function dto(row, viewer, verdict) {
    const mine = row.author_id !== null && row.author_id === viewer.id;
    return {
      id: row.id,
      category_id: row.category_id,
      title: row.title,
      store: row.store,
      price_now_cents: row.price_now_cents,
      price_old_cents: row.price_old_cents,
      discount_pct: Math.round(((row.price_old_cents - row.price_now_cents) / row.price_old_cents) * 100),
      ean: row.ean,
      link_url: row.link_url,
      has_photo: Boolean(row.photo_id),
      temperature: row.temperature,
      votes_up: round1(row.up),
      votes_down: round1(row.down),
      my_vote: row.my_vote ?? 0,
      mine,
      author: row.author_handle ?? null,
      created_at: iso(row.created_at),
      verdict: verdict ?? verdictOf(row).verdict,
      ...(mine || isMod(viewer) ? { status: row.status } : {}),
    };
  }

  /** Una oferta no publicada solo existe para su autor y para los moderadores (para todos los demás: 404). */
  function visibleTo(row, viewer) {
    return row && (row.status === 'visible' || (row.author_id !== null && row.author_id === viewer.id) || isMod(viewer));
  }

  /** Efectos de que una oferta pase a ser visible: precio observado + avisos a alertas. */
  function onVisible(o) {
    if (o.author_id && !db.get(PP_RECENT, { uid: o.author_id, pk: o.product_key, sn: o.store_norm, since: dayAgo(o.created_at) })) {
      // Un usuario aporta como máximo un precio por producto y tienda cada 24 h: no puede inundar el historial.
      db.run(PP_INSERT, { pk: o.product_key, sn: o.store_norm, price: o.price_now_cents, at: o.created_at, uid: o.author_id, offer: o.id });
    } else if (!o.author_id) {
      db.run(PP_INSERT, { pk: o.product_key, sn: o.store_norm, price: o.price_now_cents, at: o.created_at, uid: null, offer: o.id });
    }
    alerts.matchOffer(o);
  }

  return {
    categories() { return db.all(CATEGORIES).map((c) => ({ id: c.id, name: c.name, is_base: Boolean(c.is_base) })); },

    list(viewer, { category, sort, q, cursor, limit }) {
      const qn = q ? normText(q) : null;
      const base = { me: viewer.id, cat: category ?? null, q: qn, lim: limit + 1 };
      let rows;
      if (sort === 'new') {
        const c = decodeCursor(cursor, ['int', 'uuid']);
        rows = db.all(LIST_NEW, { ...base, cc: c ? c[0] : null, ci: c ? c[1] : null });
      } else {
        const c = decodeCursor(cursor, ['int', 'int', 'uuid']);
        rows = db.all(LIST_HOT, { ...base, ct: c ? c[0] : null, cc: c ? c[1] : null, ci: c ? c[2] : null });
      }
      const more = rows.length > limit;
      if (more) rows.length = limit;
      const last = rows[rows.length - 1];
      const next = more && last ? encodeCursor(sort === 'new' ? [last.created_at, last.id] : [last.temperature, last.created_at, last.id]) : null;
      return { items: rows.map((r) => dto(r, viewer)), next_cursor: next };
    },

    get(viewer, id) {
      const row = db.get(GET_ONE, { me: viewer.id, id });
      if (!visibleTo(row, viewer)) throw notFound();
      return dto(row, viewer);
    },

    byEan(viewer, code) {
      const ean = isValidEAN(code) ? toEAN13(code) : null;
      if (!ean) throw E.badRequest('invalid_ean', 'Código de barras inválido (usa EAN-13 o UPC-A)');
      const rows = db.all(BY_EAN, { me: viewer.id, ean });
      return { ean, items: rows.map((r) => dto(r, viewer)) };
    },

    priceHistory(viewer, id) {
      const row = db.get(GET_ONE, { me: viewer.id, id });
      if (!visibleTo(row, viewer)) throw notFound();
      const { history, verdict } = verdictOf(row);
      return {
        window_days: HISTORY_DAYS,
        points: history.map((h) => ({ days_ago: Math.round(h.daysAgo * 10) / 10, price_cents: h.cents })),
        declared_old_cents: row.price_old_cents,
        price_now_cents: row.price_now_cents,
        verdict,
      };
    },

    photo(viewer, id) {
      const row = db.get(PHOTO, { id });
      const ok = row && (row.status === 'visible' || (row.author_id !== null && row.author_id === viewer.id) || isMod(viewer));
      if (!ok) throw E.notFound('photo_not_found', 'Foto no encontrada');
      return Buffer.from(row.data);
    },

    create(user, input) {
      const t = now();
      if (input.price_now_cents >= input.price_old_cents) throw E.badRequest('invalid_prices', 'El precio actual debe ser menor que el anterior');
      if (!input.link_url && !input.photo) throw E.badRequest('evidence_required', 'Agrega una foto o un enlace como evidencia');

      let link = null;
      if (input.link_url) {
        const a = analyzeUrl(input.link_url);
        if (!a.ok) throw E.badRequest('unsafe_link', 'El enlace no es seguro', { flags: a.flags });
        link = a.url;
      }
      let ean = null;
      if (input.ean) {
        ean = isValidEAN(input.ean) ? toEAN13(input.ean) : null;
        if (!ean) throw E.badRequest('invalid_ean', 'Código de barras inválido (usa EAN-13 o UPC-A)');
      }
      let photo = null;
      if (input.photo) {
        try { photo = sanitizeJpeg(Buffer.from(input.photo, 'base64')); }
        catch (err) {
          if (err instanceof InvalidImage) throw E.badRequest('invalid_image', 'La foto no es un JPEG válido o es demasiado grande');
          throw err;
        }
      }
      if (!db.get(CATEGORY, { id: input.category_id })) throw E.badRequest('invalid_category', 'La categoría no existe');

      const titleNorm = normText(input.title);
      const storeNorm = normText(input.store);
      const productKey = ean ? `ean:${ean}` : `t:${titleNorm}`;
      const id = uuid();

      return db.tx(() => {
        const daily = db.get(COUNT_DAILY, { uid: user.id, since: t - DAY });
        if (daily.c >= DAILY_OFFER_CAP) throw E.tooMany(((daily.oldest + DAY) - t) / 1000);
        if (db.get(DUPLICATE, { uid: user.id, pk: productKey, sn: storeNorm, price: input.price_now_cents, since: t - DAY })) {
          throw E.conflict('duplicate_offer', 'Ya publicaste esta oferta hoy');
        }
        const visible = !config.requireApproval;
        db.run(INSERT_OFFER, {
          id, uid: user.id, cat: input.category_id, title: input.title, tn: titleNorm, store: input.store, sn: storeNorm, pk: productKey, ean,
          pnow: input.price_now_cents, pold: input.price_old_cents, link, status: visible ? 'visible' : 'pending', t, vis: visible ? t : null,
        });
        if (photo) {
          const pid = uuid();
          db.run(INSERT_PHOTO, { id: pid, offer: id, uid: user.id, data: photo.bytes, w: photo.width, h: photo.height, t });
          db.run(SET_PHOTO, { pid, id });
        }
        points.award(user.id, 'publish', id);
        if (visible) onVisible({ id, author_id: user.id, title: input.title, title_norm: titleNorm, store_norm: storeNorm, product_key: productKey, price_now_cents: input.price_now_cents, created_at: t });
        return dto(db.get(GET_ONE, { me: user.id, id }), user);
      });
    },

    /** Voto +1 / -1 (0 lo retira). El peso depende del nivel de quien vota; las cuentas nuevas pesan menos. */
    vote(user, id, value) {
      return db.tx(() => {
        const o = db.get(OFFER_MIN, { id });
        if (!o || o.status !== 'visible') throw notFound();
        if (o.author_id !== null && o.author_id === user.id) throw E.forbidden('own_offer', 'No puedes votar tu propia oferta');
        const prev = db.get(VOTE_GET, { offer: id, uid: user.id });
        if (value === 0) {
          if (prev) db.run(VOTE_DEL, { offer: id, uid: user.id });
        } else {
          db.run(VOTE_UPSERT, { offer: id, uid: user.id, value, weight: voteWeight(user.karma), t: now() });
          if (!prev) points.award(user.id, 'vote', id);
        }
        const s = db.get(VOTE_SUM, { offer: id });
        db.run(SET_TEMP, { offer: id, up: s.up, down: s.down, temp: Math.round(s.up - s.down) });
        return dto(db.get(GET_ONE, { me: user.id, id }), user);
      });
    },

    addComment(user, id, { body }) {
      return db.tx(() => {
        const o = db.get(OFFER_MIN, { id });
        if (!o || o.status !== 'visible') throw notFound();
        const cid = uuid();
        const t = now();
        db.run(COMMENT_INSERT, { id: cid, offer: id, uid: user.id, body, t });
        const ownOffer = o.author_id !== null && o.author_id === user.id;
        if (!ownOffer) points.award(user.id, 'comment'); // comentar la propia oferta no da karma
        if (o.author_id && o.author_id !== user.id) notify.send(o.author_id, 'reply', { offer_id: id, title: o.title, by: user.handle });
        return { id: cid, body, author: user.handle, created_at: iso(t) };
      });
    },

    comments(viewer, id, { cursor, limit }) {
      const o = db.get(OFFER_MIN, { id });
      if (!visibleTo(o, viewer)) throw notFound();
      const c = decodeCursor(cursor, ['int', 'uuid']);
      const rows = db.all(COMMENT_LIST, { offer: id, cc: c ? c[0] : null, ci: c ? c[1] : null, lim: limit + 1 });
      const more = rows.length > limit;
      if (more) rows.length = limit;
      const last = rows[rows.length - 1];
      return {
        items: rows.map((r) => ({ id: r.id, body: r.body, author: r.handle ?? null, created_at: iso(r.created_at) })),
        next_cursor: more && last ? encodeCursor([last.created_at, last.id]) : null,
      };
    },

    report(user, id, { reason, detail }) {
      return db.tx(() => {
        const o = db.get(OFFER_MIN, { id });
        if (!o || o.status !== 'visible') throw notFound();
        if (o.author_id !== null && o.author_id === user.id) throw E.forbidden('own_offer', 'No puedes reportar tu propia oferta');
        const t = now();
        const res = db.run(REPORT_INSERT, { id: uuid(), offer: id, uid: user.id, reason, detail: detail ?? null, t });
        if (res.changes !== 1) throw E.conflict('already_reported', 'Ya reportaste esta oferta');
        // Solo cuentan los reportes de cuentas con antigüedad: frena el "reporte masivo" con cuentas desechables.
        const counted = db.get(REPORT_COUNTED, { offer: id, minage: config.newAccountHours * HOUR, minkarma: REPORTER_MIN_KARMA }).c;
        if (counted >= AUTOHIDE_REPORTS && db.run(HIDE, { id }).changes === 1) {
          notify.send(o.author_id, 'hidden', { offer_id: id, title: o.title });
          audit.log('offer_auto_hidden', { detail: { offer: id, reports: counted } });
        }
        return { received: true };
      });
    },

    onVisible,
    visibleTo,
  };
}

module.exports = { createOffers };
