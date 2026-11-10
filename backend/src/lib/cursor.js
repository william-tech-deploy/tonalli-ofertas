'use strict';
/**
 * Cursores de paginación. Son opacos para el cliente (base64url de un arreglo JSON), pero NO son un
 * secreto ni una credencial: solo indican "desde dónde seguir". Aun así se validan con rigor
 * (largo, alfabeto, forma y tipo de cada posición) antes de llegar a la base de datos.
 */
const { E } = require('./errors');
const { UUID_RE } = require('./schema');

function encodeCursor(values) {
  return Buffer.from(JSON.stringify(values), 'utf8').toString('base64url');
}

/** shape: arreglo con 'int' | 'uuid' por posición. Devuelve null si no hay cursor. */
function decodeCursor(text, shape) {
  if (text === undefined || text === '') return null;
  const bad = () => E.badRequest('bad_cursor', 'Cursor inválido');
  if (typeof text !== 'string' || text.length > 200 || !/^[A-Za-z0-9_-]+$/.test(text)) throw bad();
  let arr;
  try { arr = JSON.parse(Buffer.from(text, 'base64url').toString('utf8')); } catch { throw bad(); }
  if (!Array.isArray(arr) || arr.length !== shape.length) throw bad();
  arr.forEach((v, i) => {
    const ok = shape[i] === 'int' ? Number.isSafeInteger(v) : typeof v === 'string' && UUID_RE.test(v);
    if (!ok) throw bad();
  });
  return arr;
}

module.exports = { encodeCursor, decodeCursor };
