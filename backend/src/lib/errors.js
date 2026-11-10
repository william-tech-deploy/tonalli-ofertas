'use strict';
/**
 * Errores de aplicación. Solo los AppError se muestran al cliente; cualquier otro
 * error se registra internamente y el cliente recibe un 500 genérico (sin pistas).
 */
class AppError extends Error {
  constructor(status, code, message, extra) {
    super(message || code);
    this.name = 'AppError';
    this.status = status;
    this.code = code;
    this.extra = extra || null; // p. ej. { issues: [...] }
    this.headers = null;
  }
}

const E = {
  badRequest: (code = 'bad_request', msg = 'Solicitud inválida', extra) => new AppError(400, code, msg, extra),
  unauthorized: (code = 'unauthorized', msg = 'Autenticación requerida') => {
    const e = new AppError(401, code, msg);
    e.headers = { 'WWW-Authenticate': 'Bearer' };
    return e;
  },
  forbidden: (code = 'forbidden', msg = 'No tienes permiso para esto') => new AppError(403, code, msg),
  notFound: (code = 'not_found', msg = 'No encontrado') => new AppError(404, code, msg),
  methodNotAllowed: (allow) => {
    const e = new AppError(405, 'method_not_allowed', 'Método no permitido');
    e.headers = { Allow: allow.join(', ') };
    return e;
  },
  conflict: (code = 'conflict', msg = 'Conflicto con el estado actual') => new AppError(409, code, msg),
  payloadTooLarge: () => new AppError(413, 'payload_too_large', 'El contenido es demasiado grande'),
  uriTooLong: () => new AppError(414, 'uri_too_long', 'La dirección es demasiado larga'),
  unsupported: (msg = 'Tipo de contenido no soportado') => new AppError(415, 'unsupported_media_type', msg),
  tooMany: (retryAfterSec) => {
    const e = new AppError(429, 'rate_limited', 'Demasiadas solicitudes. Intenta más tarde');
    e.headers = { 'Retry-After': String(Math.max(1, Math.ceil(retryAfterSec || 1))) };
    return e;
  },
  busy: () => {
    const e = new AppError(503, 'server_busy', 'Servidor ocupado. Intenta de nuevo en unos segundos');
    e.headers = { 'Retry-After': '5' };
    return e;
  },
};

module.exports = { AppError, E };
