// Conductor aprobado: perfil, turnos, servicio, panel de asientos (ocupados y reservas), avisos y GPS.
// Los vehículos no tienen cámaras: el conductor marca en su panel los asientos ocupados.
const express = require('express');
const { query, transaccion } = require('../db/postgres');
const mongo = require('../db/mongo');
const { ruta, ErrorHttp, autenticar, requiereRol, nombreCorto, MINUTOS_VIGENCIA_RESERVA } = require('../util');
const { expirarVencidas } = require('../reservas');
const { turnoVigente, semanaDelConductor } = require('../turnos');

const router = express.Router();

const VELOCIDAD_MAXIMA_KMH = 150;

const AVISOS = {
  'AC-01': 'Voy con retraso',
  'AC-02': 'Desvío',
  'AC-03': 'Desperfecto',
  'AC-05': 'QR dañado',
  'AC-06': 'Emergencia',
};

/** Carga el conductor de la sesión; solo los aprobados pueden operar. */
const conductorAprobado = [autenticar, requiereRol('CONDUCTOR'), ruta(async (req, res, next) => {
  const r = await query('SELECT id, nombre_completo, rut, estado FROM conductores WHERE usuario_id = $1', [req.usuario.id]);
  if (r.rowCount === 0) throw new ErrorHttp(404, 'No encontramos tus datos de conductor');
  if (r.rows[0].estado !== 'APROBADA') throw new ErrorHttp(403, 'Tu cuenta está pendiente de validación');
  req.conductor = r.rows[0];
  next();
})];

/** Vehículo y recorrido asignados al conductor (o null). */
async function asignacionDe(conductorId) {
  const r = await query(
    `SELECT a.id AS automovil_id, a.patente, a.capacidad, r.folio, r.linea
     FROM asignaciones s JOIN automoviles a ON a.id = s.automovil_id AND a.activo
     JOIN rutas r ON r.folio = s.ruta_folio
     WHERE s.conductor_id = $1 AND s.activo LIMIT 1`,
    [conductorId],
  );
  return r.rows[0] ?? null;
}

async function servicioAbierto(conductorId) {
  const r = await query(
    `SELECT sv.id, sv.automovil_id, sv.ruta_folio AS folio, sv.inicio, sv.ocupados, a.patente, a.capacidad, r.linea
     FROM servicios sv JOIN automoviles a ON a.id = sv.automovil_id JOIN rutas r ON r.folio = sv.ruta_folio
     WHERE sv.conductor_id = $1 AND sv.fin IS NULL`,
    [conductorId],
  );
  return r.rows[0] ?? null;
}

/** Lo que muestra el panel de servicio: asientos ocupados y reservas activas (vencen a los 3 minutos). */
async function estadoServicio(conductorId) {
  await expirarVencidas();
  const sv = await servicioAbierto(conductorId);
  if (!sv) return { en_servicio: false };
  const espera = await query(
    `SELECT re.id AS reserva_id, re.asientos, re.creada_en, u.nombre
     FROM reservas re JOIN usuarios u ON u.id = re.pasajero_id
     WHERE re.automovil_id = $1 AND re.estado = 'ACTIVA'
     ORDER BY re.creada_en`,
    [sv.automovil_id],
  );
  return {
    en_servicio: true,
    servicio_id: sv.id,
    inicio: sv.inicio,
    patente: sv.patente,
    folio: sv.folio,
    linea: sv.linea,
    capacidad: sv.capacidad,
    a_bordo: sv.ocupados,
    reservados: espera.rows.reduce((t, e) => t + e.asientos, 0),
    minutos_vigencia: MINUTOS_VIGENCIA_RESERVA,
    en_espera: espera.rows.map((e) => ({
      reserva_id: e.reserva_id, nombre: nombreCorto(e.nombre), asientos: e.asientos, creada_en: e.creada_en,
    })),
  };
}

router.get('/api/conductor/perfil', conductorAprobado, ruta(async (req, res) => {
  res.json({ nombre: req.conductor.nombre_completo, rut: req.conductor.rut, asignacion: await asignacionDe(req.conductor.id) });
}));

/** Turnos de los próximos 7 días (los define la administración; un día sin turno es libre). */
router.get('/api/conductor/turnos', conductorAprobado, ruta(async (req, res) => {
  res.json(await semanaDelConductor(req.conductor.id));
}));

router.get('/api/conductor/servicio', conductorAprobado, ruta(async (req, res) => {
  res.json(await estadoServicio(req.conductor.id));
}));

router.post('/api/conductor/servicio/iniciar', conductorAprobado, ruta(async (req, res) => {
  if (!(await servicioAbierto(req.conductor.id))) {
    // Solo dentro de un turno asignado por la administración (o hasta 15 minutos antes), con el vehículo de ese turno.
    const asig = await turnoVigente(req.conductor.id);
    if (!asig) throw new ErrorHttp(409, 'No tienes un turno asignado para esta hora. Revisa tus turnos o consulta a la administración.');
    // Solo con licencia vigente (Ley 18.290: licencia profesional A2/A3 al día).
    const lic = await query(
      `SELECT fecha_vencimiento_licencia < (NOW() AT TIME ZONE 'America/Santiago')::date AS vencida
       FROM conductores WHERE id = $1`, [req.conductor.id],
    );
    if (lic.rows[0]?.vencida) throw new ErrorHttp(403, 'Tu licencia de conducir está vencida. Actualízala con la administración.');
    try {
      await query('INSERT INTO servicios (conductor_id, automovil_id, ruta_folio) VALUES ($1, $2, $3)',
        [req.conductor.id, asig.automovil_id, asig.folio]);
    } catch (err) {
      if (err.code === '23505') throw new ErrorHttp(409, `El vehículo ${asig.patente} ya está en servicio con otro conductor`);
      throw err;
    }
    await mongo.auditar(req.usuario, 'INICIAR_SERVICIO', { patente: asig.patente });
  }
  res.json(await estadoServicio(req.conductor.id));
}));

router.post('/api/conductor/servicio/finalizar', conductorAprobado, ruta(async (req, res) => {
  const sv = await servicioAbierto(req.conductor.id);
  if (sv) {
    const canceladas = await transaccion(async (c) => {
      await c.query('UPDATE servicios SET fin = NOW() WHERE id = $1', [sv.id]);
      // Las reservas pendientes se cancelan porque el colectivo sale de servicio.
      const r = await c.query(
        `UPDATE reservas SET estado = 'CANCELADA', actualizada_en = NOW()
         WHERE automovil_id = $1 AND estado = 'ACTIVA' RETURNING pasajero_id`,
        [sv.automovil_id],
      );
      return r.rows.map((x) => x.pasajero_id);
    });
    await mongo.notificar(canceladas, 'RESERVA_CANCELADA', 'Reserva cancelada',
      `El colectivo ${sv.patente} salió de servicio. Puedes reservar en otro colectivo.`);
    await mongo.auditar(req.usuario, 'FINALIZAR_SERVICIO', { patente: sv.patente });
  }
  res.json({ en_servicio: false });
}));

/** El pasajero con reserva subió: la reserva pasa a COMPLETADA y su asiento queda ocupado. */
router.post('/api/conductor/abordaje', conductorAprobado, ruta(async (req, res) => {
  await expirarVencidas();
  const sv = await servicioAbierto(req.conductor.id);
  if (!sv) throw new ErrorHttp(409, 'Primero inicia tu servicio');
  await transaccion(async (c) => {
    const r = await c.query(
      `UPDATE reservas SET estado = 'COMPLETADA', actualizada_en = NOW()
       WHERE id = $1 AND automovil_id = $2 AND estado = 'ACTIVA' RETURNING asientos`,
      [req.body.reserva_id, sv.automovil_id],
    );
    if (r.rowCount === 0) throw new ErrorHttp(404, 'Esa reserva ya no está activa (pudo expirar)');
    await c.query('UPDATE servicios SET ocupados = LEAST($2, ocupados + $3) WHERE id = $1',
      [sv.id, sv.capacidad, r.rows[0].asientos]);
  });
  await mongo.auditar(req.usuario, 'CONFIRMAR_ABORDAJE', { reserva: Number(req.body.reserva_id) });
  res.json(await estadoServicio(req.conductor.id));
}));

/** El conductor elimina una reserva de su vehículo; se avisa al pasajero. */
router.post('/api/conductor/reservas/:id/cancelar', conductorAprobado, ruta(async (req, res) => {
  const sv = await servicioAbierto(req.conductor.id);
  if (!sv) throw new ErrorHttp(409, 'Primero inicia tu servicio');
  const r = await query(
    `UPDATE reservas SET estado = 'CANCELADA', actualizada_en = NOW()
     WHERE id = $1 AND automovil_id = $2 AND estado = 'ACTIVA' RETURNING pasajero_id`,
    [req.params.id, sv.automovil_id],
  );
  if (r.rowCount === 0) throw new ErrorHttp(404, 'Esa reserva ya no está activa');
  await mongo.notificar([r.rows[0].pasajero_id], 'RESERVA_CANCELADA', 'Reserva cancelada',
    `El conductor de ${sv.patente} canceló tu reserva. Puedes reservar en otro colectivo.`);
  await mongo.auditar(req.usuario, 'CONDUCTOR_CANCELA_RESERVA', { reserva: Number(req.params.id) });
  res.json(await estadoServicio(req.conductor.id));
}));

/** Aviso de un toque. AC-01 y AC-02 llegan como notificación a los pasajeros con reserva. */
router.post('/api/conductor/avisos', conductorAprobado, ruta(async (req, res) => {
  const codigo = String(req.body.codigo || '');
  const titulo = AVISOS[codigo];
  if (!titulo) throw new ErrorHttp(400, 'Aviso desconocido');
  const sv = await servicioAbierto(req.conductor.id);
  const asig = sv ?? (await asignacionDe(req.conductor.id));
  await query('INSERT INTO avisos (conductor_id, automovil_id, ruta_folio, codigo, titulo) VALUES ($1, $2, $3, $4, $5)',
    [req.conductor.id, asig?.automovil_id ?? null, asig?.folio ?? null, codigo, titulo]);
  if (asig && (codigo === 'AC-01' || codigo === 'AC-02')) {
    const pasajeros = await query(
      "SELECT pasajero_id FROM reservas WHERE automovil_id = $1 AND estado = 'ACTIVA'", [asig.automovil_id],
    );
    const ids = pasajeros.rows.map((p) => p.pasajero_id);
    if (codigo === 'AC-01') {
      await mongo.notificar(ids, 'RETRASO', 'Tu colectivo va con retraso', `El conductor de ${asig.patente} informó un retraso.`);
    } else {
      await mongo.notificar(ids, 'DESVIO', 'Desvío en el recorrido', `El conductor de ${asig.patente} informó un desvío en la Línea ${asig.linea}.`);
    }
  }
  await mongo.auditar(req.usuario, 'AVISO', { codigo });
  res.status(201).json({ codigo, titulo });
}));

/** Asientos ocupados que marca el conductor en su panel (sube o baja un pasajero sin reserva). */
router.post('/api/conductor/ocupacion', conductorAprobado, ruta(async (req, res) => {
  await expirarVencidas();
  const sv = await servicioAbierto(req.conductor.id);
  if (!sv) throw new ErrorHttp(409, 'Primero inicia tu servicio');
  const ocupados = Number(req.body.a_bordo);
  const reservados = (await query(
    "SELECT COALESCE(SUM(asientos), 0)::int AS n FROM reservas WHERE automovil_id = $1 AND estado = 'ACTIVA'",
    [sv.automovil_id],
  )).rows[0].n;
  if (!Number.isInteger(ocupados) || ocupados < 0 || ocupados + reservados > sv.capacidad) {
    throw new ErrorHttp(400, reservados > 0
      ? `Con ${reservados} reservado(s), los ocupados pueden ser de 0 a ${sv.capacidad - reservados}`
      : `Los ocupados deben estar entre 0 y ${sv.capacidad}`);
  }
  await query('UPDATE servicios SET ocupados = $2 WHERE id = $1', [sv.id, ocupados]);
  res.json(await estadoServicio(req.conductor.id));
}));

/** GPS del teléfono del conductor mientras está en servicio. */
router.post('/api/conductor/posicion', conductorAprobado, ruta(async (req, res) => {
  const { lat, lng, velocidad, rumbo } = req.body;
  if (typeof lat !== 'number' || typeof lng !== 'number' || Math.abs(lat) > 90 || Math.abs(lng) > 180) {
    throw new ErrorHttp(400, 'Posición inválida');
  }
  // Plausibilidad: un colectivo no supera los 150 km/h; se descarta una posición con velocidad imposible.
  if (typeof velocidad === 'number' && (velocidad < 0 || velocidad > VELOCIDAD_MAXIMA_KMH)) {
    throw new ErrorHttp(422, 'Posición descartada: velocidad no plausible');
  }
  const sv = await servicioAbierto(req.conductor.id);
  if (!sv) throw new ErrorHttp(409, 'Primero inicia tu servicio');
  await mongo.guardarPosicion({
    conductorId: req.conductor.id, patente: sv.patente, rutaFolio: sv.folio, sentido: null, lat, lng,
    rumbo: typeof rumbo === 'number' ? rumbo : null, velocidad: typeof velocidad === 'number' ? velocidad : null,
  });
  req.app.locals.io?.to(`route_${sv.folio}`).emit('position_update', { patente: sv.patente, lat, lng, velocidad, rumbo, timestamp: Date.now() });
  res.status(204).end();
}));

module.exports = { router };
