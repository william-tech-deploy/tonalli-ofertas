# Imagen mínima: Node 22 (slim), SIN dependencias npm (no hay "npm install"), usuario sin privilegios.
# Para fijar la imagen por huella (recomendado): docker pull node:22-bookworm-slim && usa node:22-bookworm-slim@sha256:<huella>
FROM node:22-bookworm-slim
ENV NODE_ENV=production
WORKDIR /app
COPY --chown=root:root package.json ./
COPY --chown=root:root src ./src
COPY --chown=root:root migrations ./migrations
COPY --chown=root:root scripts ./scripts
# Solo /app/data (la base de datos) es escribible, y solo por el usuario "node".
RUN mkdir -p /app/data && chown node:node /app/data && chmod 700 /app/data
USER node
EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD node -e "fetch('http://127.0.0.1:3000/ready').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
# Modelo de permisos de Node: el proceso solo puede LEER /app y ESCRIBIR /app/data; sin hijos, sin workers.
CMD ["node", "--disable-proto=delete", "--max-http-header-size=16384", "--experimental-permission", "--allow-fs-read=/app", "--allow-fs-write=/app/data", "src/server.js"]
