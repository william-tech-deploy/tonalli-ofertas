'use strict';
/**
 * Contraseñas.
 *
 * Almacenamiento: scrypt (N=2^17, r=8, p=1), una de las configuraciones que recomienda OWASP
 * cuando no se usa Argon2id, con sal única de 128 bits y "pepper" secreto aplicado con HMAC-SHA-256
 * ANTES de scrypt. El pepper vive en una variable de entorno, no en la base de datos: si roban solo
 * la base de datos, ni siquiera pueden intentar adivinar contraseñas sin él.
 *
 * Política (NIST SP 800-63B-4): mínimo 15 caracteres porque la contraseña es el único factor,
 * se aceptan hasta 128, SIN reglas de composición (mayúsculas/símbolos obligatorios) y con lista
 * de contraseñas prohibidas + palabras del contexto (correo, usuario, nombre de la app).
 */
const crypto = require('node:crypto');
const { promisify } = require('node:util');
const { hmacSha256, safeEqual } = require('./crypto');
const { E } = require('./errors');

const scrypt = promisify(crypto.scrypt);
const DEFAULTS = { ln: 17, r: 8, p: 1, keylen: 32, saltlen: 16 };
const MIN_LEN = 15;
const MAX_LEN = 128;

/** Semáforo: limita cuántos hashes (128 MiB cada uno) corren a la vez y cuántos esperan. */
class Semaphore {
  constructor(max, maxQueue) { this.max = max; this.maxQueue = maxQueue; this.active = 0; this.queue = []; }
  async acquire() {
    if (this.active < this.max) { this.active++; return; }
    if (this.queue.length >= this.maxQueue) throw E.busy();
    await new Promise((resolve) => this.queue.push(resolve)); // el turno se cede sin cambiar "active"
  }
  release() {
    const next = this.queue.shift();
    if (next) next(); else this.active--;
  }
}

const COMMON = new Set([
  'password', 'contrasena', 'contraseña', 'passw0rd', 'qwerty', 'qwertyuiop', 'asdfghjkl', 'zxcvbnm', 'abc123', 'letmein',
  'welcome', 'iloveyou', 'admin', 'administrator', 'monkey', 'dragon', 'football', 'baseball', 'master', 'login', 'princess',
  'sunshine', 'shadow', 'superman', 'trustno1', 'whatever', 'freedom', 'starwars', 'passphrase', 'changeme', 'secret',
  'bajo', 'bajoapp', 'tonalli', 'tonalliapp', 'ahorro', 'ahorros', 'ofertas', 'oferta', 'descuento', 'supermercado', 'clave', 'claves', 'usuario',
  'teamo', 'tequiero', 'amor', 'futbol', 'barcelona', 'realmadrid', 'elsalvador', 'salvador', 'sansalvador', 'guanaco',
  'contrasenasegura', 'micontrasena', 'micontraseña', 'passwordpassword', 'qwertyqwerty', 'correo', 'universidad',
]);

/** Devuelve null si la contraseña es aceptable, o un código de motivo. */
function checkPasswordPolicy(password, { email, handle } = {}) {
  if (typeof password !== 'string') return 'type';
  const pw = password.normalize('NFKC');
  if (!pw.isWellFormed()) return 'invalid_text';
  const len = [...pw].length;
  if (len < MIN_LEN) return 'too_short';
  if (len > MAX_LEN) return 'too_long';
  if (new Set([...pw]).size < 6) return 'too_repetitive';
  const lower = pw.toLowerCase();
  const letters = lower.replace(/[^\p{L}]/gu, '');
  const stripped = lower.replace(/[\d\W_]+/gu, ''); // sin números ni símbolos
  if (COMMON.has(lower) || COMMON.has(letters) || COMMON.has(stripped)) return 'common_password';
  // Variantes tipo "password123456!" o "bajo2026bajo2026" o "tonalli2026tonalli2026"
  for (const w of COMMON) if (w.length >= 6 && lower.replace(/[\d\W_]+/gu, '').split(w).join('').length < 4) return 'common_password';
  if (/^(?:0123456789|1234567890|abcdefghij|qwertyuiop|asdfghjklñ)/.test(lower) && len < 20) return 'sequence';
  const local = email ? String(email).split('@')[0].toLowerCase() : '';
  if (local.length >= 4 && lower.includes(local)) return 'contains_identity';
  if (handle && String(handle).length >= 4 && lower.includes(String(handle).toLowerCase())) return 'contains_identity';
  return null;
}

/**
 * Comprobación opcional contra contraseñas filtradas en brechas (Have I Been Pwned, rango k-anonymity):
 * solo se envían 5 caracteres del SHA-1; la contraseña NUNCA sale del servidor. Si el servicio no
 * responde, se permite continuar (fail-open) para no impedir registros.
 */
async function isBreached(password, fetchImpl = fetch) {
  try {
    const sha1 = crypto.createHash('sha1').update(password.normalize('NFKC'), 'utf8').digest('hex').toUpperCase();
    const prefix = sha1.slice(0, 5);
    const suffix = sha1.slice(5);
    const res = await fetchImpl(`https://api.pwnedpasswords.com/range/${prefix}`, {
      headers: { 'Add-Padding': 'true', 'User-Agent': 'tonalli-backend' },
      signal: AbortSignal.timeout(2000),
    });
    if (!res.ok) return false;
    const body = await res.text();
    for (const line of body.split('\n')) {
      const [h, count] = line.trim().split(':');
      if (h === suffix && Number(count) > 0) return true;
    }
    return false;
  } catch {
    return false;
  }
}

function createPasswordService({ pepper, concurrency = 2, ln = DEFAULTS.ln }) {
  if (!pepper || pepper.length < 32) throw new Error('PASSWORD_PEPPER inválido');
  if (!Number.isInteger(ln) || ln < 10 || ln > 17) throw new Error('scrypt ln fuera de rango (10-17)');
  const P = { ...DEFAULTS, ln }; // parámetros vigentes (solo las pruebas usan un ln menor que 17)
  const sem = new Semaphore(concurrency, 24);

  const derive = async (password, salt, { ln, r, p, keylen }) => {
    const prehash = hmacSha256(pepper, password.normalize('NFKC')); // 32 bytes, nunca contiene la contraseña en claro
    return scrypt(prehash, salt, keylen, { N: 2 ** ln, r, p, maxmem: 256 * 1024 * 1024 });
  };

  async function hash(password) {
    await sem.acquire();
    try {
      const salt = crypto.randomBytes(DEFAULTS.saltlen);
      const key = await derive(password, salt, P);
      return `$scrypt$ln=${P.ln},r=${P.r},p=${P.p}$${salt.toString('base64')}$${key.toString('base64')}`;
    } finally { sem.release(); }
  }

  function parse(stored) {
    const m = /^\$scrypt\$ln=(\d{1,2}),r=(\d{1,2}),p=(\d{1,2})\$([A-Za-z0-9+/]+={0,2})\$([A-Za-z0-9+/]+={0,2})$/.exec(String(stored));
    if (!m) return null;
    const ln = +m[1], r = +m[2], p = +m[3];
    // Tope defensivo: una fila manipulada no puede pedir cantidades absurdas de memoria/CPU.
    // ln máximo 17: con r=8 ya usa 128 MiB; más allá se superaría el límite de memoria (maxmem) de la derivación.
    if (ln < 10 || ln > 17 || r < 1 || r > 16 || p < 1 || p > 4) return null;
    return { ln, r, p, salt: Buffer.from(m[4], 'base64'), key: Buffer.from(m[5], 'base64') };
  }

  /** Devuelve { ok, needsRehash }. Siempre gasta tiempo comparable, exista o no el usuario. */
  async function verify(password, stored) {
    const parsed = parse(stored);
    if (!parsed || typeof password !== 'string') return { ok: false, needsRehash: false };
    await sem.acquire();
    try {
      const key = await derive(password, parsed.salt, { ln: parsed.ln, r: parsed.r, p: parsed.p, keylen: parsed.key.length });
      const ok = key.length === parsed.key.length && crypto.timingSafeEqual(key, parsed.key);
      const needsRehash = ok && (parsed.ln < P.ln || parsed.r < P.r || parsed.p < P.p);
      return { ok, needsRehash };
    } finally { sem.release(); }
  }

  return { hash, verify, semaphore: sem };
}

module.exports = { createPasswordService, checkPasswordPolicy, isBreached, MIN_LEN, MAX_LEN, safeEqual };
