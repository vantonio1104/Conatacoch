// T1 · Sesiones revocables: el servidor guarda cada sesión y puede cerrarla (corrige el defecto D-13).
require('./preparar');
const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const request = require('supertest');
const jwt = require('jsonwebtoken');
const { app } = require('../server');
const { inicializarBaseDeDatos } = require('../db/init');
const { pool, query } = require('../db/postgres');
const { loginAdmin } = require('./ayuda');

const DEMO = 'colectivo-demo-2026';
const conToken = (t) => ({ Authorization: `Bearer ${t}` });

async function login(email) {
  const r = await request(app).post('/api/auth/login').send({ email, password: DEMO });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.token;
}
const reservas = (t) => request(app).get('/api/reservas').set(conToken(t));
const sid = (t) => jwt.decode(t).sid;

before(inicializarBaseDeDatos);
after(() => pool.end());

test('SE-01 cada inicio de sesión crea una sesión y el token la usa', async () => {
  const t = await login('camila.reyes@correo.cl');
  assert.ok(sid(t));
  assert.equal((await query('SELECT COUNT(*)::int AS n FROM sesiones WHERE id = $1', [sid(t)])).rows[0].n, 1);
  assert.equal((await reservas(t)).status, 200);
});

test('SE-02 cerrar sesión revoca el token actual y no los demás', async () => {
  const a = await login('camila.reyes@correo.cl');
  const b = await login('camila.reyes@correo.cl');
  assert.equal((await request(app).post('/api/auth/logout').set(conToken(a))).status, 204);
  assert.equal((await reservas(a)).status, 401);
  assert.equal((await reservas(b)).status, 200);
});

test('SE-03 una sesión revocada en el servidor deja de servir', async () => {
  const t = await login('camila.reyes@correo.cl');
  await query('UPDATE sesiones SET revocada = TRUE WHERE id = $1', [sid(t)]);
  assert.equal((await reservas(t)).status, 401);
});

test('SE-04 el token del administrador dura 8 horas; el de la app, 30 días', async () => {
  const admin = jwt.decode(await loginAdmin(app));
  const pasajero = jwt.decode(await login('camila.reyes@correo.cl'));
  assert.equal(admin.exp - admin.iat, 8 * 3600);
  assert.equal(pasajero.exp - pasajero.iat, 30 * 24 * 3600);
});

test('SE-05 el administrador sale tras 30 minutos sin actividad; el pasajero no', async () => {
  const admin = await loginAdmin(app);
  const pasajero = await login('camila.reyes@correo.cl');
  await query("UPDATE sesiones SET ultimo_uso = NOW() - INTERVAL '31 minutes' WHERE id = ANY($1)", [[sid(admin), sid(pasajero)]]);
  assert.equal((await request(app).get('/api/admin/resumen').set(conToken(admin))).status, 401);
  assert.equal((await reservas(pasajero)).status, 200);
});

test('SE-06 una cuenta desactivada pierde sus sesiones', async () => {
  const t = await login('camila.reyes@correo.cl');
  await query("UPDATE usuarios SET activo = FALSE WHERE email = 'camila.reyes@correo.cl'");
  try {
    assert.equal((await reservas(t)).status, 401);
  } finally {
    await query("UPDATE usuarios SET activo = TRUE WHERE email = 'camila.reyes@correo.cl'");
  }
});

test('SE-07 un token firmado sin sesión (formato anterior) se rechaza', async () => {
  const u = (await query("SELECT id FROM usuarios WHERE email = 'camila.reyes@correo.cl'")).rows[0];
  const viejo = jwt.sign({ id: u.id, email: 'camila.reyes@correo.cl', rol: 'PASAJERO' }, process.env.JWT_SECRET, { expiresIn: '30d' });
  assert.equal((await reservas(viejo)).status, 401);
});
