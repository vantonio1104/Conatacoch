// Datos mal formados: el servidor responde 400 con un mensaje claro, nunca 500 «Error interno».
require('./preparar');
const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const request = require('supertest');
const { app } = require('../server');
const { inicializarBaseDeDatos } = require('../db/init');
const { pool } = require('../db/postgres');
const { conToken, loginAdmin } = require('./ayuda');

const t = {};
before(async () => {
  await inicializarBaseDeDatos();
  t.admin = await loginAdmin(app);
});
after(() => pool.end());

const CLAVE = 'una-clave-de-prueba-larga';

test('EN-01 un id que no es número responde 400 en las rutas de la administración', async () => {
  const rutas = [
    ['post', '/api/admin/cuentas/abc/cerrar-sesiones'],
    ['delete', '/api/conductores/abc'],
    ['get', '/api/conductores/abc/fotos/FRENTE'],
    ['delete', '/api/admin/turnos/abc'],
  ];
  for (const [metodo, ruta] of rutas) {
    const r = await request(app)[metodo](ruta).set(conToken(t.admin)).send({});
    assert.equal(r.status, 400, `${metodo.toUpperCase()} ${ruta}`);
    assert.match(r.body.error, /formato/);
  }
});

test('EN-02 un texto más largo que su columna responde 400', async () => {
  const r = await request(app).post('/api/auth/registro')
    .send({ nombre: 'A'.repeat(300), email: 'largo@correo.cl', password: CLAVE });
  assert.equal(r.status, 400);
  assert.match(r.body.error, /largo/);
});

test('EN-03 registro de conductor con campos que no son texto responde 400', async () => {
  const r = await request(app).post('/api/auth/registro-conductor').send({
    rut: '11111111-1', nombre: 123, direccion: 'Av. Siempre Viva 1', comuna: 'La Granja', celular: '+56911111111',
    telefonoEmergencia: '+56922222222', licencia: 'A2', comunaEmision: 'La Granja', correo: 'numero@correo.cl',
    password: CLAVE, emisionLicencia: '01-01-2020', vencimientoLicencia: '01-01-2030',
  });
  assert.equal(r.status, 400);
});

test('EN-04 una misma red puede crear hasta 20 cuentas por hora; después responde 429', async () => {
  const estados = [];
  for (let i = 0; i < 22; i++) {
    const r = await request(app).post('/api/auth/registro').send({ nombre: 'Masivo', email: `masivo${i}@correo.cl`, password: CLAVE });
    estados.push(r.status);
  }
  assert.ok(estados.includes(201));
  assert.equal(estados.at(-1), 429);
  const conductor = await request(app).post('/api/auth/registro-conductor').send({
    rut: '12345678-5', nombre: 'Masivo', direccion: 'Calle 1', comuna: 'La Granja', celular: '+56911111111',
    telefonoEmergencia: '+56922222222', licencia: 'A2', comunaEmision: 'La Granja', correo: 'masivo-c@correo.cl',
    password: CLAVE, emisionLicencia: '01-01-2020', vencimientoLicencia: '01-01-2030',
  });
  assert.equal(conductor.status, 429);
  assert.match(conductor.body.error, /demasiadas cuentas/);
});
