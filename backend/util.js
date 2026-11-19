// Utilidades compartidas por las rutas del servidor.
const crypto = require('crypto');
const jwt = require('jsonwebtoken');
const { query } = require('./db/postgres');

const JWT_SECRET = process.env.JWT_SECRET || 'conatacoch_secret_key_2026';
if (!process.env.JWT_SECRET) console.warn('[aviso] JWT_SECRET no está configurado: se usa una clave de desarrollo.');

/** Una reserva vence a los 3 minutos si el conductor no confirma el abordaje (o la elimina antes). */
const MINUTOS_VIGENCIA_RESERVA = 3;

/** Envuelve rutas async para que los errores lleguen al manejador de errores. */
const ruta = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

class ErrorHttp extends Error {
  constructor(status, mensaje) {
    super(mensaje);
    this.status = status;
  }
}

const normalizarEmail = (email) => String(email || '').trim().toLowerCase();
const emailValido = (email) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
/** Contraseña de 15 a 128 caracteres (OWASP ASVS); un emoji cuenta como un carácter. */
const PASSWORD_MIN = 15;
const PASSWORD_MAX = 128;
const passwordValida = (p) => typeof p === 'string' && [...p].length >= PASSWORD_MIN && [...p].length <= PASSWORD_MAX;
const MENSAJE_PASSWORD = `La contraseña debe tener entre ${PASSWORD_MIN} y ${PASSWORD_MAX} caracteres`;
const texto = (v) => String(v ?? '').trim();

/** "dd-mm-aaaa" o "aaaa-mm-dd" → "aaaa-mm-dd"; null si no es una fecha válida. */
function fechaIso(valor) {
  const t = texto(valor);
  let m = t.match(/^(\d{1,2})[-/.](\d{1,2})[-/.](\d{4})$/);
  let [a, mes, d] = m ? [m[3], m[2], m[1]] : [];
  if (!m) {
    m = t.match(/^(\d{4})-(\d{1,2})-(\d{1,2})/);
    if (!m) return null;
    [a, mes, d] = [m[1], m[2], m[3]];
  }
  const fecha = new Date(Date.UTC(Number(a), Number(mes) - 1, Number(d)));
  if (fecha.getUTCDate() !== Number(d) || fecha.getUTCMonth() !== Number(mes) - 1) return null;
  return fecha.toISOString().slice(0, 10);
}

/** Fecha opcional: vacío → null; con texto inválido → error 400. */
function fechaOpcional(valor, campo) {
  if (!texto(valor)) return null;
  const f = fechaIso(valor);
  if (!f) throw new ErrorHttp(400, `La fecha de ${campo} debe tener el formato dd-mm-aaaa`);
  return f;
}

/** "Camila Reyes" → "Camila R." (lo que ve el conductor). */
function nombreCorto(nombre) {
  const partes = texto(nombre).split(/\s+/).filter(Boolean);
  if (partes.length < 2) return partes[0] || 'Pasajero';
  return `${partes[0]} ${partes[1][0].toUpperCase()}.`;
}

/** Valida el dígito verificador (módulo 11) y devuelve el RUT como 12345678-9. */
function rutNormalizado(valor) {
  const limpio = texto(valor).replace(/[.\s]/g, '').toUpperCase();
  const m = limpio.match(/^(\d{7,8})-?([\dK])$/);
  if (!m) throw new ErrorHttp(400, 'El RUT debe tener el formato 12345678-9');
  let suma = 0;
  let factor = 2;
  for (let i = m[1].length - 1; i >= 0; i--) {
    suma += Number(m[1][i]) * factor;
    factor = factor === 7 ? 2 : factor + 1;
  }
  const resto = 11 - (suma % 11);
  const dv = resto === 11 ? '0' : resto === 10 ? 'K' : String(resto);
  if (dv !== m[2]) throw new ErrorHttp(400, 'El RUT no es válido (revisa el dígito verificador)');
  return `${m[1]}-${m[2]}`;
}

/** Patentes chilenas: ABCD-12 (nueva) o AB-1234 (antigua). */
function patenteNormalizada(valor) {
  const p = texto(valor).toUpperCase().replace(/[\s·.]/g, '');
  let m = p.match(/^([A-Z]{4})-?(\d{2})$/);
  if (m) return `${m[1]}-${m[2]}`;
  m = p.match(/^([A-Z]{2})-?(\d{4})$/);
  if (m) return `${m[1]}-${m[2]}`;
  throw new ErrorHttp(400, 'La patente debe tener el formato ABCD-12 o AB-1234');
}

/**
 * Límite de intentos fallidos de inicio de sesión: 5 por correo y dirección IP en 15 minutos.
 * En memoria: basta para una sola instancia del servidor (Render, plan gratis).
 */
const INTENTOS_MAX = 5;
const VENTANA_MS = 15 * 60 * 1000;
const intentosFallidos = new Map();
function claveIntento(req, email) { return `${req.ip}|${email}`; }
function bloqueado(req, email) {
  const r = intentosFallidos.get(claveIntento(req, email));
  if (!r || Date.now() - r.desde > VENTANA_MS) return false;
  return r.n >= INTENTOS_MAX;
}
function registrarFallo(req, email) {
  const k = claveIntento(req, email);
  const r = intentosFallidos.get(k);
  if (!r || Date.now() - r.desde > VENTANA_MS) intentosFallidos.set(k, { n: 1, desde: Date.now() });
  else r.n += 1;
}
function limpiarIntentos(req, email) { intentosFallidos.delete(claveIntento(req, email)); }

/** Duración del token: 8 horas para la administración (panel web) y 30 días para la app. */
const SEGUNDOS_SESION = (rol) => (rol === 'ADMIN' ? 8 * 3600 : 30 * 24 * 3600);
/** La administración sale tras 30 minutos sin actividad. */
const MINUTOS_INACTIVIDAD_ADMIN = 30;

/** Crea la sesión en la base y devuelve su token. */
async function iniciarSesion(u) {
  const sid = crypto.randomUUID();
  const segundos = SEGUNDOS_SESION(u.rol);
  await query('INSERT INTO sesiones (id, usuario_id, expira) VALUES ($1, $2, NOW() + make_interval(secs => $3))',
    [sid, u.id, segundos]);
  const datos = { sid, id: u.id, email: u.email, rol: u.rol, nombre: u.nombre };
  if (u.totp) datos.totp = true; // la sesión de la administración pasó por el segundo factor
  return jwt.sign(datos, JWT_SECRET, { expiresIn: segundos });
}

const SESION_VENCIDA = { error: 'Sesión vencida, vuelve a iniciar sesión' };

/** Exige un token válido cuya sesión siga abierta, no vencida y de una cuenta activa. */
function autenticar(req, res, next) {
  const token = (req.headers.authorization || '').split(' ')[1];
  if (!token) return res.status(401).json({ error: 'Token requerido' });
  let usuario;
  try {
    usuario = jwt.verify(token, JWT_SECRET);
  } catch {
    return res.status(401).json(SESION_VENCIDA);
  }
  if (!usuario.sid) return res.status(401).json(SESION_VENCIDA);
  query(
    `SELECT s.revocada, s.expira < NOW() AS vencida, u.activo,
            s.ultimo_uso < NOW() - make_interval(mins => $2) AS inactiva,
            s.ultimo_uso < NOW() - INTERVAL '1 minute' AS refrescar
     FROM sesiones s JOIN usuarios u ON u.id = s.usuario_id WHERE s.id = $1`,
    [usuario.sid, MINUTOS_INACTIVIDAD_ADMIN],
  ).then(async ({ rows: [s] }) => {
    if (!s || s.revocada || s.vencida || !s.activo || (usuario.rol === 'ADMIN' && s.inactiva)) {
      return res.status(401).json(SESION_VENCIDA);
    }
    if (s.refrescar) await query('UPDATE sesiones SET ultimo_uso = NOW() WHERE id = $1', [usuario.sid]);
    req.usuario = usuario;
    next();
  }).catch(next);
}

const requiereRol = (rol) => (req, res, next) =>
  req.usuario?.rol === rol ? next() : res.status(403).json({ error: 'Acceso no autorizado para este rol' });

module.exports = {
  JWT_SECRET, MINUTOS_VIGENCIA_RESERVA, ruta, ErrorHttp, normalizarEmail, emailValido, passwordValida, texto,
  fechaIso, fechaOpcional, nombreCorto, iniciarSesion, autenticar, requiereRol, MENSAJE_PASSWORD,
  rutNormalizado, patenteNormalizada, bloqueado, registrarFallo, limpiarIntentos, PASSWORD_MIN, PASSWORD_MAX,
};
