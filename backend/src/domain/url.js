'use strict';
/**
 * Análisis de enlaces de evidencia. El servidor NUNCA abre ni descarga estos enlaces
 * (así no existe SSRF); solo los analiza como texto y guarda la versión normalizada.
 * Reglas estrictas: solo https, sin credenciales, sin IP, sin acortadores, sin dominios
 * internos ni TLD de alto riesgo, sin puertos raros, sin dominios que imiten a otros (punycode).
 */
const SHORTENERS = new Set(['bit.ly', 'tinyurl.com', 't.co', 'goo.gl', 'is.gd', 'cutt.ly', 'ow.ly', 'rebrand.ly', 'shorturl.at', 'tiny.cc', 'rb.gy', 'buff.ly', 'lnkd.in', 'wa.me', 't.me']);
const RISKY_TLD = /\.(zip|mov|xyz|top|click|gq|tk|ml|cf|ga|work|country|kim|loan|men|party|review|science|stream|download|racing|win|bid|trade|cam|icu|monster|rest)$/;
const INTERNAL = /(^localhost$|\.localhost$|\.local$|\.internal$|\.lan$|\.home$|\.corp$|\.intranet$|\.onion$)/;
const MAX_LEN = 500;

function analyzeUrl(raw) {
  const flags = [];
  if (typeof raw !== 'string') return { ok: false, flags: ['invalid'] };
  const text = raw.trim();
  if (!text || text.length > MAX_LEN || /[\s\u0000-\u001f\u007f-\u009f]/.test(text)) return { ok: false, flags: ['invalid'] };
  let u;
  try { u = new URL(/^[a-z][a-z0-9+.-]*:\/\//i.test(text) ? text : `https://${text}`); } catch { return { ok: false, flags: ['invalid'] }; }
  const host = u.hostname.toLowerCase().replace(/\.$/, '');
  // Etiquetas vacías ("evil.xyz..", "a..b") se usan para esquivar los filtros por sufijo: se rechazan.
  if (host.split('.').some((p) => p === '')) return { ok: false, flags: ['invalid'] };
  if (u.protocol !== 'https:') flags.push('http');
  if (u.username || u.password) flags.push('creds');
  if (/^\d{1,3}(\.\d{1,3}){3}$/.test(host) || host.includes(':') || /^\[/.test(host) || /^0x[0-9a-f]+$/i.test(host) || /^\d+$/.test(host)) flags.push('ip');
  if (host.split('.').some((p) => p.startsWith('xn--'))) flags.push('punycode');
  if ([...SHORTENERS].some((s) => host === s || host.endsWith('.' + s))) flags.push('short'); // también www.bit.ly
  if (RISKY_TLD.test(host)) flags.push('tld');
  if (INTERNAL.test(host) || !host.includes('.')) flags.push('internal');
  if (u.port && u.port !== '443') flags.push('port');
  if (/[^\x00-\x7f]/.test(host)) flags.push('punycode');
  const href = u.href;
  if (href.length > MAX_LEN) flags.push('invalid');
  return flags.length ? { ok: false, flags: [...new Set(flags)] } : { ok: true, flags: [], url: href };
}

module.exports = { analyzeUrl };
