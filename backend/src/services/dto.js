'use strict';
/**
 * Objetos de respuesta (DTO). Cada respuesta se construye campo por campo con una lista blanca:
 * así es imposible que se escape por accidente password_hash, correos ajenos u otras columnas internas.
 */
const { levelOf, LEVELS } = require('../domain/karma');

const iso = (ms) => (ms === null || ms === undefined ? null : new Date(ms).toISOString());
const round2 = (n) => Math.round(n * 100) / 100;

/** Datos del PROPIO usuario (incluye correo; nunca se usa para mostrar a terceros). */
function selfDto(u) {
  const level = levelOf(u.karma);
  return {
    id: u.id,
    handle: u.handle,
    email: u.email,
    email_verified: Boolean(u.email_verified_at),
    mode: u.mode,
    role: u.role,
    lang: u.lang,
    karma: u.karma,
    level,
    next_level_at: LEVELS[level + 1] ?? null,
    created_at: iso(u.created_at),
  };
}

module.exports = { iso, round2, selfDto };
