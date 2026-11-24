// T6 · Asignaciones con fecha y horario (tabla turnos): choques, estados e inicio de servicio solo dentro del turno.
require('./preparar');
const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const request = require('supertest');
const { app } = require('../server');
const { inicializarBaseDeDatos } = require('../db/init');
const { pool, query } = require('../db/postgres');
const { DEMO, conToken, loginAdmin } = require('./ayuda');

const t = {};
const ids = {};
const HOY = "(NOW() AT TIME ZONE 'America/Santiago')::date";
const dia = async (n) => (await query(`SELECT (${HOY} + $1::int)::text AS f`, [n])).rows[0].f;
const crear = (datos) => request(app).post('/api/admin/turnos').set(conToken(t.admin)).send(datos);
const iniciar = () => request(app).post('/api/conductor/servicio/iniciar').set(conToken(t.conductor));
const finalizar = () => request(app).post('/api/conductor/servicio/finalizar').set(conToken(t.conductor));

before(async () => {
  await inicializarBaseDeDatos();
  t.admin = await loginAdmin(app);
  t.conductor = (await request(app).post('/api/auth/login').send({ email: 'p.soto@correo.cl', password: DEMO })).body.token;
  const id = async (sql, v) => (await query(sql, [v])).rows[0].id;
  ids.pedro = await id('SELECT id FROM conductores WHERE rut = $1', '12345678-5');
  ids.juan = await id('SELECT id FROM conductores WHERE rut = $1', '9876543-3');
  ids.abcd = await id('SELECT id FROM automoviles WHERE patente = $1', 'ABCD-12');
  ids.wxyz = await id('SELECT id FROM automoviles WHERE patente = $1', 'WXYZ-34');
  await query('DELETE FROM turnos'); // cada prueba arma sus propios turnos
  // Juan Pérez tiene la licencia vencida en los datos de ejemplo: aquí se renueva para poder asignarle turnos.
  await query(`UPDATE conductores SET fecha_vencimiento_licencia = ${HOY} + 3650 WHERE id = $1`, [ids.juan]);
});
after(async () => {
  await query('UPDATE servicios SET fin = NOW() WHERE fin IS NULL');
  await pool.end();
});

test('AS-01 crear un turno y verlo en la lista con su estado', async () => {
  const fecha = await dia(3);
  const r = await crear({ conductor_id: ids.pedro, automovil_id: ids.abcd, ruta_folio: '235003', fecha, hora_inicio: '06:00', hora_fin: '14:00' });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.estado, 'PROGRAMADA');
  const lista = await request(app).get(`/api/admin/turnos?desde=${fecha}&dias=1`).set(conToken(t.admin));
  assert.equal(lista.status, 200);
  assert.deepEqual(lista.body.map((x) => [x.conductor, x.patente, x.linea, x.fecha, x.inicio, x.fin]),
    [['Pedro Soto', 'ABCD-12', '5003', fecha, '06:00', '14:00']]);
});

test('AS-02 choque de horario del mismo conductor: 409 con el mensaje de Figma', async () => {
  const fecha = await dia(3);
  const r = await crear({ conductor_id: ids.pedro, automovil_id: ids.abcd, ruta_folio: '235003', fecha, hora_inicio: '10:00', hora_fin: '18:00' });
  assert.equal(r.status, 409);
  const [a, m, d] = fecha.split('-');
  assert.equal(r.body.error, `Choque de horario: Pedro Soto ya tiene asignada la línea 5003 el ${d}-${m}-${a} de 06:00 a 14:00.`);
});

test('AS-03 turnos que solo se tocan (14:00) no chocan', async () => {
  const r = await crear({ conductor_id: ids.pedro, automovil_id: ids.abcd, ruta_folio: '235003', fecha: await dia(3), hora_inicio: '14:00', hora_fin: '22:00' });
  assert.equal(r.status, 201, JSON.stringify(r.body));
});

test('AS-04 el mismo vehículo no puede estar con dos conductores a la vez', async () => {
  const r = await crear({ conductor_id: ids.juan, automovil_id: ids.abcd, ruta_folio: '235003', fecha: await dia(3), hora_inicio: '12:00', hora_fin: '13:00' });
  assert.equal(r.status, 409);
  assert.match(r.body.error, /^Choque de horario: el vehículo ABCD-12 ya está asignado a Pedro Soto/);
});

test('AS-05 validaciones: horario, fecha pasada, vehículo de otro recorrido y licencia vencida', async () => {
  const base = { conductor_id: ids.pedro, automovil_id: ids.abcd, ruta_folio: '235003', fecha: await dia(5), hora_inicio: '06:00', hora_fin: '14:00' };
  assert.equal((await crear({ ...base, hora_fin: '06:00' })).status, 400);
  assert.equal((await crear({ ...base, hora_inicio: '25:00' })).status, 400);
  assert.equal((await crear({ ...base, fecha: await dia(-1) })).status, 400);
  const otroRecorrido = await crear({ ...base, automovil_id: ids.wxyz });
  assert.equal(otroRecorrido.status, 400);
  assert.match(otroRecorrido.body.error, /no hace ese recorrido/);
  const antes = (await query('SELECT fecha_vencimiento_licencia FROM conductores WHERE id = $1', [ids.pedro])).rows[0].fecha_vencimiento_licencia;
  await query(`UPDATE conductores SET fecha_vencimiento_licencia = ${HOY} + 4 WHERE id = $1`, [ids.pedro]);
  try {
    const r = await crear(base); // el turno es en 5 días y la licencia vence en 4
    assert.equal(r.status, 400);
    assert.match(r.body.error, /licencia/);
  } finally {
    await query('UPDATE conductores SET fecha_vencimiento_licencia = $2 WHERE id = $1', [ids.pedro, antes]);
  }
});

test('AS-06 el conductor solo inicia servicio dentro de un turno vigente, con el vehículo del turno', async () => {
  await finalizar();
  const sinTurno = await iniciar();
  assert.equal(sinTurno.status, 409);
  assert.match(sinTurno.body.error, /turno/);
  await query(`INSERT INTO turnos (conductor_id, fecha, ruta_folio, automovil_id, hora_inicio, hora_fin)
               VALUES ($1, ${HOY}, '235003', $2, '00:00', '23:59')`, [ids.pedro, ids.abcd]);
  const r = await iniciar();
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const sv = (await query(`SELECT a.patente, sv.ruta_folio FROM servicios sv JOIN automoviles a ON a.id = sv.automovil_id
                           WHERE sv.conductor_id = $1 AND sv.fin IS NULL`, [ids.pedro])).rows[0];
  assert.deepEqual(sv, { patente: 'ABCD-12', ruta_folio: '235003' });
  await finalizar();
});

test('AS-07 la app del conductor ve sus turnos de los próximos 7 días; los días sin turno quedan libres', async () => {
  const r = await request(app).get('/api/conductor/turnos').set(conToken(t.conductor));
  assert.equal(r.status, 200);
  assert.equal(r.body.length, 7);
  const enTres = r.body[3];
  assert.equal(enTres.horario, '06:00–14:00, 14:00–22:00');
  assert.equal(enTres.linea, '5003');
  assert.equal(enTres.patente, 'ABCD-12');
  assert.equal(r.body[1].horario, null);
});

test('AS-08 borrar un turno futuro; uno que ya terminó no se borra', async () => {
  const r = await crear({ conductor_id: ids.juan, automovil_id: ids.wxyz, ruta_folio: '235005', fecha: await dia(6), hora_inicio: '06:00', hora_fin: '14:00' });
  assert.equal(r.status, 201);
  assert.equal((await request(app).delete(`/api/admin/turnos/${r.body.id}`).set(conToken(t.admin))).status, 204);
  const viejo = (await query(`INSERT INTO turnos (conductor_id, fecha, ruta_folio, automovil_id, hora_inicio, hora_fin)
                              VALUES ($1, ${HOY} - 2, '235005', $2, '06:00', '14:00') RETURNING id`, [ids.juan, ids.wxyz])).rows[0].id;
  assert.equal((await request(app).delete(`/api/admin/turnos/${viejo}`).set(conToken(t.admin))).status, 400);
  const estados = await request(app).get(`/api/admin/turnos?desde=${await dia(-2)}&dias=1`).set(conToken(t.admin));
  assert.equal(estados.body[0].estado, 'FINALIZADA');
});

test('AS-09 solo la administración crea, lista o borra turnos', async () => {
  assert.equal((await request(app).post('/api/admin/turnos').set(conToken(t.conductor)).send({})).status, 403);
  assert.equal((await request(app).get('/api/admin/turnos').set(conToken(t.conductor))).status, 403);
});
