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
