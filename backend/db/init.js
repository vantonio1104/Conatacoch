// Crea las tablas y carga los datos iniciales. Se puede ejecutar muchas veces:
// las tablas usan IF NOT EXISTS y los datos ON CONFLICT DO NOTHING, así que nunca borra nada.
//
// Lo ejecuta el servidor al arrancar. También a mano:  npm run db:init
require('dotenv').config();
const fs = require('fs');
const path = require('path');
const bcrypt = require('bcryptjs');
const { parse } = require('csv-parse/sync');
const { transaccion } = require('./postgres');

const CARPETA_DB = path.join(__dirname, '..', 'database');
const leerCsv = (archivo) => parse(fs.readFileSync(path.join(CARPETA_DB, 'seed', archivo), 'utf8'), {
  columns: true, skip_empty_lines: true, trim: true,
});

const PASSWORD_DEMO = 'colectivo-demo-2026';
/** Tarifas por línea (las demás usan la tarifa base). */
const TARIFAS = { 5011: 900 };

/** Vehículos de la demo; coinciden con los datos de ejemplo que usaba la app. */
const VEHICULOS_FIJOS = [
  { patente: 'ABCD-12', folio: '235003', marca: 'Nissan', modelo: 'Versa', ano: 2022, combustible: 'bencina', rut: '12345678-9' },
  { patente: 'GHJK-34', folio: '235003', marca: 'Hyundai', modelo: 'Accent', ano: 2021, combustible: 'bencina' },
  { patente: 'BCDF-21', folio: '235011', marca: 'Toyota', modelo: 'Corolla', ano: 2020, combustible: 'híbrido' },
  { patente: 'LMNP-56', folio: '235011', marca: 'Kia', modelo: 'Rio', ano: 2019, combustible: 'bencina' },
  { patente: 'WXYZ-34', folio: '235005', marca: 'Toyota', modelo: 'Yaris', ano: 2023, combustible: 'híbrido', rut: '9876543-K' },
];
const MODELOS = [['Nissan', 'Versa'], ['Toyota', 'Yaris'], ['Hyundai', 'Accent'], ['Kia', 'Rio'], ['Chevrolet', 'Sail']];

/** Dos vehículos por recorrido como mínimo (patentes inventadas con formato chileno). */
function vehiculosDeEjemplo(rutas) {
  const usadas = new Set(VEHICULOS_FIJOS.map((v) => v.patente));
  const letras = 'BCDFGHJKLPRSTVWXYZ';
  const lista = [...VEHICULOS_FIJOS];
  let n = 0;
  for (const r of rutas) {
    while (lista.filter((v) => v.folio === r.folio).length < 2) {
      const patente = letras[n % 18] + letras[(n * 7 + 3) % 18] + letras[(n * 5 + 1) % 18] + letras[(n * 3 + 2) % 18] + '-' + (10 + n * 3);
      n++;
      if (usadas.has(patente)) continue;
      usadas.add(patente);
      const [marca, modelo] = MODELOS[n % MODELOS.length];
      lista.push({ patente, folio: r.folio, marca, modelo, ano: 2018 + (n % 6), combustible: n % 3 === 0 ? 'híbrido' : 'bencina' });
    }
  }
  return lista;
}

async function inicializarBaseDeDatos() {
  const schema = fs.readFileSync(path.join(CARPETA_DB, 'schema.sql'), 'utf8');
  const rutas = leerCsv('rutas.csv');
  const calles = leerCsv('ruta_calles.csv');
  const conductores = leerCsv('conductores_template.csv');
  const vehiculosCsv = leerCsv('vehiculos_template.csv');
  const hash = await bcrypt.hash(PASSWORD_DEMO, 10);

  const total = await transaccion(async (c) => {
    await c.query(schema);

    for (const rol of ['ADMIN', 'CONDUCTOR', 'PASAJERO']) {
      await c.query('INSERT INTO roles (nombre) VALUES ($1) ON CONFLICT (nombre) DO NOTHING', [rol]);
    }
    const usuario = (email, nombre, rol) => c.query(
      `INSERT INTO usuarios (email, password_hash, nombre, rol_id, verificado)
       VALUES ($1, $2, $3, (SELECT id FROM roles WHERE nombre = $4), TRUE)
       ON CONFLICT (email) DO NOTHING`,
      [email, hash, nombre, rol],
    );
    await usuario('admin@conatacoch.cl', 'Administración CONATACOCH', 'ADMIN');
    await usuario('camila.reyes@correo.cl', 'Camila Reyes', 'PASAJERO');
    await usuario('p.soto@correo.cl', 'Pedro Soto Muñoz', 'CONDUCTOR');

    // Recorridos oficiales.
    for (const r of rutas) {
      await c.query('INSERT INTO operadores (nombre) VALUES ($1) ON CONFLICT (nombre) DO NOTHING', [r.operador]);
      await c.query(
        `INSERT INTO rutas (folio, linea, tipo_servicio, operador_id, flota_vigente, origen_comuna, origen_calle,
                            destino_comuna, destino_calle, comunas_que_atraviesa, tarifa)
         VALUES ($1, $2, $3, (SELECT id FROM operadores WHERE nombre = $4), $5, $6, $7, $8, $9, $10, $11)
         ON CONFLICT (folio) DO NOTHING`,
        [r.folio, r.linea, r.tipo_servicio, r.operador, Number(r.flota_vigente), r.origen_comuna, r.origen_calle,
          r.destino_comuna, r.destino_calle, r.comunas_que_atraviesa, TARIFAS[r.linea] ?? 800],
      );
    }
    for (const k of calles) {
      await c.query(
        `INSERT INTO ruta_calles (ruta_folio, sentido, orden, calle, comuna) VALUES ($1, $2, $3, $4, $5)
         ON CONFLICT (ruta_folio, sentido, orden) DO NOTHING`,
        [k.folio, k.sentido, Number(k.orden), k.calle, k.comuna],
      );
    }

    // Conductores de ejemplo (Pedro Soto tiene cuenta para iniciar sesión).
    for (const d of conductores) {
      const vence = new Date(d.fecha_obtencion_licencia);
      vence.setFullYear(vence.getFullYear() + Number(d.duracion_licencia_anos));
      await c.query(
        `INSERT INTO conductores (usuario_id, nombre_completo, rut, direccion, fono_celular, fono_domicilio,
                                  fecha_incorporacion, tipo_licencia, municipio_licencia, fecha_obtencion_licencia,
                                  duracion_licencia_anos, fecha_vencimiento_licencia, estado)
         VALUES ((SELECT id FROM usuarios WHERE email = $1), $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, 'APROBADA')
         ON CONFLICT (rut) DO NOTHING`,
        [d.rut === '12345678-9' ? 'p.soto@correo.cl' : null, d.nombre_completo, d.rut, d.direccion, d.fono_celular,
          d.fono_domicilio, d.fecha_incorporacion, d.tipo_licencia, d.municipio_licencia, d.fecha_obtencion_licencia,
          Number(d.duracion_licencia_anos), vence.toISOString().slice(0, 10)],
      );
    }

    // Vehículos y su asignación a un recorrido.
    const lista = vehiculosDeEjemplo(rutas);
    for (const v of lista) {
      await c.query(
        `INSERT INTO automoviles (patente, marca, modelo, ano, combustible) VALUES ($1, $2, $3, $4, $5)
         ON CONFLICT (patente) DO NOTHING`,
        [v.patente, v.marca, v.modelo, v.ano, v.combustible],
      );
      await c.query(
        `INSERT INTO asignaciones (conductor_id, automovil_id, ruta_folio)
         SELECT (SELECT id FROM conductores WHERE rut = $2), a.id, $3 FROM automoviles a WHERE a.patente = $1
         ON CONFLICT DO NOTHING`,
        [v.patente, v.rut ?? null, v.folio],
      );
    }
    // Documentos de los vehículos del CSV.
    for (const v of vehiculosCsv) {
      const id = (await c.query('SELECT id FROM automoviles WHERE patente = $1', [v.patente])).rows[0]?.id;
      if (!id) continue;
      const yaTiene = (await c.query('SELECT 1 FROM permisos_circulacion WHERE automovil_id = $1', [id])).rowCount > 0;
      if (yaTiene) continue;
      await c.query('INSERT INTO permisos_circulacion (automovil_id, comuna, fecha_otorgacion, fecha_vencimiento) VALUES ($1, $2, $3, $4)',
        [id, v.comuna_permiso, v.fecha_otorgacion_permiso, v.vencimiento_permiso]);
      await c.query('INSERT INTO revisiones_tecnicas (automovil_id, planta_revision, comuna, fecha_vencimiento) VALUES ($1, $2, $3, $4)',
        [id, v.planta_revision, v.comuna_revision, v.vencimiento_revision]);
      await c.query('INSERT INTO seguros_obligatorios (automovil_id, compania, prima, fecha_vencimiento) VALUES ($1, $2, $3, $4)',
        [id, v.compania_seguro, Number(v.prima_seguro), v.vencimiento_seguro]);
    }

    // Vehículos de ejemplo sin documentos: se les cargan vencimientos variados para que el semáforo
    // de la administración muestre los tres colores (vigente, por vencer y vencido).
    const sinDocs = (await c.query(
      `SELECT a.id FROM automoviles a WHERE a.activo AND NOT EXISTS
         (SELECT 1 FROM permisos_circulacion p WHERE p.automovil_id = a.id) ORDER BY a.id`,
    )).rows;
    for (const [i, { id }] of sinDocs.entries()) {
      const dias = i % 9 === 4 ? -10 : i % 7 === 3 ? 15 : 120 + (i * 37) % 300;
      const fecha = (extra) => `CURRENT_DATE + ${dias + extra}`;
      await c.query(`INSERT INTO permisos_circulacion (automovil_id, fecha_vencimiento) VALUES ($1, ${fecha(60)}) ON CONFLICT DO NOTHING`, [id]);
      await c.query(`INSERT INTO revisiones_tecnicas (automovil_id, fecha_vencimiento) VALUES ($1, ${fecha(0)}) ON CONFLICT DO NOTHING`, [id]);
      await c.query(`INSERT INTO seguros_obligatorios (automovil_id, fecha_vencimiento) VALUES ($1, ${fecha(90)}) ON CONFLICT DO NOTHING`, [id]);
    }

    return (await c.query('SELECT COUNT(*)::int AS n FROM automoviles')).rows[0].n;
  });

  console.log(`[postgres] base de datos lista: ${rutas.length} recorridos, ${total} vehículos`);
}

module.exports = { inicializarBaseDeDatos };

// npm run db:init
if (require.main === module) {
  const { conectarMongo } = require('./mongo');
  conectarMongo()
    .catch((err) => console.error('[mongo] no se pudo conectar:', err.message))
    .then(inicializarBaseDeDatos)
    .then(() => process.exit(0))
    .catch((err) => {
      console.error(err);
      process.exit(1);
    });
}
