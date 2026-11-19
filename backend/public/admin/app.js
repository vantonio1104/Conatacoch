// Panel web de administración: sesión, tema, menú y navegación por #pantalla.
import { api, sesion, siVenceLaSesion, ErrorApi } from './api.js';
import { el, icono, mensaje } from './ui.js';

/** Pantallas del menú, en el orden de Figma. Cada una vive en pantallas/<id>.js y exporta mostrar(raiz). */
const PANTALLAS = [
  { id: 'inicio', titulo: 'Inicio', subtitulo: 'Panel de administración de CONATACOCH', icono: 'inicio' },
];

const MINUTOS_INACTIVIDAD = 30;
const $ = (id) => document.getElementById(id);

// ---------- Tema claro u oscuro (se recuerda en este navegador) ----------
function temaGuardado() {
  try { return localStorage.getItem('conatacoch.tema'); } catch { return null; }
}
function aplicarTema(tema) {
  document.documentElement.dataset.theme = tema;
  const boton = $('boton-tema');
  boton.replaceChildren(icono('luna'), tema === 'dark' ? 'Modo oscuro: activado' : 'Modo oscuro');
  boton.setAttribute('aria-pressed', String(tema === 'dark'));
}
$('boton-tema').addEventListener('click', () => {
  const nuevo = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
  try { localStorage.setItem('conatacoch.tema', nuevo); } catch { /* sin almacenamiento: solo esta visita */ }
  aplicarTema(nuevo);
});
aplicarTema(temaGuardado() ?? (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'));

// ---------- Inicio y cierre de sesión ----------
function mostrarLogin(nota = '') {
  $('vista-panel').hidden = true;
  $('vista-login').hidden = false;
  $('login-tarjeta-principal').hidden = false;
  $('alta-totp').hidden = true;
  $('login-error').replaceChildren();
  $('login-nota').replaceChildren(icono('reloj'), el('span', {}, nota || 'Por seguridad, la sesión se cierra automáticamente tras 30 minutos sin actividad.'));
  $('login-correo').focus();
}

async function cerrarSesion(nota) {
  if (sesion.leer()) await api.post('/api/auth/logout').catch(() => {}); // si ya venció, da igual
  sesion.borrar();
  clearTimeout(temporizador);
  mostrarLogin(nota);
}
siVenceLaSesion(() => {
  sesion.borrar();
  mostrarLogin('Tu sesión se cerró. Vuelve a iniciar sesión.');
});

const soloDigitos = (v) => v.replace(/\s+/g, '');

/** Entra con el token que entrega el servidor después del segundo factor. */
function entrar(r) {
  sesion.guardar({ token: r.token, nombre: r.nombre, email: r.email, totp: true });
  $('form-login').reset();
  $('form-alta').reset();
  $('alta-totp').hidden = true;
  mostrarPanel();
}

async function conBotonOcupado(boton, salidaError, fn) {
  boton.disabled = true;
  salidaError.replaceChildren();
  try {
    await fn();
  } catch (err) {
    salidaError.replaceChildren(mensaje('error', err.message));
  } finally {
    boton.disabled = false;
  }
}

let desafio = null; // vale 5 minutos: lo entrega la contraseña correcta y lo canjea el código
$('form-login').addEventListener('submit', (e) => {
  e.preventDefault();
  conBotonOcupado(e.submitter, $('login-error'), async () => {
    const r = await api.post('/api/auth/login', { email: $('login-correo').value, password: $('login-clave').value, rol: 'ADMIN' });
    desafio = r.desafio;
    if (r.configurar) {
      const alta = await api.post('/api/auth/totp/alta', { desafio });
      $('alta-qr').src = alta.qr;
      $('alta-clave').textContent = alta.secreto.match(/.{1,4}/g).join(' ');
      $('login-tarjeta-principal').hidden = true;
      $('alta-totp').hidden = false;
      $('alta-codigo').focus();
      return;
    }
    const codigo = soloDigitos($('login-codigo').value);
    if (!codigo) {
      $('login-codigo').setAttribute('aria-invalid', 'true');
      $('login-codigo').focus();
      throw new Error('Ingresa el código de 6 dígitos de tu app autenticadora.');
    }
    $('login-codigo').removeAttribute('aria-invalid');
    entrar(await api.post('/api/auth/totp', { desafio, codigo }));
  });
});

$('form-alta').addEventListener('submit', (e) => {
  e.preventDefault();
  conBotonOcupado(e.submitter, $('alta-error'), async () => {
    entrar(await api.post('/api/auth/totp', { desafio, codigo: soloDigitos($('alta-codigo').value) }));
  });
});

// ---------- Cierre por inactividad (el servidor también lo aplica) ----------
let temporizador;
function reiniciarInactividad() {
  clearTimeout(temporizador);
  if (!sesion.leer()) return;
  temporizador = setTimeout(() => cerrarSesion('Tu sesión se cerró tras 30 minutos sin actividad.'), MINUTOS_INACTIVIDAD * 60_000);
}
for (const evento of ['pointerdown', 'keydown']) document.addEventListener(evento, reiniciarInactividad, { passive: true });

// ---------- Panel ----------
function armarMenu() {
  $('menu').replaceChildren(...PANTALLAS.map((p) => el('li', {},
    el('a', { class: 'lateral-item', href: `#${p.id}`, 'data-id': p.id }, icono(p.icono), p.titulo))));
  $('boton-salir').replaceChildren(icono('salir'), 'Cerrar sesión');
}
$('boton-salir').addEventListener('click', () => cerrarSesion());

function mostrarPanel() {
  const s = sesion.leer();
  $('vista-login').hidden = true;
  $('vista-panel').hidden = false;
  $('nombre-usuario').textContent = s.nombre;
  $('insignia-totp').hidden = !s.totp;
  $('insignia-totp').replaceChildren(icono('escudo'), 'Sesión protegida con TOTP');
  $('iniciales').textContent = s.nombre.split(/\s+/).map((p) => p[0]).join('').slice(0, 2).toUpperCase();
  armarMenu();
  reiniciarInactividad();
  navegar();
}

async function navegar() {
  if (!sesion.leer()) return;
  const id = location.hash.slice(1);
  const p = PANTALLAS.find((x) => x.id === id) ?? PANTALLAS[0];
  for (const a of document.querySelectorAll('#menu a')) {
    if (a.dataset.id === p.id) a.setAttribute('aria-current', 'page'); else a.removeAttribute('aria-current');
  }
  $('titulo').textContent = p.titulo;
  $('subtitulo').textContent = p.subtitulo;
  document.title = `${p.titulo} · CONATACOCH`;
  const raiz = $('contenido');
  raiz.replaceChildren(el('p', { class: 'secundario', 'aria-busy': 'true' }, 'Cargando…'));
  try {
    const modulo = await import(`./pantallas/${p.id}.js`);
    await modulo.mostrar(raiz, { api, sesion });
  } catch (err) {
    if (err instanceof ErrorApi && err.status === 401) return;
    raiz.replaceChildren(mensaje('error', err instanceof ErrorApi ? err.message : 'No se pudo abrir esta sección. Recarga la página.'));
  }
  raiz.focus();
}
addEventListener('hashchange', navegar);

if (sesion.leer()) mostrarPanel(); else mostrarLogin();
