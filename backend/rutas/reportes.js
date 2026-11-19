// Reportes en PDF para la administración (protocolo de entrega final):
//  1. Conductores cuya licencia de conducir vence dentro de los próximos 30 días.
//  2. Vehículos con algún documento vencido (permiso de circulación, seguro obligatorio o revisión técnica).
// Solo ADMIN. Cada reporte queda en la auditoría. Con ?formato=json devuelve los mismos datos sin PDF.
const express = require('express');
const PDFDocument = require('pdfkit');
const { query } = require('../db/postgres');
const mongo = require('../db/mongo');
const { ruta, ErrorHttp, autenticar, requiereRol } = require('../util');

const router = express.Router();
const soloAdmin = [autenticar, requiereRol('ADMIN')];
const HOY = "(NOW() AT TIME ZONE 'America/Santiago')::date";

/** Conductores con licencia que vence entre hoy y hoy + dias (ambos incluidos). */
async function licenciasPorVencer(dias) {
  const r = await query(
    `SELECT nombre_completo, rut, tipo_licencia, municipio_licencia,
            fecha_vencimiento_licencia::text AS vencimiento,
            (fecha_vencimiento_licencia - ${HOY}) AS dias_restantes
     FROM conductores
     WHERE fecha_vencimiento_licencia BETWEEN ${HOY} AND ${HOY} + $1::int
     ORDER BY fecha_vencimiento_licencia, nombre_completo`,
    [dias],
  );
  return r.rows;
}

/** Vehículos activos con al menos un documento vencido; indica cuáles. */
async function vehiculosConDocumentosVencidos() {
  const r = await query(
    `SELECT a.patente, a.marca, a.modelo, s.ruta_folio, ru.linea,
            p.fecha_vencimiento::text AS vencimiento_permiso,
            rt.fecha_vencimiento::text AS vencimiento_revision,
            so.fecha_vencimiento::text AS vencimiento_seguro,
            (p.fecha_vencimiento < ${HOY}) AS permiso_vencido,
            (rt.fecha_vencimiento < ${HOY}) AS revision_vencida,
            (so.fecha_vencimiento < ${HOY}) AS seguro_vencido
     FROM automoviles a
     LEFT JOIN asignaciones s ON s.automovil_id = a.id AND s.activo
     LEFT JOIN rutas ru ON ru.folio = s.ruta_folio
     LEFT JOIN permisos_circulacion p ON p.automovil_id = a.id
     LEFT JOIN revisiones_tecnicas rt ON rt.automovil_id = a.id
     LEFT JOIN seguros_obligatorios so ON so.automovil_id = a.id
     WHERE a.activo
       AND (p.fecha_vencimiento < ${HOY} OR rt.fecha_vencimiento < ${HOY} OR so.fecha_vencimiento < ${HOY})
     ORDER BY ru.linea NULLS LAST, a.patente`,
  );
  return r.rows;
}

/** "2026-11-19" → "19-11-2026". */
const fechaCl = (iso) => (iso ? `${iso.slice(8, 10)}-${iso.slice(5, 7)}-${iso.slice(0, 4)}` : '—');

function hoyCl() {
  const partes = new Intl.DateTimeFormat('es-CL', {
    timeZone: 'America/Santiago', day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).formatToParts(new Date());
  const v = (t) => partes.find((p) => p.type === t).value;
  return `${v('day')}-${v('month')}-${v('year')} ${v('hour')}:${v('minute')}`;
}

/** Dibuja un PDF A4 con título, tabla y pie, y lo envía en la respuesta. */
function enviarPdf(res, { archivo, titulo, subtitulo, columnas, filas, vacio, emisor }) {
  const doc = new PDFDocument({ size: 'A4', margin: 40, bufferPages: true, info: { Title: titulo, Author: 'CONATACOCH' } });
  res.setHeader('Content-Type', 'application/pdf');
  res.setHeader('Content-Disposition', `attachment; filename="${archivo}"`);
  res.setHeader('Cache-Control', 'no-store');
  doc.pipe(res);

  const izq = doc.page.margins.left;
  const ancho = doc.page.width - izq - doc.page.margins.right;

  doc.fillColor('#2E7D32').font('Helvetica-Bold').fontSize(16).text('CONATACOCH', izq, 40);
  doc.fillColor('#231F20').fontSize(13).text(titulo);
  doc.font('Helvetica').fontSize(9).fillColor('#555555')
    .text(`${subtitulo}  ·  Emitido el ${hoyCl()} por ${emisor}`);
  doc.moveDown(0.8);

  const anchoTotal = columnas.reduce((s, c) => s + c.ancho, 0);
  const escala = ancho / anchoTotal;
  const encabezado = () => {
    const y = doc.y;
    doc.rect(izq, y - 3, ancho, 18).fill('#E8F5E9');
    let x = izq;
    doc.fillColor('#1B5E20').font('Helvetica-Bold').fontSize(8.5);
    for (const c of columnas) {
      doc.text(c.titulo, x + 3, y, { width: c.ancho * escala - 6 });
      x += c.ancho * escala;
    }
    doc.y = y + 18;
  };

  encabezado();
  if (filas.length === 0) {
    doc.font('Helvetica').fontSize(10).fillColor('#231F20').text(vacio, izq, doc.y + 6);
  }
  doc.font('Helvetica').fontSize(8.5);
  filas.forEach((fila, i) => {
    if (doc.y > doc.page.height - 80) {
      doc.addPage();
      doc.y = 40;
      encabezado();
      doc.font('Helvetica').fontSize(8.5);
    }
    const y = doc.y;
    if (i % 2 === 1) doc.rect(izq, y - 3, ancho, 16).fill('#F5F7F5');
    let x = izq;
    columnas.forEach((c) => {
      const valor = String(fila[c.campo] ?? '—');
      doc.fillColor(c.alerta && c.alerta(fila) ? '#B3261E' : '#231F20')
        .text(valor, x + 3, y, { width: c.ancho * escala - 6, lineBreak: false, ellipsis: true });
      x += c.ancho * escala;
    });
    doc.y = y + 16;
  });

  doc.moveDown(1);
  doc.font('Helvetica-Bold').fontSize(9).fillColor('#231F20').text(`Total: ${filas.length}`, izq);

  // Pie en cada página: aviso de datos personales y número de página.
  const rango = doc.bufferedPageRange();
  for (let p = rango.start; p < rango.start + rango.count; p++) {
    doc.switchToPage(p);
    doc.page.margins.bottom = 0; // el pie va bajo el margen: sin esto PDFKit lo pasa a una página nueva
    doc.font('Helvetica').fontSize(7.5).fillColor('#777777').text(
      `Documento de uso interno con datos personales (Ley 21.719). No compartir fuera de la administración.  ·  Página ${p + 1} de ${rango.count}`,
      izq, doc.page.height - 30, { width: ancho, align: 'center', lineBreak: false },
    );
  }
  doc.end();
}

router.get('/api/admin/reportes/licencias', soloAdmin, ruta(async (req, res) => {
  const dias = req.query.dias === undefined ? 30 : Number(req.query.dias);
  if (!Number.isInteger(dias) || dias < 0 || dias > 365) throw new ErrorHttp(400, 'Los días deben ser un entero entre 0 y 365');
  const filas = await licenciasPorVencer(dias);
  await mongo.auditar(req.usuario, 'GENERAR_REPORTE', { reporte: 'licencias', dias, filas: filas.length });
  if (req.query.formato === 'json') return res.json(filas);
  enviarPdf(res, {
    archivo: `licencias-por-vencer-${dias}-dias.pdf`,
    titulo: `Conductores con licencia que vence en los próximos ${dias} días`,
    subtitulo: 'Licencias profesionales A2 y A3',
    emisor: req.usuario.email,
    vacio: `Ningún conductor tiene la licencia por vencer en los próximos ${dias} días.`,
    columnas: [
      { titulo: 'Conductor', campo: 'nombre_completo', ancho: 30 },
      { titulo: 'RUT', campo: 'rut', ancho: 14 },
      { titulo: 'Clase', campo: 'tipo_licencia', ancho: 8 },
      { titulo: 'Municipalidad', campo: 'municipio', ancho: 18 },
      { titulo: 'Vence', campo: 'vence', ancho: 13 },
      { titulo: 'Días', campo: 'dias_restantes', ancho: 8, alerta: (f) => f.dias_restantes <= 7 },
    ],
    filas: filas.map((f) => ({ ...f, municipio: f.municipio_licencia, vence: fechaCl(f.vencimiento) })),
  });
}));

router.get('/api/admin/reportes/vehiculos-vencidos', soloAdmin, ruta(async (req, res) => {
  const filas = await vehiculosConDocumentosVencidos();
  await mongo.auditar(req.usuario, 'GENERAR_REPORTE', { reporte: 'vehiculos-vencidos', filas: filas.length });
  if (req.query.formato === 'json') return res.json(filas);
  const doc = (fecha, vencido) => `${fechaCl(fecha)}${vencido ? ' (vencido)' : ''}`;
  enviarPdf(res, {
    archivo: 'vehiculos-documentos-vencidos.pdf',
    titulo: 'Vehículos con documentación vencida',
    subtitulo: 'Permiso de circulación, seguro obligatorio (SOAP) o revisión técnica',
    emisor: req.usuario.email,
    vacio: 'Ningún vehículo activo tiene documentos vencidos.',
    columnas: [
      { titulo: 'Patente', campo: 'patente', ancho: 11 },
      { titulo: 'Vehículo', campo: 'vehiculo', ancho: 18 },
      { titulo: 'Línea', campo: 'linea', ancho: 8 },
      { titulo: 'Permiso', campo: 'permiso', ancho: 18, alerta: (f) => f.permiso_vencido },
      { titulo: 'Seguro', campo: 'seguro', ancho: 18, alerta: (f) => f.seguro_vencido },
      { titulo: 'Revisión técnica', campo: 'revision', ancho: 18, alerta: (f) => f.revision_vencida },
    ],
    filas: filas.map((f) => ({
      ...f,
      vehiculo: `${f.marca} ${f.modelo}`,
      permiso: doc(f.vencimiento_permiso, f.permiso_vencido),
      seguro: doc(f.vencimiento_seguro, f.seguro_vencido),
      revision: doc(f.vencimiento_revision, f.revision_vencida),
    })),
  });
}));

module.exports = { router, licenciasPorVencer, vehiculosConDocumentosVencidos };
