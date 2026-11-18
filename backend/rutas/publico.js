// Datos públicos: los ve cualquiera, incluso sin cuenta (recorridos, colectivos, trazados, salud).
const express = require('express');
const { query } = require('../db/postgres');
const mongo = require('../db/mongo');
const { ruta, ErrorHttp } = require('../util');
const { expirarVencidas } = require('../reservas');

const router = express.Router();

// /api/salud NO consulta la base de datos a propósito: cron-job.org la llama cada 10 minutos para que
// Render no duerma el servidor, y así Neon sí puede suspenderse cuando nadie usa la app (ahorra su cuota gratis).
router.get('/', (req, res) => res.json({ servicio: 'CONATACOCH API', estado: 'ok' }));
router.get('/api/salud', (req, res) => res.json({ ok: true, hora: new Date() }));
router.get('/api/salud/db', ruta(async (req, res) => {
  let postgres = false;
  try {
    await query('SELECT 1');
    postgres = true;
  } catch (err) {
    console.error('[salud] postgres:', err.message);
  }
  res.status(postgres ? 200 : 503).json({ ok: postgres, postgres, mongo: mongo.mongoDisponible(), hora: new Date() });
}));

router.get('/api/rutas', ruta(async (req, res) => {
  const rutas = await query(
    `SELECT r.folio, r.linea, o.nombre AS operador, r.tipo_servicio, r.origen_comuna, r.origen_calle,
            r.destino_comuna, r.destino_calle, r.comunas_que_atraviesa, r.tarifa, r.flota_vigente,
            (SELECT COUNT(*) FROM asignaciones s JOIN automoviles a ON a.id = s.automovil_id AND a.activo
              WHERE s.ruta_folio = r.folio AND s.activo)::int AS vehiculos_asignados,
            (SELECT MAX(version) FROM ruta_trazados t WHERE t.ruta_folio = r.folio) AS version_trazado
     FROM rutas r LEFT JOIN operadores o ON o.id = r.operador_id ORDER BY r.id`,
  );
  const calles = await query('SELECT ruta_folio, sentido, calle, comuna FROM ruta_calles ORDER BY ruta_folio, orden');
  const porRuta = {};
  for (const k of calles.rows) {
    const destino = (porRuta[k.ruta_folio] ??= { ida: [], regreso: [] });
    (k.sentido === 'IDA' ? destino.ida : destino.regreso).push({ calle: k.calle, comuna: k.comuna });
  }
  res.json(rutas.rows.map((r) => ({ ...r, ...(porRuta[r.folio] ?? { ida: [], regreso: [] }) })));
}));

/** Trazado guardado por la administración (líneas sobre el mapa). 404 si aún no hay uno. */
router.get('/api/rutas/:folio/trazado', ruta(async (req, res) => {
  const r = await query(
    `SELECT DISTINCT ON (sentido) sentido, geometria, version, fecha_actualizacion
     FROM ruta_trazados WHERE ruta_folio = $1 AND estado = 'ACTIVO'
     ORDER BY sentido, version DESC`,
    [req.params.folio],
  );
  if (r.rowCount === 0) throw new ErrorHttp(404, 'Este recorrido aún no tiene trazado guardado');
  const sentido = (s) => r.rows.find((x) => x.sentido === s);
  res.json({
    version: Math.max(...r.rows.map((x) => x.version)),
    ida: sentido('IDA') ? JSON.parse(sentido('IDA').geometria) : [],
    regreso: sentido('REGRESO') ? JSON.parse(sentido('REGRESO').geometria) : [],
  });
}));

/**
 * Vehículos de un recorrido. Solo los que están en servicio tienen asientos para reservar:
 * libres = capacidad − ocupados (los marca el conductor) − reservas activas.
 */
async function colectivosDe(folio) {
  await expirarVencidas();
  const r = await query(
    `SELECT a.patente, a.capacidad, r.folio AS ruta_folio, r.linea, sv.ocupados,
            (sv.id IS NOT NULL) AS en_servicio,
            (SELECT COALESCE(SUM(re.asientos), 0) FROM reservas re
              WHERE re.automovil_id = a.id AND re.estado = 'ACTIVA')::int AS reservados
     FROM asignaciones s
     JOIN automoviles a ON a.id = s.automovil_id AND a.activo
     JOIN rutas r ON r.folio = s.ruta_folio
     LEFT JOIN servicios sv ON sv.automovil_id = a.id AND sv.fin IS NULL
     WHERE s.activo AND r.folio = $1
     ORDER BY a.id`,
    [folio],
  );
  const patentes = r.rows.filter((v) => v.en_servicio).map((v) => v.patente);
  const posiciones = await mongo.posicionesRecientes(patentes).catch(() => ({}));
  return r.rows.map((v) => ({
    patente: v.patente,
    ruta_folio: v.ruta_folio,
    linea: v.linea,
    capacidad: v.capacidad,
    en_servicio: v.en_servicio,
    // null = fuera de servicio (no se puede reservar).
    asientos_disponibles: v.en_servicio ? Math.max(0, v.capacidad - v.ocupados - v.reservados) : null,
    posicion: posiciones[v.patente] ?? null,
  }));
}

router.get('/api/rutas/:folio/colectivos', ruta(async (req, res) => {
  res.json(await colectivosDe(req.params.folio));
}));

module.exports = { router, colectivosDe };
