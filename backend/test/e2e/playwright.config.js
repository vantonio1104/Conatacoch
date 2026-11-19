// Prueba de humo del panel web en Chromium. Uso: TEST_DATABASE_URL=... npm run test:e2e
const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: __dirname,
  timeout: 60_000,
  workers: 1,
  reporter: 'list',
  use: { trace: 'off', locale: 'es-CL', timezoneId: 'America/Santiago' },
});
