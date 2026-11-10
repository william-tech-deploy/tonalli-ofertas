-- Esquema MySQL de Tonalli Web, equivalente a las entidades JPA.
-- Sirve para la etapa de la semana 7 (base de datos con clases DAO, sin JPA).
-- Con JPA (semanas 9 en adelante) Hibernate crea estas tablas solo (spring.jpa.hibernate.ddl-auto=update).
-- Uso:  mysql -u root -p < docs/esquema.sql

CREATE DATABASE IF NOT EXISTS tonalliweb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE tonalliweb;

CREATE TABLE usuarios (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    correo                   VARCHAR(120) NOT NULL,
    nombre                   VARCHAR(60)  NOT NULL,
    password_hash            VARCHAR(100) NOT NULL,          -- hash BCrypt, nunca la contraseña
    rol                      VARCHAR(20)  NOT NULL,          -- USER | MODERADOR | ADMIN
    karma                    INT          NOT NULL DEFAULT 0,
    verificado               BOOLEAN      NOT NULL DEFAULT FALSE,
    token_verificacion_hash  VARCHAR(64)  NULL,              -- SHA-256 del token enviado por correo
    token_expira             DATETIME(6)  NULL,
    creado_en                DATETIME(6)  NOT NULL,
    CONSTRAINT uk_usuarios_correo UNIQUE (correo)
) ENGINE = InnoDB;

CREATE TABLE categorias (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre  VARCHAR(40) NOT NULL,
    base    BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_categorias_nombre UNIQUE (nombre)
) ENGINE = InnoDB;

CREATE TABLE ofertas (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    titulo                    VARCHAR(120) NOT NULL,
    tienda                    VARCHAR(80)  NOT NULL,
    precio_centavos           INT          NOT NULL,         -- enteros: sin errores de redondeo
    precio_anterior_centavos  INT          NULL,
    estado                    VARCHAR(20)  NOT NULL,         -- ACTIVA | OCULTA | ELIMINADA
    categoria_id              BIGINT       NOT NULL,
    autor_id                  BIGINT       NOT NULL,
    creada_en                 DATETIME(6)  NOT NULL,
    CONSTRAINT fk_ofertas_categoria FOREIGN KEY (categoria_id) REFERENCES categorias (id),
    CONSTRAINT fk_ofertas_autor     FOREIGN KEY (autor_id)     REFERENCES usuarios (id),
    INDEX ix_ofertas_estado_creada (estado, creada_en),
    INDEX ix_ofertas_categoria (categoria_id)
) ENGINE = InnoDB;

CREATE TABLE votos (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id  BIGINT NOT NULL,
    oferta_id   BIGINT NOT NULL,
    valor       INT    NOT NULL,                              -- +1 caliente, -1 frío
    peso        INT    NOT NULL DEFAULT 1,
    CONSTRAINT fk_votos_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    CONSTRAINT fk_votos_oferta  FOREIGN KEY (oferta_id)  REFERENCES ofertas (id),
    CONSTRAINT uk_votos_usuario_oferta UNIQUE (usuario_id, oferta_id)   -- un voto por usuario y oferta
) ENGINE = InnoDB;

CREATE TABLE comentarios (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    oferta_id  BIGINT       NOT NULL,
    autor_id   BIGINT       NOT NULL,
    texto      VARCHAR(500) NOT NULL,
    creado_en  DATETIME(6)  NOT NULL,
    CONSTRAINT fk_comentarios_oferta FOREIGN KEY (oferta_id) REFERENCES ofertas (id),
    CONSTRAINT fk_comentarios_autor  FOREIGN KEY (autor_id)  REFERENCES usuarios (id)
) ENGINE = InnoDB;

CREATE TABLE reportes (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    oferta_id       BIGINT      NOT NULL,
    reportante_id   BIGINT      NOT NULL,
    motivo          VARCHAR(20) NOT NULL,                     -- SPAM | PRECIO_FALSO | EXPIRADA | OTRO
    estado          VARCHAR(20) NOT NULL,                     -- ABIERTO | RESUELTO | DESCARTADO
    creado_en       DATETIME(6) NOT NULL,
    CONSTRAINT fk_reportes_oferta     FOREIGN KEY (oferta_id)     REFERENCES ofertas (id),
    CONSTRAINT fk_reportes_reportante FOREIGN KEY (reportante_id) REFERENCES usuarios (id),
    CONSTRAINT uk_reportes_reportante_oferta UNIQUE (reportante_id, oferta_id)
) ENGINE = InnoDB;

CREATE TABLE propuestas (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre     VARCHAR(40) NOT NULL,
    autor_id   BIGINT      NOT NULL,
    estado     VARCHAR(20) NOT NULL,                          -- PENDIENTE | APROBADA | RECHAZADA
    creada_en  DATETIME(6) NOT NULL,
    CONSTRAINT fk_propuestas_autor FOREIGN KEY (autor_id) REFERENCES usuarios (id)
) ENGINE = InnoDB;

CREATE TABLE propuesta_apoyos (
    propuesta_id  BIGINT NOT NULL,
    usuario_id    BIGINT NOT NULL,
    PRIMARY KEY (propuesta_id, usuario_id),                   -- un apoyo por usuario
    CONSTRAINT fk_apoyos_propuesta FOREIGN KEY (propuesta_id) REFERENCES propuestas (id)
) ENGINE = InnoDB;

CREATE TABLE alertas (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id           BIGINT      NOT NULL,
    palabra_clave        VARCHAR(60) NOT NULL,
    precio_max_centavos  INT        NULL,
    CONSTRAINT fk_alertas_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
) ENGINE = InnoDB;

INSERT INTO categorias (nombre, base) VALUES ('Supermercado', TRUE), ('Tecnología', TRUE), ('Ropa', TRUE);
