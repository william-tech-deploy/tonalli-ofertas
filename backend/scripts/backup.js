'use strict';
/**
 * Respaldo consistente (VACUUM INTO) con permisos 0600 y borrado de respaldos antiguos.
 * Uso: npm run backup [-- carpeta]   (por defecto ./data/backups). Programar con cron/systemd timer.
 * Recuerda: copia los respaldos FUERA del servidor y cífralos (p. ej. con age o gpg).
 */
const fs = require('node:fs');
const path = require('node:path');
const { loadConfig } = require('../src/config');
const { Database } = require('../src/db');

const KEEP = 14;
try {
  const config = loadConfig();
  const dir = path.resolve(process.argv[2] || path.join(path.dirname(config.databasePath), 'backups'));
  fs.mkdirSync(dir, { recursive: true, mode: 0o700 });
  const stamp = new Date().toISOString().replace(/[:.]/g, '-');
  const dest = path.join(dir, `tonalli-${stamp}.db`);
  const db = new Database(config.databasePath);
  db.backupTo(dest);
  db.close();
  const old = fs.readdirSync(dir).filter((f) => /^tonalli-.*\.db$/.test(f)).sort().slice(0, -KEEP);
  for (const f of old) fs.unlinkSync(path.join(dir, f));
  process.stdout.write(`Respaldo creado: ${dest} (se conservan los últimos ${KEEP})\n`);
} catch (err) {
  process.stderr.write(`Error: ${err.message}\n`);
  process.exit(1);
}
