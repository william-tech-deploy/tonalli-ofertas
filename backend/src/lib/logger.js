'use strict';
/**
 * Registro estructurado (una línea JSON por evento) con REDACCIÓN automática.
 * Regla de oro: nunca se registran contraseñas, tokens, cabeceras de autorización,
 * correos ni cuerpos de solicitudes. Si alguien pasa un campo con un nombre sensible
 * por error, igualmente se oculta.
 */
const LEVELS = { debug: 10, info: 20, warn: 30, error: 40 };
const SENSITIVE_KEY = /pass|token|secret|authorization|cookie|pepper|key|hash|credential|email|bearer/i;

function redact(value, depth = 0) {
  if (value === null || value === undefined) return value;
  if (typeof value === 'string') return value.length > 300 ? value.slice(0, 300) + '…' : value;
  if (typeof value === 'number' || typeof value === 'boolean') return value;
  if (value instanceof Error) {
    return { name: value.name, message: String(value.message).slice(0, 300), stack: String(value.stack || '').split('\n').slice(0, 6).join('\n') };
  }
  if (depth >= 4) return '[depth]';
  if (Array.isArray(value)) return value.slice(0, 20).map((v) => redact(v, depth + 1));
  if (typeof value === 'object') {
    const out = {};
    for (const k of Object.keys(value).slice(0, 40)) {
      out[k] = SENSITIVE_KEY.test(k) ? '[REDACTED]' : redact(value[k], depth + 1);
    }
    return out;
  }
  return String(value).slice(0, 100);
}

function createLogger(level = 'info', sink = (line) => process.stdout.write(line + '\n')) {
  const min = LEVELS[level] ?? LEVELS.info;
  const emit = (lvl) => (msg, fields) => {
    if (LEVELS[lvl] < min) return;
    const line = { level: lvl, time: new Date().toISOString(), msg: String(msg).slice(0, 200), ...(fields ? redact(fields) : {}) };
    sink(JSON.stringify(line)); // JSON.stringify escapa saltos de línea: no hay inyección de logs
  };
  return { debug: emit('debug'), info: emit('info'), warn: emit('warn'), error: emit('error') };
}

module.exports = { createLogger, redact };
