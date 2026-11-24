// Pruebas de integración de la API con PostgreSQL: acceso, roles, CRUD, reglas de negocio y reportes.
// Cada prueba lleva el ID del plan de pruebas del informe (PU, PI, PS, PSE, RP).
require('./preparar');
const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const request = require('supertest');
const jwt = require('jsonwebtoken');
const { app } = require('../server');
const { inicializarBaseDeDatos } = require('../db/init');
const { pool, query } = require('../db/postgres');
const { expirarVencidas } = require('../reservas');
const { loginAdmin } = require('./ayuda');

const DEMO = 'colectivo-demo-2026';
const CLAVE = 'una-clave-de-prueba-segura';
const tokens = {};

async function login(email, password = DEMO) {
  const r = await request(app).post('/api/auth/login').send({ email, password });
  assert.equal(r.status, 200, `login de ${email}: ${JSON.stringify(r.body)}`);
  return r.body.token;
}
const conToken = (t) => ({ Authorization: `Bearer ${t}` });
const fecha = (dias) => `(NOW() AT TIME ZONE 'America/Santiago')::date + ${dias}`;

async function registrarPasajero(email) {
  const r = await request(app).post('/api/auth/registro').send({ nombre: 'Pasajero Prueba', email, password: CLAVE });
  assert.equal(r.status, 201);
  return r.body.token;
}

before(async () => {
  await inicializarBaseDeDatos();
  // Base limpia para las reglas que dependen del estado.
  await query("UPDATE servicios SET fin = NOW() WHERE fin IS NULL");
  await query("UPDATE reservas SET estado = 'CANCELADA' WHERE estado = 'ACTIVA'");
  tokens.admin = await loginAdmin(app);
  tokens.pasajero = await login('camila.reyes@correo.cl');
  tokens.conductor = await login('p.soto@correo.cl');
  // El conductor solo inicia servicio dentro de un turno: uno que cubre todo el día de hoy.
  await query(`INSERT INTO turnos (conductor_id, fecha, ruta_folio, automovil_id, hora_inicio, hora_fin)
               SELECT c.id, (NOW() AT TIME ZONE 'America/Santiago')::date, '235003', a.id, '00:00', '23:59'
               FROM conductores c, automoviles a WHERE c.rut = '12345678-5' AND a.patente = 'ABCD-12'`);
});

after(async () => {
  await query("UPDATE servicios SET fin = NOW() WHERE fin IS NULL");
  await pool.end();
});

// ---------- Acceso y sesiones ----------

test('PI-01 login con contraseña incorrecta: 401 con mensaje genérico', async () => {
  const r = await request(app).post('/api/auth/login').send({ email: 'camila.reyes@correo.cl', password: 'incorrecta-1234567' });
  assert.equal(r.status, 401);
  assert.equal(r.body.error, 'Correo o contraseña incorrectos');
  const r2 = await request(app).post('/api/auth/login').send({ email: 'noexiste@correo.cl', password: 'incorrecta-1234567' });
  assert.equal(r2.body.error, 'Correo o contraseña incorrectos'); // mismo mensaje: no se revela si el correo existe
});

test('PI-02 sexto intento fallido seguido: 429 (límite de intentos)', async () => {
  const email = 'fuerza.bruta@correo.cl';
  for (let i = 0; i < 5; i++) {
    const r = await request(app).post('/api/auth/login').send({ email, password: `mala-${i}-xxxxxxxxxx` });
    assert.equal(r.status, 401);
  }
  const r = await request(app).post('/api/auth/login').send({ email, password: 'otra-mala-xxxxxxxxxx' });
  assert.equal(r.status, 429);
});

test('PU-03 el registro no acepta el rol desde el cliente', async () => {
  const r = await request(app).post('/api/auth/registro')
    .send({ nombre: 'Intruso', email: 'intruso@correo.cl', password: CLAVE, rol: 'ADMIN' });
  assert.equal(r.status, 201);
  assert.equal(r.body.rol, 'PASAJERO');
  assert.equal(jwt.decode(r.body.token).rol, 'PASAJERO');
});

test('PU-02I registro con contraseña de 14 caracteres: 400', async () => {
  const r = await request(app).post('/api/auth/registro').send({ nombre: 'Corta', email: 'corta@correo.cl', password: 'a'.repeat(14) });
  assert.equal(r.status, 400);
});

test('PI-04 rol equivocado: 403; sin token: 401', async () => {
  assert.equal((await request(app).get('/api/conductores').set(conToken(tokens.pasajero))).status, 403);
  assert.equal((await request(app).get('/api/automoviles').set(conToken(tokens.conductor))).status, 403);
  assert.equal((await request(app).get('/api/admin/resumen').set(conToken(tokens.pasajero))).status, 403);
  assert.equal((await request(app).get('/api/conductores')).status, 401);
});

test('PI-05 / PSE-04 token alterado o vencido: 401', async () => {
  const falso = jwt.sign({ id: 1, rol: 'ADMIN' }, 'otra-clave');
  assert.equal((await request(app).get('/api/conductores').set(conToken(falso))).status, 401);
  const vencido = jwt.sign({ id: 1, rol: 'ADMIN', exp: Math.floor(Date.now() / 1000) - 60 }, process.env.JWT_SECRET);
  assert.equal((await request(app).get('/api/conductores').set(conToken(vencido))).status, 401);
});

test('PSE-10 verificar cuenta ya no entrega sesión sin estar autenticado (D-02)', async () => {
  const r = await request(app).post('/api/auth/verificar').send({ email: 'admin@conatacoch.cl', codigo: '123456' });
  assert.equal(r.status, 401);
});

test('PSE-03 registro de conductor con RUT inválido: 400', async () => {
  const r = await request(app).post('/api/auth/registro-conductor').send({
    rut: '12.345.678-9', nombre: 'Prueba', direccion: 'Calle 1', comuna: 'La Granja', celular: '+56911111111',
    telefonoEmergencia: '+56922222222', licencia: 'A2', comunaEmision: 'La Granja', correo: 'rutmalo@correo.cl',
    password: CLAVE, emisionLicencia: '10-03-2025', vencimientoLicencia: '10-03-2029',
  });
  assert.equal(r.status, 400);
});

test('PI-06 conductor pendiente de validación no puede iniciar servicio: 403', async () => {
  const r = await request(app).post('/api/auth/registro-conductor').send({
    rut: '15.234.567-4', nombre: 'Rodrigo Morales', direccion: 'Av. Santa Rosa 1234', comuna: 'La Granja',
    celular: '+56987654321', telefonoEmergencia: '+56912345678', licencia: 'A2', comunaEmision: 'La Granja',
    correo: 'r.morales@correo.cl', password: CLAVE, emisionLicencia: '18-03-2023', vencimientoLicencia: '18-03-2027',
  });
  assert.equal(r.status, 201);
  assert.equal(r.body.estado, 'PENDIENTE');
  const t = await login('r.morales@correo.cl', CLAVE);
  const s = await request(app).post('/api/conductor/servicio/iniciar').set(conToken(t));
  assert.equal(s.status, 403);
});

// ---------- CRUD de conductores ----------

test('CRUD-C conductores: crear, leer, editar, duplicado, validaciones y eliminar', async () => {
  const base = {
    rut: '11.111.117-0', nombre_completo: 'Conductor CRUD', direccion: 'Calle 2', fono_celular: '+56933333333',
    fono_domicilio: '225551111', tipo_licencia: 'A3', municipio_licencia: 'San Ramón',
    fecha_obtencion_licencia: '01-06-2024', fecha_vencimiento_licencia: '01-06-2028',
  };
  const c = await request(app).post('/api/conductores').set(conToken(tokens.admin)).send(base);
  assert.equal(c.status, 201);
  assert.equal(c.body.rut, '11111117-0');
  const lista = await request(app).get('/api/conductores').set(conToken(tokens.admin));
  assert.ok(lista.body.some((x) => x.id === c.body.id));
  const e = await request(app).put(`/api/conductores/${c.body.id}`).set(conToken(tokens.admin)).send({ ...base, direccion: 'Calle 3' });
  assert.equal(e.status, 200);
  assert.equal(e.body.direccion, 'Calle 3');
  assert.equal((await request(app).post('/api/conductores').set(conToken(tokens.admin)).send(base)).status, 409);
  assert.equal((await request(app).post('/api/conductores').set(conToken(tokens.admin)).send({ ...base, rut: '11.111.117-1' })).status, 400);
  assert.equal((await request(app).post('/api/conductores').set(conToken(tokens.admin)).send({ ...base, rut: '7.654.321-6', tipo_licencia: 'B' })).status, 400);
  assert.equal((await request(app).post('/api/conductores').set(conToken(tokens.admin))
    .send({ ...base, rut: '7.654.321-6', fecha_vencimiento_licencia: '01-01-2024' })).status, 400);
  assert.equal((await request(app).delete(`/api/conductores/${c.body.id}`).set(conToken(tokens.admin))).status, 204);
  assert.equal((await request(app).delete(`/api/conductores/${c.body.id}`).set(conToken(tokens.admin))).status, 404);
});

// ---------- CRUD de automóviles ----------

test('CRUD-A automóviles: crear, leer, editar, duplicado, validaciones y dar de baja', async () => {
  const base = { patente: 'prue-99', marca: 'Kia', modelo: 'Rio', ano: 2022, combustible: 'bencina',
    vencimiento_permiso: '31-03-2027', vencimiento_revision: '30-06-2027', vencimiento_seguro: '31-03-2027' };
  const c = await request(app).post('/api/automoviles').set(conToken(tokens.admin)).send(base);
  assert.equal(c.status, 201);
  assert.equal(c.body.patente, 'PRUE-99');
  assert.equal(c.body.semaforo, 'VIGENTE');
  const e = await request(app).put(`/api/automoviles/${c.body.id}`).set(conToken(tokens.admin)).send({ ...base, modelo: 'Soluto' });
  assert.equal(e.status, 200);
  assert.equal(e.body.modelo, 'Soluto');
  assert.equal((await request(app).post('/api/automoviles').set(conToken(tokens.admin)).send(base)).status, 409);
  assert.equal((await request(app).post('/api/automoviles').set(conToken(tokens.admin)).send({ ...base, patente: '12-ABCD' })).status, 400);
  assert.equal((await request(app).post('/api/automoviles').set(conToken(tokens.admin)).send({ ...base, patente: 'PRUE-98', ano: 1980 })).status, 400);
  assert.equal((await request(app).post('/api/automoviles').set(conToken(tokens.admin)).send({ ...base, patente: 'PRUE-98', combustible: 'leña' })).status, 400);
  assert.equal((await request(app).delete(`/api/automoviles/${c.body.id}`).set(conToken(tokens.admin))).status, 204);
  const lista = await request(app).get('/api/automoviles').set(conToken(tokens.admin));
  assert.ok(!lista.body.some((x) => x.id === c.body.id)); // dado de baja: ya no aparece
});

// ---------- Servicio, reservas y ocupación ----------

test('PS-03 / PU-04 / PU-06 servicio, reserva única y ocupación dentro de la capacidad', async () => {
  const s = await request(app).post('/api/conductor/servicio/iniciar').set(conToken(tokens.conductor));
  assert.equal(s.status, 200, JSON.stringify(s.body));
  const r1 = await request(app).post('/api/reservas').set(conToken(tokens.pasajero)).send({ patente: 'ABCD-12' });
  assert.equal(r1.status, 201, JSON.stringify(r1.body));
  const r2 = await request(app).post('/api/reservas').set(conToken(tokens.pasajero)).send({ patente: 'ABCD-12' });
  assert.equal(r2.status, 409); // PU-04: una sola reserva activa por pasajero
  const o = await request(app).post('/api/conductor/ocupacion').set(conToken(tokens.conductor)).send({ a_bordo: 4 });
  assert.equal(o.status, 400); // PU-06: 4 a bordo + 1 reservado supera la capacidad de 4
  const ok = await request(app).post('/api/conductor/ocupacion').set(conToken(tokens.conductor)).send({ a_bordo: 2 });
  assert.equal(ok.status, 200);
  assert.equal((await request(app).delete('/api/reservas/activa').set(conToken(tokens.pasajero))).status < 300, true);
});

test('PI-13 dos pasajeros piden el último asiento al mismo tiempo: solo uno lo obtiene', async () => {
  const a = await registrarPasajero('concurrente.a@correo.cl');
  const b = await registrarPasajero('concurrente.b@correo.cl');
  const o = await request(app).post('/api/conductor/ocupacion').set(conToken(tokens.conductor)).send({ a_bordo: 3 });
  assert.equal(o.status, 200);
  const [ra, rb] = await Promise.all([
    request(app).post('/api/reservas').set(conToken(a)).send({ patente: 'ABCD-12' }),
    request(app).post('/api/reservas').set(conToken(b)).send({ patente: 'ABCD-12' }),
  ]);
  const creadas = [ra.status, rb.status].filter((x) => x === 201).length;
  const activas = (await query(
    "SELECT COUNT(*)::int AS n FROM reservas r JOIN automoviles a ON a.id = r.automovil_id WHERE a.patente = 'ABCD-12' AND r.estado = 'ACTIVA'",
  )).rows[0].n;
  await query("UPDATE reservas SET estado = 'CANCELADA' WHERE estado = 'ACTIVA'");
  assert.equal(creadas, 1, `respuestas ${ra.status} y ${rb.status}`);
  assert.equal(activas, 1);
});

test('PU-07 posición con velocidad imposible: 422', async () => {
  const r = await request(app).post('/api/conductor/posicion').set(conToken(tokens.conductor))
    .send({ lat: -33.53, lng: -70.66, velocidad: 300 });
  assert.equal(r.status, 422);
  const ok = await request(app).post('/api/conductor/posicion').set(conToken(tokens.conductor))
    .send({ lat: -33.53, lng: -70.66, velocidad: 40 });
  assert.equal(ok.status, 204);
});

test('PU-05 una reserva sin abordaje vence a los 3 minutos', async () => {
  const pas = (await query("SELECT id FROM usuarios WHERE email = 'camila.reyes@correo.cl'")).rows[0].id;
  const auto = (await query("SELECT id FROM automoviles WHERE patente = 'ABCD-12'")).rows[0].id;
  const vieja = (await query(
    `INSERT INTO reservas (pasajero_id, automovil_id, ruta_folio, creada_en)
     VALUES ($1, $2, '235003', NOW() - INTERVAL '3 minutes 1 second') RETURNING id`, [pas, auto],
  )).rows[0].id;
  await expirarVencidas();
  assert.equal((await query('SELECT estado FROM reservas WHERE id = $1', [vieja])).rows[0].estado, 'EXPIRADA');
  const nueva = (await query(
    `INSERT INTO reservas (pasajero_id, automovil_id, ruta_folio, creada_en)
     VALUES ($1, $2, '235003', NOW() - INTERVAL '2 minutes') RETURNING id`, [pas, auto],
  )).rows[0].id;
  await expirarVencidas();
  assert.equal((await query('SELECT estado FROM reservas WHERE id = $1', [nueva])).rows[0].estado, 'ACTIVA');
  await query("UPDATE reservas SET estado = 'CANCELADA' WHERE id = $1", [nueva]);
});

test('PU-08 conductor con licencia vencida no puede iniciar servicio: 403', async () => {
  await request(app).post('/api/conductor/servicio/finalizar').set(conToken(tokens.conductor));
  const antes = (await query("SELECT fecha_vencimiento_licencia FROM conductores WHERE rut = '12345678-5'")).rows[0];
  await query(`UPDATE conductores SET fecha_vencimiento_licencia = ${fecha(-1)} WHERE rut = '12345678-5'`);
  try {
    const r = await request(app).post('/api/conductor/servicio/iniciar').set(conToken(tokens.conductor));
    assert.equal(r.status, 403);
  } finally {
    await query('UPDATE conductores SET fecha_vencimiento_licencia = $1 WHERE rut = \'12345678-5\'', [antes.fecha_vencimiento_licencia]);
  }
});

test('PU-10 la base rechaza un turno que termina antes de empezar (los choques se prueban en AS-02 y AS-04)', async () => {
  const c = (await query("SELECT id FROM conductores WHERE rut = '12345678-5'")).rows[0].id;
  const a = (await query("SELECT id FROM automoviles WHERE patente = 'ABCD-12'")).rows[0].id;
  await assert.rejects(query(`INSERT INTO turnos (conductor_id, fecha, ruta_folio, automovil_id, hora_inicio, hora_fin)
                              VALUES ($1, DATE '2030-01-01', '235003', $2, '14:00', '06:00')`, [c, a]),
  (e) => e.code === '23514');
});

// ---------- Reportes PDF ----------

async function crearConductorConVencimiento(rut, nombre, dias) {
  await query(
    `INSERT INTO conductores (nombre_completo, rut, direccion, fono_celular, tipo_licencia, municipio_licencia,
                              fecha_obtencion_licencia, fecha_vencimiento_licencia, estado)
     VALUES ($1, $2, 'Calle 9', '+56900000000', 'A2', 'La Granja', ${fecha(-1000)}, ${fecha(dias)}, 'APROBADA')
     ON CONFLICT (rut) DO UPDATE SET fecha_vencimiento_licencia = EXCLUDED.fecha_vencimiento_licencia`,
    [nombre, rut],
  );
}

test('RP-01 reporte de licencias: incluye las que vencen en 29 y 30 días, no la de 31', async () => {
  await crearConductorConVencimiento('10000013-K', 'Vence en 29 días', 29);
  await crearConductorConVencimiento('7654321-6', 'Vence en 30 días', 30);
  await crearConductorConVencimiento('15234567-4', 'Vence en 31 días', 31);
  const r = await request(app).get('/api/admin/reportes/licencias?formato=json').set(conToken(tokens.admin));
  assert.equal(r.status, 200);
  const ruts = r.body.map((f) => f.rut);
  assert.ok(ruts.includes('10000013-K'));
  assert.ok(ruts.includes('7654321-6'));
  assert.ok(!ruts.includes('15234567-4'));
  assert.ok(!ruts.includes('9876543-3')); // ya vencida (2022): no es "por vencer"
});

test('RP-02 reporte de vehículos: aparece el que tiene un solo documento vencido, no el que está al día', async () => {
  const crear = async (patente) => (await request(app).post('/api/automoviles').set(conToken(tokens.admin)).send({
    patente, marca: 'Hyundai', modelo: 'Accent', ano: 2021, combustible: 'bencina',
    vencimiento_permiso: '31-03-2027', vencimiento_revision: '30-06-2027', vencimiento_seguro: '31-03-2027',
  })).body.id;
  const vencido = await crear('REPO-11');
  const alDia = await crear('REPO-22');
  await query(`UPDATE seguros_obligatorios SET fecha_vencimiento = ${fecha(-1)} WHERE automovil_id = $1`, [vencido]);
  const r = await request(app).get('/api/admin/reportes/vehiculos-vencidos?formato=json').set(conToken(tokens.admin));
  const fila = r.body.find((f) => f.patente === 'REPO-11');
  assert.ok(fila);
  assert.equal(fila.seguro_vencido, true);
  assert.equal(fila.permiso_vencido, false);
  assert.equal(fila.revision_vencida, false);
  assert.ok(!r.body.some((f) => f.patente === 'REPO-22'));
  for (const id of [vencido, alDia]) await request(app).delete(`/api/automoviles/${id}`).set(conToken(tokens.admin));
});

test('RP-03 solo el administrador genera reportes; RP-04 la respuesta es un PDF', async () => {
  assert.equal((await request(app).get('/api/admin/reportes/licencias').set(conToken(tokens.pasajero))).status, 403);
  assert.equal((await request(app).get('/api/admin/reportes/vehiculos-vencidos').set(conToken(tokens.conductor))).status, 403);
  assert.equal((await request(app).get('/api/admin/reportes/licencias')).status, 401);
  for (const ruta of ['/api/admin/reportes/licencias', '/api/admin/reportes/vehiculos-vencidos']) {
    const r = await request(app).get(ruta).set(conToken(tokens.admin)).buffer(true)
      .parse((res, cb) => { const d = []; res.on('data', (c) => d.push(c)); res.on('end', () => cb(null, Buffer.concat(d))); });
    assert.equal(r.status, 200);
    assert.match(r.headers['content-type'], /application\/pdf/);
    assert.equal(r.body.subarray(0, 4).toString(), '%PDF');
  }
});

// ---------- Datos propios, aprobación y tiempo real ----------

/** PU-08 cierra el servicio: estas pruebas lo vuelven a abrir con el auto vacío. */
async function servicioAbierto() {
  await request(app).post('/api/conductor/servicio/iniciar').set(conToken(tokens.conductor));
  await request(app).post('/api/conductor/ocupacion').set(conToken(tokens.conductor)).send({ a_bordo: 0 });
}

test('PI-03 / PSE-02 un pasajero solo ve sus propias reservas', async () => {
  await servicioAbierto();
  const a = await registrarPasajero('propio.a@correo.cl');
  const b = await registrarPasajero('propio.b@correo.cl');
  const r = await request(app).post('/api/reservas').set(conToken(a)).send({ patente: 'ABCD-12' });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const deA = await request(app).get('/api/reservas').set(conToken(a));
  const deB = await request(app).get('/api/reservas').set(conToken(b));
  assert.equal(deA.body.length, 1);
  assert.equal(deB.body.length, 0); // B no ve la reserva de A
  await request(app).delete('/api/reservas/activa').set(conToken(a));
});

test('PI-12 el administrador aprueba a un conductor pendiente', async () => {
  const id = (await query("SELECT id FROM conductores WHERE rut = '15234567-4'")).rows[0].id;
  const r = await request(app).post(`/api/conductores/${id}/estado`).set(conToken(tokens.admin)).send({ estado: 'APROBADA' });
  assert.equal(r.status, 200);
  assert.equal(r.body.estado, 'APROBADA');
  const t = await login('r.morales@correo.cl', CLAVE);
  const s = await request(app).post('/api/conductor/servicio/iniciar').set(conToken(t));
  assert.equal(s.status, 409); // ya no es 403 (pendiente): ahora falta que le asignen un vehículo
});

test('PI-09 la posición del conductor llega por WebSocket; sin token se rechaza la conexión', async () => {
  await servicioAbierto();
  const { io: cliente } = require('socket.io-client');
  const servidor = require('http').createServer(app);
  const { Server } = require('socket.io');
  const io = new Server(servidor);
  const jwtLib = require('jsonwebtoken');
  io.use((socket, next) => {
    try { socket.user = jwtLib.verify(socket.handshake.auth.token, process.env.JWT_SECRET); next(); } catch { next(new Error('Token WebSocket inválido')); }
  });
  io.on('connection', (socket) => socket.on('subscribe_route', (folio) => socket.join(`route_${folio}`)));
  app.locals.io = io;
  await new Promise((ok) => servidor.listen(0, ok));
  const url = `http://127.0.0.1:${servidor.address().port}`;
  try {
    const sinToken = cliente(url, { transports: ['websocket'], reconnection: false });
    const error = await new Promise((ok) => sinToken.on('connect_error', (e) => ok(e.message)));
    assert.match(error, /inválido/);
    sinToken.close();

    const pasajero = cliente(url, { transports: ['websocket'], auth: { token: tokens.pasajero }, reconnection: false });
    await new Promise((ok) => pasajero.on('connect', ok));
    pasajero.emit('subscribe_route', '235003');
    await new Promise((ok) => setTimeout(ok, 100));
    const llega = new Promise((ok) => pasajero.on('position_update', ok));
    const inicio = Date.now();
    const p = await request(app).post('/api/conductor/posicion').set(conToken(tokens.conductor)).send({ lat: -33.531, lng: -70.661, velocidad: 30 });
    assert.equal(p.status, 204);
    const evento = await llega;
    assert.equal(evento.patente, 'ABCD-12');
    assert.ok(Date.now() - inicio < 2000, 'la posición debe llegar en menos de 2 s');
    pasajero.close();
  } finally {
    app.locals.io = undefined;
    io.close();
  }
});

// ---------- Panel web: validación de conductores ----------

test('AD-01 la lista de pendientes trae la fecha de registro y los datos de la licencia', async () => {
  const nuevo = await request(app).post('/api/auth/registro-conductor').send({
    rut: '11.111.111-1', nombre: 'Felipe Contreras Lagos', direccion: 'Calle 2', comuna: 'La Pintana',
    celular: '+56922222222', telefonoEmergencia: '+56933333333', licencia: 'A3', comunaEmision: 'La Pintana',
    correo: 'f.contreras@correo.cl', password: CLAVE, emisionLicencia: '01-02-2024', vencimientoLicencia: '01-02-2030',
  });
  assert.equal(nuevo.status, 201);
  const r = await request(app).get('/api/conductores?estado=PENDIENTE').set(conToken(tokens.admin));
  assert.equal(r.status, 200);
  assert.ok(r.body.some((c) => c.rut === '11111111-1'));
  for (const c of r.body) {
    assert.equal(c.estado, 'PENDIENTE');
    assert.match(c.registrado, /^\d{4}-\d{2}-\d{2}$/);
    assert.ok(c.tipo_licencia && c.municipio_licencia && c.fecha_vencimiento_licencia);
  }
});
