'use strict';
/**
 * Punto de entrada: arranca el servidor HTTP con tiempos de espera estrictos (anti "slowloris") y
 * se apaga ordenadamente con SIGTERM/SIGINT (termina las solicitudes en curso y cierra la base).
 * El HTTPS lo termina el proxy inverso (Caddy); este proceso solo habla HTTP con el proxy.
 */
const http = require('node:http');
const { loadConfig } = require('./config');
const { createLogger } = require('./lib/logger');
const { createApp } = require('./app');

let config;
try {
  config = loadConfig();
} catch (err) {
  // Falla cerrado: no se arranca con una configuración insegura o incompleta.
  process.stderr.write(`Configuración inválida: ${err.message}\n`);
  process.exit(1);
}

const logger = createLogger(config.logLevel);
let app;
try {
  app = createApp(config, { logger });
} catch (err) {
  logger.error('startup_failed', { err });
  process.exit(1);
}

const server = http.createServer({ maxHeaderSize: 16384, keepAliveTimeout: 65_000 }, app.handler);
server.headersTimeout = 15_000;   // tiempo máximo para recibir las cabeceras
server.requestTimeout = 30_000;   // tiempo máximo para recibir TODA la solicitud
server.timeout = 0;
server.maxHeadersCount = 50;
server.maxRequestsPerSocket = 1000;
server.on('clientError', (err, socket) => {
  if (socket.writable) socket.end('HTTP/1.1 400 Bad Request\r\nConnection: close\r\nContent-Length: 0\r\n\r\n');
  else socket.destroy();
});

server.listen(config.port, config.host, () => {
  logger.info('listening', { host: config.host, port: config.port, env: config.env, trust_proxy_hops: config.trustProxyHops });
});

let closing = false;
async function shutdown(signal) {
  if (closing) return;
  closing = true;
  logger.info('shutdown', { signal });
  const force = setTimeout(() => { logger.error('shutdown_forced', {}); process.exit(1); }, 10_000);
  force.unref();
  server.close(async () => {
    try { await app.close(); } finally { process.exit(0); }
  });
  server.closeIdleConnections();
}
process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));
// Un error no controlado deja el proceso en estado desconocido: se registra y se reinicia limpio (el supervisor lo levanta).
process.on('uncaughtException', (err) => { logger.error('uncaught_exception', { err }); process.exit(1); });
process.on('unhandledRejection', (err) => { logger.error('unhandled_rejection', { err }); process.exit(1); });
