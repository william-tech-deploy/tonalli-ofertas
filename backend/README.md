# Tonalli API (backend seguro)

API de ofertas colaborativas. **Cero dependencias npm**, Node ≥ 22.13, SQLite nativo (`node:sqlite`).
La explicación completa (por qué cada control, qué estándar se siguió, cómo aplicarlo y subirlo) está en el documento
"Tonalli API: seguridad, código y despliegue".

## Arranque local (desarrollo)
```bash
cd backend
export NODE_ENV=development
export PASSWORD_PEPPER="$(openssl rand -base64 48)"   # guárdalo; no lo cambies luego
export CORS_ORIGINS="http://localhost:8080"            # tu web local (origen exacto)
npm run migrate
npm start                                              # http://127.0.0.1:3000  (los correos salen por consola)
```

## Comandos
| Comando | Qué hace |
|---|---|
| `npm test` | 62 pruebas (autenticación, endurecimiento, reglas de negocio, dominio, scripts) |
| `npm run check:security` | Revisión estática (sin eval/Math.random/child_process, SQL solo en db.js, cero dependencias, sin secretos) |
| `npm run migrate` | Aplica migraciones (detecta si una ya aplicada fue alterada) |
| `npm run make-moderator -- usuario [moderador\|admin\|user]` | Único camino para dar rol (queda en la bitácora) |
| `npm run backup` | Respaldo consistente con permisos 0600 (conserva los últimos 14) |
| `npm run start:hardened` | Arranque con el modelo de permisos de Node (solo lee `.`, solo escribe `./data`) |

## Producción (resumen)
1. VPS pequeño con Docker; apunta un dominio (registro A) al VPS.
2. `cp .env.example .env`, rellena `PASSWORD_PEPPER`, `API_DOMAIN`, `PUBLIC_BASE_URL`, `CORS_ORIGINS`, `RESEND_API_KEY`.
3. `docker compose up -d --build` (Caddy consigue el certificado HTTPS solo).
4. Crea tu usuario en la app, verifica el correo y ejecuta `docker compose exec api node scripts/make-moderator.js tu_usuario admin`.
5. Programa `scripts/backup.js` (cron/systemd timer) y copia los respaldos fuera del servidor, cifrados.

> SQLite = una sola instancia con disco persistente. Para varias instancias habría que pasar a PostgreSQL y mover el limitador a Redis.
