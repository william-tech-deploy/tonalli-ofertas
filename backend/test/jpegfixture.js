'use strict';
/** JPEG mínimo hecho a mano (1 componente, 8x8) con EXIF/GPS, comentario y un "polyglot" pegado al final. */
function seg(marker, payload) { const b = Buffer.alloc(4 + payload.length); b[0] = 0xff; b[1] = marker; b.writeUInt16BE(payload.length + 2, 2); payload.copy(b, 4); return b; }
function makeJpeg({ exif = true, trailer = true, width = 8, height = 8 } = {}) {
  const parts = [Buffer.from([0xff, 0xd8])];
  if (exif) parts.push(seg(0xe1, Buffer.from('Exif\0\0GPS-LAT-13.69-LON-89.19-SECRETO', 'latin1')));
  parts.push(seg(0xfe, Buffer.from('comentario oculto')));
  parts.push(seg(0xdb, Buffer.concat([Buffer.from([0]), Buffer.alloc(64, 1)])));
  const sof = Buffer.alloc(9); sof[0] = 8; sof.writeUInt16BE(height, 1); sof.writeUInt16BE(width, 3); sof[5] = 1; sof[6] = 1; sof[7] = 0x11; sof[8] = 0;
  parts.push(seg(0xc0, sof));
  parts.push(seg(0xda, Buffer.from([1, 1, 0, 0, 0x3f, 0])));
  parts.push(Buffer.from([0x12, 0x34, 0x56, 0x78, 0x9a]));
  parts.push(Buffer.from([0xff, 0xd9]));
  if (trailer) parts.push(Buffer.from('<script>alert(1)</script>PK\x03\x04'));
  return Buffer.concat(parts);
}
module.exports = { makeJpeg };
