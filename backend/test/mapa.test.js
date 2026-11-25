// T13 · Mapa global en vivo: Leaflet servido desde el propio servidor y posiciones de la flota.
require('./preparar');
const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const request = require('supertest');
const { app } = require('../server');
const { inicializarBaseDeDatos } = require('../db/init');
const { pool } = require('../db/postgres');
const { DEMO, conToken, loginAdmin } = require('./ayuda');

const t = {};
before(async () => {
  await inicializarBaseDeDatos();
  t.admin = await loginAdmin(app);
  t.pasajero = (await request(app).post('/api/auth/login').send({ email: 'camila.reyes@correo.cl', password: DEMO })).body.token;
});
after(() => pool.end());

test('MA-01 Leaflet se entrega desde /admin/vendor con la misma CSP del panel', async () => {
  const js = await request(app).get('/admin/vendor/leaflet/leaflet.js');
  assert.equal(js.status, 200);
  assert.match(js.headers['content-type'], /javascript/);
  assert.match(js.headers['content-security-policy'], /script-src 'self'/);
  assert.match(js.headers['content-security-policy'], /img-src [^;]*https:\/\/tile\.openstreetmap\.org/);
  assert.equal((await request(app).get('/admin/vendor/leaflet/leaflet.css')).status, 200);
});

test('MA-02 la flota trae patente, línea, conductor, servicio y posición; solo para la administración', async () => {
  const r = await request(app).get('/api/admin/flota').set(conToken(t.admin));
  assert.equal(r.status, 200);
  const abcd = r.body.find((v) => v.patente === 'ABCD-12');
  assert.equal(abcd.linea, '5003');
  assert.equal(abcd.conductor, 'Pedro Soto');
  assert.equal(typeof abcd.en_servicio, 'boolean');
  assert.equal(abcd.posicion, null); // las pruebas corren sin MongoDB
  assert.equal((await request(app).get('/api/admin/flota').set(conToken(t.pasajero))).status, 403);
});
