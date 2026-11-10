'use strict';
/**
 * Configuración desde variables de entorno, validada AL ARRANCAR. Si algo es inseguro o falta,
 * el servidor se niega a iniciar (falla cerrado) en vez de arrancar "medio configurado".
 * Los secretos nunca se escriben en el código ni en el repositorio.
 */
const WEAK_SECRETS = /^(changeme|change-me|secret|password|pepper|default|example|test|0+|1+|a+|x+)$/i;

function loadConfig(env = process.env) {
  // Sin NODE_ENV explícito NO se arranca: el valor por defecto "desarrollo" es más permisivo y sería un fallo abierto.
  if (!env.NODE_ENV) throw new Error('NODE_ENV es obligatorio (production, development o test)');
  const nodeEnv = env.NODE_ENV;
  if (!['production', 'development', 'test'].includes(nodeEnv)) throw new Error('NODE_ENV debe ser production, development o test');
  const prod = nodeEnv === 'production';

  const int = (name, def, min, max) => {
    const raw = env[name];
    if (raw === undefined || raw === '') return def;
    if (!/^\d{1,9}$/.test(raw)) throw new Error(`${name} debe ser un entero`);
    const n = Number(raw);
    if (n < min || n > max) throw new Error(`${name} fuera de rango (${min}-${max})`);
    return n;
  };
  const bool = (name, def) => (env[name] === undefined || env[name] === '' ? def : ['1', 'true', 'yes'].includes(String(env[name]).toLowerCase()));
  const list = (v) => String(v || '').split(',').map((s) => s.trim()).filter(Boolean);

  const cfg = {
    env: nodeEnv,
    prod,
    host: env.HOST || '127.0.0.1',
    port: int('PORT', 3000, 1, 65535),
    databasePath: env.DATABASE_PATH || './data/tonalli.db',
    pepper: env.PASSWORD_PEPPER || '',
    corsOrigins: list(env.CORS_ORIGINS),
    trustProxyHops: int('TRUST_PROXY_HOPS', 0, 0, 3),
    publicBaseUrl: (env.PUBLIC_BASE_URL || 'http://localhost:3000').replace(/\/+$/, ''),
    mailDriver: env.MAIL_DRIVER || (prod ? '' : 'console'),
    mailFrom: env.MAIL_FROM || 'Tonalli <no-reply@localhost>',
    resendApiKey: env.RESEND_API_KEY || '',
    accessTtlSec: int('ACCESS_TTL_SEC', 900, 60, 3600),
    refreshTtlDays: int('REFRESH_TTL_DAYS', 30, 1, 90),
    sessionMaxDays: int('SESSION_MAX_DAYS', 90, 1, 365),
    maxSessionsPerUser: int('MAX_SESSIONS_PER_USER', 10, 1, 50),
    hashConcurrency: int('HASH_CONCURRENCY', 2, 1, 8),
    registrationEnabled: bool('REGISTRATION_ENABLED', true),
    breachCheck: bool('BREACH_CHECK', prod),
    globalRatePerMin: int('GLOBAL_RATE_PER_MIN', 300, 10, 100000),
    logLevel: env.LOG_LEVEL || (prod ? 'info' : 'debug'),
    // Coste de scrypt: 2^17 (128 MiB) es el mínimo recomendado por OWASP; solo se baja en pruebas.
    scryptLn: int('SCRYPT_LN', 17, 10, 17),
    // Una cuenta recién creada no puede publicar hasta que pasen estas horas (frena cuentas desechables).
    newAccountHours: int('NEW_ACCOUNT_HOURS', prod ? 24 : 0, 0, 720),
    // Si es true, toda oferta nueva queda "pending" hasta que un moderador la apruebe.
    requireApproval: bool('REQUIRE_APPROVAL', false),
    jobsEnabled: bool('JOBS_ENABLED', nodeEnv !== 'test'),
  };

  // --- Reglas que no se pueden saltar ---
  if (cfg.pepper.length < 32 || WEAK_SECRETS.test(cfg.pepper) || new Set(cfg.pepper).size < 8) {
    throw new Error('PASSWORD_PEPPER es obligatorio: mínimo 32 caracteres aleatorios (openssl rand -base64 48)');
  }
  for (const o of cfg.corsOrigins) {
    let u;
    try { u = new URL(o); } catch { throw new Error(`CORS_ORIGINS contiene un origen inválido: ${o}`); }
    if (u.origin !== o || o === 'null') throw new Error(`CORS_ORIGINS debe tener orígenes exactos (esquema://host[:puerto]): ${o}`);
    if (prod && u.protocol !== 'https:') throw new Error(`En producción CORS_ORIGINS exige https: ${o}`);
  }
  if (!['console', 'resend', 'memory'].includes(cfg.mailDriver) && cfg.mailDriver !== '') throw new Error('MAIL_DRIVER debe ser console, resend o memory');
  if (prod) {
    if (cfg.scryptLn < 17) throw new Error('En producción SCRYPT_LN debe ser 17 (coste mínimo recomendado por OWASP)');
    if (!cfg.publicBaseUrl.startsWith('https://')) throw new Error('En producción PUBLIC_BASE_URL debe ser https');
    if (cfg.mailDriver !== 'resend') throw new Error('En producción MAIL_DRIVER debe ser resend (el correo es necesario para verificar cuentas)');
    if (!cfg.resendApiKey) throw new Error('Falta RESEND_API_KEY');
    if (cfg.trustProxyHops === 0 && cfg.host === '0.0.0.0') {
      // Escuchar en todas las interfaces sin proxy declarado sugiere que el servicio queda expuesto sin TLS.
      throw new Error('HOST=0.0.0.0 en producción requiere TRUST_PROXY_HOPS>=1 (el servicio debe ir detrás de un proxy con HTTPS)');
    }
  }
  return Object.freeze(cfg);
}

module.exports = { loadConfig };
