'use strict';
/** Validación de códigos de barras EAN-13, EAN-8 y UPC-A por su dígito verificador. */
function checkDigit13(d12) {
  let sum = 0;
  for (let i = 0; i < 12; i++) sum += Number(d12[i]) * (i % 2 ? 3 : 1);
  return (10 - (sum % 10)) % 10;
}

function isValidEAN(code) {
  const c = String(code).replace(/\D/g, '');
  if (c.length === 13) return checkDigit13(c.slice(0, 12)) === Number(c[12]);
  if (c.length === 12) return isValidEAN('0' + c);
  if (c.length === 8) {
    let sum = 0;
    for (let i = 0; i < 7; i++) sum += Number(c[i]) * (i % 2 ? 1 : 3);
    return (10 - (sum % 10)) % 10 === Number(c[7]);
  }
  return false;
}

/** Normaliza a 13 dígitos (UPC-A → anteponer 0). Los EAN-8 se rechazan para mantener una sola forma. */
function toEAN13(code) {
  const c = String(code).replace(/\D/g, '');
  if (c.length === 12) return '0' + c;
  if (c.length === 13) return c;
  return null;
}

module.exports = { isValidEAN, toEAN13, checkDigit13 };
