// T10 · Recorridos y tarifas (Admin 8): lista para la administración y cambio de tarifa.
require('./preparar');
const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const request = require('supertest');
const { app } = require('../server');
const { inicializarBaseDeDatos } = require('../db/init');
const { pool } = require('../db/postgres');
const { DEMO, conToken, loginAdmin } = require('./ayuda');

const t = {};
const recorridos = (token = t.admin) => request(app).get('/api/admin/recorridos').set(conToken(token));
const tarifa = (folio, valor, token = t.admin) => request(app).put(`/api/admin/recorridos/${folio}/tarifa`).set(conToken(token)).send({ tarifa: valor });

before(async () => {
  await inicializarBaseDeDatos();
  t.admin = await loginAdmin(app);
  t.pasajero = (await request(app).post('/api/auth/login').send({ email: 'camila.reyes@correo.cl', password: DEMO })).body.token;
});
after(() => pool.end());

test('RE-01 la lista trae los 15 recorridos con tarifa, trazado y vehículos con turno hoy', async () => {
  const r = await recorridos();
  assert.equal(r.status, 200);
  assert.equal(r.body.length, 15);
  const l5003 = r.body.find((x) => x.folio === '235003');
  assert.equal(l5003.linea, '5003');
  assert.equal(l5003.tarifa, 800);
  assert.equal(typeof l5003.trazado_guardado, 'boolean');
  assert.ok(l5003.asignados_hoy >= 1); // Pedro Soto tiene turno hoy en los datos de ejemplo
  assert.ok(l5003.origen_comuna && l5003.destino_comuna);
});

test('RE-02 cambiar la tarifa: la app del pasajero ve la nueva en /api/rutas', async () => {
  const r = await tarifa('235003', 900);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.tarifa, 900);
  const publica = (await request(app).get('/api/rutas')).body.find((x) => x.folio === '235003');
  assert.equal(publica.tarifa, 900);
  await tarifa('235003', 800);
});

test('RE-03 tarifas inválidas: 400; recorrido inexistente: 404', async () => {
  for (const v of [0, -5, 'abc', 800.5, 20001, null]) assert.equal((await tarifa('235003', v)).status, 400, `tarifa ${v}`);
  assert.equal((await tarifa('999999', 900)).status, 404);
});

test('RE-04 solo la administración ve la lista y cambia tarifas', async () => {
  assert.equal((await recorridos(t.pasajero)).status, 403);
  assert.equal((await tarifa('235003', 900, t.pasajero)).status, 403);
});
