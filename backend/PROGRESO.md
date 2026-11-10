# PROGRESO del backend de Tonalli (punto de control para retomar)

> Si una sesión se corta, retoma desde la PRIMERA casilla sin marcar. No rehagas lo marcado.
> Última actualización: 2026-10-18 (sesión 2). Carpeta: `tonalli-proyecto/backend/` (Node 22.13+, SIN dependencias npm, SQLite nativo).

## Decisiones ya tomadas (no cambiar sin motivo)
- JavaScript CommonJS, cero dependencias de terceros (`package.json` sin `dependencies`): menos superficie de ataque de cadena de suministro.
- Base de datos: `node:sqlite` (SQLite) con tablas STRICT y CHECK; SQL solo con la etiqueta `sql\`\`` (prohíbe interpolar).
- Tokens opacos (no JWT) con SHA-256 en la base; refresh rotativo con detección de reutilización.
- Contraseñas: scrypt N=2^17 + pepper (HMAC) ; política NIST 800-63B (mínimo 15).
- API en centavos enteros (`price_now_cents`, `price_old_cents`, `max_price_cents`).
- Despliegue previsto: VPS pequeño + Docker + Caddy (HTTPS automático) porque SQLite necesita disco persistente.

## Hecho (sesiones 1 y 2)
- [x] `src/config.js`, `src/db.js`, `migrations/001_init.sql`
- [x] `src/lib/*` (errors, http, router, schema, text, crypto, password, ratelimit, logger, mailer, jpeg, cursor)
- [x] `src/domain/*` (verdict, url, ean, karma)
- [x] `src/services/*`: audit, throttle, sessions, auth, alerts, notify, points, dto, offers, moderation, proposals, profile
- [x] `src/routes/index.js` (todas las rutas declaradas)

## Pendiente (en este orden) — app.js y server.js listos y con smoke test OK (health, ready, 401, register 202, 404)
- [x] `src/app.js` (ensamblado y cadena de seguridad por petición) y `src/server.js` (arranque, tiempos de espera, apagado limpio)
- [x] `scripts/`: `migrate.js`, `make-moderator.js`, `backup.js`, `check-security.js`
- [x] Pruebas `test/*.test.js`: 49/49 pasan con `npm test` (auth 11, hardening 14, business 11, domain 13); check:security OK
- [x] Despliegue: Dockerfile, docker-compose.yml, deploy/Caddyfile, deploy/tonalli.service, .env.example, .gitignore, .dockerignore, ../.github/workflows/ci.yml (SIN probar en Docker real: no hay daemon aquí)
- [x] Arranque real con --experimental-permission (lectura '.', escritura './data'), /ready, registro por curl y apagado SIGTERM limpio: OK
- [x] Revisión de seguridad independiente (agente que no escribió el código) y corregir hallazgos
- [x] Documento (12 secciones rellenas) "Tonalli API: seguridad, código y despliegue" (Docs: https://claude.ai/code/artifact/9dac824f-40ea-44d4-a40f-a1cb91e42956) — rellenar sección por sección (ya existe el esquema con 12 secciones pendientes)
- [x] Actualizar `tonalli-proyecto/README.md` (mencionar backend) y regenerar `tonalli-proyecto.zip`; enviarlo con SendUserFile

## Notas para quien retome
- El cliente (web/Android) AÚN usa localStorage; conectarlo a esta API es un paso posterior.
- Rutas de verificación/reinicio de correo usan `#/verify/<token>` y `#/reset/<token>` (el cliente debe manejarlas).
- No hay acceso a npm ni a internet desde el entorno de desarrollo: por eso se evitaron las dependencias.

## Estado de pruebas (retomar aquí)
- test/helper.js listo (start(), signup(n,{mode}), clock controlable, lastToken).
- test/auth.test.js: 11/11 pasan.
- test/hardening.test.js: 14/14 pasan.
- test/business.test.js: 11/11 pasan.
- SIGUIENTE: test/domain.test.js (verdict, url, ean, schema, jpeg, password policy, migración alterada, scripts) y luego deploy/

## Revisión independiente
- Agente revisor lanzado (solo lectura). Cuando avise: corregir hallazgos CONFIRMADOS en src/, añadir prueba por cada uno en test/, volver a correr npm test + check:security.
- Mientras tanto: verificar estándares (ASVS 5.0, API Top 10 2023) con búsqueda web para el documento.
- Estándares verificados (cheatsheetseries.owasp.org/IndexASVS = ASVS 5.0.x): V1 Encoding and Sanitization, V2 Validation and Business Logic, V3 Web Frontend Security, V4 API and Web Service, V5 File Handling, V6 Authentication, V7 Session Management, V8 Authorization, V9 Self-contained Tokens, V10 OAuth and OIDC, V11 Cryptography, V12 Secure Communication, V13 Configuration, V14 Data Protection, V15 Secure Coding and Architecture, V16 Security Logging and Error Handling, V17 WebRTC.
- API Top 10 2023 (api-security.owasp.org): API1 BOLA, API2 Broken Authentication, API3 Broken Object Property Level Authorization, API4 Unrestricted Resource Consumption, API5 Broken Function Level Authorization, API6 Unrestricted Access to Sensitive Business Flows, API7 SSRF, API8 Security Misconfiguration, API9 Improper Inventory Management, API10 Unsafe Consumption of APIs.

## Hallazgos del revisor (corregir en este orden; marcar al terminar y añadir prueba en test/review.test.js)
Sin hallazgos en: SQLi, refresh tokens, BOLA, mass assignment, XFF, CORS, ReDoS, cuerpos, bitácora, roles.
- [x] C1 reportes: exigir karma>=50 al reportante y ponderar; auto-ocultar sólo con peso>=3 de reportantes distintos
- [x] C2 veredicto: >=3 usuarios distintos en prior, excluir puntos del autor y de la propia oferta
- [x] C3 karma: UNIQUE(user,offer,kind) para 'vote'; sin pago por comentar oferta propia; clawback al remover oferta
- [x] C4 tope diario: contar removed/hidden dentro de la ventana
- [x] C5 carrera login/reset: comprobar password_changed_at y status dentro de sessions.create (misma tx)
- [x] C6 registro: comprobar handle antes y responder 202 igual (sin 409 que revele correo)
- [x] C7 correo masivo: throttle por destinatario (3/h) y purgar cuentas sin verificar a las 48 h
- [x] C8 alerta vacía: rechazar keyword_norm<2, exigir verified, ORDER BY en matchOffer
- [x] C9 analyzeUrl: rechazar etiquetas vacías, sufijos para acortadores/TLD/internos
- [x] C10 voto/comentario: exigir minAge; normalizar +alias
- [x] C11 bloqueo por (cuenta, IP) además del límite por cuenta más alto
- [x] C12 precalcular dummyHash al arrancar
- [x] P1 NODE_ENV obligatorio; P4 limitador antes de OPTIONS

## Estado tras correcciones
- 62/62 pruebas pasan (auth 11, hardening 14, business 11, domain 13, review 13). check:security OK. Migración 002 añadida.
- Aceptados/pendientes (documentar como límites): P2 saturación del semáforo scrypt, P3 coste de búsqueda/veredicto por fila, P5 handles de suplantación (homoglifos), P6 forgot-password invalida enlaces previos, P7 JPEG saneado pero no recodificado, P8 inundación de notificaciones, P9 busy_timeout bloquea el event loop con respaldo concurrente.
- SIGUIENTE: rellenar el documento (Docs) sección por sección; corregir el párrafo inicial (dice Node/TypeScript/PostgreSQL: es JavaScript + SQLite); luego zip + memoria + mensaje final.

## FINAL
- Documento completo; README actualizado; falta solo: zip final, memoria, mensaje al usuario.
- Siguiente paso real del proyecto: conectar web/Android a la API.
