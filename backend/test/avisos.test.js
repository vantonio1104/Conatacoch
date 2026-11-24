// T8 · Consulta de avisos (Admin 5): ubicación del aviso, filtros por fecha y tipo, y emergencia reciente.
require('./preparar');
const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const request = require('supertest');
const { app } = require('../server');
const { inicializarBaseDeDatos } = require('../db/init');
const { pool, query } = require('../db/postgres');
const { DEMO, conToken, loginAdmin } = require('./ayuda');

const t = {};
const avisar = (datos) => request(app).post('/api/conductor/avisos').set(conToken(t.conductor)).send(datos);
const lista = (q = '') => request(app).get(`/api/admin/avisos${q}`).set(conToken(t.admin));
const hoy = async () => (await query("SELECT (NOW() AT TIME ZONE 'America/Santiago')::date::text AS f")).rows[0].f;

before(async () => {
  await inicializarBaseDeDatos();
  await query('DELETE FROM avisos');
  t.admin = await loginAdmin(app);
  t.conductor = (await request(app).post('/api/auth/login').send({ email: 'p.soto@correo.cl', password: DEMO })).body.token;
});
after(() => pool.end());

test('AV-01 la emergencia guarda la ubicación y aparece primera, marcada como reciente', async () => {
  assert.equal((await avisar({ codigo: 'AC-01' })).status, 201);
  assert.equal((await avisar({ codigo: 'AC-06', lat: -33.5312, lng: -70.6623 })).status, 201);
  const r = await lista();
  assert.equal(r.status, 200);
  const [ultimo, anterior] = r.body;
  assert.equal(ultimo.codigo, 'AC-06');
  assert.equal(ultimo.titulo, 'Emergencia');
  assert.equal(ultimo.conductor, 'Pedro Soto');
  assert.equal(ultimo.patente, 'ABCD-12');
  assert.equal(ultimo.lat, -33.5312);
  assert.equal(ultimo.lng, -70.6623);
  assert.equal(ultimo.fecha, await hoy());
  assert.match(ultimo.hora, /^\d{2}:\d{2}$/);
  assert.equal(ultimo.reciente, true);
  assert.equal(anterior.codigo, 'AC-01');
  assert.equal(anterior.lat, null); // sin GPS (las pruebas corren sin MongoDB)
});

test('AV-02 una ubicación imposible no se guarda, pero el aviso sí', async () => {
  assert.equal((await avisar({ codigo: 'AC-03', lat: 200, lng: -70.6 })).status, 201);
  const [ultimo] = (await lista()).body;
  assert.equal(ultimo.codigo, 'AC-03');
  assert.equal(ultimo.lat, null);
});

test('AV-03 filtros por fecha y tipo; fecha inválida: 400', async () => {
  const solo = await lista('?codigo=AC-06');
  assert.deepEqual(solo.body.map((a) => a.codigo), ['AC-06']);
  assert.equal((await lista(`?fecha=${await hoy()}`)).body.length, 3);
  assert.equal((await lista('?fecha=2020-01-01')).body.length, 0);
  assert.equal((await lista('?fecha=31-02-2026')).status, 400);
});

test('AV-04 el aviso AC-04 (falla de cámara) no existe; una emergencia antigua no es reciente', async () => {
  assert.equal((await avisar({ codigo: 'AC-04' })).status, 400);
  await query("UPDATE avisos SET creado_en = NOW() - INTERVAL '2 hours' WHERE codigo = 'AC-06'");
  const emergencia = (await lista('?codigo=AC-06')).body[0];
  assert.equal(emergencia.reciente, false);
});
