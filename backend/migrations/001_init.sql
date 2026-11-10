-- Esquema inicial de Tonalli. Todas las tablas son STRICT (SQLite rechaza tipos incorrectos)
-- y llevan restricciones CHECK: la base de datos es la última línea de defensa si el código falla.

CREATE TABLE users (
  id TEXT PRIMARY KEY CHECK (length(id) = 36),
  email TEXT NOT NULL UNIQUE CHECK (length(email) BETWEEN 6 AND 254 AND email = lower(email)),
  handle TEXT NOT NULL COLLATE NOCASE UNIQUE CHECK (length(handle) BETWEEN 3 AND 20 AND handle NOT GLOB '*[^A-Za-z0-9_.]*'),
  password_hash TEXT NOT NULL,
  email_verified_at INTEGER,
  mode TEXT NOT NULL DEFAULT 'rastreador' CHECK (mode IN ('rastreador', 'cazador')),
  role TEXT NOT NULL DEFAULT 'user' CHECK (role IN ('user', 'moderador', 'admin')),
  lang TEXT NOT NULL DEFAULT 'es' CHECK (lang IN ('es', 'en', 'pt', 'nah')),
  karma INTEGER NOT NULL DEFAULT 0 CHECK (karma >= 0),
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'suspended')),
  created_at INTEGER NOT NULL,
  password_changed_at INTEGER NOT NULL
) STRICT;

-- Datos internos de arranque (p. ej. la "huella" del pepper para detectar que cambió por error).
CREATE TABLE system_meta (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
) STRICT;

CREATE TABLE sessions (
  id TEXT PRIMARY KEY CHECK (length(id) = 36),
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  revoked_at INTEGER,
  device_name TEXT CHECK (device_name IS NULL OR length(device_name) <= 60),
  last_ip TEXT
) STRICT;
CREATE INDEX sessions_user ON sessions(user_id);

CREATE TABLE refresh_tokens (
  token_hash TEXT PRIMARY KEY,
  session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  expires_at INTEGER NOT NULL,
  used_at INTEGER
) STRICT;
CREATE INDEX refresh_session ON refresh_tokens(session_id);

CREATE TABLE access_tokens (
  token_hash TEXT PRIMARY KEY,
  session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  expires_at INTEGER NOT NULL
) STRICT;
CREATE INDEX access_session ON access_tokens(session_id);

CREATE TABLE one_time_tokens (
  token_hash TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  purpose TEXT NOT NULL CHECK (purpose IN ('verify_email', 'reset_password')),
  expires_at INTEGER NOT NULL,
  used_at INTEGER,
  created_at INTEGER NOT NULL
) STRICT;
CREATE INDEX one_time_user ON one_time_tokens(user_id, purpose);

-- Contadores de intentos fallidos (inicio de sesión, correos, verificación de contraseña).
CREATE TABLE auth_throttle (
  key TEXT PRIMARY KEY,
  count INTEGER NOT NULL,
  window_start INTEGER NOT NULL
) STRICT;

CREATE TABLE categories (
  id TEXT PRIMARY KEY CHECK (id GLOB '[a-z0-9]*' AND length(id) BETWEEN 2 AND 40),
  name TEXT NOT NULL CHECK (length(name) BETWEEN 2 AND 40),
  is_base INTEGER NOT NULL DEFAULT 0 CHECK (is_base IN (0, 1)),
  created_at INTEGER NOT NULL
) STRICT;
INSERT INTO categories(id, name, is_base, created_at) VALUES
  ('super', 'Supermercado', 1, 0),
  ('tech', 'Tecnología', 1, 0),
  ('ropa', 'Ropa', 1, 0);

CREATE TABLE offers (
  id TEXT PRIMARY KEY CHECK (length(id) = 36),
  author_id TEXT REFERENCES users(id) ON DELETE SET NULL,
  category_id TEXT NOT NULL REFERENCES categories(id),
  title TEXT NOT NULL CHECK (length(title) BETWEEN 3 AND 100),
  title_norm TEXT NOT NULL,
  store TEXT NOT NULL CHECK (length(store) BETWEEN 2 AND 60),
  store_norm TEXT NOT NULL,
  product_key TEXT NOT NULL,
  ean TEXT CHECK (ean IS NULL OR (length(ean) = 13 AND ean NOT GLOB '*[^0-9]*')),
  price_now_cents INTEGER NOT NULL CHECK (price_now_cents BETWEEN 1 AND 10000000),
  price_old_cents INTEGER NOT NULL CHECK (price_old_cents BETWEEN 1 AND 10000000),
  link_url TEXT CHECK (link_url IS NULL OR length(link_url) <= 500),
  photo_id TEXT,
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'visible', 'hidden', 'removed')),
  up REAL NOT NULL DEFAULT 0,
  down REAL NOT NULL DEFAULT 0,
  temperature INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  visible_at INTEGER,
  CHECK (price_now_cents < price_old_cents)
) STRICT;
CREATE INDEX offers_feed_hot ON offers(category_id, status, temperature DESC, created_at DESC);
CREATE INDEX offers_feed_new ON offers(category_id, status, created_at DESC);
CREATE INDEX offers_ean ON offers(ean) WHERE ean IS NOT NULL;
CREATE INDEX offers_author ON offers(author_id, created_at);

CREATE TABLE photos (
  id TEXT PRIMARY KEY CHECK (length(id) = 36),
  offer_id TEXT NOT NULL REFERENCES offers(id) ON DELETE CASCADE,
  owner_id TEXT REFERENCES users(id) ON DELETE SET NULL,
  data BLOB NOT NULL CHECK (length(data) BETWEEN 100 AND 1048576),
  width INTEGER NOT NULL,
  height INTEGER NOT NULL,
  created_at INTEGER NOT NULL
) STRICT;
CREATE INDEX photos_offer ON photos(offer_id);

CREATE TABLE votes (
  offer_id TEXT NOT NULL REFERENCES offers(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  value INTEGER NOT NULL CHECK (value IN (-1, 1)),
  weight REAL NOT NULL CHECK (weight > 0 AND weight <= 1),
  created_at INTEGER NOT NULL,
  PRIMARY KEY (offer_id, user_id)
) STRICT;
CREATE INDEX votes_user ON votes(user_id);

CREATE TABLE comments (
  id TEXT PRIMARY KEY CHECK (length(id) = 36),
  offer_id TEXT NOT NULL REFERENCES offers(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  body TEXT NOT NULL CHECK (length(body) BETWEEN 1 AND 300),
  created_at INTEGER NOT NULL
) STRICT;
CREATE INDEX comments_offer ON comments(offer_id, created_at);

CREATE TABLE reports (
  id TEXT PRIMARY KEY CHECK (length(id) = 36),
  offer_id TEXT NOT NULL REFERENCES offers(id) ON DELETE CASCADE,
  reporter_id TEXT REFERENCES users(id) ON DELETE SET NULL,
  reason TEXT NOT NULL CHECK (reason IN ('spam', 'link', 'illegal', 'fake', 'other')),
  detail TEXT CHECK (detail IS NULL OR length(detail) <= 200),
  status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'upheld', 'dismissed')),
  created_at INTEGER NOT NULL,
  resolved_at INTEGER,
  resolved_by TEXT,
  UNIQUE (offer_id, reporter_id)
) STRICT;
CREATE INDEX reports_status ON reports(status, created_at);

-- Historial de precios OBSERVADOS (de ahí sale el veredicto "rebaja real / engañosa").
CREATE TABLE price_points (
  id INTEGER PRIMARY KEY,
  product_key TEXT NOT NULL,
  store_norm TEXT NOT NULL,
  price_cents INTEGER NOT NULL CHECK (price_cents BETWEEN 1 AND 10000000),
  observed_at INTEGER NOT NULL,
  user_id TEXT REFERENCES users(id) ON DELETE SET NULL,
  offer_id TEXT REFERENCES offers(id) ON DELETE SET NULL
) STRICT;
CREATE INDEX price_points_lookup ON price_points(product_key, store_norm, observed_at);

CREATE TABLE category_proposals (
  id TEXT PRIMARY KEY CHECK (length(id) = 36),
  name TEXT NOT NULL CHECK (length(name) BETWEEN 3 AND 30),
  name_norm TEXT NOT NULL UNIQUE,
  why TEXT CHECK (why IS NULL OR length(why) <= 200),
  proposer_id TEXT REFERENCES users(id) ON DELETE SET NULL,
  status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'created', 'rejected')),
  votes INTEGER NOT NULL DEFAULT 0 CHECK (votes >= 0),
  created_at INTEGER NOT NULL
) STRICT;
CREATE TABLE category_votes (
  proposal_id TEXT NOT NULL REFERENCES category_proposals(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (proposal_id, user_id)
) STRICT;

CREATE TABLE alerts (
  id TEXT PRIMARY KEY CHECK (length(id) = 36),
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  keyword TEXT NOT NULL CHECK (length(keyword) BETWEEN 2 AND 40),
  keyword_norm TEXT NOT NULL,
  max_price_cents INTEGER NOT NULL CHECK (max_price_cents BETWEEN 1 AND 10000000),
  active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
  created_at INTEGER NOT NULL
) STRICT;
CREATE INDEX alerts_user ON alerts(user_id);
CREATE TABLE alert_hits (
  alert_id TEXT NOT NULL REFERENCES alerts(id) ON DELETE CASCADE,
  offer_id TEXT NOT NULL REFERENCES offers(id) ON DELETE CASCADE,
  PRIMARY KEY (alert_id, offer_id)
) STRICT;

CREATE TABLE notifications (
  id TEXT PRIMARY KEY CHECK (length(id) = 36),
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kind TEXT NOT NULL CHECK (kind IN ('alert', 'reply', 'category', 'hidden')),
  params TEXT NOT NULL CHECK (length(params) <= 1000),
  read_at INTEGER,
  created_at INTEGER NOT NULL
) STRICT;
CREATE INDEX notifications_user ON notifications(user_id, created_at DESC);

-- El karma solo se calcula en el servidor, con topes diarios por tipo de acción.
CREATE TABLE karma_events (
  id INTEGER PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kind TEXT NOT NULL,
  points INTEGER NOT NULL CHECK (points > 0),
  created_at INTEGER NOT NULL
) STRICT;
CREATE INDEX karma_user ON karma_events(user_id, kind, created_at);

-- Bitácora de seguridad: solo se puede AGREGAR; no se puede editar ni borrar antes de 180 días.
CREATE TABLE audit_log (
  id INTEGER PRIMARY KEY,
  ts INTEGER NOT NULL,
  event TEXT NOT NULL,
  user_id TEXT,
  ip TEXT,
  detail TEXT
) STRICT;
CREATE INDEX audit_ts ON audit_log(ts);
CREATE INDEX audit_user ON audit_log(user_id, ts);
CREATE TRIGGER audit_no_update BEFORE UPDATE ON audit_log
BEGIN
  SELECT RAISE(ABORT, 'audit_log es de solo anexar');
END;
CREATE TRIGGER audit_retention BEFORE DELETE ON audit_log
WHEN OLD.ts > (CAST(strftime('%s', 'now') AS INTEGER) - 15552000) * 1000
BEGIN
  SELECT RAISE(ABORT, 'audit_log: retención mínima de 180 días');
END;
