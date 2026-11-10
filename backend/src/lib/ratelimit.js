'use strict';
/**
 * Límite de peticiones en memoria con ventana deslizante aproximada (contador actual + anterior).
 * - Memoria acotada: máximo de claves; al llenarse se barren las vencidas y luego se expulsan las más viejas.
 * - Las IPv6 se agrupan por /64 (un atacante suele tener un /64 completo: sin esto rotaría de dirección).
 * Nota: vive en la memoria de UN proceso. Si algún día hay varias instancias, hay que moverlo a Redis.
 */
const net = require('node:net');

function expandIPv6(ip) {
  let addr = ip.split('%')[0].toLowerCase();
  if (addr.includes('.')) { // IPv4 incrustada: ::ffff:1.2.3.4
    const i = addr.lastIndexOf(':');
    const v4 = addr.slice(i + 1).split('.').map(Number);
    if (v4.length !== 4 || v4.some((n) => !(n >= 0 && n <= 255))) return null;
    addr = addr.slice(0, i + 1) + (((v4[0] << 8) | v4[1]).toString(16)) + ':' + (((v4[2] << 8) | v4[3]).toString(16));
  }
  const halves = addr.split('::');
  if (halves.length > 2) return null;
  const head = halves[0] ? halves[0].split(':') : [];
  const tail = halves.length === 2 && halves[1] ? halves[1].split(':') : [];
  const missing = 8 - head.length - tail.length;
  if ((halves.length === 1 && missing !== 0) || missing < 0) return null;
  const parts = [...head, ...Array(halves.length === 2 ? missing : 0).fill('0'), ...tail];
  if (parts.length !== 8) return null;
  return parts.map((h) => h.padStart(4, '0'));
}

/** Clave estable para limitar por origen: IPv4 completa o prefijo /64 de IPv6. */
function ipKey(ip) {
  if (typeof ip !== 'string') return 'unknown';
  if (net.isIPv4(ip)) return ip;
  if (net.isIPv6(ip)) {
    const parts = expandIPv6(ip);
    if (!parts) return 'unknown';
    if (parts.slice(0, 5).every((p) => p === '0000') && parts[5] === 'ffff') { // IPv4 mapeada
      const hi = parseInt(parts[6], 16), lo = parseInt(parts[7], 16);
      return `${hi >> 8}.${hi & 255}.${lo >> 8}.${lo & 255}`;
    }
    return parts.slice(0, 4).join(':') + '::/64';
  }
  return 'unknown';
}

class RateLimiter {
  constructor({ maxKeys = 200000, now = () => Date.now() } = {}) {
    this.map = new Map();
    this.maxKeys = maxKeys;
    this.now = now;
    this.timer = setInterval(() => this.sweep(), 60000);
    this.timer.unref();
  }

  /** @returns {{allowed:boolean, remaining:number, retryAfterSec:number}} */
  hit(key, limit, windowMs) {
    const now = this.now();
    const idx = Math.floor(now / windowMs);
    let e = this.map.get(key);
    if (!e) {
      if (this.map.size >= this.maxKeys) this.evict(now);
      e = { idx, count: 0, prev: 0, windowMs };
    } else {
      this.map.delete(key); // reinserta al final: orden de uso reciente
      if (e.idx === idx - 1) { e.prev = e.count; e.count = 0; e.idx = idx; }
      else if (e.idx < idx - 1) { e.prev = 0; e.count = 0; e.idx = idx; }
    }
    const elapsed = (now % windowMs) / windowMs;
    const estimate = e.prev * (1 - elapsed) + e.count;
    this.map.set(key, e);
    if (estimate + 1 > limit) {
      return { allowed: false, remaining: 0, retryAfterSec: Math.ceil((windowMs - (now % windowMs)) / 1000) };
    }
    e.count++;
    return { allowed: true, remaining: Math.max(0, Math.floor(limit - estimate - 1)), retryAfterSec: 0 };
  }

  sweep() {
    const now = this.now();
    for (const [k, e] of this.map) if (Math.floor(now / e.windowMs) > e.idx + 1) this.map.delete(k);
  }

  evict(now) {
    this.sweep();
    if (this.map.size < this.maxKeys) return;
    let drop = Math.ceil(this.maxKeys * 0.1);
    for (const k of this.map.keys()) { this.map.delete(k); if (--drop <= 0) break; }
  }

  close() { clearInterval(this.timer); }
}

module.exports = { RateLimiter, ipKey, expandIPv6 };
