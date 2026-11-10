'use strict';
/**
 * Validación de entrada por "lista blanca" (allow-list), sin librerías.
 *  - Los objetos son ESTRICTOS: cualquier campo que no esté en el esquema se rechaza
 *    (evita "mass assignment": que alguien envíe role:"admin" o karma:9999).
 *  - Se construye un objeto NUEVO solo con los campos permitidos (evita contaminación de prototipo).
 *  - Los errores solo dicen QUÉ campo y QUÉ regla falló; nunca repiten el valor recibido.
 */
const { E } = require('./errors');
const { cleanText, normalizeEmail } = require('./text');

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const isObj = (v) => {
  if (v === null || typeof v !== 'object' || Array.isArray(v)) return false;
  const p = Object.getPrototypeOf(v);
  return p === Object.prototype || p === null;
};
const join = (p, k) => (p ? `${p}.${k}` : k);
const codePoints = (s) => { let n = 0; for (const _ of s) n++; return n; }; // eslint-disable-line no-unused-vars

function optional(fn, def) {
  const w = (v, p, i) => fn(v, p, i);
  w.optional = true;
  if (def !== undefined) w.default = def;
  return w;
}

/** Texto. clean=true limpia caracteres invisibles; raw=true no toca el texto (contraseñas). */
function string({ min = 0, max = 200, pattern, clean = true, raw = false, multiline = false } = {}) {
  return (v, path, issues) => {
    if (typeof v !== 'string') { issues.push({ path, code: 'type' }); return undefined; }
    if (v.length > max * 4 + 16) { issues.push({ path, code: 'too_long' }); return undefined; }
    if (!v.isWellFormed()) { issues.push({ path, code: 'invalid_text' }); return undefined; }
    let s = v;
    if (!raw) s = clean ? cleanText(v, { multiline }) : v.trim();
    const len = codePoints(s);
    if (len < min) { issues.push({ path, code: 'too_short' }); return undefined; }
    if (len > max) { issues.push({ path, code: 'too_long' }); return undefined; }
    if (pattern && !pattern.test(s)) { issues.push({ path, code: 'format' }); return undefined; }
    return s;
  };
}

/** Entero. coerce=true acepta "12" (para query strings). */
function int({ min = Number.MIN_SAFE_INTEGER, max = Number.MAX_SAFE_INTEGER, coerce = false } = {}) {
  return (v, path, issues) => {
    let n = v;
    if (coerce && typeof v === 'string') {
      if (!/^-?\d{1,15}$/.test(v)) { issues.push({ path, code: 'type' }); return undefined; }
      n = Number(v);
    }
    if (typeof n !== 'number' || !Number.isSafeInteger(n)) { issues.push({ path, code: 'type' }); return undefined; }
    if (n < min || n > max) { issues.push({ path, code: 'range' }); return undefined; }
    return n;
  };
}

function bool({ coerce = false } = {}) {
  return (v, path, issues) => {
    if (typeof v === 'boolean') return v;
    if (coerce && (v === 'true' || v === 'false')) return v === 'true';
    issues.push({ path, code: 'type' });
    return undefined;
  };
}

function oneOf(values) {
  const set = new Set(values);
  return (v, path, issues) => {
    if (typeof v !== 'string' || !set.has(v)) { issues.push({ path, code: 'enum' }); return undefined; }
    return v;
  };
}

function uuid() {
  return (v, path, issues) => {
    if (typeof v !== 'string' || !UUID_RE.test(v)) { issues.push({ path, code: 'format' }); return undefined; }
    return v;
  };
}

function email() {
  return (v, path, issues) => {
    if (typeof v !== 'string') { issues.push({ path, code: 'type' }); return undefined; }
    const e = normalizeEmail(v);
    if (!e) { issues.push({ path, code: 'format' }); return undefined; }
    return e;
  };
}

/** Texto en base64 estándar con tope de bytes decodificados (para la foto dentro del JSON). */
function base64({ maxBytes }) {
  const maxLen = Math.ceil(maxBytes / 3) * 4;
  return (v, path, issues) => {
    if (typeof v !== 'string') { issues.push({ path, code: 'type' }); return undefined; }
    if (v.length > maxLen) { issues.push({ path, code: 'too_long' }); return undefined; }
    if (v.length < 8 || v.length % 4 !== 0 || !/^[A-Za-z0-9+/]+={0,2}$/.test(v)) { issues.push({ path, code: 'format' }); return undefined; }
    return v;
  };
}

function object(shape) {
  const keys = Object.keys(shape);
  return (input, path = '', issues = []) => {
    if (!isObj(input)) { issues.push({ path, code: 'type' }); return undefined; }
    const out = {};
    for (const k of Object.keys(input)) {
      if (!Object.hasOwn(shape, k)) issues.push({ path: join(path, k), code: 'unknown_field' });
    }
    for (const k of keys) {
      const fn = shape[k];
      const present = Object.hasOwn(input, k) && input[k] !== undefined;
      if (!present) {
        if (!fn.optional) issues.push({ path: join(path, k), code: 'required' });
        else if ('default' in fn) out[k] = fn.default;
        continue;
      }
      const val = fn(input[k], join(path, k), issues);
      if (val !== undefined) out[k] = val;
    }
    return out;
  };
}

/** Ejecuta un esquema y lanza 400 con la lista de problemas (máx. 10) si algo falla. */
function parse(schema, input) {
  const issues = [];
  const value = schema(input, '', issues);
  if (issues.length) throw E.badRequest('validation_error', 'Datos inválidos', { issues: issues.slice(0, 10) });
  return value;
}

module.exports = { string, int, bool, oneOf, uuid, email, base64, object, optional, parse, UUID_RE };
