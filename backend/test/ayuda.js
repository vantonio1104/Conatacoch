// Ayudas compartidas por las pruebas del servidor.
const assert = require('node:assert/strict');
const request = require('supertest');
const totp = require('../totp');
const { query } = require('../db/postgres');

const DEMO = 'colectivo-demo-2026';
const conToken = (t) => ({ Authorization: `Bearer ${t}` });

/** Inicio de sesión del administrador con contraseña y TOTP (configura el TOTP la primera vez). */
async function loginAdmin(app, email = 'admin@conatacoch.cl', password = DEMO) {
  const r = await request(app).post('/api/auth/login').send({ email, password });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.requiereTotp, true);
  let secreto;
  if (r.body.configurar) {
    const alta = await request(app).post('/api/auth/totp/alta').send({ desafio: r.body.desafio });
    assert.equal(alta.status, 200, JSON.stringify(alta.body));
    secreto = alta.body.secreto;
  } else {
    secreto = (await query('SELECT totp_secreto FROM usuarios WHERE email = $1', [email])).rows[0].totp_secreto;
  }
  // Solo en pruebas: permite varios ingresos dentro de los mismos 30 segundos (el servidor rechaza códigos repetidos).
  await query('UPDATE usuarios SET totp_ultimo_paso = NULL WHERE email = $1', [email]);
  const t = await request(app).post('/api/auth/totp').send({ desafio: r.body.desafio, codigo: totp.codigo(secreto) });
  assert.equal(t.status, 200, JSON.stringify(t.body));
  return t.body.token;
}

module.exports = { DEMO, conToken, loginAdmin };
