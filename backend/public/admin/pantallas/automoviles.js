// Mantenedor de automóviles con sus documentos (permiso de circulación, revisión técnica y seguro obligatorio).
// Sin vista en Figma; estilo del panel. «Dar de baja» no borra el historial.
import { campo, chipSemaforo, el, fechaCl, mensaje } from '../ui.js';

const COMBUSTIBLES = ['bencina', 'diésel', 'eléctrico', 'híbrido', 'gas'];

export async function mostrar(raiz, { api }) {
  let autos;
  const [rutas, conductores] = await Promise.all([api.get('/api/rutas'), api.get('/api/conductores?estado=APROBADA')]);
  const buscar = el('input', { id: 'buscar-auto', type: 'search', placeholder: 'Patente, línea o conductor' });
  const cuerpo = el('tbody');
  const estado = el('div', { 'aria-live': 'polite' });
  const dialogo = el('dialog', { class: 'dialogo dialogo-ancho', 'aria-labelledby': 'dialogo-titulo' });

  async function recargar(texto) {
    autos = await api.get('/api/automoviles');
    pintar();
    if (texto) estado.replaceChildren(mensaje('info', texto));
  }

  function pintar() {
    const q = buscar.value.trim().toLowerCase();
    const filas = autos.filter((a) => !q || [a.patente, a.linea, a.conductor].some((x) => (x ?? '').toLowerCase().includes(q)));
    cuerpo.replaceChildren(...(filas.length ? filas.map((a) => {
      const editar = el('button', { type: 'button', class: 'boton boton-contorno boton-chico', 'aria-label': `Editar ${a.patente}` }, 'Editar');
      editar.addEventListener('click', () => abrir(a));
      const baja = el('button', { type: 'button', class: 'boton boton-peligro boton-chico', 'aria-label': `Dar de baja ${a.patente}` }, 'Dar de baja');
      baja.addEventListener('click', async () => {
        if (!confirm(`¿Dar de baja ${a.patente}? Deja de operar, se cancelan sus reservas activas y sus turnos futuros. Su historial se conserva.`)) return;
        baja.disabled = true;
        try {
          await api.delete(`/api/automoviles/${a.id}`);
          await recargar(`${a.patente} quedó dado de baja.`);
        } catch (err) {
          estado.replaceChildren(mensaje('error', err.message));
          baja.disabled = false;
        }
      });
      return el('tr', {},
        el('td', { class: 'fuerte' }, a.patente),
        el('td', {}, `${a.marca} ${a.modelo} ${a.ano} · ${a.capacidad} asientos`),
        el('td', {}, a.linea ? `Línea ${a.linea}` : 'Sin recorrido'),
        el('td', {}, a.conductor ?? '—'),
        el('td', { class: 'celda-ajustable' }, el('div', { class: 'usuario-celda' }, chipSemaforo(a.semaforo),
          el('span', { class: 'secundario' }, `Permiso ${fechaCl(a.vencimiento_permiso)} · Revisión ${fechaCl(a.vencimiento_revision)} · Seguro ${fechaCl(a.vencimiento_seguro)}`))),
        el('td', {}, el('div', { class: 'acciones acciones-fila' }, editar, baja)));
    }) : [el('tr', {}, el('td', { colspan: 6, class: 'vacio' }, 'Ningún vehículo coincide con la búsqueda.'))]));
  }
  buscar.addEventListener('input', pintar);

  /** Formulario para crear (a = undefined) o editar. */
  function abrir(a) {
    const v = (k) => a?.[k] ?? '';
    const entrada = (id, valor, extra = {}) => el('input', { id, value: valor, ...extra });
    const f = {
      patente: entrada('a-patente', v('patente'), { required: true, placeholder: 'ABCD-12' }),
      marca: entrada('a-marca', v('marca'), { required: true }),
      modelo: entrada('a-modelo', v('modelo'), { required: true }),
      ano: entrada('a-ano', v('ano'), { type: 'number', min: 1990, step: 1, required: true, inputmode: 'numeric' }),
      combustible: el('select', { id: 'a-combustible' }, COMBUSTIBLES.map((x) => el('option', { value: x, selected: (v('combustible') || 'bencina') === x }, x))),
      capacidad: entrada('a-capacidad', v('capacidad') || 4, { type: 'number', min: 1, max: 6, step: 1, inputmode: 'numeric' }),
      folio: el('select', { id: 'a-folio' }, el('option', { value: '' }, 'Sin recorrido'),
        rutas.map((r) => el('option', { value: r.folio, selected: v('ruta_folio') === r.folio }, `Línea ${r.linea}`))),
      conductor: el('select', { id: 'a-conductor' }, el('option', { value: '' }, 'Sin conductor'),
        conductores.map((c) => el('option', { value: c.rut, selected: v('conductor_rut') === c.rut }, c.nombre_completo))),
      comunaPermiso: entrada('a-comuna-permiso', v('comuna_permiso')),
      otorgacion: entrada('a-otorgacion', v('otorgacion_permiso'), { type: 'date' }),
      permiso: entrada('a-permiso', v('vencimiento_permiso'), { type: 'date' }),
      planta: entrada('a-planta', v('planta_revision')),
      comunaRevision: entrada('a-comuna-revision', v('comuna_revision')),
      revision: entrada('a-revision', v('vencimiento_revision'), { type: 'date' }),
      compania: entrada('a-compania', v('compania_seguro')),
      prima: entrada('a-prima', v('prima_seguro'), { type: 'number', min: 1, step: 1, inputmode: 'numeric' }),
      seguro: entrada('a-seguro', v('vencimiento_seguro'), { type: 'date' }),
    };
    const salida = el('div', { 'aria-live': 'polite' });
    const guardar = el('button', { type: 'submit', class: 'boton boton-principal' }, a ? 'Guardar cambios' : 'Crear automóvil');
    const form = el('form', { method: 'dialog', novalidate: true },
      el('h2', { id: 'dialogo-titulo' }, a ? `Editar ${a.patente}` : 'Nuevo automóvil'),
      el('h3', {}, 'Vehículo'),
      el('div', { class: 'rejilla-form' },
        campo('a-patente', 'Patente', f.patente), campo('a-marca', 'Marca', f.marca), campo('a-modelo', 'Modelo', f.modelo),
        campo('a-ano', 'Año', f.ano), campo('a-combustible', 'Combustible', f.combustible), campo('a-capacidad', 'Asientos para pasajeros', f.capacidad)),
      el('h3', {}, 'Asignación'),
      el('div', { class: 'rejilla-form' }, campo('a-folio', 'Recorrido', f.folio), campo('a-conductor', 'Conductor', f.conductor)),
      el('h3', {}, 'Permiso de circulación'),
      el('div', { class: 'rejilla-form' }, campo('a-comuna-permiso', 'Comuna', f.comunaPermiso),
        campo('a-otorgacion', 'Fecha de otorgación', f.otorgacion), campo('a-permiso', 'Vencimiento', f.permiso)),
      el('h3', {}, 'Revisión técnica'),
      el('div', { class: 'rejilla-form' }, campo('a-planta', 'Planta', f.planta),
        campo('a-comuna-revision', 'Comuna', f.comunaRevision), campo('a-revision', 'Vencimiento', f.revision)),
      el('h3', {}, 'Seguro obligatorio (SOAP)'),
      el('div', { class: 'rejilla-form' }, campo('a-compania', 'Compañía', f.compania),
        campo('a-prima', 'Prima (CLP)', f.prima), campo('a-seguro', 'Vencimiento', f.seguro)),
      salida,
      el('div', { class: 'acciones acciones-dialogo' },
        el('button', { type: 'button', class: 'boton boton-contorno', onclick: () => dialogo.close() }, 'Cancelar'), guardar));
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      guardar.disabled = true;
      const datos = {
        patente: f.patente.value, marca: f.marca.value, modelo: f.modelo.value, ano: Number(f.ano.value),
        combustible: f.combustible.value, capacidad: Number(f.capacidad.value), ruta_folio: f.folio.value, conductor_rut: f.conductor.value,
        comuna_permiso: f.comunaPermiso.value, otorgacion_permiso: f.otorgacion.value, vencimiento_permiso: f.permiso.value,
        planta_revision: f.planta.value, comuna_revision: f.comunaRevision.value, vencimiento_revision: f.revision.value,
        compania_seguro: f.compania.value, prima_seguro: f.prima.value, vencimiento_seguro: f.seguro.value,
      };
      try {
        const r = a ? await api.put(`/api/automoviles/${a.id}`, datos) : await api.post('/api/automoviles', datos);
        dialogo.close();
        await recargar(a ? `Se guardaron los cambios de ${r.patente}.` : `${r.patente} quedó registrado.`);
      } catch (err) {
        salida.replaceChildren(mensaje('error', err.message));
        guardar.disabled = false;
      }
    });
    dialogo.replaceChildren(form);
    dialogo.showModal();
    f.patente.focus();
  }

  await recargar();
  raiz.replaceChildren(estado, el('section', { class: 'tarjeta', 'aria-labelledby': 'titulo-autos' },
    el('div', { class: 'fila-titulo fila-busqueda' },
      el('h2', { id: 'titulo-autos' }, 'Automóviles'),
      el('div', { class: 'filtros-cuentas' },
        el('div', { class: 'campo-busqueda' }, el('label', { for: 'buscar-auto' }, 'Buscar'), buscar),
        el('button', { type: 'button', class: 'boton boton-principal boton-nuevo-fila', onclick: () => abrir() }, '+ Nuevo automóvil'))),
    el('div', { class: 'tabla-contenedor' }, el('table', { class: 'tabla' },
      el('thead', {}, el('tr', {}, ['Patente', 'Vehículo', 'Recorrido', 'Conductor', 'Documentos', 'Acciones'].map((h) => el('th', { scope: 'col' }, h)))),
      cuerpo))), dialogo);
}
