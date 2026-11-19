// Códigos de un solo uso basados en tiempo (TOTP, RFC 6238): SHA-1, 6 dígitos, pasos de 30 segundos.
// Compatible con Google Authenticator, Microsoft Authenticator y similares.
const crypto = require('crypto');

const ALFABETO = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
const PASO_MS = 30_000;

/** Texto o bytes → base32 (RFC 4648, sin relleno). */
function base32(datos) {
  const bytes = Buffer.from(datos);
  let bits = '';
  for (const b of bytes) bits += b.toString(2).padStart(8, '0');
  let salida = '';
  for (let i = 0; i < bits.length; i += 5) salida += ALFABETO[parseInt(bits.slice(i, i + 5).padEnd(5, '0'), 2)];
  return salida;
}

function bytesDeBase32(texto) {
  let bits = '';
  for (const c of texto.replace(/=+$/, '').toUpperCase()) bits += ALFABETO.indexOf(c).toString(2).padStart(5, '0');
  const bytes = [];
  for (let i = 0; i + 8 <= bits.length; i += 8) bytes.push(parseInt(bits.slice(i, i + 8), 2));
  return Buffer.from(bytes);
}

/** Clave nueva de 160 bits (32 caracteres en base32). */
const nuevoSecreto = () => base32(crypto.randomBytes(20));

const pasoDe = (ms) => Math.floor(ms / PASO_MS);

function codigoDePaso(secreto, paso) {
  const contador = Buffer.alloc(8);
  contador.writeBigUInt64BE(BigInt(paso));
  const hmac = crypto.createHmac('sha1', bytesDeBase32(secreto)).update(contador).digest();
  const desde = hmac[hmac.length - 1] & 0x0f;
  const numero = (hmac.readUInt32BE(desde) & 0x7fffffff) % 1_000_000;
  return String(numero).padStart(6, '0');
}

/** Código vigente en el momento [ms]. */
const codigo = (secreto, ms = Date.now()) => codigoDePaso(secreto, pasoDe(ms));

/**
 * Revisa el código aceptando un paso de desfase (±30 s de reloj) y rechazando pasos ya usados.
 * Devuelve el paso aceptado (para guardarlo) o null.
 */
function verificar(secreto, recibido, ultimoPasoUsado = null, ms = Date.now()) {
  if (!/^\d{6}$/.test(String(recibido))) return null;
  const actual = pasoDe(ms);
  for (const paso of [actual - 1, actual, actual + 1]) {
    if (ultimoPasoUsado !== null && paso <= ultimoPasoUsado) continue;
    const esperado = Buffer.from(codigoDePaso(secreto, paso));
    if (crypto.timingSafeEqual(esperado, Buffer.from(String(recibido)))) return paso;
  }
  return null;
}

/** Enlace que leen las apps autenticadoras (el QR lo contiene). */
const uri = (email, secreto) =>
  `otpauth://totp/CONATACOCH:${encodeURIComponent(email)}?secret=${secreto}&issuer=CONATACOCH&algorithm=SHA1&digits=6&period=30`;

module.exports = { base32, nuevoSecreto, codigo, verificar, uri };
