'use strict';
/**
 * Único camino para dar rol de moderador/admin: se ejecuta en el servidor, nunca por la API
 * (así una cuenta comprometida no puede auto-ascenderse). Queda registrado en audit_log.
 * Uso: npm run make-moderator -- <usuario> [moderador|admin|user]
 */
const { loadConfig } = require('../src/config');
const { Database, sql } = require('../src/db');

const FIND = sql`SELECT id, handle, role FROM users WHERE handle = :h`;
const SET = sql`UPDATE users SET role = :r WHERE id = :id`;
const LOG = sql`INSERT INTO audit_log(ts, event, user_id, ip, detail) VALUES (:ts, 'role_changed_cli', :id, NULL, :d)`;

const [handle, role = 'moderador'] = process.argv.slice(2);
if (!handle || !['moderador', 'admin', 'user'].includes(role)) {
  process.stderr.write('Uso: npm run make-moderator -- <usuario> [moderador|admin|user]\n');
  process.exit(2);
}
try {
  const db = new Database(loadConfig().databasePath);
  const u = db.get(FIND, { h: handle });
  if (!u) throw new Error('Usuario no encontrado');
  db.tx(() => {
    db.run(SET, { r: role, id: u.id });
    db.run(LOG, { ts: Date.now(), id: u.id, d: JSON.stringify({ from: u.role, to: role }) });
  });
  db.close();
  process.stdout.write(`${u.handle}: ${u.role} -> ${role}\n`);
} catch (err) {
  process.stderr.write(`Error: ${err.message}\n`);
  process.exit(1);
}
