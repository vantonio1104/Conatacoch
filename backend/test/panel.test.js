// T2 · El servidor entrega el panel web de administración en /admin con cabeceras de seguridad.
require('./preparar');
const { test, after } = require('node:test');
const assert = require('node:assert/strict');
const request = require('supertest');
const { app } = require('../server');
const { pool } = require('../db/postgres');

after(() => pool.end());

test('PW-01 /admin/ entrega el panel con una CSP estricta', async () => {
  const r = await request(app).get('/admin/');
  assert.equal(r.status, 200);
  assert.match(r.headers['content-type'], /text\/html/);
  assert.match(r.text, /Panel de administración/);
  const csp = r.headers['content-security-policy'];
  assert.match(csp, /default-src 'self'/);
  assert.match(csp, /script-src 'self'(;|$)/); // sin 'unsafe-inline'
  assert.match(csp, /frame-ancestors 'none'/);
  assert.equal(r.headers['x-content-type-options'], 'nosniff');
});

test('PW-02 /admin redirige a /admin/ y los archivos del panel se entregan', async () => {
  const r = await request(app).get('/admin');
  assert.equal(r.status, 301);
  assert.equal(r.headers.location, '/admin/');
  const js = await request(app).get('/admin/app.js');
  assert.equal(js.status, 200);
  assert.match(js.headers['content-type'], /javascript/);
});

test('PW-03 una ruta inexistente de la API sigue respondiendo JSON 404', async () => {
  const r = await request(app).get('/api/no-existe');
  assert.equal(r.status, 404);
  assert.equal(r.body.error, 'Ruta no encontrada');
});
