// Admin 8 · Recorridos y tarifas. La sección «Recaudación» de Figma no se incluye: los pagos aún son simulados.
import { el, icono, mensaje } from '../ui.js';

const pesos = (n) => `$${Number(n).toLocaleString('es-CL')}`;
const vehiculos = (n) => (n === 0 ? 'Ninguno' : n === 1 ? '1 vehículo' : `${n} vehículos`);

export async function mostrar(raiz, { api }) {
  let recorridos = await api.get('/api/admin/recorridos');
  const cuerpo = el('tbody');

  function pintarTabla() {
    cuerpo.replaceChildren(...recorridos.map((r) => el('tr', {},
      el('td', {}, el('div', { class: 'usuario-celda' }, el('span', { class: 'fuerte' }, `Línea ${r.linea}`),
        el('span', { class: 'secundario' }, `${r.origen_comuna} – ${r.destino_comuna}`))),
      el('td', {}, r.trazado_guardado
        ? el('span', { class: 'insignia insignia-verde' }, 'Ida y regreso')
        : el('span', { class: 'insignia insignia-gris', title: 'Se calcula con OpenStreetMap hasta que la administración guarde uno' }, 'Automático')),
      el('td', { class: 'fuerte' }, pesos(r.tarifa)),
      el('td', {}, vehiculos(r.asignados_hoy)))));
  }

  // ---------- Editar tarifa ----------
  const selRecorrido = el('select', { id: 'tarifa-recorrido' },
    recorridos.map((r) => el('option', { value: r.folio }, `Línea ${r.linea}`)));
  const inTarifa = el('input', { id: 'tarifa-valor', type: 'number', inputmode: 'numeric', min: 1, max: 20000, step: 1 });
  const salida = el('div', { 'aria-live': 'polite' });
  const guardar = el('button', { type: 'submit', class: 'boton boton-principal boton-ancho' }, 'Guardar tarifa');
  const elegido = () => recorridos.find((r) => r.folio === selRecorrido.value);
  const mostrarTarifa = () => { inTarifa.value = elegido()?.tarifa ?? ''; salida.replaceChildren(); };
  selRecorrido.addEventListener('change', mostrarTarifa);

  const form = el('form', { class: 'tarjeta', 'aria-labelledby': 'titulo-tarifa', novalidate: true },
    el('h2', { id: 'titulo-tarifa' }, 'Editar tarifa'),
    el('label', { for: 'tarifa-recorrido' }, 'Recorrido'), selRecorrido,
    el('label', { for: 'tarifa-valor' }, 'Tarifa (CLP)'), inTarifa,
    el('p', { class: 'mensaje mensaje-info' }, icono('info'),
      el('span', {}, 'El pasajero ve esta tarifa al escanear el QR y no puede cambiarla. Un cambio rige para los pagos nuevos; los anteriores conservan su monto y el cambio queda en la auditoría.')),
    salida, guardar);
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const valor = Number(inTarifa.value);
    if (!Number.isInteger(valor) || valor < 1 || valor > 20000) {
      salida.replaceChildren(mensaje('error', 'La tarifa debe ser un número entero entre $1 y $20.000.'));
      return;
    }
    guardar.disabled = true;
    try {
      const r = await api.put(`/api/admin/recorridos/${selRecorrido.value}/tarifa`, { tarifa: valor });
      recorridos = recorridos.map((x) => (x.folio === r.folio ? { ...x, tarifa: r.tarifa } : x));
      pintarTabla();
      salida.replaceChildren(mensaje('info', `Tarifa de la línea ${r.linea} actualizada a ${pesos(r.tarifa)}.`));
    } catch (err) {
      salida.replaceChildren(mensaje('error', err.message));
    } finally {
      guardar.disabled = false;
    }
  });

  pintarTabla();
  mostrarTarifa();
  raiz.replaceChildren(el('div', { class: 'dos-columnas dos-columnas-derecha' },
    el('section', { class: 'tarjeta', 'aria-labelledby': 'titulo-recorridos' },
      el('h2', { id: 'titulo-recorridos' }, 'Recorridos'),
      el('div', { class: 'tabla-contenedor' }, el('table', { class: 'tabla' },
        el('thead', {}, el('tr', {}, ['Recorrido', 'Trazado', 'Tarifa', 'Asignados hoy'].map((h) => el('th', { scope: 'col' }, h)))),
        cuerpo))),
    form));
}
