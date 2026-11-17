# Tonalli Web

> **Marca.** Esta web usa la identidad Tonalli (antes Bajó): colores y logo del manual en `design/`. Las tipografías de la
> marca (Plus Jakarta Sans y Bricolage Grotesque) no se cargan desde internet porque la política CSP solo permite recursos
> propios; para usarlas, copia los `.woff2` a `src/main/resources/static/fonts/` y declara `@font-face` en `estilo.css`.

Sitio web de ofertas colaborativas para la materia **Utilización de Frameworks para el desarrollo de software**
(ITCA-FEPADE Santa Ana). Es la versión web de la app Android Tonalli: catálogo público, votos («temperatura»),
comentarios, reportes, alertas, propuestas de categorías, panel de moderación y panel de administración con gráficos.

**Stack:** Java 17 · Spring Boot 3.5 (Web, Data JPA, Security, Validation, Mail, Actuator) · JSP + JSTL · MySQL 8.4 · Docker.

> **Estado de verificación.** El código se escribió en un entorno sin acceso a Maven Central, así que **el proyecto no se
> compiló ni se ejecutó ahí**. Sí se verificó: la sintaxis de todo el Java (javac no reporta errores de sintaxis), la
> lógica pura (formato, bloqueo por intentos, descuentos: 17 comprobaciones), el script de los gráficos en un navegador
> real, y que el `pom.xml`, el `docker-compose.yml` y las JSP estén bien formados. La primera compilación real la haces
> tú; si algo falla, mira «Si algo falla» más abajo.

## 1. Probarlo en IntelliJ IDEA (sin instalar MySQL ni Docker)

1. **File → Open** y elige el archivo `pom.xml`; marca «Open as Project». IntelliJ descarga las dependencias.
2. En **Project Structure → SDK** usa un JDK 17 o superior.
3. Abre `TonalliWebApplication` → **Edit Configurations → Active profiles:** `dev` → Run.
4. Entra a <http://localhost:8080>.

El perfil `dev` usa una base H2 en memoria con datos de ejemplo y **credenciales públicas de ejemplo**
(no usar fuera de tu computadora):

| Rol | Correo | Contraseña |
| --- | --- | --- |
| Administrador | `admin@tonalli.local` | `CambiaEstaClave#2026` |
| Usuario | `demo@tonalli.local` | `DemoClave#2026!` |

Por consola: `mvn spring-boot:run -Dspring-boot.run.profiles=dev`

## 2. Pruebas

```
mvn test
```

Hay pruebas unitarias (formato, bloqueo por intentos, token, peso del voto), de reglas de negocio con H2
(`OfertaFlujoTest`) y de seguridad de punta a punta (`SeguridadWebTest`): rutas protegidas, CSRF, cabeceras,
login correcto e incorrecto y bloqueo tras 5 fallos. `VistasJspTest` arranca el Tomcat real y dibuja los JSP de
verdad: detecta bucles de redirección y acentos rotos, que MockMvc no ve.

## 3. Desplegar con Docker (MySQL + aplicación)

**En tu computadora (Windows):** el script `crear-env.ps1` escribe el `.env` con claves al azar y te las muestra.

```
powershell -ExecutionPolicy Bypass -File .\crear-env.ps1
docker compose up -d --build
docker compose ps            # db y web deben decir (healthy)
```

**En Linux, macOS o un servidor:** `cp .env.example .env` y cambia TODAS las contraseñas antes de levantar.

Abre <http://localhost:8080>. Si cambias la contraseña de MySQL después del primer arranque, borra también el
volumen (`docker compose down -v`): MySQL solo lee esas variables la primera vez.

**Datos de ejemplo:** con `TONALLI_DEMO=true` en el `.env` (el script lo pone), el sitio carga 5 usuarios y 15 ofertas
de tiendas de El Salvador la primera vez que arranca con la base vacía. Los precios y enlaces se copiaron de las
páginas públicas de las tiendas el 10 de noviembre de 2026 y pueden haber cambiado. Los cinco usuarios
(`demo@`, `ana.martinez@`, `carlos.rivas@`, `sofia.hernandez@` y `diego.flores@tonalli.local`) comparten la contraseña
`TONALLI_DEMO_PASSWORD`. En un servidor público deja `TONALLI_DEMO=false`. Para publicarlo en internet con HTTPS automático:

1. Apunta un dominio al servidor (registro A) y abre los puertos 80 y 443.
2. En `.env` define `DOMINIO`, `BASE_URL=https://tu-dominio`, `COOKIE_SECURE=true`.
3. `docker compose --profile https up -d --build`

**Correo de verificación:** en producción define `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME` y
`SPRING_MAIL_PASSWORD` con un servicio SMTP de terceros. Sin SMTP, el enlace de verificación solo se escribe en el
registro del servidor (útil en desarrollo, inútil para usuarios reales).

## 4. Rutas

| Ruta | Quién | Qué hace |
| --- | --- | --- |
| `/`, `/ofertas`, `/ofertas/{id}` | Todos | Catálogo y detalle |
| `/login`, `/registro`, `/verificar` | Todos | Acceso y verificación de correo |
| `/ofertas/nueva`, `/alertas`, `/propuestas` | Con sesión | Publicar, alertas, proponer categorías |
| `/moderacion` | MODERADOR, ADMIN | Cola de reportes, restaurar o eliminar |
| `/admin/usuarios`, `/admin/categorias`, `/admin/graficos` | ADMIN | Roles, categorías, gráficos |
| `GET /api/ofertas`, `GET /api/ofertas/{id}` | Todos | API REST (JSON) |
| `POST /api/ofertas`, `POST /api/ofertas/{id}/voto` | Con sesión + token CSRF | Crear y votar |
| `GET /api/estadisticas/**` | ADMIN | Datos de los gráficos |
| `/actuator/health` | Todos | Estado del servicio |

## 5. Estructura

```
src/main/java/com/tonalli/web/
  model/        entidades JPA y enumeraciones
  repository/   interfaces de Spring Data (consultas parametrizadas)
  service/      reglas de negocio (las usan las JSP y la API por igual)
  security/     Spring Security: login, bloqueo por intentos, sesión
  web/          controladores de páginas (JSP)
  api/          controladores REST (JSON)
  dto/          formularios con validación y vistas (nunca se exponen las entidades)
  config/       datos iniciales y datos de ejemplo (solo con TONALLI_DEMO=true o el perfil dev)
src/main/webapp/WEB-INF/jsp/   vistas JSP y JSTL
src/main/resources/            propiedades y archivos estáticos (CSS y JS propios)
docs/                          esquema.sql y guía paso a paso por semanas
```

## 6. Límites conocidos

- El bloqueo por intentos de login vive en la memoria de un solo servidor; con varias instancias hay que moverlo a Redis.
- `ddl-auto=update` es cómodo para el curso; en producción real conviene `validate` con migraciones (Flyway).
- No hay «reenviar correo de verificación» ni recuperación de contraseña.
- La web tiene su propia base de datos: lo publicado en la app Android no aparece aquí.
- No se hicieron pruebas de carga: no se afirma ninguna capacidad de usuarios concurrentes.
- Los gráficos usan canvas propio, no Chart.js, para no abrir la política CSP a una CDN. Para cambiar a Chart.js,
  agrega la dependencia `org.webjars.npm:chart.js` y carga su script desde `/webjars/...` (sigue siendo `'self'`).

## 7. Si algo falla al compilar

- **`release version 17 not supported`:** el JDK de IntelliJ/Maven es anterior al 17.
- **Error de un import o método de Spring:** copia el mensaje completo; casi siempre es una diferencia menor de
  versión (el `pom.xml` fija Spring Boot 3.5.0). Prueba con la última 3.5.x.
- **`ERR_TOO_MANY_REDIRECTS` en todas las páginas:** falta la regla `dispatcherTypeMatchers(DispatcherType.FORWARD)`
  en `SecurityConfig`; sin ella el reenvío interno al JSP se trata como página protegida.
- **`web` nunca llega a `(healthy)` y `/actuator/health` dice `DOWN`:** revisa que `management.health.mail.enabled=false`
  siga en `application.properties`; sin SMTP configurado, el chequeo de correo marcaba caído todo el sitio.
- **`Could not resolve view with name ...`:** las JSP solo funcionan con `spring-boot:run`, el WAR ejecutable o Tomcat;
  no con la ejecución «sin servidor» de las pruebas (en MockMvc solo se comprueba el estado HTTP).
- **El correo no llega:** revisa `SPRING_MAIL_*`; mientras tanto, el enlace aparece en el registro del servidor.
