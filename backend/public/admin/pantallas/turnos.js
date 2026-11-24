// Admin 4 · Programación de salidas y turnos: semana por recorrido; crear y borrar turnos.
import { el, fechaCl, icono, mensaje } from '../ui.js';
import { choqueCon } from './asignar.js';

const DIAS = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];
const RAPIDOS = { 'Mañana 06:00–14:00': ['06:00', '14:00'], 'Tarde 14:00–22:00': ['14:00', '22:00'] };

/** "2026-10-26" ± días, sin pasar por zonas horarias. */
function sumarDias(iso, n) {
  const d = new Date(`${iso}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}
const isoLocal = (f) => `${f.getFullYear()}-${String(f.getMonth() + 1).padStart(2, '0')}-${String(f.getDate()).padStart(2, '0')}`;
const lunesDe = (fecha) => sumarDias(isoLocal(fecha), -((fecha.getDay() + 6) % 7));
const claseTurno = (t) => (t.inicio === '06:00' && t.fin === '14:00' ? 'turno-manana' : t.inicio === '14:00' && t.fin === '22:00' ? 'turno-tarde' : 'turno-otro');

export async function mostrar(raiz, { api }) {
  const [conductores, autos] = await Promise.all([api.get('/api/conductores?estado=APROBADA'), api.get('/api/automoviles')]);
  const recorridos = [...new Map(autos.filter((a) => a.ruta_folio).map((a) => [a.ruta_folio, a.linea])).entries()]
    .sort((a, b) => a[1].localeCompare(b[1]));
  let folio = recorridos[0]?.[0] ?? '';
  let lunes = lunesDe(new Date());
  let turnos = [];

  const selRecorrido = el('select', { id: 'semana-recorrido' }, recorridos.map(([f, linea]) => el('option', { value: f }, `Línea ${linea}`)));
  const titulo = el('strong', { class: 'semana-titulo', 'aria-live': 'polite' });
  const rejilla = el('div', { class: 'tabla-contenedor' });
  const aviso = el('div', { 'aria-live': 'polite' });
  const dialogo = el('dialog', { class: 'dialogo', 'aria-labelledby': 'dialogo-titulo' });

  async function cargar() {
    rejilla.setAttribute('aria-busy', 'true');
    turnos = await api.get(`/api/admin/turnos?desde=${lunes}&dias=7&folio=${folio}`);
    rejilla.removeAttribute('aria-busy');
    pintar();
  }

  function pintar() {
    const domingo = sumarDias(lunes, 6);
    titulo.textContent = `Semana ${fechaCl(lunes).slice(0, 5)} al ${fechaCl(domingo)}`;
    const fechas = DIAS.map((_, i) => sumarDias(lunes, i));
    const patentes = new Set(autos.filter((a) => a.ruta_folio === folio).map((a) => a.patente));
    // Filas: conductores vinculados a un vehículo del recorrido y los que tienen turnos en él esta semana.
    const filas = conductores.filter((c) => patentes.has(c.patente) || turnos.some((t) => t.conductor_id === c.id));
    if (!filas.length) {
      rejilla.replaceChildren(el('p', { class: 'secundario', role: 'status' }, 'Ningún conductor trabaja en este recorrido. Vincula uno en «Validar conductores» o «Automóviles».'));
      return;
    }
    rejilla.replaceChildren(el('table', { class: 'tabla tabla-semana' },
      el('thead', {}, el('tr', {}, el('th', { scope: 'col' }, 'Conductor'),
        ...fechas.map((f, i) => el('th', { scope: 'col' }, `${DIAS[i]} ${f.slice(8)}`)))),
      el('tbody', {}, ...filas.map((c) => el('tr', {},
        el('th', { scope: 'row' }, el('span', { class: 'fuerte' }, c.nombre_completo), el('span', { class: 'secundario patente' }, c.patente ?? 'Sin vehículo')),
        ...fechas.map((f) => celda(c, f)))))));
  }

  function celda(c, fecha) {
    const delDia = turnos.filter((t) => t.conductor_id === c.id && t.fecha === fecha);
    if (!delDia.length && fecha < isoLocal(new Date())) {
      return el('td', {}, el('span', { class: 'turno turno-libre turno-pasado' }, 'Libre')); // no se asignan días pasados
    }
    if (!delDia.length) {
      return el('td', {}, el('button', {
        type: 'button', class: 'turno turno-libre', 'aria-label': `${c.nombre_completo}, ${fechaCl(fecha)}: libre. Asignar turno`,
        onclick: () => abrirNuevo({ conductor: c, fecha }),
      }, 'Libre'));
    }
    return el('td', {}, el('div', { class: 'turnos-dia' }, delDia.map((t) => el('button', {
      type: 'button', class: `turno ${claseTurno(t)}`, 'aria-label': `${c.nombre_completo}, ${fechaCl(fecha)}: ${t.inicio} a ${t.fin} en ${t.patente}`,
      onclick: () => abrirTurno(t),
    }, `${t.inicio}–${t.fin}`))));
  }

  // ---------- Diálogos ----------
  function cerrar() { dialogo.close(); }

  function abrirTurno(t) {
    const salida = el('div', { 'aria-live': 'polite' });
    const borrar = el('button', { type: 'button', class: 'boton boton-peligro' }, 'Borrar turno');
    borrar.addEventListener('click', async () => {
      borrar.disabled = true;
      try {
        await api.delete(`/api/admin/turnos/${t.id}`);
        turnos = turnos.filter((x) => x.id !== t.id);
        cerrar();
        aviso.replaceChildren(mensaje('info', `Turno borrado: ${t.conductor}, ${fechaCl(t.fecha)} de ${t.inicio} a ${t.fin}.`));
        pintar();
      } catch (err) {
        salida.replaceChildren(mensaje('error', err.message));
        borrar.disabled = false;
      }
    });
    dialogo.replaceChildren(
      el('h2', { id: 'dialogo-titulo' }, `Turno de ${t.conductor}`),
      el('div', { class: 'datos datos-2' },
        el('div', { class: 'dato' }, el('span', {}, 'Fecha'), el('strong', {}, fechaCl(t.fecha))),
        el('div', { class: 'dato' }, el('span', {}, 'Horario'), el('strong', {}, `${t.inicio}–${t.fin}`)),
        el('div', { class: 'dato' }, el('span', {}, 'Vehículo'), el('strong', {}, t.patente)),
        el('div', { class: 'dato' }, el('span', {}, 'Recorrido'), el('strong', {}, `Línea ${t.linea}`))),
      salida,
      el('div', { class: 'acciones acciones-dialogo' },
        el('button', { type: 'button', class: 'boton boton-contorno', onclick: cerrar }, 'Cerrar'), borrar));
    dialogo.showModal();
  }

  function abrirNuevo({ conductor, fecha } = {}) {
    const delRecorrido = autos.filter((a) => a.ruta_folio === folio);
    const selConductor = el('select', { id: 'turno-conductor' }, el('option', { value: '' }, 'Elige un conductor'),
      conductores.map((c) => el('option', { value: c.id, selected: c === conductor }, c.nombre_completo)));
    const selVehiculo = el('select', { id: 'turno-vehiculo' }, el('option', { value: '' }, 'Elige un vehículo'),
      delRecorrido.map((a) => el('option', { value: a.id, selected: a.patente === conductor?.patente }, a.patente)));
    const inFecha = el('input', { id: 'turno-fecha', type: 'date', value: fecha ?? lunes });
    const desde = el('input', { id: 'turno-desde', type: 'time', value: '06:00' });
    const hasta = el('input', { id: 'turno-hasta', type: 'time', value: '14:00' });
    const salida = el('div', { 'aria-live': 'polite' });
    const guardar = el('button', { type: 'submit', class: 'boton boton-principal' }, 'Guardar turno');

    const nuevo = () => ({ conductor_id: Number(selConductor.value), automovil_id: Number(selVehiculo.value), ruta_folio: folio,
      fecha: inFecha.value, inicio: desde.value, fin: hasta.value });
    function revisar() {
      const n = nuevo();
      const completo = n.conductor_id && n.automovil_id && n.fecha && n.inicio && n.fin;
      const problema = completo && n.fin <= n.inicio ? 'La hora «Hasta» debe ser posterior a «Desde».' : null;
      const choque = completo && !problema ? choqueCon(turnos, n) : null;
      salida.replaceChildren(choque ? el('div', { class: 'mensaje mensaje-error', role: 'alert' }, icono('alerta'),
        el('span', {}, el('strong', {}, 'Choque de horario: '), choque)) : problema ? mensaje('error', problema) : '');
      guardar.disabled = !completo || Boolean(choque || problema);
    }
    selConductor.addEventListener('change', () => {
      const c = conductores.find((x) => String(x.id) === selConductor.value);
      const vinculado = delRecorrido.find((a) => a.patente === c?.patente);
      if (vinculado) selVehiculo.value = String(vinculado.id);
      revisar();
    });
    for (const campo of [selVehiculo, inFecha, desde, hasta]) campo.addEventListener('input', revisar);

    const form = el('form', { method: 'dialog', novalidate: true },
      el('h2', { id: 'dialogo-titulo' }, 'Nuevo turno'),
      el('p', { class: 'secundario' }, `Recorrido: Línea ${recorridos.find(([f]) => f === folio)?.[1] ?? ''}`),
      el('label', { for: 'turno-conductor' }, 'Conductor'), selConductor,
      el('label', { for: 'turno-vehiculo' }, 'Vehículo'), selVehiculo,
      el('label', { for: 'turno-fecha' }, 'Fecha'), inFecha,
      el('fieldset', { class: 'rapidos' }, el('legend', {}, 'Horario'),
        Object.entries(RAPIDOS).map(([texto, [i, f]]) => el('button', {
          type: 'button', class: 'boton boton-contorno', onclick: () => { desde.value = i; hasta.value = f; revisar(); },
        }, texto))),
      el('div', { class: 'datos datos-2' },
        el('div', {}, el('label', { for: 'turno-desde' }, 'Desde'), desde),
        el('div', {}, el('label', { for: 'turno-hasta' }, 'Hasta'), hasta)),
      salida,
      el('div', { class: 'acciones acciones-dialogo' },
        el('button', { type: 'button', class: 'boton boton-contorno', onclick: cerrar }, 'Cancelar'), guardar));
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const n = nuevo();
      guardar.disabled = true;
      try {
        const creado = await api.post('/api/admin/turnos', { ...n, hora_inicio: n.inicio, hora_fin: n.fin });
        if (creado.fecha >= lunes && creado.fecha <= sumarDias(lunes, 6)) turnos.push(creado);
        cerrar();
        aviso.replaceChildren(mensaje('info', `Turno asignado: ${creado.conductor}, ${fechaCl(creado.fecha)} de ${creado.inicio} a ${creado.fin}.`));
        pintar();
      } catch (err) {
        salida.replaceChildren(mensaje('error', err.message));
        guardar.disabled = false;
      }
    });
    dialogo.replaceChildren(form);
    revisar();
    dialogo.showModal();
    selConductor.focus();
  }

  // ---------- Barra superior ----------
  selRecorrido.addEventListener('change', () => { folio = selRecorrido.value; aviso.replaceChildren(); cargar(); });
  const mover = (n) => () => { lunes = sumarDias(lunes, 7 * n); aviso.replaceChildren(); cargar(); };
  const barra = el('div', { class: 'barra-semana' },
    el('div', {}, el('label', { for: 'semana-recorrido' }, 'Recorrido'), selRecorrido),
    el('div', { class: 'navegar-semana' },
      el('button', { type: 'button', class: 'boton-circular', 'aria-label': 'Semana anterior', onclick: mover(-1) }, '‹'),
      titulo,
      el('button', { type: 'button', class: 'boton-circular', 'aria-label': 'Semana siguiente', onclick: mover(1) }, '›')),
    el('button', { type: 'button', class: 'boton boton-principal boton-nuevo', onclick: () => abrirNuevo() }, '+ Nuevo turno'));

  const leyenda = el('ul', { class: 'leyenda' },
    el('li', {}, el('span', { class: 'muestra turno-manana', 'aria-hidden': 'true' }), 'Mañana 06:00–14:00'),
    el('li', {}, el('span', { class: 'muestra turno-tarde', 'aria-hidden': 'true' }), 'Tarde 14:00–22:00'),
    el('li', {}, el('span', { class: 'muestra turno-otro', 'aria-hidden': 'true' }), 'Otro horario'),
    el('li', {}, el('span', { class: 'muestra turno-libre', 'aria-hidden': 'true' }), 'Libre'));

  raiz.replaceChildren(barra, aviso, el('section', { class: 'tarjeta', 'aria-label': 'Turnos de la semana' }, rejilla, leyenda), dialogo);
  if (folio) await cargar(); else rejilla.replaceChildren(el('p', { class: 'secundario' }, 'No hay recorridos con vehículos asignados.'));
}
