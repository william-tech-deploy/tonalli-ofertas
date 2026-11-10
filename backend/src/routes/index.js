'use strict';
/**
 * Tabla de rutas. Cada ruta DECLARA lo que exige y el servidor (app.js) lo aplica ANTES de llamar al manejador:
 *   auth      false = pública (por defecto exige sesión)
 *   role      roles permitidos (moderación)
 *   mode      'cazador' = solo quien activó el modo Cazador
 *   verified  true = correo verificado
 *   minAge    true = la cuenta debe tener la antigüedad mínima (frena cuentas desechables)
 *   ip/user   [nombre, máximo, ventana_ms] límite por IP o por usuario
 *   body      esquema ESTRICTO del cuerpo JSON (sin él, la ruta rechaza cualquier cuerpo)
 *   maxBody   tope de bytes del cuerpo
 *   query     esquema de los parámetros de la URL (sin él, la ruta rechaza cualquier parámetro)
 * Una ruta nueva que olvide declarar algo queda en el modo MÁS restrictivo, no en el más permisivo.
 */
const { object, string, int, oneOf, optional, email, base64 } = require('../lib/schema');
const { TOKEN_RE } = require('../lib/crypto');
const { selfDto } = require('../services/dto');

const MIN = 60_000;
const HOUR = 60 * MIN;
const DAY = 24 * HOUR;

const LANG = oneOf(['es', 'en', 'pt', 'nah']);
const PASSWORD = string({ min: 1, max: 128, raw: true });
const token = (re) => string({ min: 47, max: 47, pattern: re, raw: true });
const SLUG = string({ min: 2, max: 40, pattern: /^[a-z0-9][a-z0-9-]*$/ });
const CURSOR = string({ max: 200, clean: false, pattern: /^[A-Za-z0-9_-]+$/ });
const noContent = { status: 204 };

function registerAuth(router, s) {
  const { auth } = s;
  router.add('POST', '/v1/auth/register', {
    auth: false, ip: ['register', 20, HOUR], maxBody: 2048,
    body: object({ email: email(), handle: string({ min: 3, max: 20, pattern: /^[A-Za-z0-9_.]+$/ }), password: PASSWORD, lang: optional(LANG, 'es') }),
  }, async (c) => {
    await auth.register(c.body, c.ip);
    // Misma respuesta exista o no el correo (anti-enumeración).
    return { status: 202, body: { status: 'accepted', message: 'Si los datos son válidos, recibirás un correo para confirmar tu cuenta' } };
  });

  router.add('POST', '/v1/auth/verify-email', { auth: false, ip: ['verify', 30, HOUR], maxBody: 512, body: object({ token: token(TOKEN_RE.once) }) },
    (c) => { auth.verifyEmail(c.body.token, c.ip); return noContent; });

  router.add('POST', '/v1/auth/login', {
    auth: false, ip: ['login', 60, 15 * MIN], maxBody: 2048,
    body: object({ email: email(), password: PASSWORD, device_name: optional(string({ min: 1, max: 60 })) }),
  }, async (c) => ({ status: 200, body: await auth.login(c.body, c.ip) }));

  router.add('POST', '/v1/auth/refresh', { auth: false, ip: ['refresh', 120, 15 * MIN], maxBody: 512, body: object({ refresh_token: token(TOKEN_RE.refresh) }) },
    (c) => ({ status: 200, body: auth.refresh(c.body.refresh_token, c.ip) }));

  router.add('POST', '/v1/auth/forgot-password', { auth: false, ip: ['forgot', 10, HOUR], maxBody: 512, body: object({ email: email() }) },
    (c) => { auth.forgotPassword(c.body, c.ip); return { status: 202, body: { status: 'accepted' } }; });

  router.add('POST', '/v1/auth/reset-password', { auth: false, ip: ['reset', 20, HOUR], maxBody: 1024, body: object({ token: token(TOKEN_RE.once), new_password: PASSWORD }) },
    async (c) => { await auth.resetPassword(c.body, c.ip); return noContent; });

  router.add('POST', '/v1/auth/logout', {}, (c) => { auth.logout(c.user.session_id, c.user.id, c.ip); return noContent; });
  router.add('POST', '/v1/auth/logout-all', {}, (c) => { auth.logoutAll(c.user.id, c.ip); return noContent; });
  router.add('POST', '/v1/auth/resend-verification', { user: ['resend', 10, HOUR] }, (c) => { auth.resendVerification(c.user, c.ip); return { status: 202, body: { status: 'accepted' } }; });
  router.add('POST', '/v1/auth/change-password', { maxBody: 1024, user: ['chpw', 10, HOUR], body: object({ current_password: PASSWORD, new_password: PASSWORD }) },
    async (c) => { await auth.changePassword(c.user, c.user.session_id, c.body, c.ip); return noContent; });
}

function registerMe(router, s) {
  const { profile, auth } = s;
  router.add('GET', '/v1/me', {}, (c) => ({ status: 200, body: selfDto(c.user) }));
  router.add('PATCH', '/v1/me', { maxBody: 512, user: ['patchme', 60, HOUR], body: object({ lang: optional(LANG), mode: optional(oneOf(['rastreador', 'cazador'])) }) },
    (c) => ({ status: 200, body: profile.update(c.user, c.body) }));
  router.add('DELETE', '/v1/me', { maxBody: 512, user: ['delme', 5, HOUR], body: object({ password: PASSWORD }) },
    async (c) => { await auth.deleteAccount(c.user, c.body, c.ip); return noContent; });
  router.add('GET', '/v1/me/sessions', {}, (c) => ({ status: 200, body: { items: profile.sessions(c.user) } }));
  router.add('DELETE', '/v1/me/sessions/:id', { user: ['delsess', 30, HOUR] }, (c) => { profile.revokeSession(c.user, c.params.id); return noContent; });
  router.add('GET', '/v1/notifications', { query: object({ cursor: optional(CURSOR), limit: optional(int({ min: 1, max: 50, coerce: true }), 20) }) },
    (c) => ({ status: 200, body: profile.notifications(c.user, c.query) }));
  router.add('POST', '/v1/notifications/read', { user: ['notifread', 60, HOUR] }, (c) => ({ status: 200, body: profile.markNotificationsRead(c.user) }));
}

function registerOffers(router, s) {
  const { offers } = s;
  router.add('GET', '/v1/categories', {}, () => ({ status: 200, body: { items: offers.categories() } }));

  router.add('GET', '/v1/offers', {
    query: object({
      category: optional(SLUG), sort: optional(oneOf(['hot', 'new']), 'hot'), q: optional(string({ min: 2, max: 60 })),
      cursor: optional(CURSOR), limit: optional(int({ min: 1, max: 50, coerce: true }), 20),
    }),
  }, (c) => ({ status: 200, body: offers.list(c.user, c.query) }));

  router.add('POST', '/v1/offers', {
    mode: 'cazador', verified: true, minAge: true, user: ['offer', 10, HOUR], maxBody: 480 * 1024,
    body: object({
      category_id: SLUG,
      title: string({ min: 3, max: 100 }),
      store: string({ min: 2, max: 60 }),
      price_old_cents: int({ min: 2, max: 10_000_000 }),
      price_now_cents: int({ min: 1, max: 10_000_000 }),
      ean: optional(string({ min: 8, max: 14, pattern: /^\d+$/, clean: false })),
      link_url: optional(string({ min: 4, max: 500, clean: false })),
      photo: optional(base64({ maxBytes: 300 * 1024 })),
    }),
  }, (c) => ({ status: 201, body: offers.create(c.user, c.body) }));

  router.add('GET', '/v1/offers/:id', {}, (c) => ({ status: 200, body: offers.get(c.user, c.params.id) }));
  router.add('GET', '/v1/offers/:id/price-history', {}, (c) => ({ status: 200, body: offers.priceHistory(c.user, c.params.id) }));
  router.add('GET', '/v1/offers/:id/photo', {}, (c) => ({
    status: 200, raw: offers.photo(c.user, c.params.id),
    headers: { 'Content-Type': 'image/jpeg', 'Cache-Control': 'private, max-age=300', 'Content-Security-Policy': "default-src 'none'; sandbox" },
  }));
  router.add('PUT', '/v1/offers/:id/vote', { verified: true, minAge: true, user: ['vote', 120, HOUR], maxBody: 128, body: object({ value: int({ min: -1, max: 1 }) }) },
    (c) => ({ status: 200, body: offers.vote(c.user, c.params.id, c.body.value) }));
  router.add('GET', '/v1/offers/:id/comments', { query: object({ cursor: optional(CURSOR), limit: optional(int({ min: 1, max: 50, coerce: true }), 30) }) },
    (c) => ({ status: 200, body: offers.comments(c.user, c.params.id, c.query) }));
  router.add('POST', '/v1/offers/:id/comments', {
    mode: 'cazador', verified: true, minAge: true, user: ['comment', 30, HOUR], maxBody: 1536,
    body: object({ body: string({ min: 1, max: 300, multiline: true }) }),
  }, (c) => ({ status: 201, body: offers.addComment(c.user, c.params.id, c.body) }));
  router.add('POST', '/v1/offers/:id/reports', {
    verified: true, user: ['report', 20, DAY], maxBody: 1024,
    body: object({ reason: oneOf(['spam', 'link', 'illegal', 'fake', 'other']), detail: optional(string({ min: 1, max: 200 })) }),
  }, (c) => ({ status: 202, body: offers.report(c.user, c.params.id, c.body) }));

  router.add('GET', '/v1/products/:ean', {}, (c) => ({ status: 200, body: offers.byEan(c.user, c.params.ean) }));
}

function registerAlerts(router, s) {
  const { alerts } = s;
  const MAX = int({ min: 1, max: 10_000_000 });
  router.add('GET', '/v1/alerts', {}, (c) => ({ status: 200, body: { items: alerts.list(c.user) } }));
  router.add('POST', '/v1/alerts', { verified: true, user: ['alertw', 60, HOUR], maxBody: 512, body: object({ keyword: string({ min: 2, max: 40 }), max_price_cents: MAX }) },
    (c) => ({ status: 201, body: alerts.create(c.user, c.body) }));
  router.add('PATCH', '/v1/alerts/:id', {
    user: ['alertw', 60, HOUR], maxBody: 512,
    body: object({ active: optional(oneOfBool()), max_price_cents: optional(MAX) }),
  }, (c) => ({ status: 200, body: alerts.update(c.user, c.params.id, c.body) }));
  router.add('DELETE', '/v1/alerts/:id', { user: ['alertw', 60, HOUR] }, (c) => { alerts.remove(c.user, c.params.id); return noContent; });
  router.add('POST', '/v1/alerts/check', { user: ['alertcheck', 30, HOUR] }, (c) => ({ status: 200, body: { new_notifications: alerts.check(c.user) } }));
}

function oneOfBool() {
  return (v, path, issues) => {
    if (typeof v === 'boolean') return v;
    issues.push({ path, code: 'type' });
    return undefined;
  };
}

function registerProposals(router, s) {
  const { proposals } = s;
  router.add('GET', '/v1/category-proposals', {}, (c) => ({ status: 200, body: proposals.list(c.user) }));
  router.add('POST', '/v1/category-proposals', {
    mode: 'cazador', verified: true, minAge: true, user: ['propose', 5, DAY], maxBody: 1024,
    body: object({
      name: string({ min: 3, max: 30, pattern: /^[\p{L}\p{N}][\p{L}\p{N} &-]{1,28}[\p{L}\p{N}]$/u }),
      why: optional(string({ min: 1, max: 200 })),
    }),
  }, (c) => ({ status: 201, body: proposals.propose(c.user, c.body) }));
  router.add('PUT', '/v1/category-proposals/:id/vote', { verified: true, minAge: true, user: ['catvote', 60, HOUR] },
    (c) => ({ status: 200, body: proposals.vote(c.user, c.params.id) }));
}

function registerModeration(router, s) {
  const { moderation } = s;
  const mod = { role: ['moderador', 'admin'], verified: true, user: ['mod', 300, HOUR] };
  router.add('GET', '/v1/mod/queue', mod, () => ({ status: 200, body: moderation.queue() }));
  router.add('POST', '/v1/mod/offers/:id/approve', mod, (c) => ({ status: 200, body: moderation.approve(c.user, c.params.id, c.ip) }));
  router.add('POST', '/v1/mod/offers/:id/restore', mod, (c) => ({ status: 200, body: moderation.restore(c.user, c.params.id, c.ip) }));
  router.add('POST', '/v1/mod/offers/:id/remove', mod, (c) => ({ status: 200, body: moderation.remove(c.user, c.params.id, c.ip) }));
}

function registerHealth(router, s) {
  router.add('GET', '/health', { auth: false }, () => ({ status: 200, body: { status: 'ok' } }));
  router.add('GET', '/ready', { auth: false }, () => {
    s.db.get(s.READY);
    return { status: 200, body: { status: 'ready' } };
  });
}

function registerRoutes(router, services) {
  registerHealth(router, services);
  registerAuth(router, services);
  registerMe(router, services);
  registerOffers(router, services);
  registerAlerts(router, services);
  registerProposals(router, services);
  registerModeration(router, services);
}

module.exports = { registerRoutes };
