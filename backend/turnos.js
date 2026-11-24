// Turnos: cuándo trabaja cada conductor (fecha, horario, vehículo y recorrido). Los define la administración
// en el panel web (Asignar rutas y Salidas y turnos). Un día sin turno es un día libre.
const { query, transaccion } = require('./db/postgres');
const { ErrorHttp, fechaIso } = require('./util');

/** Fecha y hora de Santiago, sin zona: se compara con fecha + hora del turno. */
const AHORA = "(NOW() AT TIME ZONE 'America/Santiago')";
/** Horarios rápidos de Figma (Admin 4). */
const HORARIOS = { MANANA: ['06:00', '14:00'], TARDE: ['14:00', '22:00'] };
/** El conductor puede iniciar servicio hasta 15 minutos antes de su turno. */
const MINUTOS_ANTES = 15;

const SQL_TURNO = `
  SELECT t.id, t.fecha::text AS fecha, to_char(t.hora_inicio, 'HH24:MI') AS inicio, to_char(t.hora_fin, 'HH24:MI') AS fin,
         t.conductor_id, c.nombre_completo AS conductor, t.automovil_id, a.patente, t.ruta_folio, r.linea,
         CASE WHEN t.fecha > ${AHORA}::date THEN 'PROGRAMADA'
              WHEN t.fecha + t.hora_fin <= ${AHORA} THEN 'FINALIZADA' ELSE 'ACTIVA' END AS estado
  FROM turnos t JOIN conductores c ON c.id = t.conductor_id
  JOIN automoviles a ON a.id = t.automovil_id JOIN rutas r ON r.folio = t.ruta_folio`;

const fechaCl = (iso) => iso.split('-').reverse().join('-');

/** Turnos entre [desde] y [desde + dias - 1], opcionalmente de un recorrido. */
async function listar({ desde, dias, folio }) {
  const r = await query(
    `${SQL_TURNO} WHERE t.fecha BETWEEN $1::date AND $1::date + ($2::int - 1) AND ($3::text IS NULL OR t.ruta_folio = $3)
     ORDER BY t.fecha, t.hora_inicio, c.nombre_completo`,
    [desde, dias, folio || null],
  );
  return r.rows;
}

const HORA = /^([01]\d|2[0-3]):[0-5]\d$/;

/** Valida y guarda un turno. Rechaza choques de horario del conductor o del vehículo (409). */
async function crear(b) {
  const fecha = fechaIso(b.fecha);
  const inicio = String(b.hora_inicio || '');
  const fin = String(b.hora_fin || '');
  if (!fecha) throw new ErrorHttp(400, 'La fecha debe tener el formato dd-mm-aaaa');
  if (!HORA.test(inicio) || !HORA.test(fin)) throw new ErrorHttp(400, 'Las horas deben tener el formato hh:mm (00:00 a 23:59)');
  if (fin <= inicio) throw new ErrorHttp(400, 'La hora de término debe ser posterior a la de inicio');

  return transaccion(async (c) => {
    // Un turno a la vez: así dos administradores no pueden crear turnos que choquen al mismo tiempo.
    await c.query('LOCK TABLE turnos IN SHARE ROW EXCLUSIVE MODE');
    const hoy = (await c.query(`SELECT ${AHORA}::date::text AS hoy`)).rows[0].hoy;
    if (fecha < hoy) throw new ErrorHttp(400, 'No se pueden asignar turnos en fechas pasadas');
    const cond = (await c.query("SELECT id, nombre_completo, usuario_id, fecha_vencimiento_licencia::text AS vence FROM conductores WHERE id = $1 AND estado = 'APROBADA'",
      [b.conductor_id])).rows[0];
    if (!cond) throw new ErrorHttp(400, 'Elige un conductor aprobado');
    if (cond.vence < fecha) {
      throw new ErrorHttp(400, `La licencia de ${cond.nombre_completo} vence el ${fechaCl(cond.vence)}, antes de ese turno`);
    }
    const auto = (await c.query(
      `SELECT a.id, a.patente FROM automoviles a JOIN asignaciones s ON s.automovil_id = a.id AND s.activo
       WHERE a.id = $1 AND a.activo AND s.ruta_folio = $2`, [b.automovil_id, b.ruta_folio],
    )).rows[0];
    if (!auto) throw new ErrorHttp(400, 'Ese vehículo no hace ese recorrido (revísalo en Automóviles)');

    const choque = async (campo, id) => (await c.query(
      `${SQL_TURNO} WHERE t.${campo} = $1 AND t.fecha = $2 AND t.hora_inicio < $4::time AND t.hora_fin > $3::time
       ORDER BY t.hora_inicio LIMIT 1`, [id, fecha, inicio, fin],
    )).rows[0];
    const delConductor = await choque('conductor_id', cond.id);
    if (delConductor) {
      throw new ErrorHttp(409, `Choque de horario: ${cond.nombre_completo} ya tiene asignada la línea ${delConductor.linea} `
        + `el ${fechaCl(fecha)} de ${delConductor.inicio} a ${delConductor.fin}.`);
    }
    const delVehiculo = await choque('automovil_id', auto.id);
    if (delVehiculo) {
      throw new ErrorHttp(409, `Choque de horario: el vehículo ${auto.patente} ya está asignado a ${delVehiculo.conductor} `
        + `el ${fechaCl(fecha)} de ${delVehiculo.inicio} a ${delVehiculo.fin}.`);
    }
    const id = (await c.query(
      'INSERT INTO turnos (conductor_id, fecha, ruta_folio, automovil_id, hora_inicio, hora_fin) VALUES ($1, $2, $3, $4, $5, $6) RETURNING id',
      [cond.id, fecha, b.ruta_folio, auto.id, inicio, fin],
    )).rows[0].id;
    const turno = (await c.query(`${SQL_TURNO} WHERE t.id = $1`, [id])).rows[0];
    return { turno, usuarioConductor: cond.usuario_id };
  });
}

/** Borra un turno que aún no empieza. */
async function borrar(id) {
  const t = (await query(`SELECT t.fecha + t.hora_inicio > ${AHORA} AS futuro, t.conductor_id FROM turnos t WHERE t.id = $1`, [id])).rows[0];
  if (!t) throw new ErrorHttp(404, 'Turno no encontrado');
  if (!t.futuro) throw new ErrorHttp(400, 'No se puede borrar un turno que ya empezó o terminó');
  await query('DELETE FROM turnos WHERE id = $1', [id]);
  return t;
}

/** Turno del conductor que cubre este momento (o empieza en los próximos 15 minutos), con su vehículo. */
async function turnoVigente(conductorId) {
  const r = await query(
    `SELECT t.automovil_id, t.ruta_folio AS folio, a.patente FROM turnos t JOIN automoviles a ON a.id = t.automovil_id AND a.activo
     WHERE t.conductor_id = $1 AND t.fecha BETWEEN ${AHORA}::date - 1 AND ${AHORA}::date + 1
       AND t.fecha + t.hora_inicio - make_interval(mins => $2) <= ${AHORA} AND t.fecha + t.hora_fin > ${AHORA}
     ORDER BY t.fecha, t.hora_inicio LIMIT 1`,
    [conductorId, MINUTOS_ANTES],
  );
  return r.rows[0] ?? null;
}

/** Los próximos 7 días del conductor (para la app): día sin turno = libre (horario null). */
async function semanaDelConductor(conductorId) {
  const r = await query(
    `SELECT d::date::text AS fecha, x.linea, x.patente, x.horario
     FROM generate_series(${AHORA}::date, ${AHORA}::date + 6, INTERVAL '1 day') d
     LEFT JOIN LATERAL (
       SELECT (array_agg(r.linea ORDER BY t.hora_inicio))[1] AS linea, (array_agg(a.patente ORDER BY t.hora_inicio))[1] AS patente,
              string_agg(to_char(t.hora_inicio, 'HH24:MI') || '–' || to_char(t.hora_fin, 'HH24:MI'), ', ' ORDER BY t.hora_inicio) AS horario
       FROM turnos t JOIN rutas r ON r.folio = t.ruta_folio JOIN automoviles a ON a.id = t.automovil_id
       WHERE t.conductor_id = $1 AND t.fecha = d::date) x ON TRUE
     ORDER BY d`,
    [conductorId],
  );
  return r.rows;
}

module.exports = { HORARIOS, listar, crear, borrar, turnoVigente, semanaDelConductor, fechaCl };
