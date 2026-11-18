// Reglas de reservas compartidas por las rutas del pasajero, del conductor y las públicas.
const { query } = require('./db/postgres');
const mongo = require('./db/mongo');
const { MINUTOS_VIGENCIA_RESERVA } = require('./util');

/**
 * Las reservas activas que nadie confirmó en 3 minutos pasan a EXPIRADA y se avisa al pasajero.
 * Se llama antes de leer asientos o reservas, así nunca se muestra una reserva vencida.
 */
async function expirarVencidas() {
  const r = await query(
    `UPDATE reservas re SET estado = 'EXPIRADA', actualizada_en = NOW()
     FROM automoviles a
     WHERE a.id = re.automovil_id AND re.estado = 'ACTIVA' AND re.creada_en < NOW() - make_interval(mins => $1)
     RETURNING re.pasajero_id, a.patente`,
    [MINUTOS_VIGENCIA_RESERVA],
  );
  for (const fila of r.rows) {
    await mongo.notificar([fila.pasajero_id], 'RESERVA_CANCELADA', 'Tu reserva expiró',
      `Pasaron ${MINUTOS_VIGENCIA_RESERVA} minutos sin confirmar tu abordaje en ${fila.patente}. Puedes reservar de nuevo.`);
  }
}

module.exports = { expirarVencidas };
