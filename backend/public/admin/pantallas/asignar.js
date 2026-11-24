// Admin 3 · Asignación de rutas: turnos con fecha y horario, con aviso de choque antes de guardar.
import { el, fechaCl, icono, mensaje } from '../ui.js';

const ESTADOS = {
  PROGRAMADA: ['Programada', 'insignia-azul'],
  ACTIVA: ['Activa', 'insignia-verde'],
  FINALIZADA: ['Finalizada', 'insignia-gris'],
};
export const chipEstado = (estado) => el('span', { class: `insignia ${ESTADOS[estado][1]}` }, ESTADOS[estado][0]);

const seCruzan = (a, b) => a.inicio < b.fin && b.inicio < a.fin;

/** Turno existente que choca con el nuevo (mismo conductor o mismo vehículo, el mismo día). */
export function choqueCon(turnos, nuevo) {
  const mismoDia = turnos.filter((t) => t.fecha === nuevo.fecha && seCruzan(t, nuevo));
  const conductor = mismoDia.find((t) => t.conductor_id === nuevo.conductor_id);
  if (conductor) return `${conductor.conductor} ya tiene asignada la línea ${conductor.linea} el ${fechaCl(nuevo.fecha)} de ${conductor.inicio} a ${conductor.fin}.`;
  const vehiculo = mismoDia.find((t) => t.automovil_id === nuevo.automovil_id);
  if (vehiculo) return `el vehículo ${vehiculo.patente} ya está asignado a ${vehiculo.conductor} el ${fechaCl(nuevo.fecha)} de ${vehiculo.inicio} a ${vehiculo.fin}.`;
  return null;
}

export async function mostrar(raiz, { api }) {
  const [turnos, conductores, autos] = await Promise.all([
    api.get('/api/admin/turnos'),
    api.get('/api/conductores?estado=APROBADA'),
    api.get('/api/automoviles'),
  ]);
  const conRecorrido = autos.filter((a) => a.ruta_folio);

  // ---------- Tabla de asignaciones ----------
  const buscar = el('input', { id: 'buscar-asignacion', type: 'search', placeholder: 'Ej: ABCD-12' });
  const cuerpo = el('tbody');
  function pintarTabla() {
    const q = buscar.value.trim().toLowerCase();
    const filas = turnos.filter((t) => !q || t.patente.toLowerCase().includes(q) || t.conductor.toLowerCase().includes(q));
    cuerpo.replaceChildren(...(filas.length ? filas.map((t) => el('tr', {},
      el('td', { class: 'fuerte' }, t.conductor), el('td', {}, t.patente), el('td', {}, `Línea ${t.linea}`),
      el('td', {}, fechaCl(t.fecha)), el('td', {}, `${t.inicio}–${t.fin}`), el('td', {}, chipEstado(t.estado))))
      : [el('tr', {}, el('td', { colspan: 6, class: 'vacio' }, q ? 'Ninguna asignación coincide con la búsqueda.' : 'Aún no hay asignaciones.'))]));
  }
  buscar.addEventListener('input', pintarTabla);
  const tabla = el('section', { class: 'tarjeta', 'aria-labelledby': 'titulo-vigentes' },
    el('div', { class: 'fila-titulo fila-busqueda' },
      el('h2', { id: 'titulo-vigentes' }, 'Asignaciones vigentes'),
      el('div', { class: 'campo-busqueda' }, el('label', { for: 'buscar-asignacion' }, 'Buscar por patente o conductor'), buscar)),
    el('div', { class: 'tabla-contenedor' }, el('table', { class: 'tabla' },
      el('thead', {}, el('tr', {}, ['Conductor', 'Vehículo', 'Recorrido', 'Fecha', 'Horario', 'Estado'].map((h) => el('th', { scope: 'col' }, h)))),
      cuerpo)));

  // ---------- Formulario: nueva asignación ----------
  const selConductor = el('select', { id: 'nuevo-conductor' }, el('option', { value: '' }, 'Elige un conductor'),
    conductores.map((c) => el('option', { value: c.id }, c.nombre_completo)));
  const selVehiculo = el('select', { id: 'nuevo-vehiculo' }, el('option', { value: '' }, 'Elige un vehículo'),
    conRecorrido.map((a) => el('option', { value: a.id }, a.patente)));
  const selRecorrido = el('select', { id: 'nuevo-recorrido', disabled: true }, el('option', { value: '' }, 'Según el vehículo'));
  const fecha = el('input', { id: 'nueva-fecha', type: 'date' });
  const desde = el('input', { id: 'nuevo-desde', type: 'time', value: '06:00' });
  const hasta = el('input', { id: 'nuevo-hasta', type: 'time', value: '14:00' });
  const alerta = el('div', { 'aria-live': 'polite' });
  const boton = el('button', { type: 'submit', class: 'boton boton-principal boton-ancho' }, 'Asignar ruta');

  const auto = () => conRecorrido.find((a) => String(a.id) === selVehiculo.value);
  const nuevo = () => ({
    conductor_id: Number(selConductor.value), automovil_id: Number(selVehiculo.value), ruta_folio: auto()?.ruta_folio,
    fecha: fecha.value, inicio: desde.value, fin: hasta.value,
  });

  function revisar() {
    const a = auto();
    selRecorrido.replaceChildren(el('option', { value: a?.ruta_folio ?? '' }, a ? `Línea ${a.linea}` : 'Según el vehículo'));
    const n = nuevo();
    const completo = n.conductor_id && n.automovil_id && n.fecha && n.inicio && n.fin;
    let problema = null;
    if (completo && n.fin <= n.inicio) problema = 'La hora «Hasta» debe ser posterior a «Desde».';
    const choque = completo && !problema ? choqueCon(turnos, n) : null;
    for (const campo of [desde, hasta]) campo.setAttribute('aria-invalid', String(Boolean(choque || problema)));
    alerta.replaceChildren(choque ? el('div', { class: 'mensaje mensaje-error', role: 'alert' }, icono('alerta'),
      el('span', {}, el('strong', {}, 'Choque de horario: '), choque)) : problema ? mensaje('error', problema) : '');
    boton.disabled = !completo || Boolean(choque || problema);
  }
  selConductor.addEventListener('change', () => {
    // Propone el vehículo que el conductor tiene vinculado.
    const c = conductores.find((x) => String(x.id) === selConductor.value);
    const vinculado = conRecorrido.find((a) => a.patente === c?.patente);
    if (vinculado) selVehiculo.value = String(vinculado.id);
    revisar();
  });
  for (const campo of [selVehiculo, fecha, desde, hasta]) campo.addEventListener('input', revisar);

  const form = el('form', { class: 'tarjeta', 'aria-labelledby': 'titulo-nueva', novalidate: true },
    el('h2', { id: 'titulo-nueva' }, 'Nueva asignación'),
    el('label', { for: 'nuevo-conductor' }, 'Conductor'), selConductor, el('p', { class: 'ayuda' }, 'Solo conductores aprobados'),
    el('label', { for: 'nuevo-vehiculo' }, 'Vehículo'), selVehiculo,
    el('label', { for: 'nuevo-recorrido' }, 'Recorrido'), selRecorrido,
    el('label', { for: 'nueva-fecha' }, 'Fecha'), fecha,
    el('label', { for: 'nuevo-desde' }, 'Desde'), desde,
    el('label', { for: 'nuevo-hasta' }, 'Hasta'), hasta,
    alerta, boton);
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const n = nuevo();
    boton.disabled = true;
    try {
      const creado = await api.post('/api/admin/turnos', { ...n, hora_inicio: n.inicio, hora_fin: n.fin });
      turnos.push(creado);
      turnos.sort((a, b) => (a.fecha + a.inicio).localeCompare(b.fecha + b.inicio));
      pintarTabla();
      alerta.replaceChildren(mensaje('info', `Turno asignado: ${creado.conductor}, ${creado.patente}, ${fechaCl(creado.fecha)} de ${creado.inicio} a ${creado.fin}.`));
    } catch (err) {
      alerta.replaceChildren(mensaje('error', err.message));
    } finally {
      boton.disabled = false;
    }
  });

  pintarTabla();
  revisar();
  raiz.replaceChildren(el('div', { class: 'dos-columnas dos-columnas-derecha' }, tabla, form));
}
