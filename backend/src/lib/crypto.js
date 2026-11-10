'use strict';
/**
 * Utilidades criptográficas. Todo sale de node:crypto (CSPRNG del sistema operativo).
 * Nunca se usa Math.random() para nada de seguridad (lo verifica scripts/check-security.js).
 */
const crypto = require('node:crypto');

/** Token aleatorio de 256 bits con prefijo (los prefijos ayudan a detectar fugas con escáneres de secretos). */
function randomToken(prefix, bytes = 32) {
  return prefix + crypto.randomBytes(bytes).toString('base64url');
}

/** Los tokens tienen forma fija: se valida ANTES de tocar la base de datos. */
const TOKEN_RE = {
  access: /^bat_[A-Za-z0-9_-]{43}$/,
  refresh: /^brt_[A-Za-z0-9_-]{43}$/,
  once: /^bot_[A-Za-z0-9_-]{43}$/,
};

/** Se guarda solo el SHA-256 del token: si roban la base de datos no obtienen tokens usables. */
function sha256(text) {
  return crypto.createHash('sha256').update(text, 'utf8').digest('hex');
}

function hmacSha256(key, data) {
  return crypto.createHmac('sha256', key).update(data, 'utf8').digest();
}

/** Comparación en tiempo constante. */
function safeEqual(a, b) {
  const A = Buffer.from(String(a));
  const B = Buffer.from(String(b));
  if (A.length !== B.length) return false;
  return crypto.timingSafeEqual(A, B);
}

const uuid = () => crypto.randomUUID();

module.exports = { randomToken, TOKEN_RE, sha256, hmacSha256, safeEqual, uuid };
