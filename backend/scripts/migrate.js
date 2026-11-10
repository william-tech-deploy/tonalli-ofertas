'use strict';
/** Aplica las migraciones pendientes y termina. Uso: npm run migrate */
const { loadConfig } = require('../src/config');
const { Database } = require('../src/db');

try {
  const config = loadConfig();
  const db = new Database(config.databasePath);
  const applied = db.migrate();
  db.close();
  process.stdout.write(applied.length ? `Migraciones aplicadas: ${applied.join(', ')}\n` : 'Base de datos al día.\n');
} catch (err) {
  process.stderr.write(`Error: ${err.message}\n`);
  process.exit(1);
}
