'use strict';
/**
 * Piezas de bajo nivel del servidor HTTP: dirección IP real del cliente, lectura acotada del
 * cuerpo, cabeceras de seguridad y respuestas JSON.
 */
const net = require('node:net');
const { E } = require('./errors');

/**
 * IP del cliente. Con hops=0 (sin proxy) se IGNORA X-Forwarded-For (cualquiera puede falsificarlo).
 * Con hops=N se toma la entrada N-ésima contando DESDE LA DERECHA: es la que añadió nuestro proxy
 * de confianza; lo que el cliente escribió a la izquierda no cuenta.
 */
function clientIp(req, hops) {
  const remote = req.socket.remoteAddress || '';
  if (!hops || hops < 1) return remote;
  const raw = req.headers['x-forwarded-for'];
  if (typeof raw !== 'string') return remote;
  const parts = raw.split(',').map((s) => s.trim()).filter(Boolean);
  const candidate = parts[parts.length - hops];
  return candidate && net.isIP(candidate) ? candidate : remote;
}

/** Lee el cuerpo con tope duro de bytes; corta la lectura en cuanto se supera. */
function readBody(req, max) {
  return new Promise((resolve, reject) => {
    const declared = req.headers['content-length'];
    if (declared !== undefined) {
      if (!/^\d{1,10}$/.test(declared)) return reject(E.badRequest('bad_content_length', 'Content-Length inválido'));
      if (Number(declared) > max) return reject(E.payloadTooLarge());
    }
    const chunks = [];
    let size = 0;
    let done = false;
    const finish = (fn, arg) => { if (!done) { done = true; fn(arg); } };
    req.on('data', (c) => {
      if (done) return;
      size += c.length;
      if (size > max) { chunks.length = 0; finish(reject, E.payloadTooLarge()); return; }
      chunks.push(c);
    });
    req.on('end', () => finish(resolve, Buffer.concat(chunks, size)));
    req.on('error', (err) => finish(reject, err));
    req.on('aborted', () => finish(reject, E.badRequest('aborted', 'Solicitud interrumpida')));
  });
}

/** Cuerpo JSON: UTF-8 estricto, objeto/arreglo JSON válido, sin BOM. */
function parseJson(buf) {
  if (buf.length === 0) throw E.badRequest('empty_body', 'Se esperaba un cuerpo JSON');
  let text;
  try { text = new TextDecoder('utf-8', { fatal: true, ignoreBOM: true }).decode(buf); }
  catch { throw E.badRequest('invalid_encoding', 'El cuerpo debe ser UTF-8 válido'); }
  if (text.charCodeAt(0) === 0xfeff) throw E.badRequest('invalid_encoding', 'El cuerpo no debe llevar BOM');
  try { return JSON.parse(text); } catch { throw E.badRequest('invalid_json', 'JSON mal formado'); }
}

/** Cabeceras de seguridad que llevan TODAS las respuestas (también las de error). */
function baseHeaders(res, { requestId, prod }) {
  res.setHeader('X-Request-Id', requestId);
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Referrer-Policy', 'no-referrer');
  res.setHeader('X-Frame-Options', 'DENY');
  res.setHeader('Content-Security-Policy', "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'");
  res.setHeader('Cross-Origin-Resource-Policy', 'same-site');
  res.setHeader('Cross-Origin-Opener-Policy', 'same-origin');
  res.setHeader('Permissions-Policy', 'camera=(), microphone=(), geolocation=(), payment=(), usb=(), interest-cohort=()');
  if (prod) res.setHeader('Strict-Transport-Security', 'max-age=63072000; includeSubDomains');
}

function sendJson(res, status, body, headers) {
  if (res.headersSent) return;
  const payload = body === undefined ? '' : JSON.stringify(body);
  res.statusCode = status;
  if (headers) for (const [k, v] of Object.entries(headers)) res.setHeader(k, v);
  if (payload) {
    if (!res.getHeader('Content-Type')) res.setHeader('Content-Type', 'application/json; charset=utf-8');
    res.setHeader('Content-Length', Buffer.byteLength(payload));
    res.end(payload);
  } else {
    res.removeHeader('Content-Type');
    res.end();
  }
}

/**
 * Query string → objeto plano. Rechaza claves repetidas (contaminación de parámetros HTTP),
 * exceso de parámetros y valores enormes. El objeto no tiene prototipo.
 */
function parseQuery(search, { maxParams = 8, maxValue = 200 } = {}) {
  const out = Object.create(null);
  if (!search) return out;
  if (search.length > 1024) throw E.badRequest('bad_query', 'Consulta demasiado larga');
  let n = 0;
  for (const [k, v] of new URLSearchParams(search)) {
    if (++n > maxParams) throw E.badRequest('bad_query', 'Demasiados parámetros');
    if (Object.hasOwn(out, k)) throw E.badRequest('bad_query', 'Parámetro repetido');
    if (k.length > 40 || v.length > maxValue) throw E.badRequest('bad_query', 'Parámetro demasiado largo');
    out[k] = v;
  }
  return out;
}

/** true solo si Content-Type es application/json (con charset utf-8 opcional y nada más). */
function isJsonContentType(header) {
  if (typeof header !== 'string') return false;
  const [type, ...params] = header.toLowerCase().split(';').map((s) => s.trim());
  return type === 'application/json' && params.every((p) => p === 'charset=utf-8');
}

module.exports = { clientIp, readBody, parseJson, baseHeaders, sendJson, parseQuery, isJsonContentType };
