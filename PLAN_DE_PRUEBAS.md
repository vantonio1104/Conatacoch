# Plan de pruebas · CONATACOCH

Solo se reportan resultados de pruebas que se ejecutaron. El detalle de cada caso está en el punto 13 del informe final.

## Cómo ejecutarlas

```bash
# App Android (JUnit): validadores, avisos, asientos y trazados
./gradlew testDebugUnitTest
# App Android (interfaz, con emulador abierto)
./gradlew connectedAndroidTest

# Servidor: unitarias e integración contra una base PostgreSQL de PRUEBA
cd backend
TEST_DATABASE_URL=postgresql://usuario:clave@localhost:5432/conatacoch_test npm test
# Panel web en Chromium (prueba de humo de punta a punta; usa el Playwright del proyecto)
TEST_DATABASE_URL=postgresql://usuario:clave@localhost:5432/conatacoch_test npm run test:e2e
# Rendimiento (20, 50 y 100 usuarios simultáneos, 10 s por escenario)
TEST_DATABASE_URL=postgresql://usuario:clave@localhost:5432/conatacoch_test npm run test:carga
# Dependencias con vulnerabilidades conocidas
npm audit --omit=dev
```

`TEST_DATABASE_URL` debe apuntar a una base cuyo nombre contenga `test`: las pruebas se niegan a correr contra otra.

En el entorno de desarrollo, `backend/test/correr.sh` recrea la base de prueba local y corre `npm test`.

## Resultados (25-11-2026)

| Nivel | Herramienta | Casos | Aprobados |
| --- | --- | --- | --- |
| Unitarias de la app | JUnit (`test/`), con el APK compilado el 25-11-2026 | 32 | 32 |
| Unitarias del servidor | node:test (`unitarias.test.js`, TOTP con los vectores del RFC 6238) | 5 + 1 | 6 |
| Integración API + PostgreSQL | node:test + Supertest + socket.io-client (`api`, `sesiones`, `totp`, `fotos`, `asignaciones`, `avisos`, `cuentas`, `recorridos`, `mapa`, `panel`) | 75 | 75 |
| Panel web de punta a punta | Playwright + Chromium (`test/e2e/humo.spec.js`, HU-01 a HU-13) | 13 | 13 |
| Rendimiento | autocannon (`backend/test/carga.js`) | 9 escenarios | ver informe |
| Dependencias | npm audit (producción) | — | 0 vulnerabilidades |

La prueba de humo recorre el panel en orden sobre un mismo servidor: un pasajero no entra; primer ingreso del
administrador con alta del TOTP; ingreso con código; validar y vincular un conductor; asignar un turno (con aviso de
choque); semana de turnos; avisos con emergencia; cuentas y administrador nuevo con contraseña temporal; tarifas;
inicio con los 2 PDF; mantenedores de conductores y automóviles; y mapa en vivo.

Pruebas manuales e instrumentadas en Android (Compose UI Test), usabilidad (SUS) y escaneo OWASP ZAP se documentan en el informe cuando el equipo las ejecuta.
