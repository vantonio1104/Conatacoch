// Cuentas: registro e inicio de sesión de pasajeros, conductores y administración.
const express = require('express');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const QRCode = require('qrcode');
const totp = require('../totp');
const { query, transaccion } = require('../db/postgres');
const mongo = require('../db/mongo');
const {
  ruta, ErrorHttp, normalizarEmail, emailValido, passwordValida, fechaIso, iniciarSesion, autenticar, MENSAJE_PASSWORD,
  rutNormalizado, bloqueado, registrarFallo, limpiarIntentos, JWT_SECRET,
} = require('../util');

const router = express.Router();

router.post('/api/auth/registro', ruta(async (req, res) => {
  const nombre = String(req.body.nombre || '').trim();
  const email = normalizarEmail(req.body.email);
  const { password } = req.body;
  if (!nombre) throw new ErrorHttp(400, 'Ingresa tu nombre');
  if (!emailValido(email)) throw new ErrorHttp(400, 'Ingresa un correo válido');
  if (!passwordValida(password)) throw new ErrorHttp(400, MENSAJE_PASSWORD);

  const hash = await bcrypt.hash(password, 10);
  // Registro sin código por correo: la cuenta queda lista para usar y se entrega la sesión.
  const r = await query(
    `INSERT INTO usuarios (email, password_hash, nombre, rol_id, verificado)
     VALUES ($1, $2, $3, (SELECT id FROM roles WHERE nombre = 'PASAJERO'), TRUE)
     ON CONFLICT (email) DO NOTHING RETURNING id`,
    [email, hash, nombre],
  );
  if (r.rowCount === 0) throw new ErrorHttp(409, 'Ese correo ya tiene una cuenta. Inicia sesión.');
  const usuario = { id: r.rows[0].id, email, nombre, rol: 'PASAJERO' };
  await mongo.auditar(usuario, 'REGISTRO_PASAJERO');
  res.status(201).json({ email, nombre, rol: 'PASAJERO', token: await iniciarSesion(usuario) });
}));

// Ya no se usa desde la app (el registro no pide código). Se deja por compatibilidad, pero solo con la
// sesión de la propia cuenta: antes entregaba una sesión para cualquier correo con cualquier código (defecto D-02).
router.post('/api/auth/verificar', autenticar, ruta(async (req, res) => {
  const codigo = String(req.body.codigo || '');
  if (!/^\d{6}$/.test(codigo)) throw new ErrorHttp(400, 'Código incorrecto');
  const r = await query(
    `UPDATE usuarios u SET verificado = TRUE FROM roles r
     WHERE u.rol_id = r.id AND u.id = $1 RETURNING u.id, u.email, u.nombre, r.nombre AS rol`,
    [req.usuario.id],
  );
  if (r.rowCount === 0) throw new ErrorHttp(404, 'No encontramos esa cuenta');
  const u = r.rows[0];
  res.json({ token: await iniciarSesion(u), rol: u.rol, nombre: u.nombre, email: u.email });
}));

router.post('/api/auth/reenviar-codigo', (req, res) => res.status(204).end());

router.post('/api/auth/login', ruta(async (req, res) => {
  const email = normalizarEmail(req.body.email);
  const { password, rol } = req.body;
  if (bloqueado(req, email)) throw new ErrorHttp(429, 'Demasiados intentos fallidos. Espera 15 minutos e inténtalo de nuevo.');
  const r = await query(
    `SELECT u.id, u.email, u.nombre, u.password_hash, u.totp_activo, r.nombre AS rol, c.estado AS estado_conductor
     FROM usuarios u JOIN roles r ON r.id = u.rol_id
     LEFT JOIN conductores c ON c.usuario_id = u.id
     WHERE u.email = $1 AND u.activo`,
    [email],
  );
  const u = r.rows[0];
  // Mensaje genérico: no se revela si falló el correo o la contraseña.
  if (!u || !(await bcrypt.compare(String(password || ''), u.password_hash)) || (rol && rol !== u.rol)) {
    registrarFallo(req, email);
    await mongo.auditar({ email, rol: null }, 'INICIO_SESION_FALLIDO');
    throw new ErrorHttp(401, 'Correo o contraseña incorrectos');
  }
  limpiarIntentos(req, email);
  // La administración necesita además el código de su app autenticadora (segundo factor).
  if (u.rol === 'ADMIN') {
    const desafio = jwt.sign({ tipo: 'desafio-totp', id: u.id }, JWT_SECRET, { expiresIn: MINUTOS_DESAFIO * 60 });
    return res.json({ requiereTotp: true, configurar: !u.totp_activo, desafio });
  }
  await mongo.auditar(u, 'INICIO_SESION');
  res.json({ token: await iniciarSesion(u), rol: u.rol, nombre: u.nombre, email: u.email, estado: u.estado_conductor ?? null });
}));

// ---------- Segundo factor de la administración (TOTP) ----------
const MINUTOS_DESAFIO = 5;

/** Administrador activo al que pertenece el desafío que entregó el inicio de sesión con contraseña. */
async function adminDelDesafio(desafio) {
  let datos;
  try {
    datos = jwt.verify(String(desafio || ''), JWT_SECRET);
  } catch {
    datos = null;
  }
  if (datos?.tipo !== 'desafio-totp') throw new ErrorHttp(401, 'El inicio de sesión venció. Vuelve a ingresar tu contraseña.');
  const r = await query(
    `SELECT u.id, u.email, u.nombre, u.totp_secreto, u.totp_activo, u.totp_ultimo_paso, r.nombre AS rol
     FROM usuarios u JOIN roles r ON r.id = u.rol_id WHERE u.id = $1 AND u.activo AND r.nombre = 'ADMIN'`,
    [datos.id],
  );
  if (!r.rows[0]) throw new ErrorHttp(401, 'El inicio de sesión venció. Vuelve a ingresar tu contraseña.');
  return r.rows[0];
}

/** Primera vez: crea la clave y entrega el QR para registrarla en la app autenticadora. */
router.post('/api/auth/totp/alta', ruta(async (req, res) => {
  const u = await adminDelDesafio(req.body.desafio);
  if (u.totp_activo) throw new ErrorHttp(409, 'El segundo factor ya está configurado');
  const secreto = totp.nuevoSecreto();
  await query('UPDATE usuarios SET totp_secreto = $2, totp_ultimo_paso = NULL WHERE id = $1', [u.id, secreto]);
  const uri = totp.uri(u.email, secreto);
  res.json({ secreto, uri, qr: await QRCode.toDataURL(uri, { margin: 1, width: 220 }) });
}));

/** Revisa el código de 6 dígitos y entrega la sesión (8 horas). La primera vez además activa el TOTP. */
router.post('/api/auth/totp', ruta(async (req, res) => {
  const u = await adminDelDesafio(req.body.desafio);
  if (bloqueado(req, u.email)) throw new ErrorHttp(429, 'Demasiados intentos fallidos. Espera 15 minutos e inténtalo de nuevo.');
  const paso = u.totp_secreto ? totp.verificar(u.totp_secreto, req.body.codigo, u.totp_ultimo_paso) : null;
  if (paso === null) {
    registrarFallo(req, u.email);
    await mongo.auditar(u, 'TOTP_FALLIDO');
    throw new ErrorHttp(401, 'Código incorrecto. Revisa tu app autenticadora.');
  }
  await query('UPDATE usuarios SET totp_activo = TRUE, totp_ultimo_paso = $2 WHERE id = $1', [u.id, paso]);
  limpiarIntentos(req, u.email);
  await mongo.auditar(u, u.totp_activo ? 'INICIO_SESION' : 'TOTP_ACTIVADO');
  res.json({ token: await iniciarSesion({ ...u, totp: true }), rol: u.rol, nombre: u.nombre, email: u.email });
}));

/** Cierra la sesión actual (el token deja de servir). */
router.post('/api/auth/logout', autenticar, ruta(async (req, res) => {
  await query('UPDATE sesiones SET revocada = TRUE WHERE id = $1', [req.usuario.sid]);
  res.status(204).end();
}));

router.post('/api/auth/registro-conductor', ruta(async (req, res) => {
  const b = req.body;
  const email = normalizarEmail(b.correo);
  const obligatorios = ['rut', 'nombre', 'direccion', 'comuna', 'celular', 'telefonoEmergencia', 'licencia', 'comunaEmision'];
  const falta = obligatorios.find((k) => !String(b[k] || '').trim());
  if (falta) throw new ErrorHttp(400, `Falta el campo ${falta}`);
  if (!emailValido(email)) throw new ErrorHttp(400, 'Ingresa un correo válido');
  if (!passwordValida(b.password)) throw new ErrorHttp(400, MENSAJE_PASSWORD);
  const rut = rutNormalizado(b.rut);
  if (!['A2', 'A3'].includes(b.licencia)) throw new ErrorHttp(400, 'La licencia debe ser A2 o A3');
  const emision = fechaIso(b.emisionLicencia);
  const vencimiento = fechaIso(b.vencimientoLicencia);
  if (!emision || !vencimiento) throw new ErrorHttp(400, 'Las fechas de la licencia deben tener el formato dd-mm-aaaa');
  if (vencimiento <= emision) throw new ErrorHttp(400, 'El vencimiento de la licencia debe ser posterior a la emisión');

  const hash = await bcrypt.hash(b.password, 10);
  await transaccion(async (c) => {
    const u = await c.query(
      `INSERT INTO usuarios (email, password_hash, nombre, rol_id, verificado)
       VALUES ($1, $2, $3, (SELECT id FROM roles WHERE nombre = 'CONDUCTOR'), TRUE)
       ON CONFLICT (email) DO NOTHING RETURNING id`,
      [email, hash, b.nombre.trim()],
    );
    if (u.rowCount === 0) throw new ErrorHttp(409, 'Ese correo ya tiene una cuenta');
    const cond = await c.query(
      `INSERT INTO conductores (usuario_id, nombre_completo, rut, direccion, comuna, fono_celular, fono_domicilio,
                                fono_emergencia, tipo_licencia, municipio_licencia, fecha_obtencion_licencia,
                                fecha_vencimiento_licencia)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)
       ON CONFLICT (rut) DO NOTHING RETURNING id`,
      [u.rows[0].id, b.nombre.trim(), rut, b.direccion.trim(), b.comuna.trim(), b.celular.trim(),
        String(b.telefonoFijo || '').trim() || null, b.telefonoEmergencia.trim(), b.licencia, b.comunaEmision.trim(),
        emision, vencimiento],
    );
    if (cond.rowCount === 0) throw new ErrorHttp(409, 'Ese RUT ya está registrado');
  });
  await mongo.auditar({ email, rol: 'CONDUCTOR' }, 'REGISTRO_CONDUCTOR', { rut });
  res.status(201).json({ email, estado: 'PENDIENTE' });
}));

module.exports = router;
