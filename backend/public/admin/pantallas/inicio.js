// Inicio del panel (sin vista en Figma; mismo estilo): resumen de la operación, semáforo de vigencias,
// los 2 reportes PDF del protocolo y los avisos recientes.
import { el, icono, mensaje } from '../ui.js';

const REPORTES = [
  { ruta: '/api/admin/reportes/licencias?dias=30', archivo: 'licencias-por-vencer-30-dias.pdf',
    titulo: 'Conductores con licencia que vence en 30 días' },
  { ruta: '/api/admin/reportes/vehiculos-vencidos', archivo: 'vehiculos-documentos-vencidos.pdf',
    titulo: 'Vehículos con documentación vencida' },
];

/** Fecha y hora en Santiago: "24-11-2026 16:23". */
const cuando = (iso) => new Date(iso).toLocaleString('es-CL', {
  timeZone: 'America/Santiago', day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
}).replace(/\//g, '-').replace(',', '');

const cifra = (valor, texto, enlace) => el(enlace ? 'a' : 'div', { class: 'cifra', href: enlace },
  el('strong', {}, String(valor)), el('span', {}, texto));

/** Fila del semáforo: el estado va escrito (no solo con color). */
const estado = (clase, etiqueta, valor) => el('li', { class: `semaforo-fila ${clase}` },
  el('span', { class: 'semaforo-punto', 'aria-hidden': 'true' }), el('span', {}, etiqueta), el('strong', {}, String(valor)));

export async function mostrar(raiz, { api }) {
  const r = await api.get('/api/admin/resumen');
  const c = r.conductores;
  const v = r.vehiculos;
  const salidaReportes = el('div', { 'aria-live': 'polite' });

  /** Descarga el PDF con la sesión (la ruta exige el rol ADMIN) y lo guarda con su nombre. */
  async function descargar(boton, reporte) {
    boton.disabled = true;
    const texto = boton.lastChild.textContent;
    boton.lastChild.textContent = 'Generando…';
    try {
      const url = URL.createObjectURL(await api.archivo(reporte.ruta));
      const a = el('a', { href: url, download: reporte.archivo });
      document.body.append(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 10_000);
      salidaReportes.replaceChildren(mensaje('info', `Se descargó «${reporte.archivo}». Contiene datos personales: no lo compartas fuera de la administración.`));
    } catch (err) {
      salidaReportes.replaceChildren(mensaje('error', err.message));
    } finally {
      boton.lastChild.textContent = texto;
      boton.disabled = false;
    }
  }

  raiz.replaceChildren(
    el('section', { class: 'cifras', 'aria-label': 'Resumen de la operación' },
      cifra(r.rutas, 'recorridos'),
      cifra(r.en_servicio, 'vehículos en servicio ahora'),
      cifra(r.reservas_activas, 'reservas activas'),
      cifra(r.pasajeros, 'pasajeros registrados'),
      cifra(c.pendientes, 'conductores por validar', c.pendientes ? '#validar' : null)),
    el('div', { class: 'tres-columnas' },
      el('section', { class: 'tarjeta', 'aria-labelledby': 'titulo-licencias' },
        el('h2', { id: 'titulo-licencias' }, 'Licencias de conductores'),
        el('ul', { class: 'semaforo' },
          estado('semaforo-verde', 'Vigentes', c.vigentes),
          estado('semaforo-ambar', 'Vencen en 30 días o menos', c.por_vencer),
          estado('semaforo-rojo', 'Vencidas', c.vencidos))),
      el('section', { class: 'tarjeta', 'aria-labelledby': 'titulo-documentos' },
        el('h2', { id: 'titulo-documentos' }, 'Documentos de vehículos'),
        el('p', { class: 'secundario' }, 'Permiso de circulación, seguro obligatorio y revisión técnica'),
        el('ul', { class: 'semaforo' },
          estado('semaforo-verde', 'Al día', v.al_dia),
          estado('semaforo-ambar', 'Alguno vence en 30 días o menos', v.por_vencer),
          estado('semaforo-rojo', 'Alguno vencido', v.vencidos),
          estado('semaforo-gris', 'Sin documentos registrados', v.sin_documentos))),
      el('section', { class: 'tarjeta', 'aria-labelledby': 'titulo-reportes' },
        el('h2', { id: 'titulo-reportes' }, 'Reportes PDF'),
        el('div', { class: 'botones-reportes' }, REPORTES.map((rep) => {
          const b = el('button', { type: 'button', class: 'boton boton-contorno boton-reporte' }, icono('reportes'), el('span', {}, rep.titulo));
          b.addEventListener('click', () => descargar(b, rep));
          return b;
        })),
        salidaReportes)),
    el('section', { class: 'tarjeta', 'aria-labelledby': 'titulo-recientes' },
      el('div', { class: 'fila-titulo' }, el('h2', { id: 'titulo-recientes' }, 'Avisos recientes'),
        el('a', { href: '#avisos', class: 'enlace' }, 'Ver todos')),
      r.avisos_recientes.length
        ? el('ul', { class: 'avisos-recientes' }, r.avisos_recientes.slice(0, 5).map((a) => el('li', {},
          el('span', { class: `insignia ${a.codigo === 'AC-06' ? 'insignia-emergencia' : 'insignia-gris'}` }, a.codigo),
          el('span', { class: 'fuerte' }, a.titulo),
          el('span', { class: 'secundario' }, [a.patente, a.conductor, cuando(a.creado_en)].filter(Boolean).join(' · ')))))
        : el('p', { class: 'secundario' }, 'No hay avisos.')));
}
