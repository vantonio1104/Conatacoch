// T3 · Segundo factor (TOTP, RFC 6238) para la administración.
require('./preparar');
const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const request = require('supertest');
const jwt = require('jsonwebtoken');
const totp = require('../totp');
const { app } = require('../server');
const { inicializarBaseDeDatos } = require('../db/init');
const { pool, query } = require('../db/postgres');
const { DEMO, loginAdmin } = require('./ayuda');

const ADMIN = 'admin@conatacoch.cl';
const loginClave = (email = ADMIN) => request(app).post('/api/auth/login').send({ email, password: DEMO });
const enviarCodigo = (desafio, codigo) => request(app).post('/api/auth/totp').send({ desafio, codigo });
const secretoAdmin = async () => (await query('SELECT totp_secreto FROM usuarios WHERE email = $1', [ADMIN])).rows[0].totp_secreto;

before(async () => {
  await inicializarBaseDeDatos();
  await query('UPDATE usuarios SET totp_secreto = NULL, totp_activo = FALSE, totp_ultimo_paso = NULL WHERE email = $1', [ADMIN]);
});
after(() => pool.end());

test('TO-01 el código coincide con los vectores de prueba del RFC 6238 (SHA-1)', () => {
  const secreto = totp.base32('12345678901234567890'); // clave de prueba del RFC
  assert.equal(totp.codigo(secreto, 59_000), '287082');
  assert.equal(totp.codigo(secreto, 1_111_111_109_000), '081804');
  assert.equal(totp.codigo(secreto, 1_234_567_890_000), '005924');
});

test('TO-02 la contraseña del administrador no entrega sesión: pide configurar el TOTP', async () => {
  const r = await loginClave();
  assert.equal(r.status, 200);
  assert.equal(r.body.requiereTotp, true);
  assert.equal(r.body.configurar, true);
  assert.equal(r.body.token, undefined);
});

test('TO-03 alta: entrega clave y QR; con un código correcto se activa y entrega la sesión', async () => {
  const { body: { desafio } } = await loginClave();
  const alta = await request(app).post('/api/auth/totp/alta').send({ desafio });
  assert.equal(alta.status, 200);
  assert.match(alta.body.secreto, /^[A-Z2-7]{32}$/);
  assert.match(alta.body.qr, /^data:image\/png;base64,/);
  assert.match(alta.body.uri, /^otpauth:\/\/totp\/CONATACOCH:admin%40conatacoch\.cl\?secret=/);
  assert.equal((await enviarCodigo(desafio, '000000')).status, 401);
  const r = await enviarCodigo(desafio, totp.codigo(alta.body.secreto));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const datos = jwt.decode(r.body.token);
  assert.equal(datos.rol, 'ADMIN');
  assert.equal(datos.totp, true);
  assert.ok(datos.sid);
  assert.equal((await query('SELECT totp_activo FROM usuarios WHERE email = $1', [ADMIN])).rows[0].totp_activo, true);
});

test('TO-04 con el TOTP activo no se puede volver a dar de alta ni reutilizar un código', async () => {
  const { body } = await loginClave();
  assert.equal(body.configurar, false);
  assert.equal((await request(app).post('/api/auth/totp/alta').send({ desafio: body.desafio })).status, 409);
  const usado = totp.codigo(await secretoAdmin()); // ya se usó en TO-03 (mismos 30 s) o se usa ahora
  await enviarCodigo(body.desafio, usado);
  assert.equal((await enviarCodigo(body.desafio, usado)).status, 401);
});

test('TO-05 un desafío alterado o vencido no sirve', async () => {
  const id = (await query('SELECT id FROM usuarios WHERE email = $1', [ADMIN])).rows[0].id;
  const codigo = totp.codigo(await secretoAdmin());
  const falso = jwt.sign({ tipo: 'desafio-totp', id }, 'otra-clave', { expiresIn: 300 });
  const vencido = jwt.sign({ tipo: 'desafio-totp', id, exp: Math.floor(Date.now() / 1000) - 1 }, process.env.JWT_SECRET);
  const sesionNormal = jwt.sign({ id, rol: 'ADMIN' }, process.env.JWT_SECRET, { expiresIn: 300 }); // no es un desafío
  for (const d of [falso, vencido, sesionNormal]) assert.equal((await enviarCodigo(d, codigo)).status, 401);
});

test('TO-06 cinco códigos incorrectos bloquean 15 minutos', async () => {
  const { body: { desafio } } = await loginClave();
  for (let i = 0; i < 5; i++) assert.equal((await enviarCodigo(desafio, '123456')).status, 401);
  assert.equal((await enviarCodigo(desafio, totp.codigo(await secretoAdmin()))).status, 429);
});

test('TO-07 pasajeros y conductores siguen entrando solo con contraseña', async () => {
  const r = await loginClave('camila.reyes@correo.cl');
  assert.equal(r.status, 200);
  assert.ok(r.body.token);
  assert.equal(r.body.requiereTotp, undefined);
});

test('TO-08 la ayuda de pruebas entra como administrador con TOTP', async () => {
  // El bloqueo de TO-06 es por IP y correo: se usa otra cuenta de administrador.
  await query(`INSERT INTO usuarios (email, password_hash, nombre, rol_id, verificado)
               SELECT 'otro.admin@conatacoch.cl', password_hash, 'Otro Admin', rol_id, TRUE FROM usuarios WHERE email = $1
               ON CONFLICT (email) DO NOTHING`, [ADMIN]);
  const t = await loginAdmin(app, 'otro.admin@conatacoch.cl');
  assert.equal((await request(app).get('/api/admin/resumen').set({ Authorization: `Bearer ${t}` })).status, 200);
});
