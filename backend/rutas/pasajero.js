// Pasajero con cuenta: reservas. Y notificaciones para cualquier usuario con sesión.
const express = require('express');
const { query } = require('../db/postgres');
const mongo = require('../db/mongo');
const { ruta, ErrorHttp, autenticar, requiereRol, nombreCorto } = require('../util');
const { expirarVencidas } = require('../reservas');
const { colectivosDe } = require('./publico');

const router = express.Router();

const SQL_RESERVA = `
  SELECT re.id, re.estado, re.asientos, re.creada_en, a.patente, r.folio AS ruta_folio, r.linea
  FROM reservas re JOIN automoviles a ON a.id = re.automovil_id JOIN rutas r ON r.folio = re.ruta_folio`;

/** Usuario del conductor que maneja el vehículo (el que tiene el servicio abierto o, si no, el asignado). */
async function usuarioConductorDe(automovilId) {
  const r = await query(
    `SELECT c.usuario_id FROM servicios sv JOIN conductores c ON c.id = sv.conductor_id
     WHERE sv.automovil_id = $1 AND sv.fin IS NULL
     UNION ALL
     SELECT c.usuario_id FROM asignaciones s JOIN conductores c ON c.id = s.conductor_id
     WHERE s.automovil_id = $1 AND s.activo
     LIMIT 1`,
    [automovilId],
  );
  return r.rows[0]?.usuario_id ?? null;
}

/** Ejecuta las tareas de un mismo vehículo una tras otra (una sola instancia del servidor). */
const filas = new Map();
function enFila(clave, tarea) {
  const previa = filas.get(clave) || Promise.resolve();
  const actual = previa.catch(() => {}).then(tarea);
  const marca = actual.catch(() => {});
  filas.set(clave, marca);
  marca.then(() => { if (filas.get(clave) === marca) filas.delete(clave); });
  return actual;
}

router.get('/api/reservas', autenticar, requiereRol('PASAJERO'), ruta(async (req, res) => {
  await expirarVencidas();
  const r = await query(
    `${SQL_RESERVA} WHERE re.pasajero_id = $1
     ORDER BY (re.estado = 'ACTIVA') DESC, re.creada_en DESC LIMIT 50`,
    [req.usuario.id],
  );
  res.json(r.rows);
}));

router.post('/api/reservas', autenticar, requiereRol('PASAJERO'), ruta(async (req, res) => {
  const patente = String(req.body.patente || '').trim().toUpperCase();
  const vehiculo = await query(
    `SELECT a.id, s.ruta_folio FROM automoviles a JOIN asignaciones s ON s.automovil_id = a.id AND s.activo
     WHERE a.patente = $1 AND a.activo`,
    [patente],
  );
  if (vehiculo.rowCount === 0) throw new ErrorHttp(404, 'Ese colectivo no está en servicio');
  const { id: automovilId, ruta_folio: folio } = vehiculo.rows[0];

  // Las reservas de un mismo vehículo se procesan de a una: así dos pasajeros no toman el mismo último asiento.
  const id = await enFila(automovilId, async () => {
    // colectivosDe también expira las reservas vencidas.
    const disponible = (await colectivosDe(folio)).find((c) => c.patente === patente);
    if (!disponible || !disponible.en_servicio) throw new ErrorHttp(409, 'Ese colectivo no está en servicio ahora');
    if (disponible.asientos_disponibles === 0) throw new ErrorHttp(409, 'El colectivo ya no tiene asientos');
    try {
      const r = await query(
        'INSERT INTO reservas (pasajero_id, automovil_id, ruta_folio) VALUES ($1, $2, $3) RETURNING id',
        [req.usuario.id, automovilId, folio],
      );
      return r.rows[0].id;
    } catch (err) {
      // Índice único: solo una reserva activa por pasajero.
      if (err.code === '23505') throw new ErrorHttp(409, 'Ya tienes una reserva activa');
      throw err;
    }
  });
  const reserva = (await query(`${SQL_RESERVA} WHERE re.id = $1`, [id])).rows[0];
  await mongo.auditar(req.usuario, 'RESERVA_CREADA', { patente, folio });
  await mongo.notificar([req.usuario.id], 'RESERVA_CONFIRMADA', 'Reserva confirmada',
    `Tienes 1 asiento reservado en ${patente} · Línea ${reserva.linea}.`);
  await mongo.notificar([await usuarioConductorDe(automovilId)], 'NUEVA_RESERVA', 'Nueva reserva',
    `${nombreCorto(req.usuario.nombre)} reservó 1 asiento en ${patente}.`);
  res.status(201).json(reserva);
}));

router.delete('/api/reservas/activa', autenticar, requiereRol('PASAJERO'), ruta(async (req, res) => {
  await expirarVencidas();
  const r = await query(
    `UPDATE reservas SET estado = 'CANCELADA', actualizada_en = NOW()
     WHERE pasajero_id = $1 AND estado = 'ACTIVA' RETURNING id, automovil_id`,
    [req.usuario.id],
  );
  if (r.rowCount === 0) throw new ErrorHttp(404, 'No tienes una reserva activa');
  const { id, automovil_id: automovilId } = r.rows[0];
  await mongo.auditar(req.usuario, 'RESERVA_CANCELADA', { reserva: id });
  await mongo.notificar([await usuarioConductorDe(automovilId)], 'RESERVA_CANCELADA', 'Reserva cancelada',
    `${nombreCorto(req.usuario.nombre)} canceló su reserva.`);
  res.json((await query(`${SQL_RESERVA} WHERE re.id = $1`, [id])).rows[0]);
}));

/** Notificaciones de quien inició sesión (pasajero, conductor o administración). */
router.get('/api/notificaciones', autenticar, ruta(async (req, res) => {
  const lista = await mongo.notificacionesDe(req.usuario.id).catch(() => []);
  res.json(lista.map((n) => ({ tipo: n.tipo, titulo: n.titulo, detalle: n.detalle, fecha: n.timestamp })));
}));

module.exports = { router };
