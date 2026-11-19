// Admin 2 · Validación de conductores: revisar datos y licencia, y aprobar y vincular o rechazar.
import { el, fechaCl, mensaje } from '../ui.js';

const CLASES = { A2: 'A2 – Profesional', A3: 'A3 – Profesional' };

const dato = (etiqueta, valor) => el('div', { class: 'dato' }, el('span', {}, etiqueta), el('strong', {}, valor || '—'));
const insigniaPendiente = () => el('span', { class: 'insignia insignia-ambar' }, 'PENDIENTE');

export async function mostrar(raiz, { api }) {
  const [pendientes, autos, rutas] = await Promise.all([
    api.get('/api/conductores?estado=PENDIENTE'),
    api.get('/api/automoviles'),
    api.get('/api/rutas'),
  ]);
  let elegido = pendientes[0] ?? null;

  function render(aviso) {
    if (!pendientes.length) {
      raiz.replaceChildren(el('section', { class: 'tarjeta' }, el('h2', {}, 'Pendientes (0)'),
        aviso ?? null, el('p', { class: 'secundario', role: 'status' }, 'No hay conductores pendientes de validación.')));
      return;
    }
    raiz.replaceChildren(el('div', { class: 'dos-columnas' }, lista(), detalle(elegido, aviso)));
  }

  function lista() {
    return el('section', { class: 'tarjeta lista-tarjeta', 'aria-labelledby': 'titulo-pendientes' },
      el('h2', { id: 'titulo-pendientes' }, `Pendientes (${pendientes.length})`),
      el('ul', { class: 'lista-seleccion' }, pendientes.map((c) => el('li', {},
        el('button', {
          type: 'button', class: 'item-seleccion', 'aria-current': c === elegido ? 'true' : null,
          onclick: () => { elegido = c; render(); },
        },
        el('span', { class: 'item-texto' }, el('strong', {}, c.nombre_completo),
          el('span', { class: 'secundario' }, `Registro ${fechaCl(c.registrado)} · Licencia ${c.tipo_licencia}`)),
        insigniaPendiente())))));
  }

  function detalle(c, aviso) {
    const recorridos = rutas.filter((r) => autos.some((a) => a.ruta_folio === r.folio));
    const selRecorrido = el('select', { id: 'vinculo-recorrido' },
      el('option', { value: '' }, 'Elige un recorrido'),
      recorridos.map((r) => el('option', { value: r.folio }, `Línea ${r.linea} · ${r.origen_comuna} – ${r.destino_comuna}`)));
    const selVehiculo = el('select', { id: 'vinculo-vehiculo', disabled: true }, el('option', { value: '' }, 'Elige primero el recorrido'));
    selRecorrido.addEventListener('change', () => {
      const opciones = autos.filter((a) => a.ruta_folio === selRecorrido.value);
      selVehiculo.replaceChildren(el('option', { value: '' }, 'Elige un vehículo'),
        ...opciones.map((a) => el('option', { value: a.id }, a.conductor ? `${a.patente} · hoy con ${a.conductor}` : a.patente)));
      selVehiculo.disabled = !selRecorrido.value;
    });
    const salida = el('div', { 'aria-live': 'polite' }, aviso ?? null);

    async function decidir(boton, aprobar) {
      if (aprobar && !selVehiculo.value) {
        salida.replaceChildren(mensaje('error', 'Elige el recorrido y el vehículo antes de aprobar.'));
        return;
      }
      boton.disabled = true;
      try {
        await api.post(`/api/conductores/${c.id}/estado`, { estado: aprobar ? 'APROBADA' : 'RECHAZADA' });
        let resultado = mensaje('info', `${c.nombre_completo} quedó ${aprobar ? 'aprobado' : 'rechazado'}.`);
        if (aprobar) {
          try {
            await api.post('/api/admin/asignaciones', { conductor_id: c.id, automovil_id: Number(selVehiculo.value) });
            resultado = mensaje('info', `${c.nombre_completo} quedó aprobado y vinculado al vehículo elegido.`);
          } catch (err) {
            resultado = mensaje('aviso', `${c.nombre_completo} quedó aprobado, pero no se pudo vincular: ${err.message} Hazlo en «Asignar rutas».`);
          }
        }
        pendientes.splice(pendientes.indexOf(c), 1);
        elegido = pendientes[0] ?? null;
        render(resultado);
      } catch (err) {
        salida.replaceChildren(mensaje('error', err.message));
        boton.disabled = false;
      }
    }

    return el('section', { class: 'tarjeta', 'aria-labelledby': 'titulo-detalle' },
      el('div', { class: 'fila-titulo' },
        el('div', {}, el('h2', { id: 'titulo-detalle' }, c.nombre_completo),
          el('p', { class: 'secundario' }, `Solicitud recibida el ${fechaCl(c.registrado)}`)),
        insigniaPendiente()),
      el('h3', {}, 'Datos personales'),
      el('div', { class: 'datos' }, dato('RUT', c.rut), dato('Correo', c.email), dato('Celular', c.fono_celular),
        dato('Dirección', c.direccion), dato('Comuna', c.comuna), dato('Teléfono de emergencia', c.fono_emergencia)),
      el('h3', {}, 'Licencia de conducir'),
      el('div', { class: 'licencia' },
        el('div', { class: 'datos datos-2' }, dato('Clase', CLASES[c.tipo_licencia] ?? c.tipo_licencia),
          dato('Fecha de emisión', fechaCl(c.fecha_obtencion_licencia)), dato('Comuna de emisión', c.municipio_licencia),
          dato('Fecha de vencimiento', fechaCl(c.fecha_vencimiento_licencia))),
        el('div', { class: 'foto', id: 'foto-frente' }, 'Foto frente'),
        el('div', { class: 'foto', id: 'foto-reverso' }, 'Foto reverso')),
      el('h3', {}, 'Vinculación'),
      el('div', { class: 'datos datos-2' },
        el('div', {}, el('label', { for: 'vinculo-recorrido' }, 'Servicio'), selRecorrido),
        el('div', {}, el('label', { for: 'vinculo-vehiculo' }, 'Vehículo'), selVehiculo)),
      salida,
      el('div', { class: 'pie-acciones' },
        el('p', { class: 'secundario' }, 'Solo se aceptan licencias clase A2 o A3'),
        el('div', { class: 'acciones' },
          el('button', { type: 'button', class: 'boton boton-peligro', onclick: (e) => decidir(e.currentTarget, false) }, 'Rechazar'),
          el('button', { type: 'button', class: 'boton boton-principal', onclick: (e) => decidir(e.currentTarget, true) }, 'Aprobar y vincular'))));
  }

  render();
}
