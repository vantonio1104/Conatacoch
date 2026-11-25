// Prueba de humo del panel web: levanta el servidor contra la base de PRUEBA y lo recorre en Chromium.
require('../preparar');
const { test, expect } = require('@playwright/test');
const { server } = require('../../server');
const { inicializarBaseDeDatos } = require('../../db/init');
const { pool, query } = require('../../db/postgres');
const totp = require('../../totp');

const DEMO = 'colectivo-demo-2026';
let base;

test.beforeAll(async () => {
  await inicializarBaseDeDatos();
  await new Promise((ok) => server.listen(0, '127.0.0.1', ok));
  base = `http://127.0.0.1:${server.address().port}/admin/`;
});

test.afterAll(async () => {
  await new Promise((ok) => server.close(ok));
  await pool.end();
});

async function entrar(page, email, password = DEMO, codigo = '') {
  await page.goto(base);
  const form = page.locator('#form-login');
  await form.getByLabel('Correo').fill(email);
  await form.getByLabel('Contraseña').fill(password);
  await form.getByLabel('Código de la aplicación autenticadora').fill(codigo);
  await page.getByRole('button', { name: 'Ingresar' }).click();
}

/** Código TOTP vigente del administrador (la prueba permite repetir el paso de 30 s). */
async function codigoAdmin() {
  await query("UPDATE usuarios SET totp_ultimo_paso = NULL WHERE email = 'admin@conatacoch.cl'");
  const { totp_secreto: s } = (await query("SELECT totp_secreto FROM usuarios WHERE email = 'admin@conatacoch.cl'")).rows[0];
  return totp.codigo(s);
}

test('HU-01 un pasajero no entra al panel', async ({ page }) => {
  await entrar(page, 'camila.reyes@correo.cl');
  await expect(page.getByRole('alert')).toHaveText('Correo o contraseña incorrectos');
  await expect(page.getByRole('navigation')).toBeHidden();
});

test('HU-02 primer ingreso del administrador: configura el TOTP, cambia el tema y cierra sesión', async ({ page }) => {
  const errores = [];
  page.on('pageerror', (e) => errores.push(e.message)); // errores de JavaScript (los 401 esperados no cuentan)
  await query("UPDATE usuarios SET totp_secreto = NULL, totp_activo = FALSE WHERE email = 'admin@conatacoch.cl'");
  await entrar(page, 'admin@conatacoch.cl');
  await expect(page.getByRole('heading', { name: 'Configura el segundo factor' })).toBeVisible();
  await expect(page.getByRole('img', { name: /Código QR/ })).toBeVisible();
  const clave = (await page.locator('#alta-clave').textContent()).replace(/\s+/g, '');
  await page.locator('#form-alta').getByLabel('Código de la aplicación autenticadora').fill('000000');
  await page.getByRole('button', { name: 'Activar y entrar' }).click();
  await expect(page.getByRole('alert')).toContainText('Código incorrecto');
  await page.locator('#form-alta').getByLabel('Código de la aplicación autenticadora').fill(totp.codigo(clave));
  await page.getByRole('button', { name: 'Activar y entrar' }).click();
  await expect(page.getByRole('navigation')).toBeVisible();
  await expect(page.getByText('Sesión protegida con TOTP')).toBeVisible();
  await expect(page.getByText('Administrador', { exact: true })).toBeVisible();

  const tema = page.getByRole('button', { name: /Modo oscuro/ });
  const antes = await page.locator('html').getAttribute('data-theme');
  await tema.click();
  await expect(page.locator('html')).not.toHaveAttribute('data-theme', antes ?? '');

  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await expect(page.getByRole('heading', { name: 'Panel de administración' })).toBeVisible();
  expect(errores).toEqual([]);
});

test('HU-03 ingreso normal del administrador con correo, contraseña y código', async ({ page }) => {
  await entrar(page, 'admin@conatacoch.cl');
  await expect(page.getByRole('alert')).toHaveText('Ingresa el código de 6 dígitos de tu app autenticadora.');
  await entrar(page, 'admin@conatacoch.cl', DEMO, await codigoAdmin());
  await expect(page.getByRole('navigation')).toBeVisible();
});

async function entrarAdmin(page) {
  await entrar(page, 'admin@conatacoch.cl', DEMO, await codigoAdmin());
  await expect(page.getByRole('navigation')).toBeVisible();
}

test('HU-04 validar conductores: aprobar y vincular a un pendiente', async ({ page }) => {
  const r = await fetch(new URL('/api/auth/registro-conductor', base), {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      rut: '15.234.567-4', nombre: 'Rodrigo Morales Vega', direccion: 'Av. Santa Rosa 1234', comuna: 'La Granja',
      celular: '+56987654321', telefonoEmergencia: '+56912345678', licencia: 'A2', comunaEmision: 'La Granja',
      correo: 'r.morales@correo.cl', password: 'una-clave-de-prueba-segura', emisionLicencia: '18-03-2023', vencimientoLicencia: '18-03-2029',
      fotoFrente: 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
    }),
  });
  expect(r.status).toBe(201);
  await entrarAdmin(page);
  await page.getByRole('link', { name: 'Validar conductores' }).click();
  await expect(page.getByRole('heading', { name: 'Rodrigo Morales Vega' })).toBeVisible();
  await expect(page.getByText('15234567-4')).toBeVisible();
  await expect(page.getByRole('img', { name: 'Foto frente de la licencia de Rodrigo Morales Vega' })).toBeVisible();
  await expect(page.getByText('Foto reverso · sin adjuntar')).toBeVisible();

  await page.getByRole('button', { name: 'Aprobar y vincular' }).click();
  await expect(page.getByRole('alert')).toContainText('Elige el recorrido y el vehículo');

  await page.getByLabel('Servicio').selectOption('235003');
  await page.getByLabel('Vehículo').selectOption({ label: 'GHJK-34' });
  await page.getByRole('button', { name: 'Aprobar y vincular' }).click();
  await expect(page.getByText(/quedó aprobado y vinculado/)).toBeVisible();
  await expect(page.getByText('No hay conductores pendientes de validación.')).toBeVisible();
  const fila = (await query(
    `SELECT c.estado, a.patente FROM conductores c
     LEFT JOIN asignaciones s ON s.conductor_id = c.id AND s.activo LEFT JOIN automoviles a ON a.id = s.automovil_id
     WHERE c.rut = '15234567-4'`,
  )).rows[0];
  expect(fila).toEqual({ estado: 'APROBADA', patente: 'GHJK-34' });
});

test('HU-05 asignar rutas: avisa el choque de horario y crea un turno', async ({ page }) => {
  const manana = (await query("SELECT ((NOW() AT TIME ZONE 'America/Santiago')::date + 1)::text AS f")).rows[0].f;
  await entrarAdmin(page);
  await page.getByRole('link', { name: 'Asignar rutas' }).click();
  await expect(page.getByRole('heading', { name: 'Asignaciones vigentes' })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'Pedro Soto' }).first()).toBeVisible(); // turnos de ejemplo

  // Pedro ya trabaja de 06:00 a 22:00 (datos de ejemplo): pedir 10:00–18:00 choca.
  await page.getByLabel('Conductor', { exact: true }).selectOption({ label: 'Pedro Soto' });
  await expect(page.getByLabel('Vehículo')).toHaveValue(/\d+/); // propone su vehículo vinculado
  await page.getByLabel('Fecha').fill(manana);
  await page.getByLabel('Desde').fill('10:00');
  await page.getByLabel('Hasta').fill('18:00');
  await expect(page.getByRole('alert')).toContainText('Choque de horario: Pedro Soto ya tiene asignada la línea 5003');
  await expect(page.getByRole('button', { name: 'Asignar ruta' })).toBeDisabled();

  // Rodrigo (aprobado y vinculado a GHJK-34 en HU-04) sí puede.
  await page.getByLabel('Conductor', { exact: true }).selectOption({ label: 'Rodrigo Morales Vega' });
  await page.getByLabel('Desde').fill('06:00');
  await page.getByLabel('Hasta').fill('14:00');
  await page.getByRole('button', { name: 'Asignar ruta' }).click();
  await expect(page.getByText(/Turno asignado: Rodrigo Morales Vega, GHJK-34/)).toBeVisible();
  await page.getByLabel('Buscar por patente o conductor').fill('GHJK');
  await expect(page.getByRole('row', { name: /Rodrigo Morales Vega GHJK-34 Línea 5003/ })).toContainText('Programada');
});

test('HU-06 salidas y turnos: asignar un turno desde una celda libre y borrarlo', async ({ page }) => {
  await entrarAdmin(page);
  await page.getByRole('link', { name: 'Salidas y turnos' }).click();
  await page.getByLabel('Recorrido').selectOption('235003');
  await expect(page.getByRole('rowheader', { name: /Rodrigo Morales Vega/ })).toBeVisible();
  const semana = page.locator('.semana-titulo');
  const actual = await semana.textContent();
  await page.getByRole('button', { name: 'Semana siguiente' }).click(); // toda la semana es futura
  await expect(semana).not.toHaveText(actual);
  const libre = page.getByRole('button', { name: /^Rodrigo Morales Vega, .*: libre/ }).first();
  const etiqueta = await libre.getAttribute('aria-label');
  const fecha = etiqueta.match(/\d{2}-\d{2}-\d{4}/)[0];
  await libre.click();
  const dialogo = page.getByRole('dialog');
  await expect(dialogo.getByRole('heading', { name: 'Nuevo turno' })).toBeVisible();
  await expect(dialogo.getByLabel('Vehículo')).toHaveValue(/\d+/);
  await dialogo.getByRole('button', { name: 'Tarde 14:00–22:00' }).click();
  await dialogo.getByRole('button', { name: 'Guardar turno' }).click();
  await expect(dialogo).toBeHidden();
  const chip = page.getByRole('button', { name: `Rodrigo Morales Vega, ${fecha}: 14:00 a 22:00 en GHJK-34` });
  await expect(chip).toHaveText('14:00–22:00');

  await chip.click();
  await dialogo.getByRole('button', { name: 'Borrar turno' }).click();
  await expect(page.getByText(/Turno borrado/)).toBeVisible();
  await expect(page.getByRole('button', { name: `Rodrigo Morales Vega, ${fecha}: libre. Asignar turno` })).toBeVisible();
});

test('HU-07 avisos: la emergencia reciente se destaca con su ubicación y el filtro por tipo funciona', async ({ page }) => {
  const api = (ruta, cuerpo, token) => fetch(new URL(ruta, base), {
    method: 'POST', headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify(cuerpo),
  }).then((r) => r.json());
  const { token } = await api('/api/auth/login', { email: 'p.soto@correo.cl', password: DEMO });
  await api('/api/conductor/avisos', { codigo: 'AC-01' }, token);
  await api('/api/conductor/avisos', { codigo: 'AC-06', lat: -33.5312, lng: -70.6623 }, token);

  await entrarAdmin(page);
  await page.getByRole('link', { name: 'Avisos' }).click();
  const banner = page.locator('.banner-emergencia');
  await expect(banner).toContainText('Emergencia activa · AC-06');
  await expect(banner).toContainText('ABCD-12 · Pedro Soto');
  await expect(banner.getByRole('link', { name: /Ver ubicación en el mapa/ })).toHaveAttribute('href', /mlat=-33\.5312&mlon=-70\.6623/);
  await expect(page.getByRole('row', { name: /AC-06 Emergencia Pedro Soto ABCD-12/ })).toBeVisible();

  await page.getByLabel('Tipo').selectOption('AC-01');
  await expect(page.getByRole('row', { name: /AC-06/ })).toHaveCount(0);
  await expect(page.getByRole('row', { name: /AC-01 Voy con retraso/ })).toContainText('Sin ubicación');
  await expect(banner).toContainText('Emergencia activa'); // sigue visible aunque el filtro la esconda
});

test('HU-08 cuentas: cerrar sesiones, desactivar y crear un administrador que cambia su clave al entrar', async ({ page }) => {
  page.on('dialog', (d) => d.accept()); // confirmaciones de «Cerrar sesiones» y «Desactivar»
  await entrarAdmin(page);
  await page.getByRole('link', { name: 'Cuentas' }).click();
  await expect(page.getByRole('button', { name: 'Desactivar a Administración CONATACOCH' })).toBeDisabled();

  await page.getByLabel('Rol').selectOption('PASAJERO');
  await page.getByRole('button', { name: 'Cerrar sesiones de Camila Reyes' }).click();
  await expect(page.getByText(/Se cerraron \d+ sesiones de Camila Reyes/)).toBeVisible();
  await page.getByRole('button', { name: 'Desactivar a Camila Reyes' }).click();
  await expect(page.getByRole('row', { name: /Camila Reyes/ })).toContainText('Desactivada');
  await page.getByRole('button', { name: 'Reactivar a Camila Reyes' }).click();
  await expect(page.getByRole('row', { name: /Camila Reyes/ })).toContainText('Activa');

  await page.getByLabel('Nombre completo').fill('Daniela Herrera Soto');
  await page.locator('#nuevo-admin-correo').fill('d.herrera@conatacoch.cl');
  await page.getByRole('button', { name: 'Crear administrador' }).click();
  const clave = (await page.locator('.clave-temporal .clave').textContent()).trim();
  expect(clave.length).toBeGreaterThanOrEqual(15);

  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await expect(page.getByRole('heading', { name: 'Panel de administración' })).toBeVisible();
  await entrar(page, 'd.herrera@conatacoch.cl', clave);
  await expect(page.getByRole('heading', { name: 'Configura el segundo factor' })).toBeVisible();
  const secreto = (await page.locator('#alta-clave').textContent()).replace(/\s+/g, '');
  await page.locator('#form-alta').getByLabel('Código de la aplicación autenticadora').fill(totp.codigo(secreto));
  await page.getByRole('button', { name: 'Activar y entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Crea tu contraseña' })).toBeVisible();
  const cambio = page.locator('#form-cambio');
  await cambio.getByLabel('Contraseña temporal', { exact: true }).fill(clave);
  await cambio.getByLabel('Contraseña nueva', { exact: true }).fill('una-clave-nueva-y-segura');
  await cambio.getByLabel('Repite la contraseña nueva', { exact: true }).fill('una-clave-nueva-y-segura');
  await page.getByRole('button', { name: 'Guardar y entrar' }).click();
  await expect(page.getByRole('navigation')).toBeVisible();
});
