'use strict';
/**
 * Nuevas categorías por votación de la comunidad.
 *  - Proponer exige modo Cazador, correo verificado, nivel mínimo (karma) y cuenta con antigüedad.
 *  - Un voto por persona (clave primaria compuesta) y la propuesta se convierte en categoría a los 500 votos.
 *  - La creación ocurre dentro de la misma transacción que el voto que alcanza la meta: no hay carreras.
 */
const { sql } = require('../db');
const { uuid } = require('../lib/crypto');
const { E } = require('../lib/errors');
const { normText } = require('../lib/text');
const { CATEGORY_VOTES_GOAL, PROPOSE_MIN_KARMA } = require('../domain/karma');
const { iso } = require('./dto');

const MAX_OPEN_PER_USER = 3;

const LIST = sql`SELECT p.id, p.name, p.why, p.votes, p.created_at, u.handle AS proposer,
    EXISTS (SELECT 1 FROM category_votes cv WHERE cv.proposal_id = p.id AND cv.user_id = :me) AS voted
  FROM category_proposals p LEFT JOIN users u ON u.id = p.proposer_id
  WHERE p.status = 'open' ORDER BY p.votes DESC, p.created_at DESC, p.id DESC LIMIT 50`;
const CATS = sql`SELECT id, name FROM categories`;
const PROPS = sql`SELECT name_norm FROM category_proposals`;
const OPEN_BY_USER = sql`SELECT COUNT(*) AS c FROM category_proposals WHERE proposer_id = :uid AND status = 'open'`;
const INSERT = sql`INSERT INTO category_proposals(id, name, name_norm, why, proposer_id, status, votes, created_at) VALUES (:id, :name, :nn, :why, :uid, 'open', 1, :t)`;
const VOTE_INSERT = sql`INSERT OR IGNORE INTO category_votes(proposal_id, user_id, created_at) VALUES (:p, :uid, :t)`;
const GET = sql`SELECT id, name, name_norm, proposer_id, status, votes FROM category_proposals WHERE id = :id`;
const BUMP = sql`UPDATE category_proposals SET votes = votes + 1 WHERE id = :id AND status = 'open'`;
const CREATED = sql`UPDATE category_proposals SET status = 'created' WHERE id = :id`;
const CAT_EXISTS = sql`SELECT 1 AS x FROM categories WHERE id = :id`;
const CAT_INSERT = sql`INSERT INTO categories(id, name, is_base, created_at) VALUES (:id, :name, 0, :t)`;

const slugify = (name) => normText(name).replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 36);

function createProposals({ db, now, points, notify }) {
  return {
    list(user) {
      return {
        goal: CATEGORY_VOTES_GOAL,
        items: db.all(LIST, { me: user.id }).map((r) => ({ id: r.id, name: r.name, why: r.why, votes: r.votes, voted: Boolean(r.voted), proposer: r.proposer ?? null, created_at: iso(r.created_at) })),
      };
    },

    propose(user, { name, why }) {
      if (user.karma < PROPOSE_MIN_KARMA) throw E.forbidden('level_too_low', `Necesitas ${PROPOSE_MIN_KARMA} puntos de karma para proponer una categoría`);
      const nn = normText(name);
      return db.tx(() => {
        if (db.get(OPEN_BY_USER, { uid: user.id }).c >= MAX_OPEN_PER_USER) throw E.conflict('too_many_open', `Máximo ${MAX_OPEN_PER_USER} propuestas abiertas por persona`);
        const taken = db.all(CATS).some((c) => normText(c.name) === nn) || db.all(PROPS).some((p) => p.name_norm === nn);
        if (taken) throw E.conflict('category_exists', 'Esa categoría ya existe o ya fue propuesta');
        const id = uuid();
        const t = now();
        db.run(INSERT, { id, name, nn, why: why ?? null, uid: user.id, t });
        db.run(VOTE_INSERT, { p: id, uid: user.id, t });
        points.award(user.id, 'propose');
        return { id, name, votes: 1, goal: CATEGORY_VOTES_GOAL };
      });
    },

    vote(user, id) {
      return db.tx(() => {
        const p = db.get(GET, { id });
        if (!p || p.status !== 'open') throw E.notFound('proposal_not_found', 'Propuesta no encontrada');
        if (db.run(VOTE_INSERT, { p: id, uid: user.id, t: now() }).changes !== 1) throw E.conflict('already_voted', 'Ya apoyaste esta propuesta');
        db.run(BUMP, { id });
        points.award(user.id, 'catvote');
        const votes = p.votes + 1;
        let created = null;
        if (votes >= CATEGORY_VOTES_GOAL) {
          let slug = slugify(p.name) || 'categoria';
          if (slug.length < 2) slug = `${slug}x`;
          for (let n = 2; db.get(CAT_EXISTS, { id: slug }); n++) {
            slug = `${slugify(p.name).slice(0, 34)}-${n}`;
            if (n > 20) throw E.conflict('slug_taken', 'No se pudo crear la categoría');
          }
          db.run(CAT_INSERT, { id: slug, name: p.name, t: now() });
          db.run(CREATED, { id });
          notify.send(p.proposer_id, 'category', { name: p.name });
          created = slug;
        }
        return { id, votes, goal: CATEGORY_VOTES_GOAL, created_category_id: created };
      });
    },
  };
}

module.exports = { createProposals };
