// Prueba de rendimiento (PR): levanta el servidor contra la base de PRUEBA y mide 3 escenarios
// con 20, 50 y 100 usuarios simultáneos durante 10 segundos cada uno.
// Uso: TEST_DATABASE_URL=postgresql://.../conatacoch_test npm run test:carga
require('./preparar');
const autocannon = require('autocannon');
const { app } = require('../server');
const { inicializarBaseDeDatos } = require('../db/init');
const { pool } = require('../db/postgres');

const ESCENARIOS = [
  { id: 'PR-01a', nombre: 'Consultar recorridos', metodo: 'GET', ruta: '/api/rutas' },
  { id: 'PR-01b', nombre: 'Colectivos de un recorrido', metodo: 'GET', ruta: '/api/rutas/235003/colectivos' },
  { id: 'PR-09', nombre: 'Inicio de sesión', metodo: 'POST', ruta: '/api/auth/login',
    cuerpo: JSON.stringify({ email: 'camila.reyes@correo.cl', password: 'colectivo-demo-2026' }) },
];
const USUARIOS = [20, 50, 100];

(async () => {
  await inicializarBaseDeDatos();
  const servidor = app.listen(0);
  const url = `http://127.0.0.1:${servidor.address().port}`;
  const resultados = [];
  for (const e of ESCENARIOS) {
    for (const u of USUARIOS) {
      const r = await autocannon({
        url: url + e.ruta, method: e.metodo, connections: u, duration: 10,
        headers: { 'content-type': 'application/json' }, body: e.cuerpo,
      });
      // r.errors ya incluye los timeouts (solicitudes sin respuesta); non2xx = respuestas con error.
      const total = r.requests.total + r.errors;
      const errores = r.errors + r.non2xx;
      resultados.push({
        id: e.id, escenario: e.nombre, usuarios: u, solicitudes: total, por_segundo: Math.round(r.requests.average),
        p50_ms: r.latency.p50, p97_5_ms: r.latency.p97_5, p99_ms: r.latency.p99, prom_ms: Math.round(r.latency.average),
        errores_pct: total ? Math.round((errores / total) * 10000) / 100 : 0,
      });
      console.log(JSON.stringify(resultados.at(-1)));
    }
  }
  servidor.close();
  await pool.end();
})();
