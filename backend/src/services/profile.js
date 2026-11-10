'use strict';
/**
 * Perfil, sesiones propias y notificaciones. Todas las consultas filtran por el usuario autenticado:
 * nadie puede leer ni modificar datos de otra persona (control de acceso por objeto).
 */
const { sql } = require('../db');
const { E } = require('../lib/errors');
const { encodeCursor, decodeCursor } = require('../lib/cursor');
const { selfDto, iso } = require('./dto');

const USER = sql`SELECT id, handle, email, mode, role, lang, karma, email_verified_at, created_at FROM users WHERE id = :id`;
const UPDATE = sql`UPDATE users SET lang = COALESCE(:lang, lang), mode = COALESCE(:mode, mode) WHERE id = :id`;
const NOTIFS = sql`SELECT id, kind, params, read_at, created_at FROM notifications
  WHERE user_id = :uid AND (:cc IS NULL OR (created_at, id) < (:cc, :ci))
  ORDER BY created_at DESC, id DESC LIMIT :lim`;
const UNREAD = sql`SELECT COUNT(*) AS c FROM notifications WHERE user_id = :uid AND read_at IS NULL`;
const MARK = sql`UPDATE notifications SET read_at = :t WHERE user_id = :uid AND read_at IS NULL`;

function safeParams(text) {
  try {
    const v = JSON.parse(text);
    return v && typeof v === 'object' && !Array.isArray(v) ? v : {};
  } catch { return {}; }
}

function createProfile({ db, now, sessions }) {
  return {
    update(user, { lang, mode }) {
      if (lang === undefined && mode === undefined) throw E.badRequest('empty_update', 'No hay nada que cambiar');
      db.run(UPDATE, { id: user.id, lang: lang ?? null, mode: mode ?? null });
      return selfDto(db.get(USER, { id: user.id }));
    },

    sessions(user) {
      return sessions.list(user.id).map((s) => ({ id: s.id, device_name: s.device_name ?? null, created_at: iso(s.created_at), current: s.id === user.session_id }));
    },

    revokeSession(user, id) {
      if (!sessions.revokeOwn(user.id, id)) throw E.notFound('session_not_found', 'Sesión no encontrada');
    },

    notifications(user, { cursor, limit }) {
      const c = decodeCursor(cursor, ['int', 'uuid']);
      const rows = db.all(NOTIFS, { uid: user.id, cc: c ? c[0] : null, ci: c ? c[1] : null, lim: limit + 1 });
      const more = rows.length > limit;
      if (more) rows.length = limit;
      const last = rows[rows.length - 1];
      return {
        unread: db.get(UNREAD, { uid: user.id }).c,
        items: rows.map((r) => ({ id: r.id, kind: r.kind, params: safeParams(r.params), read: r.read_at !== null, created_at: iso(r.created_at) })),
        next_cursor: more && last ? encodeCursor([last.created_at, last.id]) : null,
      };
    },

    markNotificationsRead(user) { return { updated: db.run(MARK, { uid: user.id, t: now() }).changes }; },
  };
}

module.exports = { createProfile };
