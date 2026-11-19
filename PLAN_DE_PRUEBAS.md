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
# Rendimiento (20, 50 y 100 usuarios simultáneos, 10 s por escenario)
TEST_DATABASE_URL=postgresql://usuario:clave@localhost:5432/conatacoch_test npm run test:carga
# Dependencias con vulnerabilidades conocidas
npm audit --omit=dev
```

`TEST_DATABASE_URL` debe apuntar a una base cuyo nombre contenga `test`: las pruebas se niegan a correr contra otra.

## Resultados (19-11-2026)

| Nivel | Herramienta | Casos | Aprobados |
| --- | --- | --- | --- |
| Unitarias de la app | JUnit (`test/`) | 32 | 32 |
| Unitarias del servidor | node:test (`backend/test/unitarias.test.js`) | 5 | 5 |
| Integración API + PostgreSQL | node:test + Supertest (`backend/test/api.test.js`) | 20 | 20 |
| Rendimiento | autocannon (`backend/test/carga.js`) | 9 escenarios | ver informe |
| Dependencias | npm audit (producción) | — | 0 vulnerabilidades |

Pruebas manuales e instrumentadas en Android (Compose UI Test), usabilidad (SUS) y escaneo OWASP ZAP se documentan en el informe cuando el equipo las ejecuta.
