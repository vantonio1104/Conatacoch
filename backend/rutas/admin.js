// Administración: resumen y semáforo, conductores, automóviles con documentos y asignación,
// trazados de los recorridos, flota en vivo y avisos. Todo requiere iniciar sesión como ADMIN.
const crypto = require('crypto');
const express = require('express');
const bcrypt = require('bcryptjs');
const { query, transaccion } = require('../db/postgres');
const mongo = require('../db/mongo');
const {
  ruta, ErrorHttp, autenticar, requiereRol, texto, fechaOpcional, normalizarEmail, emailValido, passwordValida,
  MENSAJE_PASSWORD, rutNormalizado, patenteNormalizada,
} = require('../util');

const turnos = require('../turnos');

const router = express.Router();
const soloAdmin = [autenticar, requiereRol('ADMIN')];

/** Semáforo de vencimientos: VENCIDO, POR_VENCER (< 30 días), VIGENTE o SIN_DATOS. */
const semaforo = (columna) => `CASE WHEN ${columna} IS NULL THEN 'SIN_DATOS'
  WHEN ${columna} < (NOW() AT TIME ZONE 'America/Santiago')::date THEN 'VENCIDO'
  WHEN ${columna} < (NOW() AT TIME ZONE 'America/Santiago')::date + 30 THEN 'POR_VENCER' ELSE 'VIGENTE' END`;

// --- Resumen (dashboard) ---

router.get('/api/admin/resumen', soloAdmin, ruta(async (req, res) => {
  const c = (await query(
    `SELECT COUNT(*)::int AS total,
            COUNT(*) FILTER (WHERE estado = 'PENDIENTE')::int AS pendientes,
            COUNT(*) FILTER (WHERE ${semaforo('fecha_vencimiento_licencia')} = 'VIGENTE')::int AS vigentes,
            COUNT(*) FILTER (WHERE ${semaforo('fecha_vencimiento_licencia')} = 'POR_VENCER')::int AS por_vencer,
            COUNT(*) FILTER (WHERE ${semaforo('fecha_vencimiento_licencia')} = 'VENCIDO')::int AS vencidos
     FROM conductores`,
  )).rows[0];
  const v = (await query(
    `WITH docs AS (
       SELECT a.id,
              LEAST(p.fecha_vencimiento, rt.fecha_vencimiento, so.fecha_vencimiento) AS primero,
              (p.id IS NULL OR rt.id IS NULL OR so.id IS NULL) AS incompleto,
              EXISTS (SELECT 1 FROM asignaciones s WHERE s.automovil_id = a.id AND s.activo) AS asignado
       FROM automoviles a
       LEFT JOIN permisos_circulacion p ON p.automovil_id = a.id
       LEFT JOIN revisiones_tecnicas rt ON rt.automovil_id = a.id
       LEFT JOIN seguros_obligatorios so ON so.automovil_id = a.id
       WHERE a.activo)
     SELECT COUNT(*)::int AS total,
            COUNT(*) FILTER (WHERE asignado)::int AS asignados,
            COUNT(*) FILTER (WHERE NOT incompleto AND ${semaforo('primero')} = 'VIGENTE')::int AS al_dia,
            COUNT(*) FILTER (WHERE NOT incompleto AND ${semaforo('primero')} = 'POR_VENCER')::int AS por_vencer,
            COUNT(*) FILTER (WHERE NOT incompleto AND ${semaforo('primero')} = 'VENCIDO')::int AS vencidos,
            COUNT(*) FILTER (WHERE incompleto)::int AS sin_documentos
     FROM docs`,
  )).rows[0];
  const otros = (await query(
    `SELECT (SELECT COUNT(*) FROM rutas)::int AS rutas,
            (SELECT COUNT(*) FROM servicios WHERE fin IS NULL)::int AS en_servicio,
            (SELECT COUNT(*) FROM reservas WHERE estado = 'ACTIVA')::int AS reservas_activas,
            (SELECT COUNT(*) FROM usuarios u JOIN roles r ON r.id = u.rol_id WHERE r.nombre = 'PASAJERO')::int AS pasajeros`,
  )).rows[0];
  const avisos = (await query(
    `SELECT av.codigo, av.titulo, av.creado_en, a.patente, r.linea, c.nombre_completo AS conductor
     FROM avisos av LEFT JOIN automoviles a ON a.id = av.automovil_id
     LEFT JOIN rutas r ON r.folio = av.ruta_folio LEFT JOIN conductores c ON c.id = av.conductor_id
     ORDER BY av.creado_en DESC LIMIT 10`,
  )).rows;
  res.json({ conductores: c, vehiculos: v, ...otros, avisos_recientes: avisos });
}));

// --- Conductores ---

const SQL_CONDUCTOR = `
  SELECT c.id, c.nombre_completo, c.rut, c.direccion, c.comuna, c.fono_celular, c.fono_domicilio, c.fono_emergencia,
         c.tipo_licencia, c.municipio_licencia, c.fecha_obtencion_licencia::text, c.fecha_vencimiento_licencia::text,
         c.estado, u.email, ${semaforo('c.fecha_vencimiento_licencia')} AS semaforo,
         COALESCE(u.created_at::date, c.fecha_incorporacion)::text AS registrado,
         ARRAY(SELECT f.lado FROM licencia_fotos f WHERE f.conductor_id = c.id ORDER BY f.lado) AS fotos,
         (SELECT a.patente FROM asignaciones s JOIN automoviles a ON a.id = s.automovil_id AND a.activo
           WHERE s.conductor_id = c.id AND s.activo LIMIT 1) AS patente
  FROM conductores c LEFT JOIN usuarios u ON u.id = c.usuario_id`;

router.get('/api/conductores', soloAdmin, ruta(async (req, res) => {
  const filtro = req.query.estado ? 'WHERE c.estado = $1' : '';
  const r = await query(`${SQL_CONDUCTOR} ${filtro} ORDER BY (c.estado = 'PENDIENTE') DESC, c.nombre_completo`,
    req.query.estado ? [req.query.estado] : []);
  res.json(r.rows);
}));

/** Lee y valida los datos del formulario de conductor. */
function datosConductor(b) {
  const d = {
    nombre: texto(b.nombre_completo ?? b.nombre),
    rut: rutNormalizado(b.rut),
    direccion: texto(b.direccion),
    comuna: texto(b.comuna) || null,
    celular: texto(b.fono_celular ?? b.celular),
    fijo: texto(b.fono_domicilio) || null,
    emergencia: texto(b.fono_emergencia) || null,
    licencia: texto(b.tipo_licencia ?? b.licencia).toUpperCase(),
    municipio: texto(b.municipio_licencia),
    obtencion: fechaOpcional(b.fecha_obtencion_licencia, 'obtención de la licencia'),
    vencimiento: fechaOpcional(b.fecha_vencimiento_licencia, 'vencimiento de la licencia'),
  };
  if (!d.nombre) throw new ErrorHttp(400, 'Ingresa el nombre');
  if (!d.direccion) throw new ErrorHttp(400, 'Ingresa la dirección');
  if (!d.celular) throw new ErrorHttp(400, 'Ingresa el celular');
  if (!['A2', 'A3'].includes(d.licencia)) throw new ErrorHttp(400, 'La licencia debe ser A2 o A3');
  if (!d.municipio) throw new ErrorHttp(400, 'Ingresa la municipalidad que otorgó la licencia');
  if (!d.obtencion || !d.vencimiento) throw new ErrorHttp(400, 'Ingresa las fechas de obtención y vencimiento de la licencia');
  if (d.vencimiento <= d.obtencion) throw new ErrorHttp(400, 'El vencimiento debe ser posterior a la obtención');
  return d;
}

const errorUnico = (err, mensaje) => {
  if (err.code === '23505') throw new ErrorHttp(409, mensaje);
  throw err;
};

router.post('/api/conductores', soloAdmin, ruta(async (req, res) => {
  const d = datosConductor(req.body);
  const email = normalizarEmail(req.body.email);
  const { password } = req.body;
  if (email && !emailValido(email)) throw new ErrorHttp(400, 'Ingresa un correo válido');
  if (email && !passwordValida(password)) throw new ErrorHttp(400, MENSAJE_PASSWORD);
  const hash = email ? await bcrypt.hash(password, 10) : null;

  const id = await transaccion(async (c) => {
    let usuarioId = null;
    if (email) {
      const u = await c.query(
        `INSERT INTO usuarios (email, password_hash, nombre, rol_id, verificado)
         VALUES ($1, $2, $3, (SELECT id FROM roles WHERE nombre = 'CONDUCTOR'), TRUE) RETURNING id`,
        [email, hash, d.nombre],
      ).catch((err) => errorUnico(err, 'Ese correo ya tiene una cuenta'));
      usuarioId = u.rows[0].id;
    }
    // Creado por la administración: queda aprobado.
    const r = await c.query(
      `INSERT INTO conductores (usuario_id, nombre_completo, rut, direccion, comuna, fono_celular, fono_domicilio,
                                fono_emergencia, tipo_licencia, municipio_licencia, fecha_obtencion_licencia,
                                fecha_vencimiento_licencia, estado)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, 'APROBADA') RETURNING id`,
      [usuarioId, d.nombre, d.rut, d.direccion, d.comuna, d.celular, d.fijo, d.emergencia, d.licencia, d.municipio,
        d.obtencion, d.vencimiento],
    ).catch((err) => errorUnico(err, 'Ese RUT ya está registrado'));
    return r.rows[0].id;
  });
  await mongo.auditar(req.usuario, 'CREAR_CONDUCTOR', { conductor: id, rut: d.rut });
  res.status(201).json((await query(`${SQL_CONDUCTOR} WHERE c.id = $1`, [id])).rows[0]);
}));

router.put('/api/conductores/:id', soloAdmin, ruta(async (req, res) => {
  const d = datosConductor(req.body);
  const r = await query(
    `UPDATE conductores SET nombre_completo = $2, rut = $3, direccion = $4, comuna = $5, fono_celular = $6,
            fono_domicilio = $7, fono_emergencia = $8, tipo_licencia = $9, municipio_licencia = $10,
            fecha_obtencion_licencia = $11, fecha_vencimiento_licencia = $12
     WHERE id = $1 RETURNING id, usuario_id`,
    [req.params.id, d.nombre, d.rut, d.direccion, d.comuna, d.celular, d.fijo, d.emergencia, d.licencia, d.municipio,
      d.obtencion, d.vencimiento],
  ).catch((err) => errorUnico(err, 'Ese RUT ya está registrado'));
  if (r.rowCount === 0) throw new ErrorHttp(404, 'Conductor no encontrado');
  if (r.rows[0].usuario_id) await query('UPDATE usuarios SET nombre = $2 WHERE id = $1', [r.rows[0].usuario_id, d.nombre]);
  await mongo.auditar(req.usuario, 'MODIFICAR_CONDUCTOR', { conductor: Number(req.params.id) });
  res.json((await query(`${SQL_CONDUCTOR} WHERE c.id = $1`, [req.params.id])).rows[0]);
}));

router.delete('/api/conductores/:id', soloAdmin, ruta(async (req, res) => {
  await transaccion(async (c) => {
    const r = await c.query('DELETE FROM conductores WHERE id = $1 RETURNING usuario_id, rut', [req.params.id]);
    if (r.rowCount === 0) throw new ErrorHttp(404, 'Conductor no encontrado');
    // Su cuenta para iniciar sesión también se elimina.
    if (r.rows[0].usuario_id) await c.query('DELETE FROM usuarios WHERE id = $1', [r.rows[0].usuario_id]);
  });
  await mongo.auditar(req.usuario, 'ELIMINAR_CONDUCTOR', { conductor: Number(req.params.id) });
  res.status(204).end();
}));

/** Foto de la licencia (frente o reverso). Datos personales: no se guarda en cachés y queda en la auditoría. */
router.get('/api/conductores/:id/fotos/:lado', soloAdmin, ruta(async (req, res) => {
  const lado = String(req.params.lado).toUpperCase();
  if (!['FRENTE', 'REVERSO'].includes(lado)) throw new ErrorHttp(400, 'El lado debe ser frente o reverso');
  const f = (await query('SELECT tipo, datos FROM licencia_fotos WHERE conductor_id = $1 AND lado = $2',
    [req.params.id, lado])).rows[0];
  if (!f) throw new ErrorHttp(404, 'El conductor no tiene esa foto');
  await mongo.auditar(req.usuario, 'VER_FOTO_LICENCIA', { conductor: Number(req.params.id), lado });
  res.set({ 'Content-Type': f.tipo, 'Cache-Control': 'no-store' }).send(f.datos);
}));

/** La administración valida (o rechaza) a un conductor recién registrado. */
router.post('/api/conductores/:id/estado', soloAdmin, ruta(async (req, res) => {
  const { estado } = req.body;
  if (!['APROBADA', 'RECHAZADA', 'PENDIENTE'].includes(estado)) throw new ErrorHttp(400, 'Estado inválido');
  const r = await query('UPDATE conductores SET estado = $1 WHERE id = $2 RETURNING id', [estado, req.params.id]);
  if (r.rowCount === 0) throw new ErrorHttp(404, 'Conductor no encontrado');
  await mongo.auditar(req.usuario, 'CAMBIO_ESTADO_CONDUCTOR', { conductor: Number(req.params.id), estado });
  res.json((await query(`${SQL_CONDUCTOR} WHERE c.id = $1`, [req.params.id])).rows[0]);
}));

// --- Automóviles, documentos y asignación ---

const SQL_AUTO = `
  SELECT a.id, a.patente, a.marca, a.modelo, a.ano, a.combustible, a.capacidad,
         s.ruta_folio, r.linea, s.conductor_id, c.nombre_completo AS conductor, c.rut AS conductor_rut,
         p.fecha_vencimiento::text AS vencimiento_permiso, rt.fecha_vencimiento::text AS vencimiento_revision,
         so.fecha_vencimiento::text AS vencimiento_seguro,
         CASE WHEN p.id IS NULL OR rt.id IS NULL OR so.id IS NULL THEN 'SIN_DATOS'
              ELSE ${semaforo('LEAST(p.fecha_vencimiento, rt.fecha_vencimiento, so.fecha_vencimiento)')} END AS semaforo,
         EXISTS (SELECT 1 FROM servicios sv WHERE sv.automovil_id = a.id AND sv.fin IS NULL) AS en_servicio
  FROM automoviles a
  LEFT JOIN asignaciones s ON s.automovil_id = a.id AND s.activo
  LEFT JOIN rutas r ON r.folio = s.ruta_folio
  LEFT JOIN conductores c ON c.id = s.conductor_id
  LEFT JOIN permisos_circulacion p ON p.automovil_id = a.id
  LEFT JOIN revisiones_tecnicas rt ON rt.automovil_id = a.id
  LEFT JOIN seguros_obligatorios so ON so.automovil_id = a.id`;

router.get('/api/automoviles', soloAdmin, ruta(async (req, res) => {
  res.json((await query(`${SQL_AUTO} WHERE a.activo ORDER BY r.linea NULLS LAST, a.patente`)).rows);
}));

function datosAuto(b) {
  const d = {
    patente: patenteNormalizada(b.patente),
    marca: texto(b.marca),
    modelo: texto(b.modelo),
    ano: Number(b.ano),
    combustible: texto(b.combustible || 'bencina').toLowerCase(),
    capacidad: Number(b.capacidad || 4),
    folio: texto(b.ruta_folio) || null,
    rutConductor: texto(b.conductor_rut) ? rutNormalizado(b.conductor_rut) : null,
    permiso: fechaOpcional(b.vencimiento_permiso, 'vencimiento del permiso de circulación'),
    revision: fechaOpcional(b.vencimiento_revision, 'vencimiento de la revisión técnica'),
    seguro: fechaOpcional(b.vencimiento_seguro, 'vencimiento del seguro'),
  };
  if (!d.marca || !d.modelo) throw new ErrorHttp(400, 'Ingresa la marca y el modelo');
  const anoMax = new Date().getFullYear() + 1;
  if (!Number.isInteger(d.ano) || d.ano < 1990 || d.ano > anoMax) throw new ErrorHttp(400, `El año debe estar entre 1990 y ${anoMax}`);
  if (!['bencina', 'diésel', 'eléctrico', 'híbrido', 'gas'].includes(d.combustible)) {
    throw new ErrorHttp(400, 'Combustible: bencina, diésel, eléctrico, híbrido o gas');
  }
  if (!Number.isInteger(d.capacidad) || d.capacidad < 1 || d.capacidad > 6) throw new ErrorHttp(400, 'La capacidad debe ser de 1 a 6 asientos');
  return d;
}

/** Guarda documentos, recorrido y conductor de un vehículo (dentro de una transacción). */
async function guardarDetallesAuto(c, autoId, d, admin) {
  if (d.permiso) {
    await c.query(
      `INSERT INTO permisos_circulacion (automovil_id, fecha_vencimiento) VALUES ($1, $2)
       ON CONFLICT (automovil_id) DO UPDATE SET fecha_vencimiento = EXCLUDED.fecha_vencimiento,
         fecha_otorgacion = CASE WHEN permisos_circulacion.fecha_otorgacion < EXCLUDED.fecha_vencimiento
                                 THEN permisos_circulacion.fecha_otorgacion END`,
      [autoId, d.permiso],
    );
  }
  if (d.revision) {
    await c.query(
      `INSERT INTO revisiones_tecnicas (automovil_id, fecha_vencimiento) VALUES ($1, $2)
       ON CONFLICT (automovil_id) DO UPDATE SET fecha_vencimiento = EXCLUDED.fecha_vencimiento`,
      [autoId, d.revision],
    );
  }
  if (d.seguro) {
    await c.query(
      `INSERT INTO seguros_obligatorios (automovil_id, fecha_vencimiento) VALUES ($1, $2)
       ON CONFLICT (automovil_id) DO UPDATE SET fecha_vencimiento = EXCLUDED.fecha_vencimiento`,
      [autoId, d.seguro],
    );
  }

  let conductorId = null;
  let usuarioConductor = null;
  if (d.rutConductor) {
    const cond = await c.query('SELECT id, usuario_id, estado FROM conductores WHERE rut = $1', [d.rutConductor]);
    if (cond.rowCount === 0) throw new ErrorHttp(400, 'No hay un conductor con ese RUT');
    if (cond.rows[0].estado !== 'APROBADA') throw new ErrorHttp(400, 'Ese conductor aún no está aprobado');
    conductorId = cond.rows[0].id;
    usuarioConductor = cond.rows[0].usuario_id;
  }
  if (d.folio) {
    const r = await c.query('SELECT 1 FROM rutas WHERE folio = $1', [d.folio]);
    if (r.rowCount === 0) throw new ErrorHttp(400, 'No existe un recorrido con ese folio');
  }
  if (conductorId && !d.folio) throw new ErrorHttp(400, 'Para asignar un conductor, elige también el recorrido');

  const actual = (await c.query(
    'SELECT ruta_folio, conductor_id FROM asignaciones WHERE automovil_id = $1 AND activo', [autoId],
  )).rows[0];
  const cambio = (actual?.ruta_folio ?? null) !== d.folio || (actual?.conductor_id ?? null) !== conductorId;
  if (!cambio) return;

  await c.query('UPDATE asignaciones SET activo = FALSE WHERE automovil_id = $1 AND activo', [autoId]);
  if (conductorId) {
    // Un conductor maneja un solo vehículo: se le quita de cualquier otro.
    await c.query('UPDATE asignaciones SET conductor_id = NULL WHERE conductor_id = $1 AND activo', [conductorId]);
  }
  if (d.folio) {
    await c.query('INSERT INTO asignaciones (conductor_id, automovil_id, ruta_folio) VALUES ($1, $2, $3)',
      [conductorId, autoId, d.folio]);
  }
  // Si el vehículo cambia de recorrido, sus turnos futuros en el recorrido anterior ya no sirven.
  await c.query(`DELETE FROM turnos WHERE automovil_id = $1 AND fecha + hora_inicio > (NOW() AT TIME ZONE 'America/Santiago')
                 AND ruta_folio IS DISTINCT FROM $2`, [autoId, d.folio]);
  if (usuarioConductor && conductorId !== (actual?.conductor_id ?? null)) {
    const linea = (await c.query('SELECT linea FROM rutas WHERE folio = $1', [d.folio])).rows[0].linea;
    await mongo.notificar([usuarioConductor], 'NUEVA_ASIGNACION', 'Nueva asignación',
      `Línea ${linea} · ${d.patente}. Revisa tus turnos en Rutas.`);
  }
  await mongo.auditar(admin, 'ASIGNAR_VEHICULO', { automovil: autoId, folio: d.folio, conductor: conductorId });
}

router.post('/api/automoviles', soloAdmin, ruta(async (req, res) => {
  const d = datosAuto(req.body);
  const id = await transaccion(async (c) => {
    const existe = (await c.query('SELECT id, activo FROM automoviles WHERE patente = $1', [d.patente])).rows[0];
    if (existe?.activo) throw new ErrorHttp(409, 'Esa patente ya está registrada');
    let autoId;
    if (existe) {
      // Un vehículo dado de baja antes se vuelve a activar.
      await c.query(
        `UPDATE automoviles SET marca = $2, modelo = $3, ano = $4, combustible = $5, capacidad = $6, activo = TRUE
         WHERE id = $1`, [existe.id, d.marca, d.modelo, d.ano, d.combustible, d.capacidad],
      );
      autoId = existe.id;
    } else {
      autoId = (await c.query(
        `INSERT INTO automoviles (patente, marca, modelo, ano, combustible, capacidad) VALUES ($1, $2, $3, $4, $5, $6)
         RETURNING id`, [d.patente, d.marca, d.modelo, d.ano, d.combustible, d.capacidad],
      )).rows[0].id;
    }
    await guardarDetallesAuto(c, autoId, d, req.usuario);
    return autoId;
  });
  await mongo.auditar(req.usuario, 'CREAR_AUTOMOVIL', { patente: d.patente });
  res.status(201).json((await query(`${SQL_AUTO} WHERE a.id = $1`, [id])).rows[0]);
}));

router.put('/api/automoviles/:id', soloAdmin, ruta(async (req, res) => {
  const d = datosAuto(req.body);
  await transaccion(async (c) => {
    const r = await c.query(
      `UPDATE automoviles SET patente = $2, marca = $3, modelo = $4, ano = $5, combustible = $6, capacidad = $7
       WHERE id = $1 AND activo RETURNING id`,
      [req.params.id, d.patente, d.marca, d.modelo, d.ano, d.combustible, d.capacidad],
    ).catch((err) => errorUnico(err, 'Esa patente ya está registrada'));
    if (r.rowCount === 0) throw new ErrorHttp(404, 'Vehículo no encontrado');
    await guardarDetallesAuto(c, Number(req.params.id), d, req.usuario);
  });
  await mongo.auditar(req.usuario, 'MODIFICAR_AUTOMOVIL', { automovil: Number(req.params.id) });
  res.json((await query(`${SQL_AUTO} WHERE a.id = $1`, [req.params.id])).rows[0]);
}));

/** Dar de baja: se conserva el historial (reservas, servicios), pero deja de operar. */
router.delete('/api/automoviles/:id', soloAdmin, ruta(async (req, res) => {
  await transaccion(async (c) => {
    const abierto = await c.query('SELECT 1 FROM servicios WHERE automovil_id = $1 AND fin IS NULL', [req.params.id]);
    if (abierto.rowCount > 0) throw new ErrorHttp(409, 'El vehículo está en servicio; el conductor debe finalizarlo primero');
    const r = await c.query('UPDATE automoviles SET activo = FALSE WHERE id = $1 AND activo RETURNING id', [req.params.id]);
    if (r.rowCount === 0) throw new ErrorHttp(404, 'Vehículo no encontrado');
    await c.query('UPDATE asignaciones SET activo = FALSE WHERE automovil_id = $1 AND activo', [req.params.id]);
    await c.query("UPDATE reservas SET estado = 'CANCELADA', actualizada_en = NOW() WHERE automovil_id = $1 AND estado = 'ACTIVA'",
      [req.params.id]);
    await c.query(`DELETE FROM turnos WHERE automovil_id = $1 AND fecha + hora_inicio > (NOW() AT TIME ZONE 'America/Santiago')`, [req.params.id]);
  });
  await mongo.auditar(req.usuario, 'BAJA_AUTOMOVIL', { automovil: Number(req.params.id) });
  res.status(204).end();
}));

// --- Recorridos: trazado calculado por la app de administración ---

function puntosValidos(lista) {
  if (!Array.isArray(lista) || lista.length > 20_000) return null;
  const ok = lista.every((p) => Array.isArray(p) && p.length === 2 && p.every((n) => typeof n === 'number' && Number.isFinite(n)));
  return ok ? lista.map(([lat, lng]) => [Math.round(lat * 1e5) / 1e5, Math.round(lng * 1e5) / 1e5]) : null;
}

// --- Recorridos y tarifas (Admin 8) ---

/** Los recorridos con su tarifa, si tienen trazado guardado y cuántos vehículos tienen turno hoy. */
router.get('/api/admin/recorridos', soloAdmin, ruta(async (req, res) => {
  const r = await query(
    `SELECT r.folio, r.linea, r.origen_comuna, r.destino_comuna, r.tarifa,
            EXISTS (SELECT 1 FROM ruta_trazados t WHERE t.ruta_folio = r.folio) AS trazado_guardado,
            (SELECT COUNT(DISTINCT t.automovil_id) FROM turnos t
              WHERE t.ruta_folio = r.folio AND t.fecha = (NOW() AT TIME ZONE 'America/Santiago')::date)::int AS asignados_hoy
     FROM rutas r ORDER BY r.linea`,
  );
  res.json(r.rows);
}));

/** Cambia la tarifa (CLP, entero de 1 a 20.000). Rige para los pagos nuevos y queda en la auditoría. */
router.put('/api/admin/recorridos/:folio/tarifa', soloAdmin, ruta(async (req, res) => {
  const tarifa = req.body.tarifa;
  if (!Number.isInteger(tarifa) || tarifa < 1 || tarifa > 20000) throw new ErrorHttp(400, 'La tarifa debe ser un número entero entre $1 y $20.000');
  const r = await query(
    `UPDATE rutas n SET tarifa = $2 FROM rutas a WHERE n.folio = $1 AND a.folio = n.folio
     RETURNING n.folio, n.linea, n.tarifa, a.tarifa AS anterior`,
    [req.params.folio, tarifa],
  );
  if (r.rowCount === 0) throw new ErrorHttp(404, 'Recorrido no encontrado');
  const { anterior, ...ruta_ } = r.rows[0];
  await mongo.auditar(req.usuario, 'CAMBIAR_TARIFA', { folio: ruta_.folio, anterior, nueva: tarifa });
  res.json(ruta_);
}));

router.put('/api/rutas/:folio/trazado', soloAdmin, ruta(async (req, res) => {
  const ida = puntosValidos(req.body.ida);
  const regreso = puntosValidos(req.body.regreso);
  if (!ida || !regreso || (ida.length < 2 && regreso.length < 2)) throw new ErrorHttp(400, 'Trazado inválido');
  const version = await transaccion(async (c) => {
    const existe = await c.query('SELECT 1 FROM rutas WHERE folio = $1', [req.params.folio]);
    if (existe.rowCount === 0) throw new ErrorHttp(404, 'Recorrido no encontrado');
    const v = (await c.query('SELECT COALESCE(MAX(version), 0) + 1 AS v FROM ruta_trazados WHERE ruta_folio = $1',
      [req.params.folio])).rows[0].v;
    for (const [sentido, puntos] of [['IDA', ida], ['REGRESO', regreso]]) {
      await c.query('INSERT INTO ruta_trazados (ruta_folio, sentido, geometria, version) VALUES ($1, $2, $3, $4)',
        [req.params.folio, sentido, JSON.stringify(puntos), v]);
    }
    return v;
  });
  await mongo.auditar(req.usuario, 'RECALCULAR_TRAZADO', { folio: req.params.folio, version });
  res.json({ folio: req.params.folio, version, puntos_ida: ida.length, puntos_regreso: regreso.length });
}));

// --- Turnos y asignación de conductores ---

// --- Turnos: asignaciones con fecha y horario (Admin 3 y Admin 4) ---

/** Turnos desde [desde] (por defecto, hoy menos 7 días) durante [dias] días (máx. 60), opcionalmente de un recorrido. */
router.get('/api/admin/turnos', soloAdmin, ruta(async (req, res) => {
  const hoy = (await query("SELECT ((NOW() AT TIME ZONE 'America/Santiago')::date - 7)::text AS d")).rows[0].d;
  const desde = req.query.desde ? fechaOpcional(req.query.desde, 'inicio') : hoy;
  const dias = Math.min(Math.max(Number(req.query.dias) || 45, 1), 60);
  res.json(await turnos.listar({ desde, dias, folio: req.query.folio }));
}));

router.post('/api/admin/turnos', soloAdmin, ruta(async (req, res) => {
  const { turno, usuarioConductor } = await turnos.crear(req.body);
  if (usuarioConductor) {
    await mongo.notificar([usuarioConductor], 'NUEVA_ASIGNACION', 'Nuevo turno',
      `El ${turnos.fechaCl(turno.fecha)} de ${turno.inicio} a ${turno.fin}: línea ${turno.linea}, vehículo ${turno.patente}.`);
  }
  await mongo.auditar(req.usuario, 'CREAR_TURNO', { turno: turno.id, conductor: turno.conductor_id, fecha: turno.fecha });
  res.status(201).json(turno);
}));

router.delete('/api/admin/turnos/:id', soloAdmin, ruta(async (req, res) => {
  await turnos.borrar(req.params.id);
  await mongo.auditar(req.usuario, 'BORRAR_TURNO', { turno: Number(req.params.id) });
  res.status(204).end();
}));

/** Asigna un vehículo (y con él, su línea) a un conductor; automovil_id null = dejarlo sin vehículo. */
router.post('/api/admin/asignaciones', soloAdmin, ruta(async (req, res) => {
  const conductorId = Number(req.body.conductor_id);
  const automovilId = req.body.automovil_id == null || req.body.automovil_id === '' ? null : Number(req.body.automovil_id);
  const resultado = await transaccion(async (c) => {
    const cond = (await c.query("SELECT id, usuario_id FROM conductores WHERE id = $1 AND estado = 'APROBADA'", [conductorId])).rows[0];
    if (!cond) throw new ErrorHttp(404, 'Conductor no encontrado o no aprobado');
    const abierto = await c.query('SELECT 1 FROM servicios WHERE conductor_id = $1 AND fin IS NULL', [conductorId]);
    if (abierto.rowCount > 0) throw new ErrorHttp(409, 'El conductor está en servicio; debe finalizarlo antes de cambiar de vehículo');
    let destino = null;
    if (automovilId) {
      destino = (await c.query(
        `SELECT s.id, s.conductor_id, a.patente, r.linea FROM asignaciones s
         JOIN automoviles a ON a.id = s.automovil_id AND a.activo JOIN rutas r ON r.folio = s.ruta_folio
         WHERE s.automovil_id = $1 AND s.activo`,
        [automovilId],
      )).rows[0];
      if (!destino) throw new ErrorHttp(400, 'Ese vehículo no tiene un recorrido asignado (asígnalo en Automóviles)');
      const enServicio = await c.query('SELECT 1 FROM servicios WHERE automovil_id = $1 AND fin IS NULL', [automovilId]);
      if (enServicio.rowCount > 0) throw new ErrorHttp(409, 'Ese vehículo está en servicio ahora');
    }
    await c.query('UPDATE asignaciones SET conductor_id = NULL WHERE conductor_id = $1 AND activo', [conductorId]);
    if (destino) await c.query('UPDATE asignaciones SET conductor_id = $2 WHERE id = $1', [destino.id, conductorId]);
    return { cond, destino };
  });
  if (resultado.destino) {
    await mongo.notificar([resultado.cond.usuario_id], 'NUEVA_ASIGNACION', 'Nueva asignación',
      `Línea ${resultado.destino.linea} · ${resultado.destino.patente}. Revisa tus turnos en Rutas.`);
  }
  await mongo.auditar(req.usuario, 'ASIGNAR_CONDUCTOR', { conductor: conductorId, automovil: automovilId });
  res.json({ conductor_id: conductorId, automovil_id: automovilId });
}));

// --- Cuentas (Admin 6) ---

router.get('/api/admin/cuentas', soloAdmin, ruta(async (req, res) => {
  const r = await query(
    `SELECT u.id, u.nombre, u.email, r.nombre AS rol, u.activo, u.debe_cambiar_clave,
            (SELECT COUNT(*) FROM sesiones s WHERE s.usuario_id = u.id AND NOT s.revocada AND s.expira > NOW())::int AS sesiones_activas
     FROM usuarios u JOIN roles r ON r.id = u.rol_id
     ORDER BY CASE r.nombre WHEN 'ADMIN' THEN 0 WHEN 'CONDUCTOR' THEN 1 ELSE 2 END, u.nombre`,
  );
  res.json(r.rows);
}));

/** «Cerrar sesiones»: invalida todos los tokens de la cuenta (por ejemplo, si le roban el teléfono a un conductor). */
router.post('/api/admin/cuentas/:id/cerrar-sesiones', soloAdmin, ruta(async (req, res) => {
  const r = await query('UPDATE sesiones SET revocada = TRUE WHERE usuario_id = $1 AND NOT revocada', [req.params.id]);
  await mongo.auditar(req.usuario, 'CERRAR_SESIONES', { usuario: Number(req.params.id), cerradas: r.rowCount });
  res.json({ cerradas: r.rowCount });
}));

/** Desactivar (no borra la cuenta: deja de poder entrar y pierde sus sesiones) o reactivar. */
router.post('/api/admin/cuentas/:id/estado', soloAdmin, ruta(async (req, res) => {
  const activo = req.body.activo === true;
  if (!activo && Number(req.params.id) === req.usuario.id) throw new ErrorHttp(400, 'No puedes desactivar tu propia cuenta');
  const r = await query('UPDATE usuarios SET activo = $2 WHERE id = $1 RETURNING id, activo', [req.params.id, activo]);
  if (r.rowCount === 0) throw new ErrorHttp(404, 'Cuenta no encontrada');
  if (!activo) await query('UPDATE sesiones SET revocada = TRUE WHERE usuario_id = $1 AND NOT revocada', [req.params.id]);
  await mongo.auditar(req.usuario, activo ? 'REACTIVAR_CUENTA' : 'DESACTIVAR_CUENTA', { usuario: Number(req.params.id) });
  res.json(r.rows[0]);
}));

/** Crea un administrador con una contraseña temporal que se muestra una sola vez y debe cambiar al entrar. */
router.post('/api/admin/administradores', soloAdmin, ruta(async (req, res) => {
  const nombre = texto(req.body.nombre);
  const email = normalizarEmail(req.body.email);
  if (!nombre) throw new ErrorHttp(400, 'Ingresa el nombre completo');
  if (!emailValido(email)) throw new ErrorHttp(400, 'Ingresa un correo válido');
  const clave = crypto.randomBytes(15).toString('base64url'); // 20 caracteres
  const r = await query(
    `INSERT INTO usuarios (email, password_hash, nombre, rol_id, verificado, debe_cambiar_clave)
     VALUES ($1, $2, $3, (SELECT id FROM roles WHERE nombre = 'ADMIN'), TRUE, TRUE)
     ON CONFLICT (email) DO NOTHING RETURNING id`,
    [email, await bcrypt.hash(clave, 10), nombre],
  );
  if (r.rowCount === 0) throw new ErrorHttp(409, 'Ese correo ya tiene una cuenta');
  await mongo.auditar(req.usuario, 'CREAR_ADMINISTRADOR', { usuario: r.rows[0].id, email });
  res.status(201).json({ id: r.rows[0].id, nombre, email, clave_temporal: clave });
}));

// --- Flota en vivo y avisos ---

router.get('/api/admin/flota', soloAdmin, ruta(async (req, res) => {
  const r = await query(
    `SELECT a.patente, r.linea, r.folio, c.nombre_completo AS conductor,
            EXISTS (SELECT 1 FROM servicios sv WHERE sv.automovil_id = a.id AND sv.fin IS NULL) AS en_servicio
     FROM automoviles a JOIN asignaciones s ON s.automovil_id = a.id AND s.activo
     JOIN rutas r ON r.folio = s.ruta_folio LEFT JOIN conductores c ON c.id = s.conductor_id
     WHERE a.activo ORDER BY en_servicio DESC, r.linea, a.patente`,
  );
  const posiciones = await mongo.ultimasPosiciones(r.rows.map((v) => v.patente)).catch(() => ({}));
  res.json(r.rows.map((v) => ({ ...v, posicion: posiciones[v.patente] ?? null })));
}));

/** Avisos de los conductores, del más nuevo al más antiguo (máx. 200). Filtros: ?fecha=aaaa-mm-dd y ?codigo=AC-0x. */
router.get('/api/admin/avisos', soloAdmin, ruta(async (req, res) => {
  const fecha = req.query.fecha ? fechaOpcional(req.query.fecha, 'los avisos') : null;
  const r = await query(
    `SELECT av.id, av.codigo, av.titulo, av.creado_en, av.lat, av.lng, a.patente, r.linea, c.nombre_completo AS conductor,
            to_char(av.creado_en AT TIME ZONE 'America/Santiago', 'YYYY-MM-DD') AS fecha,
            to_char(av.creado_en AT TIME ZONE 'America/Santiago', 'HH24:MI') AS hora,
            av.creado_en > NOW() - INTERVAL '60 minutes' AS reciente
     FROM avisos av LEFT JOIN automoviles a ON a.id = av.automovil_id
     LEFT JOIN rutas r ON r.folio = av.ruta_folio LEFT JOIN conductores c ON c.id = av.conductor_id
     WHERE ($1::date IS NULL OR (av.creado_en AT TIME ZONE 'America/Santiago')::date = $1::date)
       AND ($2::text IS NULL OR av.codigo = $2)
     ORDER BY av.creado_en DESC, av.id DESC LIMIT 200`,
    [fecha, req.query.codigo || null],
  );
  res.json(r.rows);
}));

module.exports = { router, rutNormalizado, patenteNormalizada, semaforo };
