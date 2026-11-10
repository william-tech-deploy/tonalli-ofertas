# ---------- Etapa 1: compilar ----------
# Imagen con JDK 17 y Maven. Se descarga todo aquí y no llega a la imagen final.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
# Primero solo el pom: Docker reutiliza esta capa mientras no cambien las dependencias.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# ---------- Etapa 2: ejecutar ----------
# Solo el JRE: imagen más pequeña y menos superficie de ataque.
FROM eclipse-temurin:17-jre
# curl lo usa el healthcheck de docker-compose.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
# Usuario sin privilegios: si alguien vulnera la app, no es administrador del contenedor.
RUN useradd --system --no-create-home --shell /usr/sbin/nologin app
USER app
WORKDIR /app
COPY --from=build /src/target/tonalli-web.war /app/tonalli-web.war
EXPOSE 8080
# El sitio también corre como WAR ejecutable (Tomcat va embebido).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/tonalli-web.war"]
