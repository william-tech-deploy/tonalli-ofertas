'use strict';
/**
 * Acceso a la base de datos (SQLite nativo de Node).
 *
 * Defensa estructural contra inyección SQL: la etiqueta sql`...` PROHÍBE interpolaciones
 * (${...}) y solo ella produce consultas que db.get/all/run aceptan. Es imposible armar una
 * consulta concatenando texto del usuario: el código no compila (lanza al cargar) o es rechazado
 * en ejecución. Los valores siempre viajan como parámetros con nombre (:param).
 */
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { DatabaseSync } = require('node:sqlite');

const BRAND = Symbol('tonalli.sql');

function sql(strings, ...values) {
  if (values.length > 0) throw new Error('sql``: las interpolaciones están prohibidas; usa parámetros :nombre');
  const text = strings.raw.join('');
  if (text.includes('"')) throw new Error('sql``: usa comillas simples para texto (las dobles pueden ser identificadores)');
  return Object.freeze({ [BRAND]: true, text });
}

class Database {
  constructor(filePath) {
    if (filePath !== ':memory:') {
      const dir = path.dirname(path.resolve(filePath));
      fs.mkdirSync(dir, { recursive: true, mode: 0o700 });
    }
    this.db = new DatabaseSync(filePath);
    this.cache = new Map();
    this.inTx = false;
    // Ajustes de seguridad y robustez
    this.db.exec(`
      PRAGMA journal_mode = WAL;
      PRAGMA synchronous = FULL;
      PRAGMA foreign_keys = ON;
      PRAGMA busy_timeout = 5000;
      PRAGMA secure_delete = ON;
      PRAGMA trusted_schema = OFF;
      PRAGMA cell_size_check = ON;
      PRAGMA temp_store = MEMORY;
    `);
    if (filePath !== ':memory:') { try { fs.chmodSync(filePath, 0o600); } catch { /* ya restringido por umask */ } }
  }

  #stmt(q) {
    if (!q || q[BRAND] !== true) throw new TypeError('Solo se aceptan consultas creadas con sql``');
    let s = this.cache.get(q);
    if (!s) { s = this.db.prepare(q.text); this.cache.set(q, s); }
    return s;
  }
  // undefined no es un valor SQL válido: se convierte en NULL para que un campo ausente nunca cause un 500.
  static #clean(p) {
    const o = {};
    for (const k of Object.keys(p)) o[k] = p[k] === undefined ? null : p[k];
    return o;
  }
  get(q, params) { const s = this.#stmt(q); return params ? s.get(Database.#clean(params)) : s.get(); }
  all(q, params) { const s = this.#stmt(q); return params ? s.all(Database.#clean(params)) : s.all(); }
  run(q, params) { const s = this.#stmt(q); return params ? s.run(Database.#clean(params)) : s.run(); }

  /** Transacción atómica. El callback debe ser SÍNCRONO (nada de await dentro). */
  tx(fn) {
    if (this.inTx) return fn();
    this.db.exec('BEGIN IMMEDIATE');
    this.inTx = true;
    try {
      const result = fn();
      if (result && typeof result.then === 'function') throw new Error('db.tx: el callback no puede ser asíncrono');
      this.db.exec('COMMIT');
      return result;
    } catch (err) {
      try { this.db.exec('ROLLBACK'); } catch { /* ya revertida */ }
      throw err;
    } finally {
      this.inTx = false;
    }
  }

  /** Aplica migraciones SQL en orden y verifica que las ya aplicadas no hayan sido alteradas. */
  migrate(dir = path.join(__dirname, '..', 'migrations')) {
    this.db.exec('CREATE TABLE IF NOT EXISTS schema_migrations (version TEXT PRIMARY KEY, checksum TEXT NOT NULL, applied_at INTEGER NOT NULL) STRICT');
    const files = fs.readdirSync(dir).filter((f) => /^\d{3}_[a-z0-9_]+\.sql$/.test(f)).sort();
    const getApplied = this.db.prepare('SELECT checksum FROM schema_migrations WHERE version = ?');
    const insert = this.db.prepare('INSERT INTO schema_migrations(version, checksum, applied_at) VALUES (?, ?, ?)');
    const applied = [];
    for (const file of files) {
      const text = fs.readFileSync(path.join(dir, file), 'utf8');
      const checksum = crypto.createHash('sha256').update(text).digest('hex');
      const prev = getApplied.get(file);
      if (prev) {
        if (prev.checksum !== checksum) throw new Error(`La migración ${file} fue modificada después de aplicarse`);
        continue;
      }
      this.db.exec('BEGIN IMMEDIATE');
      try {
        this.db.exec(text);
        insert.run(file, checksum, Date.now());
        this.db.exec('COMMIT');
        applied.push(file);
      } catch (err) {
        try { this.db.exec('ROLLBACK'); } catch { /* nada */ }
        throw new Error(`Falló la migración ${file}: ${err.message}`);
      }
    }
    return applied;
  }

  /** Copia consistente de la base (VACUUM INTO) con permisos 0600. */
  backupTo(destination) {
    if (fs.existsSync(destination)) throw new Error('El destino del respaldo ya existe');
    this.db.prepare('VACUUM INTO ?').run(destination);
    fs.chmodSync(destination, 0o600);
  }

  close() {
    try { this.db.exec('PRAGMA wal_checkpoint(TRUNCATE)'); } catch { /* nada */ }
    this.db.close();
  }
}

module.exports = { Database, sql };
