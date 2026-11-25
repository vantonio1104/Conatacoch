// T9 · Gestión de cuentas (Admin 6): sesiones activas, cerrar sesiones, desactivar y crear administradores.
require('./preparar');
const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const request = require('supertest');
const { app } = require('../server');
const { inicializarBaseDeDatos } = require('../db/init');
const { pool, query } = require('../db/postgres');
const { DEMO, conToken, loginAdmin } = require('./ayuda');

const t = {};
const login = (email, password = DEMO) => request(app).post('/api/auth/login').send({ email, password });
const cuentas = (token = t.admin) => request(app).get('/api/admin/cuentas').set(conToken(token));
const cuentaDe = async (email) => (await cuentas()).body.find((c) => c.email === email);
const reservas = (token) => request(app).get('/api/reservas').set(conToken(token));

before(async () => {
  await inicializarBaseDeDatos();
  t.admin = await loginAdmin(app);
  t.camila1 = (await login('camila.reyes@correo.cl')).body.token;
  t.camila2 = (await login('camila.reyes@correo.cl')).body.token;
});
after(() => pool.end());

test('CU-01 la lista trae rol, estado y sesiones activas de cada cuenta', async () => {
  const r = await cuentas();
  assert.equal(r.status, 200);
  const camila = r.body.find((c) => c.email === 'camila.reyes@correo.cl');
  assert.equal(camila.nombre, 'Camila Reyes');
  assert.equal(camila.rol, 'PASAJERO');
  assert.equal(camila.activo, true);
  assert.ok(camila.sesiones_activas >= 2);
  assert.ok(r.body.some((c) => c.rol === 'ADMIN') && r.body.some((c) => c.rol === 'CONDUCTOR'));
  assert.equal(r.body.some((c) => 'password_hash' in c || 'totp_secreto' in c), false);
});

test('CU-02 «Cerrar sesiones» invalida todos los tokens de la cuenta', async () => {
  const id = (await cuentaDe('camila.reyes@correo.cl')).id;
  const r = await request(app).post(`/api/admin/cuentas/${id}/cerrar-sesiones`).set(conToken(t.admin));
  assert.equal(r.status, 200);
  assert.ok(r.body.cerradas >= 2);
  assert.equal((await reservas(t.camila1)).status, 401);
  assert.equal((await reservas(t.camila2)).status, 401);
  assert.equal((await cuentaDe('camila.reyes@correo.cl')).sesiones_activas, 0);
});

test('CU-03 una cuenta desactivada no entra y pierde sus sesiones; al reactivarla vuelve a entrar', async () => {
  const token = (await login('camila.reyes@correo.cl')).body.token;
  const id = (await cuentaDe('camila.reyes@correo.cl')).id;
  const off = await request(app).post(`/api/admin/cuentas/${id}/estado`).set(conToken(t.admin)).send({ activo: false });
  assert.equal(off.status, 200);
  assert.equal(off.body.activo, false);
  assert.equal((await reservas(token)).status, 401);
  assert.equal((await login('camila.reyes@correo.cl')).status, 401);
  assert.equal((await request(app).post(`/api/admin/cuentas/${id}/estado`).set(conToken(t.admin)).send({ activo: true })).status, 200);
  assert.equal((await login('camila.reyes@correo.cl')).status, 200);
});

test('CU-04 un administrador no puede desactivarse a sí mismo', async () => {
  const yo = (await cuentaDe('admin@conatacoch.cl')).id;
  const r = await request(app).post(`/api/admin/cuentas/${yo}/estado`).set(conToken(t.admin)).send({ activo: false });
  assert.equal(r.status, 400);
  assert.equal((await request(app).post('/api/admin/cuentas/999999/estado').set(conToken(t.admin)).send({ activo: false })).status, 404);
});

test('CU-05 crear administrador: entrega una contraseña temporal una sola vez y valida los datos', async () => {
  const r = await request(app).post('/api/admin/administradores').set(conToken(t.admin))
    .send({ nombre: 'Daniela Herrera Soto', email: 'D.Herrera@conatacoch.cl' });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.email, 'd.herrera@conatacoch.cl');
  assert.ok([...r.body.clave_temporal].length >= 15);
  t.claveTemporal = r.body.clave_temporal;
  const dup = await request(app).post('/api/admin/administradores').set(conToken(t.admin)).send({ nombre: 'Otra', email: 'd.herrera@conatacoch.cl' });
  assert.equal(dup.status, 409);
  assert.equal((await request(app).post('/api/admin/administradores').set(conToken(t.admin)).send({ nombre: '', email: 'x@conatacoch.cl' })).status, 400);
  assert.equal((await request(app).post('/api/admin/administradores').set(conToken(t.admin)).send({ nombre: 'X', email: 'no-es-correo' })).status, 400);
  const nueva = await cuentaDe('d.herrera@conatacoch.cl');
  assert.equal(nueva.rol, 'ADMIN');
  assert.equal(nueva.debe_cambiar_clave, true);
});

test('CU-06 el nuevo administrador configura su TOTP y debe cambiar la contraseña antes de usar el panel', async () => {
  const token = await loginAdmin(app, 'd.herrera@conatacoch.cl', t.claveTemporal);
  const bloqueado = await request(app).get('/api/admin/resumen').set(conToken(token));
  assert.equal(bloqueado.status, 403);
  assert.match(bloqueado.body.error, /cambiar tu contraseña/);
  const cambiar = (actual, nueva) => request(app).post('/api/auth/cambiar-clave').set(conToken(token)).send({ actual, nueva });
  assert.equal((await cambiar('incorrecta-incorrecta', 'una-clave-nueva-y-segura')).status, 400);
  assert.equal((await cambiar(t.claveTemporal, 'corta')).status, 400);
  assert.equal((await cambiar(t.claveTemporal, t.claveTemporal)).status, 400);
  assert.equal((await cambiar(t.claveTemporal, 'una-clave-nueva-y-segura')).status, 204);
  assert.equal((await request(app).get('/api/admin/resumen').set(conToken(token))).status, 200);
  assert.equal((await login('d.herrera@conatacoch.cl', t.claveTemporal)).status, 401); // la temporal ya no sirve
  assert.equal((await cuentaDe('d.herrera@conatacoch.cl')).debe_cambiar_clave, false);
});

test('CU-07 solo la administración gestiona cuentas', async () => {
  const pasajero = (await login('camila.reyes@correo.cl')).body.token;
  assert.equal((await cuentas(pasajero)).status, 403);
  assert.equal((await request(app).post('/api/admin/administradores').set(conToken(pasajero)).send({ nombre: 'X', email: 'x@x.cl' })).status, 403);
});
