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
  await expect(page.getByText('Administración CONATACOCH', { exact: true })).toBeVisible();

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
