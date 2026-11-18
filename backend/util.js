// Utilidades compartidas por las rutas del servidor.
const jwt = require('jsonwebtoken');

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
const passwordValida = (p) => typeof p === 'string' && p.length >= 8 && p.length <= 64;
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

function firmarToken(u) {
  return jwt.sign({ id: u.id, email: u.email, rol: u.rol, nombre: u.nombre }, JWT_SECRET, { expiresIn: '30d' });
}

function autenticar(req, res, next) {
  const token = (req.headers.authorization || '').split(' ')[1];
  if (!token) return res.status(401).json({ error: 'Token requerido' });
  jwt.verify(token, JWT_SECRET, (err, usuario) => {
    if (err) return res.status(401).json({ error: 'Sesión vencida, vuelve a iniciar sesión' });
    req.usuario = usuario;
    next();
  });
}

const requiereRol = (rol) => (req, res, next) =>
  req.usuario?.rol === rol ? next() : res.status(403).json({ error: 'Acceso no autorizado para este rol' });

module.exports = {
  JWT_SECRET, MINUTOS_VIGENCIA_RESERVA, ruta, ErrorHttp, normalizarEmail, emailValido, passwordValida, texto,
  fechaIso, fechaOpcional, nombreCorto, firmarToken, autenticar, requiereRol,
};
