'use strict';
/**
 * Karma y niveles. TODO se calcula en el servidor: el cliente no puede enviar puntos.
 * Cada tipo de acción tiene un tope diario para que no se pueda "granjear" karma con scripts.
 */
const LEVELS = [0, 50, 150, 400]; // Chispa, Brasa, Fogata, Hoguera (igual que la app)
const levelOf = (karma) => LEVELS.reduce((acc, min, i) => (karma >= min ? i : acc), 0);

// puntos por acción y tope de veces por 24 h
const RULES = Object.freeze({
  publish: { points: 15, dailyCap: 5 },
  vote: { points: 1, dailyCap: 20 },
  comment: { points: 2, dailyCap: 10 },
  propose: { points: 5, dailyCap: 2 },
  catvote: { points: 1, dailyCap: 20 },
});

const PROPOSE_MIN_KARMA = LEVELS[2]; // nivel "Cazador"
const CATEGORY_VOTES_GOAL = 500;
const AUTOHIDE_REPORTS = 3;
const REPORTER_MIN_KARMA = LEVELS[1]; // solo reportes de cuentas con algo de reputación cuentan para ocultar
const DAILY_OFFER_CAP = 5; // ofertas por cuenta cada 24 h
const MAX_ALERTS = 20;

// Peso del voto según el nivel de quien vota: las cuentas nuevas pesan menos (frena votos falsos masivos).
const VOTE_WEIGHTS = Object.freeze([0.5, 0.75, 1, 1]);
const voteWeight = (karma) => VOTE_WEIGHTS[levelOf(karma)];

module.exports = {
  LEVELS, levelOf, RULES, PROPOSE_MIN_KARMA, CATEGORY_VOTES_GOAL, AUTOHIDE_REPORTS, REPORTER_MIN_KARMA,
  DAILY_OFFER_CAP, MAX_ALERTS, VOTE_WEIGHTS, voteWeight,
};
