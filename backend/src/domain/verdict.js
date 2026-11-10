'use strict';
/**
 * Veredicto de una rebaja. Es el MISMO algoritmo del prototipo, pero ahora se calcula en el
 * servidor con precios observados por la comunidad (en centavos enteros: sin errores de coma flotante).
 *
 *  - Precio habitual = mediana de lo registrado hace más de 10 días (la mediana resiste valores atípicos).
 *  - "fake" si el "antes" anunciado supera en más de 3 % el máximo histórico (el precio inflado nunca existió),
 *    o si hoy casi no hay rebaja real (<2 %) frente a un anuncio de 10 % o más.
 *  - "real" si la rebaja real es de 8 % o más; "mild" en el resto; "none" si faltan datos.
 */
function median(values) {
  const s = [...values].sort((a, b) => a - b);
  const m = s.length >> 1;
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
}

/** @param {{daysAgo:number, cents:number}[]} history */
function priceVerdict(history, declaredOldCents, nowCents) {
  const prior = history.filter((h) => h.daysAgo > 10).map((h) => h.cents);
  if (history.length < 5 || prior.length < 3) return { kind: 'none' };
  const base = median(prior);
  const maxPrior = Math.max(...prior);
  const real = (base - nowCents) / base;
  const declared = (declaredOldCents - nowCents) / declaredOldCents;
  const inflated = declaredOldCents > maxPrior * 1.03;
  const detail = { base_cents: Math.round(base), real_pct: Math.round(real * 100), declared_pct: Math.round(declared * 100) };
  if (inflated || (real < 0.02 && declared >= 0.1)) return { kind: 'fake', inflated, ...detail };
  if (real >= 0.08) return { kind: 'real', ...detail };
  return { kind: 'mild', ...detail };
}

module.exports = { priceVerdict, median };
