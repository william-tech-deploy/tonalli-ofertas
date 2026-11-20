# Conectar Tonalli (app nativa) con Supabase

## 1. Crear el proyecto
1. En supabase.com > **New organization**: nombre `Tonalli`, tipo *Personal*, plan **Free**.
2. **New project**: nombre `tonalli`, región la más cercana (East US), y una contraseña de base de datos larga. Guárdala en un gestor de contraseñas; la app no la usa.
3. Espera 1 a 2 minutos a que el proyecto quede listo.

## 2. Crear las tablas y la seguridad
1. Menú izquierdo > **SQL Editor** > **New query**.
2. Pega todo el contenido de `supabase/esquema.sql` y pulsa **Run**. Debe decir *Success. No rows returned*.
3. Revisa en **Table Editor** que existan `perfiles`, `ofertas` y `votos`, cada una con la etiqueta **RLS enabled**.

## 3. Configurar el registro
**Authentication > Sign In / Providers > Email**:
- *Enable email provider*: activado.
- *Confirm email*: para la demo de clase puedes **desactivarlo** (la cuenta queda lista al registrarse). En producción, **actívalo** y configura un SMTP propio, porque el correo incluido de Supabase permite muy pocos mensajes por hora.
- *Minimum password length*: **10**.

## 4. Copiar la dirección y la clave pública
**Project Settings > API Keys** (o **Data API**):
- **Project URL**: `https://xxxxxxxx.supabase.co`
- **Publishable key** (`sb_publishable_...`) o, en proyectos antiguos, **anon public**.

> Nunca uses la **secret key** ni la **service_role** en la app ni las compartas: saltan todas las reglas de seguridad.

## 5. Ponerlas en Android Studio
Abre `local.properties` en la raíz del proyecto (ya existe; Android Studio escribe ahí `sdk.dir`) y agrega al final:
```
supabase.url=https://xxxxxxxx.supabase.co
supabase.key=sb_publishable_xxxxxxxxxxxxxxxx
```
Luego **Sync Project with Gradle Files** y **Run**. `local.properties` no se sube a GitHub.

## 6. Probar
1. Perfil > **Crear cuenta**.
2. En Supabase > **Authentication > Users** aparece la cuenta, y en **Table Editor > perfiles** su nombre de usuario.
3. Publica una oferta: aparece en la tabla `ofertas`.
4. Desde otra cuenta (otro teléfono o el emulador), vota esa oferta: sube `votos_up` y el karma del autor.

## Notas
- El plan gratis **pausa el proyecto tras 7 días sin uso**. Antes de presentar, ábrelo en supabase.com para reactivarlo.
- Las fotos se quedan en el teléfono; no se suben a Supabase.
- Las alertas de precio se guardan solo en el teléfono (SQLite).
