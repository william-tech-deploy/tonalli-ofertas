# Tonalli (tonalli-ofertas)

Ofertas con temperatura real. Un repositorio, cuatro piezas:

| Carpeta | Qué es |
|---|---|
| `web/` | Interfaz web de un solo archivo (la misma que empaqueta Android). Se publica en GitHub Pages. |
| `android/` | App Android (Kotlin + WebView). |
| `backend/` | API segura en Node 22 + SQLite. |
| `tonalli-web/` | Sitio Spring Boot + JSP + MySQL (proyecto de Frameworks). |
| `docs/` | Documentación (conexión app ↔ API, despliegue). |

Interfaz en vivo: se publica con GitHub Pages (ver pestaña Actions). Sin servidor conectado funciona en modo demostración.

---

# Tonalli: app Android + web

> **Cambio de nombre (Bajó → Tonalli).** La app, el paquete (`app.tonalli`), la web, el backend y el sitio de la materia
> usan ahora el nombre y la marca **Tonalli**. El kit completo (logos, tokens, manual) está en `design/`. En la app web
> ya están aplicados los colores y tipografías del manual, los tres estados de temperatura con palabra e icono
> (Caliente, Tibia, Fría), los niveles de reputación Chispa, Brasa, Fogata y Hoguera, y el ícono adaptativo de Android.
> Si ya habías instalado la versión anterior, desinstálala: el identificador cambió y Android la trata como otra app.

Una sola app web (`web/index.html`) que se usa de dos formas:

- **En el teléfono:** `android/` es un proyecto de Android Studio (Kotlin) que empaqueta esa web dentro de la app y le agrega el escáner de códigos de barras de Google, el botón Atrás y los enlaces externos.
- **En internet:** la carpeta `web/` se sube tal cual a un hosting estático.

```
tonalli-proyecto/
├── web/
│   └── index.html            ← la app (HTML + CSS + JS, sin librerías)
└── android/                  ← abre ESTA carpeta en Android Studio
    ├── app/src/main/java/app/tonalli/MainActivity.kt
    ├── app/src/main/AndroidManifest.xml
    ├── app/src/main/res/      (tema, colores, ícono)
    ├── app/build.gradle.kts   (lee los assets desde ../../web)
    └── gradlew, gradle/       (Gradle 8.14.3)
```

> **Importante:** no muevas `web/` ni `android/` por separado. El proyecto Android lee la web desde `../../web`.

## 1. Abrir y ejecutar en Android Studio

1. Abre Android Studio y elige **Open**. Selecciona la carpeta `android` (no `tonalli-proyecto`).
2. Espera el **Gradle Sync**. La primera vez descarga dependencias y puede pedirte instalar el **Android SDK Platform 36**: acepta. Si sugiere actualizar el plugin de Android Gradle, puedes aceptar.
3. Elige un emulador (Device Manager) o conecta un teléfono con depuración USB.
4. Pulsa **Run ▶**.

**Si el sync falla con "Incompatible Gradle JVM version"** (Android Studio usa Java 25 y Gradle 8.14 solo llega a Java 24):
pulsa el enlace "Apply compatible Gradle JDK configuration and sync", o en `File > Settings > Build Tools > Gradle` elige **Gradle JDK = jbr-21** (o un JDK 17/21) y sincroniza de nuevo.
Abre siempre la carpeta `tonalli-proyecto/android` **sin separarla** de `tonalli-proyecto/web`: la tarea `syncWeb` copia ahí la interfaz.

Requisitos: Android Studio reciente (JDK 17 o 21, no 25) y Android 8.0 o superior en el dispositivo (`minSdk 26`).

## 2. Cómo se relacionan la web y la app

- Para cambiar la app, edita **`web/index.html`** y vuelve a ejecutar. Los cambios aparecen en la web y en Android.
- La app Android abre `https://appassets.androidplatform.net/assets/index.html`. Es una dirección interna: los archivos están dentro del APK y funcionan sin internet. Las tipografías de Google Fonts sí necesitan conexión; sin ella se usan tipografías del sistema.
- Los datos (ofertas, votos, karma, alertas) se guardan en el teléfono (`localStorage`). Siguen siendo datos de ejemplo: **todavía no hay servidor ni base de datos compartida**.
- En el navegador de escritorio también puedes probar `web/index.html` abriéndolo directo.

### Qué agrega Android respecto a la web

| Función | Cómo funciona |
| --- | --- |
| Botón **Escanear con la cámara** (pantalla Escáner) | La página llama a `window.TonalliNative.scanBarcode()`; `MainActivity` abre el escáner de Google (ML Kit) y devuelve el número a `window.onNativeBarcode`. Solo aparece dentro de la app. |
| Botón Atrás | Llama a `window.nativeBack()`; si la página no tiene a dónde volver, se cierra la app. |
| Elegir foto | Selector de archivos de Android para las fotos de las ofertas. |
| Enlaces de tiendas | Se abren en el navegador del teléfono. |
| Modo claro y oscuro | Sigue el del teléfono. |

El escáner de Google necesita **Google Play Services** en el dispositivo. Los emuladores con "Google Play" lo traen.

## 3. Cambiar nombre, paquete e ícono

- **Nombre:** `app/src/main/res/values/strings.xml`.
- **Identificador (`app.tonalli`):** cámbialo en `app/build.gradle.kts` (`namespace` y `applicationId`) y renombra la carpeta `java/app/tonalli` y la línea `package` de `MainActivity.kt`. Hazlo **antes** de publicar: en Google Play no se puede cambiar después.
- **Ícono:** `res/drawable/ic_launcher_foreground.xml` y el color `ic_background` en `res/values/colors.xml`. En Android Studio: clic derecho en `res` → New → Image Asset.

## 4. Publicar la app en Google Play

La app no se "sube a un servidor": se publica en Google Play (o se reparte como archivo APK).

1. Crea una cuenta en [Google Play Console](https://play.google.com/console). Tiene una cuota única de registro; confirma el monto vigente al registrarte.
2. En Android Studio: **Build → Generate Signed App Bundle / APK → Android App Bundle**. Crea un keystore y **guárdalo y respáldalo**: sin él no puedes actualizar la app.
3. Sube el `.aab` en Play Console, completa la ficha (descripción, capturas, ícono 512 px), el cuestionario de contenido y la **política de privacidad** (una página pública; puede estar en tu hosting web).
4. Las cuentas personales creadas después del 13 de noviembre de 2023 deben hacer antes una **prueba cerrada con al menos 12 testers durante 14 días seguidos** para pedir acceso a producción ([requisito oficial](https://support.google.com/googleplay/android-developer/answer/14151465)). Las cuentas de organización tienen otros requisitos; revísalos en Play Console.
5. Para probar sin publicar: **Build → Build APK** y pasa el archivo `app-debug.apk` al teléfono.

Google exige que las apps nuevas apunten a una versión reciente de Android; este proyecto ya usa `targetSdk 36`. Revisa en Play Console si la exigencia cambió.

## 5. Dónde subir la web

Hoy la web es un solo archivo estático, así que no necesita un servidor propio. Cualquiera de estos le sirve y trae HTTPS:

| Opción | Cuándo conviene | Cómo se sube |
| --- | --- | --- |
| **Netlify** | La más fácil para empezar | Entra a Netlify Drop y arrastra la carpeta `web` |
| **Cloudflare Pages** | Sitio rápido con dominio propio | Conecta un repositorio de GitHub o sube la carpeta `web` |
| **GitHub Pages** | Gratis, ligado al repositorio del proyecto | Sube `web/` a un repositorio y activa Pages |
| **Firebase Hosting** | Si luego usarás Firebase para cuentas y base de datos | `firebase init hosting` con carpeta pública `web`, luego `firebase deploy` |

Todas tienen plan gratuito; revisa los límites vigentes antes de decidir.

## 6. Cuando necesites un servidor de verdad

Para que los votos, ofertas, karma y alertas sean compartidos entre usuarios hace falta un **backend con base de datos** (el reporte técnico lo detalla: modelo de datos y endpoints). Dos caminos:

- **Más rápido:** Supabase (PostgreSQL, cuentas, almacenamiento de fotos y funciones de servidor) o Firebase (Firestore, Auth, Storage y Cloud Messaging para notificaciones push).
- **Más control:** una API propia en Node.js con PostgreSQL, alojada en Render, Railway o Fly.io.

**Ya está hecho con la API propia** (carpeta `backend/`): `web/index.html` tiene dos modos. Sin dirección de API funciona con datos de ejemplo; con `<meta name="tonalli-api" content="https://...">` usa el servidor. Ver `docs/CONEXION-API.md`. Las reglas de votos, karma y veredicto deben calcularse en el servidor para que nadie pueda alterarlas desde el teléfono.

## 7. Estado de esta entrega

- Se comprobó que el JavaScript no tiene errores de sintaxis, que los XML están bien formados y que los ganchos nativos (`nativeBack`, `onNativeBarcode`) responden como se espera en una prueba simulada.
- **No se compiló el proyecto Android**: el entorno donde se armó no tiene acceso al Android SDK ni a los repositorios de Google. Es posible que Android Studio proponga ajustar versiones al sincronizar. Si aparece un error, copia el mensaje completo y se corrige.
- El escáner de Google y el selector de fotos requieren probarse en un dispositivo real.

---

## Backend seguro (nuevo)

La carpeta `backend/` contiene la API (Node 22 + SQLite, sin dependencias). Empieza por `backend/README.md`.
El documento "Tonalli API: seguridad, código y despliegue" explica cada grupo de código, la rúbrica seguida
(OWASP ASVS 5.0, API Security Top 10 2023, NIST 800-63B), cómo aplicarlo y cómo subirlo.
Estado: 62 pruebas pasan. **La interfaz (`web/index.html`) ya se conecta a la API**: cuentas, ofertas, votos, comentarios,
alertas y notificaciones. Cómo activarlo y desplegarlo: `docs/CONEXION-API.md`. Prueba de punta a punta: `backend/scripts/e2e-navegador.js` (33/33).
