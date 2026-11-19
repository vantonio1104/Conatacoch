// T5 · Fotos de la licencia: las sube el conductor al registrarse y solo la administración las ve.
require('./preparar');
const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const request = require('supertest');
const { app } = require('../server');
const { inicializarBaseDeDatos } = require('../db/init');
const { pool, query } = require('../db/postgres');
const { DEMO, conToken, loginAdmin } = require('./ayuda');

const PNG = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';
const JPEG = Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0]), Buffer.alloc(200, 7)]).toString('base64');
const tokens = {};

const registro = (extra) => request(app).post('/api/auth/registro-conductor').send({
  nombre: 'Sergio Pizarro Olivares', direccion: 'Calle 3', comuna: 'San Ramón', celular: '+56944444444',
  telefonoEmergencia: '+56955555555', licencia: 'A2', comunaEmision: 'San Ramón', password: 'una-clave-de-prueba-segura',
  emisionLicencia: '01-01-2024', vencimientoLicencia: '01-01-2030', ...extra,
});
const idPorRut = async (rut) => (await query('SELECT id FROM conductores WHERE rut = $1', [rut])).rows[0]?.id;

before(async () => {
  await inicializarBaseDeDatos();
  tokens.admin = await loginAdmin(app);
  tokens.pasajero = (await request(app).post('/api/auth/login').send({ email: 'camila.reyes@correo.cl', password: DEMO })).body.token;
});
after(() => pool.end());

test('FO-01 el registro guarda la foto del frente (PNG) y del reverso (JPEG)', async () => {
  const r = await registro({ rut: '14.569.484-1', correo: 's.pizarro@correo.cl', fotoFrente: `data:image/png;base64,${PNG}`, fotoReverso: JPEG });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const fotos = (await query('SELECT lado, tipo FROM licencia_fotos WHERE conductor_id = $1 ORDER BY lado', [await idPorRut('14569484-1')])).rows;
  assert.deepEqual(fotos, [{ lado: 'FRENTE', tipo: 'image/png' }, { lado: 'REVERSO', tipo: 'image/jpeg' }]);
});

test('FO-02 una foto que no es JPEG ni PNG se rechaza y no se crea la cuenta', async () => {
  const gif = Buffer.from('GIF89a-no-sirve').toString('base64');
  const r = await registro({ rut: '6.666.666-2', correo: 'gif@correo.cl', fotoFrente: gif, fotoReverso: JPEG });
  assert.equal(r.status, 400);
  assert.match(r.body.error, /JPEG o PNG/);
  assert.equal(await idPorRut('6666666-2'), undefined);
});

test('FO-03 una foto de más de 1 MB se rechaza', async () => {
  const grande = Buffer.concat([Buffer.from([0xff, 0xd8, 0xff]), Buffer.alloc(1024 * 1024)]).toString('base64');
  const r = await registro({ rut: '7.777.777-6', correo: 'grande@correo.cl', fotoFrente: grande, fotoReverso: JPEG });
  assert.equal(r.status, 400);
  assert.match(r.body.error, /1 MB/);
});

test('FO-04 solo la administración descarga las fotos', async () => {
  const id = await idPorRut('14569484-1');
  const r = await request(app).get(`/api/conductores/${id}/fotos/frente`).set(conToken(tokens.admin));
  assert.equal(r.status, 200);
  assert.equal(r.headers['content-type'], 'image/png');
  assert.equal(r.headers['cache-control'], 'no-store');
  assert.deepEqual(r.body, Buffer.from(PNG, 'base64'));
  assert.equal((await request(app).get(`/api/conductores/${id}/fotos/frente`).set(conToken(tokens.pasajero))).status, 403);
  assert.equal((await request(app).get(`/api/conductores/${id}/fotos/frente`)).status, 401);
  const sinFotos = await idPorRut('12345678-5');
  assert.equal((await request(app).get(`/api/conductores/${sinFotos}/fotos/frente`).set(conToken(tokens.admin))).status, 404);
  assert.equal((await request(app).get(`/api/conductores/${id}/fotos/costado`).set(conToken(tokens.admin))).status, 400);
});

test('FO-05 la lista de conductores indica qué fotos tiene cada uno', async () => {
  const r = await request(app).get('/api/conductores?estado=PENDIENTE').set(conToken(tokens.admin));
  const c = r.body.find((x) => x.rut === '14569484-1');
  assert.deepEqual(c.fotos, ['FRENTE', 'REVERSO']);
});
