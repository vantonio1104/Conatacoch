// Pruebas unitarias del servidor (sin base de datos): validadores compartidos.
require('./preparar');
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { rutNormalizado, patenteNormalizada, passwordValida, fechaIso, emailValido } = require('../util');

const rechaza = (fn) => assert.throws(fn, (e) => e.status === 400);

test('PU-01S RUT con dígito verificador (servidor)', () => {
  assert.equal(rutNormalizado('12.345.678-5'), '12345678-5');
  assert.equal(rutNormalizado('10.000.013-k'), '10000013-K');
  assert.equal(rutNormalizado('7654321-6'), '7654321-6');
  rechaza(() => rutNormalizado('12.345.678-9'));
  rechaza(() => rutNormalizado('12.3A5.678-5'));
  rechaza(() => rutNormalizado(''));
});

test('PU-02S largo de la contraseña: 15 a 128 caracteres', () => {
  assert.equal(passwordValida('a'.repeat(14)), false);
  assert.equal(passwordValida('a'.repeat(15)), true);
  assert.equal(passwordValida('a'.repeat(128)), true);
  assert.equal(passwordValida('a'.repeat(129)), false);
  assert.equal(passwordValida('🚕'.repeat(15)), true); // un emoji cuenta como un carácter
  assert.equal(passwordValida(undefined), false);
});

test('PU-13 patente chilena nueva y antigua', () => {
  assert.equal(patenteNormalizada('abcd 12'), 'ABCD-12');
  assert.equal(patenteNormalizada('AB-1234'), 'AB-1234');
  rechaza(() => patenteNormalizada('12-ABCD'));
  rechaza(() => patenteNormalizada('ABC-123'));
});

test('PU-14 fechas dd-mm-aaaa y fechas imposibles', () => {
  assert.equal(fechaIso('28-02-2026'), '2026-02-28');
  assert.equal(fechaIso('2026-11-19'), '2026-11-19');
  assert.equal(fechaIso('29-02-2025'), null);
  assert.equal(fechaIso('31-04-2026'), null);
});

test('PU-15 formato de correo', () => {
  assert.equal(emailValido('camila.reyes@correo.cl'), true);
  assert.equal(emailValido('camila@'), false);
});
