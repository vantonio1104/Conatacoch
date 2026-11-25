// Mapa global en vivo (sin vista en Figma; estilo del panel): posición de cada colectivo en servicio, cada 10 s.
import { el, mensaje } from '../ui.js';

const SEGUNDOS_REFRESCO = 10;
const SEGUNDOS_SIN_SENAL = 60;
const CENTRO = [-33.53, -70.65]; // sur de Santiago, donde operan los recorridos

/** Carga Leaflet una sola vez (archivos servidos por el propio servidor). */
function cargarLeaflet() {
  if (window.L) return Promise.resolve(window.L);
  document.head.append(el('link', { rel: 'stylesheet', href: 'vendor/leaflet/leaflet.css' }));
  return new Promise((ok, falla) => {
    document.head.append(el('script', { src: 'vendor/leaflet/leaflet.js', onload: () => ok(window.L), onerror: falla }));
  });
}

/** EN_SERVICIO (con GPS reciente), SIN_SENAL (en servicio pero sin GPS en 60 s) o FUERA. */
function estadoDe(v) {
  if (!v.en_servicio) return 'FUERA';
  if (!v.posicion || Date.now() - new Date(v.posicion.timestamp).getTime() > SEGUNDOS_SIN_SENAL * 1000) return 'SIN_SENAL';
  return 'EN_SERVICIO';
}
const ESTADOS = {
  EN_SERVICIO: ['En servicio', 'insignia-verde', '#2E7D32'],
  SIN_SENAL: ['Sin señal GPS', 'insignia-ambar', '#F2A900'],
  FUERA: ['Fuera de servicio', 'insignia-gris', '#8A8A8A'],
};
const hora = (iso) => new Date(iso).toLocaleTimeString('es-CL', { timeZone: 'America/Santiago', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' });

export async function mostrar(raiz, { api }) {
  const L = await cargarLeaflet();
  const contenedor = el('div', { class: 'mapa', role: 'region', 'aria-label': 'Mapa de los colectivos' });
  const selLinea = el('select', { id: 'mapa-linea' }, el('option', { value: '' }, 'Todas'));
  const lista = el('ul', { class: 'lista-flota' });
  const estado = el('div', { 'aria-live': 'polite' });
  raiz.replaceChildren(estado, el('div', { class: 'dos-columnas dos-columnas-derecha' },
    el('section', { class: 'tarjeta tarjeta-mapa' }, contenedor),
    el('section', { class: 'tarjeta', 'aria-labelledby': 'titulo-flota' },
      el('h2', { id: 'titulo-flota' }, 'Colectivos'),
      el('label', { for: 'mapa-linea' }, 'Línea'), selLinea,
      el('p', { class: 'ayuda' }, `Se actualiza cada ${SEGUNDOS_REFRESCO} segundos.`),
      lista)));

  const mapa = L.map(contenedor, { zoomControl: true }).setView(CENTRO, 13);
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19, attribution: '© OpenStreetMap',
  }).addTo(mapa);
  const capa = L.layerGroup().addTo(mapa);
  let flota = [];

  function pintar() {
    capa.clearLayers();
    const visibles = flota.filter((v) => !selLinea.value || v.linea === selLinea.value);
    const conPosicion = [];
    for (const v of visibles) {
      if (!v.posicion || !v.en_servicio) continue;
      const [texto, , color] = ESTADOS[estadoDe(v)];
      const punto = [v.posicion.lat, v.posicion.lng];
      conPosicion.push(punto);
      L.circleMarker(punto, { radius: 9, color: '#FFFFFF', weight: 2, fillColor: color, fillOpacity: 1 })
        .bindPopup(el('div', { class: 'popup-flota' },
          el('strong', {}, `${v.patente} · Línea ${v.linea}`),
          el('span', {}, v.conductor ?? 'Sin conductor'),
          el('span', {}, `${texto} · ${v.posicion.velocidad != null ? `${Math.round(v.posicion.velocidad)} km/h · ` : ''}${hora(v.posicion.timestamp)}`)))
        .addTo(capa);
    }
    lista.replaceChildren(...(visibles.length ? visibles.map((v) => {
      const [texto, clase] = ESTADOS[estadoDe(v)];
      const contenido = [el('span', { class: 'fuerte' }, v.patente), el('span', { class: 'secundario' }, `Línea ${v.linea} · ${v.conductor ?? 'Sin conductor'}`),
        el('span', { class: `insignia ${clase}` }, texto)];
      return el('li', {}, v.posicion && v.en_servicio
        ? el('button', { type: 'button', class: 'item-flota', onclick: () => mapa.setView([v.posicion.lat, v.posicion.lng], 16) }, ...contenido)
        : el('div', { class: 'item-flota' }, ...contenido));
    }) : [el('li', { class: 'secundario' }, 'No hay colectivos en esta línea.')]));
    if (conPosicion.length && !pintar.centrado) {
      mapa.fitBounds(conPosicion, { maxZoom: 15, padding: [40, 40] });
      pintar.centrado = true;
    }
  }

  async function cargar() {
    try {
      flota = await api.get('/api/admin/flota');
      const lineas = [...new Set(flota.map((v) => v.linea))].sort();
      if (selLinea.options.length !== lineas.length + 1) {
        const actual = selLinea.value;
        selLinea.replaceChildren(el('option', { value: '' }, 'Todas'), ...lineas.map((l) => el('option', { value: l }, `Línea ${l}`)));
        selLinea.value = actual;
      }
      pintar();
      estado.replaceChildren(flota.some((v) => v.en_servicio) ? '' : mensaje('info', 'Ningún colectivo está en servicio ahora.'));
    } catch (err) {
      estado.replaceChildren(mensaje('error', err.message));
    }
  }
  selLinea.addEventListener('change', () => { pintar.centrado = false; pintar(); });

  await cargar();
  const intervalo = setInterval(cargar, SEGUNDOS_REFRESCO * 1000);
  addEventListener('hashchange', () => { clearInterval(intervalo); mapa.remove(); }, { once: true });
}
