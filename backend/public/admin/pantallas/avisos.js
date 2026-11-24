// Admin 5 · Consulta de avisos: emergencia reciente destacada, filtros y ubicación en el mapa.
import { el, fechaCl, icono, mensaje } from '../ui.js';

const TIPOS = { 'AC-01': 'Voy con retraso', 'AC-02': 'Desvío', 'AC-03': 'Desperfecto', 'AC-05': 'QR dañado', 'AC-06': 'Emergencia' };
const SEGUNDOS_REFRESCO = 30;

const urlMapa = (a) => `https://www.openstreetmap.org/?mlat=${a.lat}&mlon=${a.lng}#map=17/${a.lat}/${a.lng}`;
const enlaceMapa = (a, texto, clase) => (a.lat == null
  ? el('span', { class: 'secundario' }, 'Sin ubicación')
  : el('a', { href: urlMapa(a), target: '_blank', rel: 'noopener noreferrer', class: clase,
    'aria-label': `${texto}: aviso ${a.codigo} de ${a.conductor ?? 'conductor'} (se abre en OpenStreetMap)` }, texto));

export async function mostrar(raiz, { api }) {
  const selTipo = el('select', { id: 'filtro-tipo' }, el('option', { value: '' }, 'Todos'),
    Object.entries(TIPOS).map(([c, t]) => el('option', { value: c }, `${c} · ${t}`)));
  const inConductor = el('input', { id: 'filtro-conductor', type: 'search', placeholder: 'Nombre' });
  const inVehiculo = el('input', { id: 'filtro-vehiculo', type: 'search', placeholder: 'Patente' });
  const inFecha = el('input', { id: 'filtro-fecha', type: 'date' });
  const banner = el('div', { 'aria-live': 'assertive' });
  const cuerpo = el('tbody');
  const estado = el('div', { 'aria-live': 'polite' });
  let avisos = [];

  function pintarBanner(recientes) {
    const e = recientes.find((a) => a.codigo === 'AC-06' && a.reciente);
    banner.replaceChildren(e ? el('section', { class: 'banner-emergencia', role: 'alert' },
      icono('alerta'),
      el('div', {}, el('strong', {}, 'Emergencia activa · AC-06'),
        el('span', {}, [e.patente, e.conductor, fechaCl(e.fecha), e.hora].filter(Boolean).join(' · '))),
      enlaceMapa(e, 'Ver ubicación en el mapa', 'boton boton-blanco')) : '');
  }

  function pintarTabla() {
    const c = inConductor.value.trim().toLowerCase();
    const v = inVehiculo.value.trim().toLowerCase();
    const filas = avisos.filter((a) => (!c || (a.conductor ?? '').toLowerCase().includes(c))
      && (!v || (a.patente ?? '').toLowerCase().includes(v)));
    cuerpo.replaceChildren(...(filas.length ? filas.map((a) => el('tr', { class: a.codigo === 'AC-06' ? 'fila-emergencia' : null },
      el('td', {}, el('span', { class: 'tipo-aviso' },
        el('span', { class: `insignia ${a.codigo === 'AC-06' ? 'insignia-emergencia' : 'insignia-gris'}` }, a.codigo),
        el('span', { class: 'fuerte' }, a.titulo))),
      el('td', {}, a.conductor ?? '—'), el('td', {}, a.patente ?? '—'), el('td', {}, fechaCl(a.fecha)), el('td', {}, a.hora),
      el('td', {}, enlaceMapa(a, 'Ver en mapa', 'enlace'))))
      : [el('tr', {}, el('td', { colspan: 6, class: 'vacio' }, 'No hay avisos con estos filtros.'))]));
  }

  async function cargar() {
    try {
      const filtro = new URLSearchParams();
      if (selTipo.value) filtro.set('codigo', selTipo.value);
      if (inFecha.value) filtro.set('fecha', inFecha.value);
      const [lista, recientes] = await Promise.all([
        api.get(`/api/admin/avisos?${filtro}`),
        api.get('/api/admin/avisos?codigo=AC-06'), // la emergencia se muestra aunque los filtros la escondan
      ]);
      avisos = lista;
      pintarBanner(recientes);
      pintarTabla();
      estado.replaceChildren();
    } catch (err) {
      estado.replaceChildren(mensaje('error', err.message));
    }
  }

  selTipo.addEventListener('change', cargar);
  inFecha.addEventListener('change', cargar);
  inConductor.addEventListener('input', pintarTabla);
  inVehiculo.addEventListener('input', pintarTabla);

  const campo = (id, etiqueta, control) => el('div', {}, el('label', { for: id }, etiqueta), control);
  raiz.replaceChildren(banner, estado, el('section', { class: 'tarjeta', 'aria-label': 'Avisos' },
    el('div', { class: 'filtros' },
      campo('filtro-tipo', 'Tipo', selTipo), campo('filtro-conductor', 'Conductor', inConductor),
      campo('filtro-vehiculo', 'Vehículo', inVehiculo), campo('filtro-fecha', 'Fecha', inFecha)),
    el('div', { class: 'tabla-contenedor' }, el('table', { class: 'tabla' },
      el('thead', {}, el('tr', {}, ['Tipo', 'Conductor', 'Vehículo', 'Fecha', 'Hora', 'Ubicación'].map((h) => el('th', { scope: 'col' }, h)))),
      cuerpo)),
    el('p', { class: 'secundario nota-pie' }, 'Avisos predefinidos de un toque, sin chat ni texto libre.')));
  await cargar();

  // Se actualiza solo mientras esta pantalla está abierta, para ver emergencias nuevas.
  const intervalo = setInterval(cargar, SEGUNDOS_REFRESCO * 1000);
  addEventListener('hashchange', () => clearInterval(intervalo), { once: true });
}
