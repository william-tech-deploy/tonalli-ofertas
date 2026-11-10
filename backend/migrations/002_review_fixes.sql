-- Correcciones de la revisión de seguridad independiente.
-- C3: un mismo (usuario, tipo, referencia) paga karma una sola vez (p. ej. un voto por oferta, no por cada vuelta 1->0->1).
ALTER TABLE karma_events ADD COLUMN ref TEXT;
CREATE UNIQUE INDEX karma_once ON karma_events(user_id, kind, ref) WHERE ref IS NOT NULL;
