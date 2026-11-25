// Admin 6 · Gestión de cuentas: sesiones activas, cerrar sesiones, desactivar/reactivar y crear administradores.
import { el, mensaje } from '../ui.js';

const ROLES = { ADMIN: 'Administrador', CONDUCTOR: 'Conductor', PASAJERO: 'Pasajero' };

export async function mostrar(raiz, { api, sesion }) {
  let cuentas = await api.get('/api/admin/cuentas');
  const yo = sesion.leer().email;

  const selRol = el('select', { id: 'filtro-rol' }, el('option', { value: '' }, 'Todos'),
    Object.entries(ROLES).map(([v, t]) => el('option', { value: v }, t)));
  const buscar = el('input', { id: 'filtro-cuenta', type: 'search', placeholder: 'Nombre o correo' });
  const cuerpo = el('tbody');
  const estado = el('div', { 'aria-live': 'polite' });

  async function accion(boton, pregunta, fn) {
    if (pregunta && !confirm(pregunta)) return;
    boton.disabled = true;
    try {
      estado.replaceChildren(mensaje('info', await fn()));
      cuentas = await api.get('/api/admin/cuentas');
      pintar();
    } catch (err) {
      estado.replaceChildren(mensaje('error', err.message));
      boton.disabled = false;
    }
  }

  function fila(c) {
    const propia = c.email === yo;
    const cerrar = el('button', { type: 'button', class: 'boton boton-contorno boton-chico', 'aria-label': `Cerrar sesiones de ${c.nombre}` }, 'Cerrar sesiones');
    cerrar.addEventListener('click', () => accion(cerrar, `¿Cerrar todas las sesiones de ${c.nombre}? Tendrá que volver a iniciar sesión en sus dispositivos.`,
      async () => `Se cerraron ${(await api.post(`/api/admin/cuentas/${c.id}/cerrar-sesiones`)).cerradas} sesiones de ${c.nombre}.`));
    const cambiar = c.activo
      ? el('button', { type: 'button', class: 'boton boton-peligro boton-chico', disabled: propia, 'aria-label': `Desactivar a ${c.nombre}`,
        title: propia ? 'No puedes desactivar tu propia cuenta' : null }, 'Desactivar')
      : el('button', { type: 'button', class: 'boton boton-exito boton-chico', 'aria-label': `Reactivar a ${c.nombre}` }, 'Reactivar');
    cambiar.addEventListener('click', () => accion(cambiar,
      c.activo ? `¿Desactivar la cuenta de ${c.nombre}? No podrá entrar hasta que la reactives.` : null,
      async () => {
        await api.post(`/api/admin/cuentas/${c.id}/estado`, { activo: !c.activo });
        return `La cuenta de ${c.nombre} quedó ${c.activo ? 'desactivada' : 'activa'}.`;
      }));
    return el('tr', {},
      el('td', {}, el('div', { class: 'usuario-celda' }, el('span', { class: 'fuerte' }, c.nombre), el('span', { class: 'secundario' }, c.email))),
      el('td', {}, ROLES[c.rol] ?? c.rol),
      el('td', {}, el('span', { class: `insignia ${c.activo ? 'insignia-verde' : 'insignia-gris'}` }, c.activo ? 'Activa' : 'Desactivada'),
        c.debe_cambiar_clave ? el('span', { class: 'insignia insignia-ambar insignia-espacio' }, 'Clave temporal') : null),
      el('td', {}, String(c.sesiones_activas)),
      el('td', {}, el('div', { class: 'acciones acciones-fila' }, cerrar, cambiar)));
  }

  function pintar() {
    const q = buscar.value.trim().toLowerCase();
    const filas = cuentas.filter((c) => (!selRol.value || c.rol === selRol.value)
      && (!q || c.nombre.toLowerCase().includes(q) || c.email.toLowerCase().includes(q)));
    cuerpo.replaceChildren(...(filas.length ? filas.map(fila)
      : [el('tr', {}, el('td', { colspan: 5, class: 'vacio' }, 'Ninguna cuenta coincide con la búsqueda.'))]));
  }
  selRol.addEventListener('change', pintar);
  buscar.addEventListener('input', pintar);

  // ---------- Crear administrador ----------
  const inNombre = el('input', { id: 'nuevo-admin-nombre', autocomplete: 'off', required: true });
  const inCorreo = el('input', { id: 'nuevo-admin-correo', type: 'email', autocomplete: 'off', required: true });
  const resultado = el('div', { 'aria-live': 'polite' });
  const crear = el('button', { type: 'submit', class: 'boton boton-principal boton-ancho' }, 'Crear administrador');
  const form = el('form', { class: 'tarjeta', 'aria-labelledby': 'titulo-crear', novalidate: true },
    el('h2', { id: 'titulo-crear' }, 'Crear administrador'),
    el('label', { for: 'nuevo-admin-nombre' }, 'Nombre completo'), inNombre,
    el('label', { for: 'nuevo-admin-correo' }, 'Correo'), inCorreo,
    resultado, crear);
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    crear.disabled = true;
    try {
      const r = await api.post('/api/admin/administradores', { nombre: inNombre.value, email: inCorreo.value });
      form.reset();
      // La contraseña temporal se muestra una sola vez: el servidor no la guarda en texto.
      resultado.replaceChildren(el('div', { class: 'mensaje mensaje-aviso clave-temporal', role: 'status' },
        el('div', {},
          el('p', {}, `Cuenta creada para ${r.nombre}. Contraseña temporal (se muestra una sola vez):`),
          el('code', { class: 'clave' }, r.clave_temporal),
          el('p', {}, 'Entrégala en persona. Al entrar deberá configurar su TOTP y crear su propia contraseña.'))));
      cuentas = await api.get('/api/admin/cuentas');
      pintar();
    } catch (err) {
      resultado.replaceChildren(mensaje('error', err.message));
    } finally {
      crear.disabled = false;
    }
  });

  pintar();
  raiz.replaceChildren(estado, el('div', { class: 'dos-columnas dos-columnas-derecha' },
    el('section', { class: 'tarjeta', 'aria-labelledby': 'titulo-cuentas' },
      el('div', { class: 'fila-titulo fila-busqueda' },
        el('h2', { id: 'titulo-cuentas' }, 'Cuentas'),
        el('div', { class: 'filtros-cuentas' },
          el('div', {}, el('label', { for: 'filtro-rol' }, 'Rol'), selRol),
          el('div', {}, el('label', { for: 'filtro-cuenta' }, 'Buscar'), buscar))),
      el('div', { class: 'tabla-contenedor' }, el('table', { class: 'tabla' },
        el('thead', {}, el('tr', {}, ['Usuario', 'Rol', 'Estado', 'Sesiones activas', 'Acciones'].map((h) => el('th', { scope: 'col' }, h)))),
        cuerpo)),
      el('p', { class: 'secundario nota-pie' }, '«Cerrar sesiones» invalida todos los tokens del usuario en sus dispositivos, por ejemplo si le roban el teléfono a un conductor. Las cuentas no se borran: se desactivan.')),
    form));
}
