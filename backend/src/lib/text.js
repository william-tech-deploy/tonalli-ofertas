'use strict';
/**
 * Normalización y limpieza de texto que escribe el usuario.
 * - cleanText: elimina caracteres de control y de dirección invisibles (spoofing "Trojan Source"
 *   y texto que se ve distinto de lo que es) y normaliza a NFC.
 * - normText: versión sin tildes y en minúsculas, solo para BUSCAR/COMPARAR (nunca para mostrar).
 */
const BAD_CHARS = /[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F-\u009F​-‏‪-‮⁠-⁤⁦-⁯﻿￹-￻]/g;

function cleanText(input, { multiline = false } = {}) {
  let s = String(input).normalize('NFC').replace(BAD_CHARS, '');
  if (multiline) s = s.replace(/\r\n?/g, '\n').replace(/\t/g, ' ').replace(/\n{3,}/g, '\n\n');
  else s = s.replace(/[\r\n\t]+/g, ' ');
  return s.replace(/ {2,}/g, ' ').trim();
}

function normText(input) {
  return String(input)
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim();
}

const EMAIL_RE = /^[a-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$/;

/** Devuelve el correo normalizado (minúsculas, NFKC) o null si no es válido. Solo ASCII en el dominio. */
function normalizeEmail(input) {
  if (typeof input !== 'string' || input.length > 254) return null;
  const e = input.normalize('NFKC').trim().toLowerCase();
  if (e.length < 6 || e.length > 254 || !EMAIL_RE.test(e)) return null;
  return e;
}

module.exports = { cleanText, normText, normalizeEmail };
