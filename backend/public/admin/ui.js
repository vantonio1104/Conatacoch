// Piezas comunes del panel. Todo el texto entra con textContent: los datos del servidor nunca se tratan como HTML.

/** el('div', { class: 'x', onclick: fn }, 'texto', otroNodo) */
export function el(tag, atributos = {}, ...hijos) {
  const n = document.createElement(tag);
  for (const [k, v] of Object.entries(atributos)) {
    if (v === undefined || v === null || v === false) continue;
    if (k.startsWith('on')) n.addEventListener(k.slice(2), v);
    else if (k === 'class') n.className = v;
    else n.setAttribute(k, v === true ? '' : v);
  }
  for (const h of hijos.flat()) {
    if (h === null || h === undefined || h === false) continue;
    n.append(h instanceof Node ? h : document.createTextNode(String(h)));
  }
  return n;
}

const SVG = 'http://www.w3.org/2000/svg';
const ICONOS = {
  validar: ['M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2', 'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8', 'm16 11 2 2 4-4'],
  asignar: ['M6 3a3 3 0 1 0 0 6 3 3 0 0 0 0-6', 'M6 9v3a4 4 0 0 0 4 4h4a4 4 0 0 1 4 4v1', 'M18 15a3 3 0 1 0 0 6'],
  tarifas: ['M12 2v20', 'M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6'],
  turnos: ['M8 2v4', 'M16 2v4', 'M3 10h18', 'M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2'],
  avisos: ['M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9', 'M10.3 21a1.94 1.94 0 0 0 3.4 0'],
  cuentas: ['M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2', 'M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8'],
  inicio: ['m3 10 9-7 9 7v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z', 'M9 22V12h6v10'],
  conductores: ['M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2', 'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8', 'M22 21v-2a4 4 0 0 0-3-3.87', 'M16 3.13a4 4 0 0 1 0 7.75'],
  automoviles: ['M5 17h14v-5l-2-5H7l-2 5z', 'M5 12h14', 'M7 17v2', 'M17 17v2'],
  reportes: ['M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z', 'M14 2v6h6', 'M8 13h8', 'M8 17h8'],
  mapa: ['m3 6 6-3 6 3 6-3v15l-6 3-6-3-6 3z', 'M9 3v15', 'M15 6v15'],
  salir: ['M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4', 'm16 17 5-5-5-5', 'M21 12H9'],
  luna: ['M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9'],
  escudo: ['M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10', 'm9 12 2 2 4-4'],
  reloj: ['M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20', 'M12 6v6l4 2'],
  alerta: ['m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3', 'M12 9v4', 'M12 17h.01'],
  info: ['M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20', 'M12 16v-4', 'M12 8h.01'],
};

/** Icono decorativo (aria-hidden): siempre va junto a un texto que lo explica. */
export function icono(nombre) {
  const s = document.createElementNS(SVG, 'svg');
  for (const [k, v] of Object.entries({ viewBox: '0 0 24 24', width: 22, height: 22, fill: 'none', stroke: 'currentColor',
    'stroke-width': 2, 'stroke-linecap': 'round', 'stroke-linejoin': 'round', 'aria-hidden': 'true', class: 'icono' })) {
    s.setAttribute(k, v);
  }
  for (const d of ICONOS[nombre] ?? []) {
    const p = document.createElementNS(SVG, 'path');
    p.setAttribute('d', d);
    s.append(p);
  }
  return s;
}

/** "2026-11-19" → "19-11-2026". */
export const fechaCl = (iso) => (iso ? `${iso.slice(8, 10)}-${iso.slice(5, 7)}-${iso.slice(0, 4)}` : '—');

/** Mensaje en la pantalla (error, aviso o información). */
export function mensaje(tipo, texto) {
  return el('p', { class: `mensaje mensaje-${tipo}`, role: tipo === 'error' ? 'alert' : 'status' }, icono(tipo === 'info' ? 'info' : 'alerta'), el('span', {}, texto));
}

/** Etiqueta + control, para formularios. */
export const campo = (id, etiqueta, control, ayuda) => el('div', { class: 'campo' },
  el('label', { for: id }, etiqueta), control, ayuda ? el('p', { class: 'ayuda' }, ayuda) : null);

const SEMAFORO = {
  VIGENTE: ['Vigente', 'insignia-verde'], POR_VENCER: ['Por vencer', 'insignia-ambar'],
  VENCIDO: ['Vencido', 'insignia-roja'], SIN_DATOS: ['Sin datos', 'insignia-gris'],
};
/** Estado de vigencia escrito (no solo color). */
export const chipSemaforo = (estado) => el('span', { class: `insignia ${SEMAFORO[estado]?.[1] ?? 'insignia-gris'}` }, SEMAFORO[estado]?.[0] ?? estado);
