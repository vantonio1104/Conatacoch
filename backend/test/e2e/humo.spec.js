// Prueba de humo del panel web: levanta el servidor contra la base de PRUEBA y lo recorre en Chromium.
require('../preparar');
const { test, expect } = require('@playwright/test');
const { server } = require('../../server');
const { inicializarBaseDeDatos } = require('../../db/init');
const { pool } = require('../../db/postgres');

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

async function entrar(page, email, password = DEMO) {
  await page.goto(base);
  await page.getByLabel('Correo').fill(email);
  await page.getByLabel('Contraseña').fill(password);
  await page.getByRole('button', { name: 'Ingresar' }).click();
}

test('HU-01 un pasajero no entra al panel', async ({ page }) => {
  await entrar(page, 'camila.reyes@correo.cl');
  await expect(page.getByRole('alert')).toHaveText('Correo o contraseña incorrectos');
  await expect(page.getByRole('navigation')).toBeHidden();
});

test('HU-02 el administrador entra, cambia el tema y cierra sesión', async ({ page }) => {
  const errores = [];
  page.on('console', (m) => { if (m.type() === 'error') errores.push(m.text()); });
  await entrar(page, 'admin@conatacoch.cl');
  await expect(page.getByRole('navigation')).toBeVisible();
  await expect(page.getByText('Administración CONATACOCH', { exact: true })).toBeVisible();

  const tema = page.getByRole('button', { name: /Modo oscuro/ });
  const antes = await page.locator('html').getAttribute('data-theme');
  await tema.click();
  await expect(page.locator('html')).not.toHaveAttribute('data-theme', antes ?? '');

  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await expect(page.getByRole('heading', { name: 'Panel de administración' })).toBeVisible();
  expect(errores).toEqual([]);
});
