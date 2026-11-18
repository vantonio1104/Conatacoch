// Turnos de los conductores. Un turno usa el vehículo y la línea que el conductor tiene asignados.
// Tipos: MANANA (06:00–14:00), TARDE (14:00–22:00) o LIBRE.
const { query } = require('./db/postgres');

const HORARIOS = { MANANA: ['06:00', '14:00'], TARDE: ['14:00', '22:00'] };

/**
 * Completa los próximos 14 días que aún no tienen turno con un ciclo de 6 días
 * (2 de mañana, 2 libres, 2 de tarde). Lo que ya definió la administración no se toca.
 * [db] puede ser el pool o un cliente dentro de una transacción.
 */
async function generarTurnos(conductorId, db = { query }) {
  await db.query(
    `INSERT INTO turnos (conductor_id, fecha, ruta_folio, automovil_id, hora_inicio, hora_fin)
     SELECT $1, d::date, s.ruta_folio, s.automovil_id,
            CASE WHEN (d::date - DATE '2026-01-01') % 6 < 2 THEN TIME '06:00' ELSE TIME '14:00' END,
            CASE WHEN (d::date - DATE '2026-01-01') % 6 < 2 THEN TIME '14:00' ELSE TIME '22:00' END
     FROM generate_series((NOW() AT TIME ZONE 'America/Santiago')::date, (NOW() AT TIME ZONE 'America/Santiago')::date + 13, INTERVAL '1 day') d
     JOIN asignaciones s ON s.conductor_id = $1 AND s.activo
     JOIN automoviles a ON a.id = s.automovil_id AND a.activo
     WHERE (d::date - DATE '2026-01-01') % 6 NOT IN (2, 3)
     ON CONFLICT (conductor_id, fecha) DO NOTHING`,
    [conductorId],
  );
}

/**
 * Después de cambiar la asignación de un conductor: sus turnos futuros pasan a usar el vehículo nuevo;
 * si se quedó sin vehículo, se borran (los días libres se conservan).
 */
async function reasignarTurnos(conductorId, db = { query }) {
  const asig = (await db.query(
    `SELECT s.automovil_id, s.ruta_folio FROM asignaciones s JOIN automoviles a ON a.id = s.automovil_id AND a.activo
     WHERE s.conductor_id = $1 AND s.activo LIMIT 1`,
    [conductorId],
  )).rows[0];
  if (asig) {
    await db.query(
      `UPDATE turnos SET automovil_id = $2, ruta_folio = $3
       WHERE conductor_id = $1 AND fecha >= (NOW() AT TIME ZONE 'America/Santiago')::date AND NOT libre`,
      [conductorId, asig.automovil_id, asig.ruta_folio],
    );
  } else {
    await db.query(`DELETE FROM turnos WHERE conductor_id = $1 AND fecha >= (NOW() AT TIME ZONE 'America/Santiago')::date AND NOT libre`, [conductorId]);
  }
}

/** Define el turno de un día (lo usa la administración). Devuelve false si el conductor no tiene vehículo. */
async function definirTurno(conductorId, fecha, tipo) {
  if (tipo === 'LIBRE') {
    await query(
      `INSERT INTO turnos (conductor_id, fecha, libre) VALUES ($1, $2, TRUE)
       ON CONFLICT (conductor_id, fecha) DO UPDATE
       SET libre = TRUE, ruta_folio = NULL, automovil_id = NULL, hora_inicio = NULL, hora_fin = NULL`,
      [conductorId, fecha],
    );
    return true;
  }
  const [inicio, fin] = HORARIOS[tipo];
  const r = await query(
    `INSERT INTO turnos (conductor_id, fecha, ruta_folio, automovil_id, hora_inicio, hora_fin, libre)
     SELECT $1, $2, s.ruta_folio, s.automovil_id, $3, $4, FALSE
     FROM asignaciones s JOIN automoviles a ON a.id = s.automovil_id AND a.activo
     WHERE s.conductor_id = $1 AND s.activo
     LIMIT 1
     ON CONFLICT (conductor_id, fecha) DO UPDATE
     SET libre = FALSE, ruta_folio = EXCLUDED.ruta_folio, automovil_id = EXCLUDED.automovil_id,
         hora_inicio = EXCLUDED.hora_inicio, hora_fin = EXCLUDED.hora_fin`,
    [conductorId, fecha, inicio, fin],
  );
  return r.rowCount > 0;
}

module.exports = { HORARIOS, generarTurnos, reasignarTurnos, definirTurno };
