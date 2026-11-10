'use strict';
/**
 * Ensamblado del servidor y CADENA DE SEGURIDAD de cada petición (en este orden):
 *  1. Identificador de solicitud + cabeceras de seguridad en TODA respuesta.
 *  2. Dirección de destino acotada y sin codificaciones raras; solo métodos permitidos.
 *  3. HTTPS obligatorio en producción (tras el proxy de confianza).
 *  4. CORS con lista blanca exacta de orígenes (sin comodín).
 *  5. Límite global por IP.
 *  6. Búsqueda de ruta (404/405) y límite propio de la ruta por IP.
 *  7. Autenticación (token opaco) y comprobación de rol, modo, correo verificado y antigüedad.
 *  8. Límite propio de la ruta por usuario.
 *  9. Cuerpo: tipo exacto, tope de bytes, UTF-8 estricto, esquema ESTRICTO (lista blanca).
 * 10. Parámetros de la URL: sin repetidos, con esquema.
 * 11. Manejador; la respuesta se arma con DTO de lista blanca.
 * 12. Cualquier error inesperado se registra por dentro y el cliente solo ve un 500 genérico.
 */
const { Database, sql } = require('./db');
const { createLogger } = require('./lib/logger');
const { AppError, E } = require('./lib/errors');
const { uuid, hmacSha256 } = require('./lib/crypto');
const { clientIp, readBody, parseJson, baseHeaders, sendJson, parseQuery, isJsonContentType } = require('./lib/http');
const { Router } = require('./lib/router');
const { RateLimiter, ipKey } = require('./lib/ratelimit');
const { parse } = require('./lib/schema');
const { createPasswordService } = require('./lib/password');
const { createMailer } = require('./lib/mailer');
const { createAudit } = require('./services/audit');
const { createThrottle } = require('./services/throttle');
const { createSessions } = require('./services/sessions');
const { createAuth } = require('./services/auth');
const { createNotify } = require('./services/notify');
const { createPoints } = require('./services/points');
const { createAlerts } = require('./services/alerts');
const { createOffers } = require('./services/offers');
const { createModeration } = require('./services/moderation');
const { createProposals } = require('./services/proposals');
const { createProfile } = require('./services/profile');
const { registerRoutes } = require('./routes');

const HOUR = 3_600_000;
const METHODS = new Set(['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS']);
const READY = sql`SELECT 1 AS ok`;
const META_GET = sql`SELECT value FROM system_meta WHERE key = 'pepper_check'`;
const META_SET = sql`INSERT INTO system_meta(key, value) VALUES ('pepper_check', :v)`;

/** Si el pepper cambia por error, TODOS los inicios de sesión fallarían: mejor negarse a arrancar. */
function checkPepper(db, pepper) {
  const v = hmacSha256(pepper, 'tonalli:pepper-check:v1').toString('hex');
  const row = db.get(META_GET);
  if (!row) { db.run(META_SET, { v }); return; }
  if (row.value !== v) throw new Error('PASSWORD_PEPPER no coincide con el que usó esta base de datos. No se inicia para no bloquear a todos los usuarios.');
}

function sendRaw(res, status, buf, headers) {
  res.statusCode = status;
  for (const [k, v] of Object.entries(headers || {})) res.setHeader(k, v);
  res.setHeader('Content-Length', buf.length);
  res.end(buf);
}

function createApp(config, overrides = {}) {
  const now = overrides.now || Date.now;
  const logger = overrides.logger || createLogger(config.logLevel);
  const db = overrides.db || new Database(config.databasePath);
  db.migrate();
  checkPepper(db, config.pepper);

  // Tareas en segundo plano (correos): se siguen para poder esperarlas al apagar y para que un fallo no tumbe el proceso.
  const pending = new Set();
  const bg = (promise) => {
    const p = Promise.resolve(promise).catch((err) => logger.error('bg_failed', { err })).finally(() => pending.delete(p));
    pending.add(p);
  };

  const limiter = new RateLimiter({ now });
  const audit = createAudit({ db, now });
  const throttle = createThrottle({ db, now });
  const passwords = createPasswordService({ pepper: config.pepper, concurrency: config.hashConcurrency, ln: config.scryptLn });
  const sessions = createSessions({ db, config, now, audit });
  const mailer = overrides.mailer || createMailer({ driver: config.mailDriver, from: config.mailFrom, resendApiKey: config.resendApiKey, baseUrl: config.publicBaseUrl, logger });
  const notify = createNotify({ db, now });
  const points = createPoints({ db, now });
  const alerts = createAlerts({ db, now, notify });
  const auth = createAuth({ db, config, now, passwords, sessions, mailer, throttle, audit, bg });
  const offers = createOffers({ db, config, now, points, notify, alerts, audit });
  const moderation = createModeration({ db, now, offers, notify, audit, points });
  const proposals = createProposals({ db, now, points, notify });
  const profile = createProfile({ db, now, sessions });

  const router = new Router();
  registerRoutes(router, { db, READY, auth, sessions, offers, alerts, proposals, moderation, profile });

  function authenticate(req) {
    const h = req.headers.authorization;
    // Un único mensaje para todo fallo: no se revela si el token existe, venció o fue revocado.
    if (typeof h !== 'string' || !h.startsWith('Bearer ')) throw E.unauthorized();
    const user = sessions.authenticate(h.slice(7).trim());
    if (!user) throw E.unauthorized();
    return user;
  }

  function authorize(user, o) {
    if (o.role && !o.role.includes(user.role)) throw E.forbidden('forbidden', 'No tienes permiso para esto');
    if (o.verified && !user.email_verified_at) throw E.forbidden('email_not_verified', 'Confirma tu correo para continuar');
    if (o.mode && user.mode !== o.mode) throw E.forbidden('mode_required', 'Activa el modo Cazador para hacer esto');
    if (o.minAge && now() - user.created_at < config.newAccountHours * HOUR) throw E.forbidden('account_too_new', 'Tu cuenta es muy reciente para esta acción. Inténtalo más tarde');
  }

  function limit(key, [name, max, windowMs]) {
    const r = limiter.hit(`${name}:${key}`, max, windowMs);
    if (!r.allowed) throw E.tooMany(r.retryAfterSec);
  }

  function fail(res, req, err, requestId) {
    let e = err;
    if (!(e instanceof AppError)) {
      if (e && e.code === 'ERR_SQLITE_ERROR' && /database is locked|busy/i.test(String(e.message))) e = E.busy();
      else {
        logger.error('unhandled', { request_id: requestId, err });
        e = new AppError(500, 'internal_error', 'Error interno');
      }
    }
    // Si quedó un cuerpo sin leer, se cierra la conexión (no se deja "colgado" un envío grande).
    if (!req.readableEnded) res.setHeader('Connection', 'close');
    sendJson(res, e.status, { error: { code: e.code, message: e.message, request_id: requestId, ...(e.extra || {}) } }, e.headers || undefined);
  }

  async function handle(req, res) {
    const started = Date.now();
    const requestId = uuid();
    const ip = clientIp(req, config.trustProxyHops);
    baseHeaders(res, { requestId, prod: config.prod });
    let routeName = '-';
    let userId = null;
    try {
      if (!METHODS.has(req.method)) throw E.methodNotAllowed([...METHODS].filter((m) => m !== 'OPTIONS'));
      const target = req.url || '';
      if (target.length > 2048) throw E.uriTooLong();
      const qi = target.indexOf('?');
      const path = qi === -1 ? target : target.slice(0, qi);
      const search = qi === -1 ? '' : target.slice(qi + 1);
      // Solo caracteres sin codificación: nuestras rutas nunca los necesitan. Evita trucos con %2e%2e, //, \ y similares.
      if (!/^\/[A-Za-z0-9\-._~/]*$/.test(path) || path.startsWith('//') || path.includes('..')) throw E.notFound('route_not_found', 'Ruta no encontrada');

      const isHealth = path === '/health' || path === '/ready';
      if (config.prod && config.trustProxyHops > 0 && !isHealth) {
        const proto = String(req.headers['x-forwarded-proto'] || '').split(',')[0].trim().toLowerCase();
        if (proto !== 'https') throw E.badRequest('https_required', 'Se requiere HTTPS');
      }

      // CORS: lista blanca exacta. Sin credenciales de navegador (se usan tokens en cabecera, no cookies).
      const origin = req.headers.origin;
      if (origin !== undefined) {
        res.setHeader('Vary', 'Origin');
        if (typeof origin === 'string' && config.corsOrigins.includes(origin)) {
          res.setHeader('Access-Control-Allow-Origin', origin);
          res.setHeader('Access-Control-Expose-Headers', 'X-Request-Id, Retry-After');
          if (req.method === 'OPTIONS') {
            res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, PATCH, DELETE');
            res.setHeader('Access-Control-Allow-Headers', 'Authorization, Content-Type');
            res.setHeader('Access-Control-Max-Age', '600');
          }
        }
      }
      limit(ipKey(ip), ['global', config.globalRatePerMin, 60_000]);
      if (req.method === 'OPTIONS') { sendJson(res, 204); return; }

      const m = router.match(req.method, path);
      if (!m) throw E.notFound('route_not_found', 'Ruta no encontrada');
      if (m.allowed) throw E.methodNotAllowed(m.allowed);
      const { route, params } = m;
      const o = route.opts;
      routeName = `${route.method} ${route.pattern}`;

      if (o.ip) limit(ipKey(ip), o.ip);

      let user = null;
      if (o.auth !== false) {
        user = authenticate(req);
        userId = user.id;
        authorize(user, o);
        if (o.user) limit(user.id, o.user);
      }

      let body;
      const cl = req.headers['content-length'];
      const hasBody = (cl !== undefined && cl !== '0') || req.headers['transfer-encoding'] !== undefined;
      if (o.body) {
        if (!isJsonContentType(req.headers['content-type'])) throw E.unsupported('Content-Type debe ser application/json');
        body = parse(o.body, parseJson(await readBody(req, o.maxBody || 4096)));
      } else if (hasBody) {
        throw E.badRequest('unexpected_body', 'Esta ruta no acepta cuerpo');
      }

      const rawQuery = parseQuery(search);
      let query = rawQuery;
      if (o.query) query = parse(o.query, rawQuery);
      else if (Object.keys(rawQuery).length) throw E.badRequest('bad_query', 'Esta ruta no acepta parámetros');

      const out = await route.handler({ req, params, query, body, user, ip, requestId });
      if (out.raw) sendRaw(res, out.status, out.raw, out.headers);
      else sendJson(res, out.status, out.body, out.headers);
    } catch (err) {
      fail(res, req, err, requestId);
    } finally {
      logger.info('req', { id: requestId, method: req.method, route: routeName, status: res.statusCode, ms: Date.now() - started, user: userId, ip });
    }
  }

  const handler = (req, res) => {
    handle(req, res).catch((err) => {
      logger.error('handler_crash', { err });
      try { if (!res.headersSent) { res.statusCode = 500; res.end(); } else res.destroy(); } catch { /* nada */ }
    });
  };

  let jobTimer = null;
  if (config.jobsEnabled) {
    jobTimer = setInterval(() => {
      try { logger.info('jobs', { ...sessions.purgeExpired(), throttle: throttle.purge() }); }
      catch (err) { logger.error('jobs_failed', { err }); }
    }, HOUR);
    jobTimer.unref();
  }

  async function close() {
    if (jobTimer) clearInterval(jobTimer);
    await Promise.allSettled([...pending]);
    limiter.close();
    db.close();
  }

  return { handler, close, db, config, mailer, services: { auth, sessions, offers, alerts, proposals, moderation, profile, passwords, throttle, audit, notify, points } };
}

module.exports = { createApp };
