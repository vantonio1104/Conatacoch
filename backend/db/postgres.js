// Conexión a PostgreSQL (Neon en la nube, o uno local para desarrollo).
const { Pool } = require('pg');

if (!process.env.DATABASE_URL) {
  throw new Error('Falta DATABASE_URL (cadena de conexión de PostgreSQL). Ver INSTALACION.md');
}

const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  max: 5,
  // Neon suspende la base de datos cuando no se usa; la primera conexión puede tardar un poco.
  connectionTimeoutMillis: 15_000,
  idleTimeoutMillis: 30_000,
});

pool.on('error', (err) => console.error('[postgres] error en una conexión inactiva:', err.message));

/** Ejecuta varias consultas en una transacción. */
async function transaccion(fn) {
  const cliente = await pool.connect();
  try {
    await cliente.query('BEGIN');
    const resultado = await fn(cliente);
    await cliente.query('COMMIT');
    return resultado;
  } catch (err) {
    await cliente.query('ROLLBACK');
    throw err;
  } finally {
    cliente.release();
  }
}

module.exports = { pool, query: (texto, params) => pool.query(texto, params), transaccion };
