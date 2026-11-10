# Conexión de la app (Android y web) con el servidor

Tonalli usa **un solo archivo de interfaz**, `web/index.html`. Android lo empaqueta dentro de la app y la web lo
publica tal cual. Por eso conectarlo al servidor una vez conecta las dos.

## 1. Cómo funciona

| Modo | Cuándo se activa | Qué hace |
|---|---|---|
| **Demostración** | `content=""` en la etiqueta `tonalli-api` (valor por defecto) | Datos de ejemplo en el teléfono. No hay cuentas. Sirve para presentar sin servidor. |
| **Servidor** | `content="https://api.tu-dominio.com"` | Cuentas reales. Ofertas, votos, comentarios, alertas, karma y notificaciones viven en el servidor. |

Para activarlo, edita **una línea** en `web/index.html`:

```html
<meta name="tonalli-api" content="https://api.tu-dominio.com">
```

Reglas que el código impone solo: la dirección debe ser **HTTPS** (la única excepción es `localhost`/`127.0.0.1`
para desarrollar). Si pones `http://` hacia un servidor remoto, la app lo ignora y vuelve al modo demostración.

## 2. Por qué existe cada grupo de código (`web/index.html`)

| Grupo | Líneas clave | Por qué existe |
|---|---|---|
| `API`, `SRV` | Validación `https://` | Nunca viajan contraseñas ni tokens por una conexión sin cifrar (OWASP MASVS-NETWORK, ASVS V9). |
| `A`, `setTokens`, `clearTokens` | El token de acceso vive **solo en memoria**; el de renovación en `localStorage` | Un token de acceso robado dura 15 min. El de renovación rota en cada uso: si alguien lo copia, el servidor lo detecta y cierra la sesión (ASVS V3). |
| `refreshTok()` | Una sola renovación a la vez (`A.flight`) | El token de renovación es de **un solo uso**. Dos renovaciones simultáneas harían que el servidor lo tome por robo y cierre la sesión. |
| `api()` | `credentials:'omit'`, `cache:'no-store'`, `referrerPolicy:'no-referrer'`; ante un 401 renueva y reintenta **una** vez | Sin cookies no existe CSRF. Nada sensible queda en caché. Un token vencido no interrumpe a la persona. |
| `apiBlob()` | La foto se pide con `fetch` y se muestra como objeto local | La foto exige sesión; una etiqueta `<img src>` no puede enviar la cabecera `Authorization`. |
| `mapOffer`, `mapCat`, `mapAlert`, `mapNotif` | Centavos ÷ 100, `mine` → autor `me`, `kind` → clave de texto | El servidor guarda **centavos enteros** (sin errores de coma flotante). La interfaz usa dólares. |
| `verdictOf()` | Usa el veredicto del servidor; si no existe, calcula el local | El veredicto "real / inflada" lo decide el servidor con el historial de la comunidad, para que nadie lo altere desde el teléfono. |
| `errMsg()` + claves `e.*` | Códigos del servidor → frases en es/en/pt | Mensajes útiles sin mostrar detalles internos. Un código desconocido da un mensaje genérico. |
| `esc()` (ya existía) | Todo texto del servidor se escapa al dibujar | Evita XSS. Hay una prueba con un título `<img onerror=…>`. |
| `boot()` | Lee `#/verify/…` y `#/reset/…`, luego **borra el token de la dirección** (`history.replaceState`) | El token de un solo uso no queda en el historial ni se filtra por `Referer`. |
| `dropSession()` | Si la renovación falla, vacía el estado y vuelve al acceso | Ninguna oferta ni dato de otra sesión queda en memoria. |
| Bloque `SA`/`SC`/`SS` | Reemplaza las acciones locales por llamadas a la API | El modo demostración queda intacto; el servidor es opcional. |
| `busy()` | Desactiva el botón mientras se envía | Evita enviar dos veces (dos publicaciones, dos votos). |

Reglas del producto que **solo valida el servidor** (el cliente únicamente las explica): correo verificado para votar,
publicar y comentar; modo Cazador para publicar y comentar; no votar la propia oferta; límites por hora y por día;
nivel mínimo para proponer categorías; contraseña de 15+ caracteres no filtrada; veredicto y temperatura.

## 3. Rúbrica seguida

- **OWASP ASVS 5.0**: V2 (autenticación), V3 (sesiones), V9 (comunicación), V12 (archivos: fotos).
- **OWASP MASVS** (apps móviles): STORAGE (qué se guarda en el teléfono), NETWORK (solo TLS), AUTH.
- **OWASP API Security Top 10 2023**: el control de acceso por objeto y los límites los aplica el servidor.
- **NIST SP 800-63B**: contraseña larga, sin reglas de composición; se pide mínimo 15 caracteres.

## 4. Cómo aplicarlo, paso a paso

1. **Levanta el servidor** (`backend/README.md`). Para desarrollo local:
   ```bash
   cd backend
   NODE_ENV=development PORT=8787 PASSWORD_PEPPER=<32+ caracteres al azar> \
   MAIL_DRIVER=console NEW_ACCOUNT_HOURS=0 SCRYPT_LN=12 \
   CORS_ORIGINS=http://127.0.0.1:8081 PUBLIC_BASE_URL=http://127.0.0.1:8081 node src/server.js
   ```
   Con `MAIL_DRIVER=console` el enlace de confirmación aparece en el registro del servidor.
2. **Pon la dirección** en `web/index.html` (`<meta name="tonalli-api" …>`).
3. **CORS**: `CORS_ORIGINS` del servidor debe contener **exactamente** los orígenes desde donde se carga la interfaz:
   - App Android: `https://appassets.androidplatform.net`
   - Tu sitio web: `https://tonalli.tu-dominio.com`
4. **Android**: no hay que tocar Kotlin. La app ya usa `usesCleartextTraffic="false"`, así que la API **debe** ser HTTPS.
   Vuelve a compilar para que el APK lleve el `index.html` nuevo (`Build > Rebuild Project`).
5. **Correo**: en producción usa `MAIL_DRIVER=resend` (o el que configures) y `PUBLIC_BASE_URL` apuntando a la **web**,
   porque el enlace del correo se abre en el navegador (`/#/verify/…`). Sin correo real nadie puede verificar su cuenta.

## 5. Cómo subirlo (producción)

1. Servidor: VPS con Docker + Caddy (HTTPS automático), según `backend/deploy/` y `backend/README.md`.
   Disco persistente para SQLite y copias de seguridad.
2. Web: publica la carpeta `web/` en Netlify, Cloudflare Pages o GitHub Pages (ver README principal, sección 5).
3. Cambia `content=""` por la dirección real **antes** de publicar la web y de compilar el APK.
4. Prueba con una cuenta real: registro → correo → confirmar → entrar → modo Cazador → publicar → votar desde otra cuenta.

## 6. Pruebas hechas

- Servidor: 62 pruebas automáticas (`npm test`) + `npm run check:security`.
- Integración: `backend/scripts/e2e-navegador.js` abre un Chromium real contra el servidor real y recorre
  33 comprobaciones: acceso, registro, correo de confirmación, login, errores, lista, detalle, voto (poner y quitar),
  comentario (con la regla de modo Cazador), reporte, alerta con notificación, publicar con karma, escáner por código,
  recarga de página con sesión recuperada, cierre de sesión, restablecer contraseña, renovación automática de un
  token vencido y escape de HTML malicioso.
  ```bash
  SERVER_LOG=ruta/al/log-del-servidor.txt OUT=./capturas node scripts/e2e-navegador.js
  ```
- Modo demostración: se verificó que sigue funcionando sin servidor.

## 7. Límites conocidos (honestos)

- **No se probó en un teléfono ni se compiló el proyecto Android**: la prueba fue en Chromium de escritorio.
  El WebView de Android usa la misma interfaz, pero el origen `appassets.androidplatform.net` y el CORS deben
  comprobarse en el dispositivo.
- Se cargan las 200 ofertas más recientes (4 páginas de 50). Falta paginación infinita.
- El "Calientes" ordena localmente sobre esas 200.
- La cola de moderación (`/mod/queue`) existe en la API, pero esta interfaz aún no tiene su pantalla.
- No hay notificaciones push: la app consulta cada minuto mientras está abierta.
- El historial de karma no tiene endpoint; la lista de movimientos queda vacía en modo servidor.
- Falta una política de contenido (CSP) en el HTML; el servidor sí la aplica a sus respuestas.
- El token de renovación está en `localStorage`. En el WebView de Android es privado a la app; en la web, un XSS
  podría leerlo, por eso todo texto se escapa. A futuro: cookie `HttpOnly` desde un dominio propio.
- Los niveles del servidor se alinearon con la app: 4 niveles (0, 50, 150, 400 puntos).
