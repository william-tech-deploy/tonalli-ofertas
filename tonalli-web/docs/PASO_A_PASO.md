# Paso a paso: cómo se construyó Tonalli Web, semana por semana

El proyecto entregado es la **etapa final** (Spring Boot). Esta guía dice qué parte corresponde a cada semana y
en qué orden conviene construirla y hacer los commits, para que el historial de GitHub cuente la misma historia
que el temario. Los temas por semana son los del cronograma de la materia; el orden de trabajo es una propuesta.

| Semanas | Tema | Qué construir (en este orden) | Dónde está en el código final |
| --- | --- | --- | --- |
| S1–S4 | Java orientado a objetos y Servlets | 1. Proyecto Maven en IntelliJ. 2. Clases del dominio con encapsulación (`Oferta`, `Categoria`, `Usuario`). 3. Un Servlet que lista ofertas en memoria. 4. Validación manual de formularios. **Práctica I (S4)** | `model/`, `dto/Formato.java` |
| S5–S7 | JSP, JSTL y base de datos | 1. Pasar las páginas a JSP con JSTL (`c:forEach`, `c:out`). 2. Crear la base con `docs/esquema.sql`. 3. Clases DAO con `PreparedStatement`. 4. Separar en tres capas: `model`, `dao`, `controller`. | `webapp/WEB-INF/jsp/`, `docs/esquema.sql` |
| S8–S10 | JPA | 1. Anotar las clases como entidades (`@Entity`, `@ManyToOne`). 2. Reemplazar los DAO por repositorios. 3. CRUD completo de ofertas y categorías. **Práctica II (S8)** | `model/`, `repository/` |
| S11–S12 | Servicio REST | 1. Exponer `GET /api/ofertas`. 2. Respuestas de error en JSON. 3. Capa `service` para que JSP y REST compartan reglas. **Práctica III (S12)** | `api/`, `service/` |
| S13–S15 | Spring Boot y gestión de usuarios | 1. Registro con BCrypt. 2. Spring Security: login, roles, CSRF. 3. Correo de verificación. 4. Moderación y administración. 5. Gráficos. | `security/`, `web/`, `service/UsuarioService`, `webapp/.../admin-*.jsp` |
| S16 | Presentación del proyecto | Docker Compose, README, pruebas pasando, demostración. **Proyecto (S16)** | `Dockerfile`, `docker-compose.yml`, `src/test/` |

## Convención de commits sugerida

Un commit por paso de la tabla, con mensajes en imperativo: `Agrega entidad Oferta`, `Migra DAO a repositorios JPA`,
`Protege /admin con rol ADMIN`. Antes de cada práctica evaluada, ejecuta `mvn test` y `docker compose up --build`
en una máquina limpia.

## Regla de seguridad desde la semana 5 (no esperar a la 13)

- Toda salida en JSP con `<c:out>`; nunca `${...}` suelto dentro de HTML.
- Toda consulta con parámetros (`PreparedStatement` o JPA), nunca texto concatenado.
- Ninguna contraseña ni clave en el código: variables de entorno.
