// Mantenedor de conductores (protocolo: crear, leer, modificar y eliminar). Sin vista en Figma; estilo del panel.
import { campo, chipSemaforo, el, fechaCl, mensaje } from '../ui.js';

const ESTADOS = { APROBADA: ['Aprobado', 'insignia-verde'], PENDIENTE: ['Pendiente', 'insignia-ambar'], RECHAZADA: ['Rechazado', 'insignia-gris'] };

export async function mostrar(raiz, { api }) {
  let conductores = await api.get('/api/conductores');
  const buscar = el('input', { id: 'buscar-conductor', type: 'search', placeholder: 'Nombre o RUT' });
  const cuerpo = el('tbody');
  const estado = el('div', { 'aria-live': 'polite' });
  const dialogo = el('dialog', { class: 'dialogo dialogo-ancho', 'aria-labelledby': 'dialogo-titulo' });

  async function recargar(texto) {
    conductores = await api.get('/api/conductores');
    pintar();
    if (texto) estado.replaceChildren(mensaje('info', texto));
  }

  function pintar() {
    const q = buscar.value.trim().toLowerCase().replace(/[.\s]/g, '');
    const filas = conductores.filter((c) => !q || c.nombre_completo.toLowerCase().replace(/\s/g, '').includes(q) || c.rut.toLowerCase().includes(q));
    cuerpo.replaceChildren(...(filas.length ? filas.map((c) => {
      const editar = el('button', { type: 'button', class: 'boton boton-contorno boton-chico', 'aria-label': `Editar a ${c.nombre_completo}` }, 'Editar');
      editar.addEventListener('click', () => abrir(c));
      const eliminar = el('button', { type: 'button', class: 'boton boton-peligro boton-chico', 'aria-label': `Eliminar a ${c.nombre_completo}` }, 'Eliminar');
      eliminar.addEventListener('click', async () => {
        if (!confirm(`¿Eliminar a ${c.nombre_completo} (${c.rut})${c.email ? ` y su cuenta ${c.email}` : ''}? Esta acción no se puede deshacer.`)) return;
        eliminar.disabled = true;
        try {
          await api.delete(`/api/conductores/${c.id}`);
          await recargar(`${c.nombre_completo} fue eliminado.`);
        } catch (err) {
          estado.replaceChildren(mensaje('error', err.message));
          eliminar.disabled = false;
        }
      });
      const [txt, clase] = ESTADOS[c.estado];
      return el('tr', {},
        el('td', {}, el('div', { class: 'usuario-celda' }, el('span', { class: 'fuerte' }, c.nombre_completo), el('span', { class: 'secundario' }, c.email ?? 'Sin cuenta en la app'))),
        el('td', {}, c.rut),
        el('td', {}, el('div', { class: 'usuario-celda' }, el('span', {}, `${c.tipo_licencia} · vence ${fechaCl(c.fecha_vencimiento_licencia)}`), chipSemaforo(c.semaforo))),
        el('td', {}, el('span', { class: `insignia ${clase}` }, txt)),
        el('td', {}, c.patente ?? '—'),
        el('td', {}, el('div', { class: 'acciones acciones-fila' }, editar, eliminar)));
    }) : [el('tr', {}, el('td', { colspan: 6, class: 'vacio' }, 'Ningún conductor coincide con la búsqueda.'))]));
  }
  buscar.addEventListener('input', pintar);

  /** Formulario para crear (c = undefined) o editar. */
  function abrir(c) {
    const v = (k) => c?.[k] ?? '';
    const entrada = (id, valor, extra = {}) => el('input', { id, value: valor, ...extra });
    const f = {
      nombre: entrada('c-nombre', v('nombre_completo'), { required: true, autocomplete: 'off' }),
      rut: entrada('c-rut', v('rut'), { required: true, placeholder: '12.345.678-5' }),
      direccion: entrada('c-direccion', v('direccion'), { required: true }),
      comuna: entrada('c-comuna', v('comuna')),
      celular: entrada('c-celular', v('fono_celular'), { type: 'tel', required: true }),
      fijo: entrada('c-fijo', v('fono_domicilio'), { type: 'tel' }),
      emergencia: entrada('c-emergencia', v('fono_emergencia'), { type: 'tel' }),
      incorporacion: entrada('c-incorporacion', v('fecha_incorporacion'), { type: 'date' }),
      clase: el('select', { id: 'c-clase' }, ['A2', 'A3'].map((x) => el('option', { value: x, selected: v('tipo_licencia') === x }, x))),
      municipio: entrada('c-municipio', v('municipio_licencia'), { required: true }),
      obtencion: entrada('c-obtencion', v('fecha_obtencion_licencia'), { type: 'date', required: true }),
      duracion: entrada('c-duracion', v('duracion_licencia_anos'), { type: 'number', min: 1, max: 8, step: 1, inputmode: 'numeric' }),
      vencimiento: entrada('c-vencimiento', v('fecha_vencimiento_licencia'), { type: 'date', required: true }),
      correo: entrada('c-correo', '', { type: 'email', autocomplete: 'off' }),
      clave: entrada('c-clave', '', { type: 'password', autocomplete: 'new-password' }),
    };
    const salida = el('div', { 'aria-live': 'polite' });
    const guardar = el('button', { type: 'submit', class: 'boton boton-principal' }, c ? 'Guardar cambios' : 'Crear conductor');
    const form = el('form', { method: 'dialog', novalidate: true },
      el('h2', { id: 'dialogo-titulo' }, c ? `Editar a ${c.nombre_completo}` : 'Nuevo conductor'),
      el('h3', {}, 'Datos personales'),
      el('div', { class: 'rejilla-form' },
        campo('c-nombre', 'Nombre completo', f.nombre), campo('c-rut', 'RUT', f.rut),
        campo('c-direccion', 'Dirección', f.direccion), campo('c-comuna', 'Comuna', f.comuna),
        campo('c-celular', 'Celular', f.celular), campo('c-fijo', 'Teléfono de domicilio (opcional)', f.fijo),
        campo('c-emergencia', 'Teléfono de emergencia', f.emergencia), campo('c-incorporacion', 'Fecha de incorporación', f.incorporacion, c ? null : 'Vacío: hoy')),
      el('h3', {}, 'Licencia de conducir'),
      el('div', { class: 'rejilla-form' },
        campo('c-clase', 'Clase', f.clase), campo('c-municipio', 'Municipalidad que la otorgó', f.municipio),
        campo('c-obtencion', 'Fecha de obtención', f.obtencion), campo('c-duracion', 'Duración (años)', f.duracion),
        campo('c-vencimiento', 'Fecha de vencimiento', f.vencimiento)),
      c ? null : el('h3', {}, 'Cuenta para la app (opcional)'),
      c ? null : el('div', { class: 'rejilla-form' },
        campo('c-correo', 'Correo', f.correo), campo('c-clave', 'Contraseña', f.clave, 'De 15 a 128 caracteres')),
      salida,
      el('div', { class: 'acciones acciones-dialogo' },
        el('button', { type: 'button', class: 'boton boton-contorno', onclick: () => dialogo.close() }, 'Cancelar'), guardar));
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      guardar.disabled = true;
      const datos = {
        nombre_completo: f.nombre.value, rut: f.rut.value, direccion: f.direccion.value, comuna: f.comuna.value,
        fono_celular: f.celular.value, fono_domicilio: f.fijo.value, fono_emergencia: f.emergencia.value,
        fecha_incorporacion: f.incorporacion.value, tipo_licencia: f.clase.value, municipio_licencia: f.municipio.value,
        fecha_obtencion_licencia: f.obtencion.value, duracion_licencia_anos: f.duracion.value,
        fecha_vencimiento_licencia: f.vencimiento.value,
      };
      if (!c && f.correo.value) Object.assign(datos, { email: f.correo.value, password: f.clave.value });
      try {
        const r = c ? await api.put(`/api/conductores/${c.id}`, datos) : await api.post('/api/conductores', datos);
        dialogo.close();
        await recargar(c ? `Se guardaron los cambios de ${r.nombre_completo}.` : `${r.nombre_completo} quedó registrado y aprobado.`);
      } catch (err) {
        salida.replaceChildren(mensaje('error', err.message));
        guardar.disabled = false;
      }
    });
    dialogo.replaceChildren(form);
    dialogo.showModal();
    f.nombre.focus();
  }

  pintar();
  raiz.replaceChildren(estado, el('section', { class: 'tarjeta', 'aria-labelledby': 'titulo-conductores' },
    el('div', { class: 'fila-titulo fila-busqueda' },
      el('h2', { id: 'titulo-conductores' }, 'Conductores'),
      el('div', { class: 'filtros-cuentas' },
        el('div', { class: 'campo-busqueda' }, el('label', { for: 'buscar-conductor' }, 'Buscar'), buscar),
        el('button', { type: 'button', class: 'boton boton-principal boton-nuevo-fila', onclick: () => abrir() }, '+ Nuevo conductor'))),
    el('div', { class: 'tabla-contenedor' }, el('table', { class: 'tabla' },
      el('thead', {}, el('tr', {}, ['Conductor', 'RUT', 'Licencia', 'Estado', 'Vehículo', 'Acciones'].map((h) => el('th', { scope: 'col' }, h)))),
      cuerpo))), dialogo);
}
