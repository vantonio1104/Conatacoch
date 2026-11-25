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

test('HU-09 recorridos y tarifas: cambiar la tarifa de una línea', async ({ page }) => {
  await entrarAdmin(page);
  await page.getByRole('link', { name: 'Recorridos y tarifas' }).click();
  await expect(page.getByRole('row', { name: /Línea 5003/ })).toContainText('$800');
  await page.getByLabel('Recorrido', { exact: true }).selectOption({ label: 'Línea 5003' });
  await expect(page.getByLabel('Tarifa (CLP)')).toHaveValue('800');
  await page.getByLabel('Tarifa (CLP)').fill('850.5');
  await page.getByRole('button', { name: 'Guardar tarifa' }).click();
  await expect(page.getByRole('alert')).toContainText('número entero');
  await page.getByLabel('Tarifa (CLP)').fill('900');
  await page.getByRole('button', { name: 'Guardar tarifa' }).click();
  await expect(page.getByText('Tarifa de la línea 5003 actualizada a $900.')).toBeVisible();
  await expect(page.getByRole('row', { name: /Línea 5003/ })).toContainText('$900');
});

test('HU-10 inicio: semáforo de vigencias y descarga de los 2 reportes PDF', async ({ page }) => {
  await entrarAdmin(page);
  await expect(page.getByRole('heading', { name: 'Inicio' })).toBeVisible();
  const licencias = page.getByRole('region', { name: 'Licencias de conductores' });
  await expect(licencias.getByRole('listitem').filter({ hasText: 'Vencidas' })).toContainText(/\d+/); // Juan Pérez, vencida en 2022
  await expect(page.getByRole('region', { name: 'Documentos de vehículos' })).toContainText('Alguno vencido');

  for (const [boton, archivo] of [
    ['Conductores con licencia que vence en 30 días', 'licencias-por-vencer-30-dias.pdf'],
    ['Vehículos con documentación vencida', 'vehiculos-documentos-vencidos.pdf'],
  ]) {
    const [descarga] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: boton }).click()]);
    expect(descarga.suggestedFilename()).toBe(archivo);
    const contenido = require('fs').readFileSync(await descarga.path());
    expect(contenido.subarray(0, 4).toString()).toBe('%PDF');
    await expect(page.getByText(`Se descargó «${archivo}»`)).toBeVisible();
  }
});

test('HU-11 conductores: crear con los campos del protocolo, editar y eliminar', async ({ page }) => {
  page.on('dialog', (d) => d.accept());
  await entrarAdmin(page);
  await page.getByRole('link', { name: 'Conductores', exact: true }).click();
  await page.getByRole('button', { name: '+ Nuevo conductor' }).click();
  const d = page.getByRole('dialog');
  for (const [id, valor] of [['c-nombre', 'Manuel Rojas Castro'], ['c-rut', '13.131.313-6'], ['c-direccion', 'Av. Central 100'],
    ['c-comuna', 'La Cisterna'], ['c-celular', '+56911112222'], ['c-emergencia', '+56933334444'], ['c-incorporacion', '2025-02-01'],
    ['c-municipio', 'La Cisterna'], ['c-obtencion', '2024-05-10'], ['c-duracion', '4'], ['c-vencimiento', '2028-05-10']]) {
    await d.locator(`#${id}`).fill(valor);
  }
  await d.getByLabel('Clase').selectOption('A3');
  await d.getByRole('button', { name: 'Crear conductor' }).click();
  await expect(page.getByText('Manuel Rojas Castro quedó registrado y aprobado.')).toBeVisible();
  const fila = page.getByRole('row', { name: /Manuel Rojas Castro/ });
  await expect(fila).toContainText('A3 · vence 10-05-2028');
  await expect(fila).toContainText('Aprobado');

  await page.getByRole('button', { name: 'Editar a Manuel Rojas Castro' }).click();
  await expect(d.locator('#c-duracion')).toHaveValue('4');
  await expect(d.locator('#c-incorporacion')).toHaveValue('2025-02-01');
  await d.locator('#c-direccion').fill('Av. Central 200');
  await d.getByRole('button', { name: 'Guardar cambios' }).click();
  await expect(page.getByText('Se guardaron los cambios de Manuel Rojas Castro.')).toBeVisible();

  await page.getByRole('button', { name: 'Eliminar a Manuel Rojas Castro' }).click();
  await expect(page.getByText('Manuel Rojas Castro fue eliminado.')).toBeVisible();
  await expect(page.getByRole('row', { name: /Manuel Rojas Castro/ })).toHaveCount(0);
});

test('HU-12 automóviles: crear con sus 3 documentos, editar y dar de baja', async ({ page }) => {
  page.on('dialog', (d) => d.accept());
  await entrarAdmin(page);
  await page.getByRole('link', { name: 'Automóviles' }).click();
  await page.getByRole('button', { name: '+ Nuevo automóvil' }).click();
  const d = page.getByRole('dialog');
  for (const [id, valor] of [['a-patente', 'rstv78'], ['a-marca', 'Toyota'], ['a-modelo', 'Yaris'], ['a-ano', '2023'],
    ['a-comuna-permiso', 'San Ramón'], ['a-otorgacion', '2026-04-01'], ['a-permiso', '2027-03-31'],
    ['a-planta', 'PRT San Ramón'], ['a-comuna-revision', 'San Ramón'], ['a-revision', '2027-05-15'],
    ['a-compania', 'Consorcio'], ['a-prima', '45000'], ['a-seguro', '2027-03-30']]) {
    await d.locator(`#${id}`).fill(valor);
  }
  await d.locator('#a-folio').selectOption({ label: 'Línea 5003' });
  await d.getByRole('button', { name: 'Crear automóvil' }).click();
  await expect(page.getByText('RSTV-78 quedó registrado.')).toBeVisible();
  const fila = page.getByRole('row', { name: /RSTV-78/ });
  await expect(fila).toContainText('Línea 5003');
  await expect(fila).toContainText('Vigente');

  await page.getByRole('button', { name: 'Editar RSTV-78' }).click();
  await expect(d.locator('#a-compania')).toHaveValue('Consorcio');
  await expect(d.locator('#a-prima')).toHaveValue('45000');
  await d.locator('#a-prima').fill('-5');
  await d.getByRole('button', { name: 'Guardar cambios' }).click();
  await expect(d.getByRole('alert')).toContainText('prima');
  await d.locator('#a-prima').fill('47000');
  await d.getByRole('button', { name: 'Guardar cambios' }).click();
  await expect(page.getByText('Se guardaron los cambios de RSTV-78.')).toBeVisible();

  await page.getByRole('button', { name: 'Dar de baja RSTV-78' }).click();
  await expect(page.getByText('RSTV-78 quedó dado de baja.')).toBeVisible();
  await expect(page.getByRole('row', { name: /RSTV-78/ })).toHaveCount(0);
});

test('HU-13 mapa en vivo: marcadores de los colectivos en servicio y estado sin señal', async ({ page }) => {
  const errores = [];
  page.on('pageerror', (e) => errores.push(e.message));
  // Sin MongoDB en las pruebas no hay GPS: se agregan posiciones a la respuesta real de la flota.
  await page.route('**/api/admin/flota', async (ruta) => {
    const respuesta = await ruta.fetch();
    const flota = (await respuesta.json()).map((v) => {
      if (v.patente === 'ABCD-12') return { ...v, en_servicio: true, posicion: { lat: -33.531, lng: -70.662, velocidad: 32, timestamp: new Date().toISOString() } };
      if (v.patente === 'GHJK-34') return { ...v, en_servicio: true, posicion: { lat: -33.54, lng: -70.65, velocidad: 0, timestamp: new Date(Date.now() - 5 * 60_000).toISOString() } };
      return v;
    });
    await ruta.fulfill({ response: respuesta, json: flota });
  });
  await entrarAdmin(page);
  await page.getByRole('link', { name: 'Mapa en vivo' }).click();
  await expect(page.locator('.leaflet-container')).toBeVisible();
  await expect(page.locator('path.leaflet-interactive')).toHaveCount(2);
  const lista = page.getByRole('region', { name: 'Colectivos' }).or(page.locator('.lista-flota'));
  await expect(page.locator('.lista-flota li').filter({ hasText: 'ABCD-12' })).toContainText('En servicio');
  await expect(page.locator('.lista-flota li').filter({ hasText: 'GHJK-34' })).toContainText('Sin señal GPS');
  await page.getByLabel('Línea', { exact: true }).selectOption('5003');
  await expect(lista.first()).toBeVisible();
  await page.locator('.lista-flota').getByRole('button', { name: /ABCD-12/ }).click();
  await page.locator('path.leaflet-interactive').first().click();
  await expect(page.locator('.leaflet-popup')).toContainText('ABCD-12 · Línea 5003');
  expect(errores).toEqual([]);
});
