'use strict';
/**
 * Enrutador mínimo con coincidencia EXACTA de segmentos. Los parámetros de ruta solo aceptan
 * un formato fijo (UUID en minúsculas, EAN numérico o "slug"); cualquier otra cosa es 404, así
 * que nunca llegan valores raros a los manejadores ni a la base de datos.
 */
const PARAM_FORMATS = {
  id: /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/,
  ean: /^\d{8,14}$/,
  slug: /^[a-z0-9][a-z0-9-]{0,39}$/,
};

class Router {
  constructor() { this.routes = []; }

  add(method, pattern, opts, handler) {
    const segments = pattern.split('/').slice(1).map((s) => {
      if (!s.startsWith(':')) return { literal: s };
      const name = s.slice(1);
      if (!PARAM_FORMATS[name]) throw new Error(`Parámetro de ruta sin formato definido: ${name}`);
      return { name, re: PARAM_FORMATS[name] };
    });
    this.routes.push({ method, pattern, segments, opts: opts || {}, handler });
  }

  /** @returns {{route, params}|{allowed:string[]}|null} */
  match(method, path) {
    const parts = path.split('/').slice(1);
    const allowed = new Set();
    for (const route of this.routes) {
      if (route.segments.length !== parts.length) continue;
      const params = Object.create(null);
      let ok = true;
      for (let i = 0; i < parts.length && ok; i++) {
        const seg = route.segments[i];
        if (seg.literal !== undefined) ok = seg.literal === parts[i];
        else if (seg.re.test(parts[i])) params[seg.name] = parts[i];
        else ok = false;
      }
      if (!ok) continue;
      if (route.method === method) return { route, params };
      allowed.add(route.method);
    }
    return allowed.size ? { allowed: [...allowed] } : null;
  }
}

module.exports = { Router };
