// Conexión a MongoDB (Atlas en la nube). Requerimiento 4b: base de datos no relacional.
//
// Colecciones:
//  - posiciones_vehiculos: GPS que envían los conductores en servicio (se borran solas a las 24 h).
//  - auditoria:            registro de acciones (registros, inicios de sesión, reservas).
//  - notificaciones:       avisos para pasajeros y conductores (nueva reserva, retraso, desvío...).
//
// MongoDB es opcional para que el servidor siga funcionando si Atlas no responde:
// en ese caso no hay posiciones GPS, notificaciones ni auditoría.
const { MongoClient } = require('mongodb');

let db = null;

async function conectarMongo() {
  const uri = process.env.MONGODB_URI;
  if (!uri) {
    console.warn('[mongo] MONGODB_URI no está configurado: se omiten GPS, ocupación y auditoría.');
    return null;
  }
  const cliente = new MongoClient(uri, { serverSelectionTimeoutMS: 10_000 });
  await cliente.connect();
  db = cliente.db(process.env.MONGODB_DB || 'conatacoch');

  await db.collection('posiciones_vehiculos').createIndex({ timestamp: 1 }, { expireAfterSeconds: 86_400 });
  await db.collection('posiciones_vehiculos').createIndex({ patente: 1, timestamp: -1 });
  await db.collection('posiciones_vehiculos').createIndex({ ubicacion: '2dsphere' });
  await db.collection('auditoria').createIndex({ timestamp: -1 });
  await db.collection('notificaciones').createIndex({ usuario_id: 1, timestamp: -1 });
  // Las notificaciones se borran solas a los 30 días.
  await db.collection('notificaciones').createIndex({ timestamp: 1 }, { expireAfterSeconds: 30 * 86_400 });
  console.log('[mongo] conectado');
  return db;
}

const mongoDisponible = () => db !== null;

/** Registra una acción. Nunca hace fallar la petición si Mongo no responde. */
async function auditar(usuario, accion, detalles = {}) {
  if (!db) return;
  try {
    await db.collection('auditoria').insertOne({
      usuario_id: usuario?.id ?? null,
      email: usuario?.email ?? null,
      rol: usuario?.rol ?? null,
      accion,
      detalles,
      timestamp: new Date(),
    });
  } catch (err) {
    console.error('[mongo] no se pudo auditar:', err.message);
  }
}

/** Última posición GPS (de los últimos 2 minutos) por patente: { patente: {lat, lng} }. */
async function posicionesRecientes(patentes) {
  if (!db || patentes.length === 0) return {};
  const desde = new Date(Date.now() - 2 * 60_000);
  const filas = await db.collection('posiciones_vehiculos').aggregate([
    { $match: { patente: { $in: patentes }, timestamp: { $gte: desde } } },
    { $sort: { timestamp: -1 } },
    { $group: { _id: '$patente', ubicacion: { $first: '$ubicacion' }, sentido: { $first: '$sentido' } } },
  ]).toArray();
  return Object.fromEntries(filas.map((f) => [f._id, {
    lat: f.ubicacion.coordinates[1], lng: f.ubicacion.coordinates[0], sentido: f.sentido,
  }]));
}

async function guardarPosicion({ conductorId, patente, rutaFolio, sentido, lat, lng, rumbo, velocidad }) {
  if (!db) return;
  await db.collection('posiciones_vehiculos').insertOne({
    conductor_id: conductorId,
    patente,
    ruta_folio: rutaFolio,
    sentido,
    ubicacion: { type: 'Point', coordinates: [lng, lat] },
    rumbo,
    velocidad,
    timestamp: new Date(),
  });
}

/** Última posición GPS (sin límite de tiempo) por patente, con su hora: { patente: {lat, lng, velocidad, timestamp} }. */
async function ultimasPosiciones(patentes) {
  if (!db || patentes.length === 0) return {};
  const filas = await db.collection('posiciones_vehiculos').aggregate([
    { $match: { patente: { $in: patentes } } },
    { $sort: { timestamp: -1 } },
    { $group: { _id: '$patente', ubicacion: { $first: '$ubicacion' }, sentido: { $first: '$sentido' },
      velocidad: { $first: '$velocidad' }, timestamp: { $first: '$timestamp' } } },
  ]).toArray();
  return Object.fromEntries(filas.map((f) => [f._id, {
    lat: f.ubicacion.coordinates[1], lng: f.ubicacion.coordinates[0], sentido: f.sentido,
    velocidad: f.velocidad ?? null, timestamp: f.timestamp,
  }]));
}

/** Envía una notificación a uno o varios usuarios. Nunca hace fallar la petición. */
async function notificar(usuarioIds, tipo, titulo, detalle) {
  const ids = [...new Set(usuarioIds.filter((id) => id != null))];
  if (!db || ids.length === 0) return;
  try {
    const ahora = new Date();
    await db.collection('notificaciones').insertMany(
      ids.map((usuarioId) => ({ usuario_id: usuarioId, tipo, titulo, detalle, leida: false, timestamp: ahora })),
    );
  } catch (err) {
    console.error('[mongo] no se pudo notificar:', err.message);
  }
}

async function notificacionesDe(usuarioId) {
  if (!db) return [];
  return db.collection('notificaciones').find({ usuario_id: usuarioId }).sort({ timestamp: -1 }).limit(50).toArray();
}

module.exports = {
  conectarMongo, mongoDisponible, auditar, posicionesRecientes, ultimasPosiciones, guardarPosicion,
  notificar, notificacionesDe,
};
