'use strict';
/**
 * Saneador de JPEG sin librerías. NUNCA se guarda ni se sirve el archivo tal como llegó:
 * se recorre su estructura y se reconstruye con SOLO los segmentos necesarios.
 *
 *  - Se eliminan TODOS los metadatos (APPn: EXIF con GPS, ICC, XMP; y comentarios COM):
 *    protege la privacidad (ubicación del domicilio) y quita los lugares donde se esconden cargas.
 *  - Se descartan los bytes posteriores al marcador de fin (EOI): ahí se pegan los "polyglots"
 *    (un JPEG válido con un script o ZIP anexado).
 *  - Se validan dimensiones antes de que nadie decodifique la imagen (anti "bomba de descompresión").
 *  - Cualquier estructura rara se RECHAZA (lista blanca de marcadores), no se intenta "arreglar".
 */
const SOI = 0xd8, EOI = 0xd9, SOS = 0xda, DNL = 0xdc;

class InvalidImage extends Error {}

function sanitizeJpeg(buf, { maxBytes = 300 * 1024, maxDim = 4096, maxPixels = 12_000_000 } = {}) {
  if (!Buffer.isBuffer(buf) || buf.length < 100 || buf.length > maxBytes) throw new InvalidImage('size');
  if (buf[0] !== 0xff || buf[1] !== SOI) throw new InvalidImage('magic');

  const out = [Buffer.from([0xff, SOI])];
  let pos = 2;
  let width = 0, height = 0, components = 0;
  let sawSof = false, sawSos = false, sawEoi = false;

  const needByte = (i) => { if (i >= buf.length) throw new InvalidImage('truncated'); return buf[i]; };

  while (!sawEoi) {
    // Buscar el siguiente marcador (FF xx con xx != 00), tolerando bytes de relleno FF
    if (needByte(pos) !== 0xff) throw new InvalidImage('marker');
    while (needByte(pos + 1) === 0xff) pos++;
    const marker = needByte(pos + 1);
    if (marker === 0x00) throw new InvalidImage('marker');
    if (marker === EOI) { sawEoi = true; break; }
    if (marker === SOI || marker === 0x01 || (marker >= 0xd0 && marker <= 0xd7)) { pos += 2; continue; } // sin longitud
    if (marker === DNL) throw new InvalidImage('dnl');

    const len = (needByte(pos + 2) << 8) | needByte(pos + 3);
    if (len < 2) throw new InvalidImage('length');
    const end = pos + 2 + len;
    if (end > buf.length) throw new InvalidImage('truncated');
    const segment = buf.subarray(pos, end);

    const isApp = marker >= 0xe0 && marker <= 0xef;
    const isCom = marker === 0xfe;
    const isSof = marker >= 0xc0 && marker <= 0xcf && marker !== 0xc4 && marker !== 0xc8 && marker !== 0xcc;

    if (isApp || isCom) { pos = end; continue; } // se descarta (metadatos)

    if (isSof) {
      if (marker !== 0xc0 && marker !== 0xc1 && marker !== 0xc2) throw new InvalidImage('sof_type'); // solo baseline/extendido/progresivo
      if (sawSof || segment.length < 10) throw new InvalidImage('sof');
      if (segment[4] !== 8) throw new InvalidImage('precision');
      height = segment.readUInt16BE(5);
      width = segment.readUInt16BE(7);
      components = segment[9];
      // Disposición: FF Cx | longitud(2) | precisión(1) | alto(2) | ancho(2) | N componentes(1) | 3 bytes por componente
      if (!(components === 1 || components === 3) || segment.length !== 10 + components * 3) throw new InvalidImage('components');
      if (width < 1 || height < 1 || width > maxDim || height > maxDim || width * height > maxPixels) throw new InvalidImage('dimensions');
      sawSof = true;
      out.push(segment);
      pos = end;
      continue;
    }

    const allowed = marker === 0xdb /* DQT */ || marker === 0xc4 /* DHT */ || marker === 0xdd /* DRI */ || marker === SOS;
    if (!allowed) throw new InvalidImage('marker_not_allowed');

    if (marker === SOS) {
      if (!sawSof) throw new InvalidImage('sos_before_sof');
      out.push(segment);
      sawSos = true;
      // Datos entropy-coded: copiar hasta el próximo marcador real (FF seguido de algo que no sea 00 ni RSTn)
      let q = end;
      for (;;) {
        if (q + 1 >= buf.length) throw new InvalidImage('truncated');
        if (buf[q] === 0xff) {
          const m = buf[q + 1];
          if (m === 0x00 || (m >= 0xd0 && m <= 0xd7)) { q += 2; continue; }
          if (m === 0xff) { q += 1; continue; }
          break;
        }
        q++;
      }
      out.push(buf.subarray(end, q));
      pos = q;
      continue;
    }

    out.push(segment);
    pos = end;
  }

  if (!sawSof || !sawSos) throw new InvalidImage('incomplete');
  out.push(Buffer.from([0xff, EOI]));
  return { bytes: Buffer.concat(out), width, height };
}

module.exports = { sanitizeJpeg, InvalidImage };
