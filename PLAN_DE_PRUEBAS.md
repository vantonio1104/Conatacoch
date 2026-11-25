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
| Unitarias de la app | JUnit (`test/`), con el APK compilado el 25-11-2026 | 36 | 36 |
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

## Pruebas manuales en tu computador y teléfono

Estas pruebas no se pueden hacer en el entorno de desarrollo en la nube (no hay emulador ni teléfono). Cada una lleva el
ID de su caso en el informe: anota el resultado («Aprobada» o qué pasó) y saca la captura indicada.

**Preparación**

1. En Android Studio: **Device Manager** → crea un emulador Pixel 8 con API 35 (necesita la virtualización activa en
   el BIOS). Mejor aún, conecta tu teléfono por USB con la **depuración USB** activada.
2. Instala la app: `./gradlew installDebug` (usa el servidor de Render; espera ~1 minuto si estaba dormido).
3. Abre el panel web en el navegador del PC para preparar datos: `https://conatacoch-api.onrender.com/admin`.
4. Para probar en tiempo real hacen falta dos dispositivos (emulador + teléfono, o dos emuladores): uno pasajero y uno conductor.

**Pasajero**

- [ ] **PS-01** Registro y acceso: «Crear cuenta» con contraseña de 15 caracteres o más → entra directo al mapa.
- [ ] **PS-02** Ver colectivos: en el buscador elegir la línea 5003 → se ven el recorrido y los colectivos en servicio; al
      tocar uno, el detalle muestra tiempo estimado y asientos. 📷 *Captura 1 del informe.*
- [ ] **PS-03** Reserva y abordaje: reservar un asiento → aparece «Cerca de tu paradero»; en el conductor, tocar «Subió»
      → la reserva pasa a completada en Historial. 📷 *Captura 2.*
- [ ] **PS-04** Reserva vencida: reservar y esperar 3 minutos sin tocar «Subió» → pasa a «Expirada» en Historial y
      llega la notificación.
- [ ] **PS-05** Pago con QR (simulado): «Pagar viaje» → el primer intento se rechaza y «Intentar de nuevo» se aprueba.
- [ ] **PS-15** Preferencias: en Perfil desactivar «Retrasos del colectivo»; el conductor envía «Voy con retraso»
      (AC-01) → el pasajero **no** ve esa notificación. Activarla y repetir → sí la ve.

**Conductor**

- [ ] **PS-07** Registro y validación: registrarse con las dos fotos de la licencia (cámara del teléfono) → queda
      pendiente; en el panel, **Validar conductores** muestra las fotos legibles → «Aprobar y vincular». Comprueba además
      el defecto **D-14** (fotos en resolución completa).
- [ ] **PS-08** Turnos: en el panel asignar un turno de hoy → en la app, la tarjeta «Tu asignación de hoy» muestra
      línea, patente y horario. Sin turno hoy, el botón «Iniciar servicio» queda deshabilitado.
- [ ] **PS-09** Servicio y fuera de servicio: «Iniciar servicio» → el colectivo aparece en el mapa del pasajero y en
      **Mapa en vivo** del panel; con una reserva activa, «Fuera de servicio» → la reserva se cancela y el pasajero recibe
      el aviso. 📷 *Captura 3 (panel de asientos y reservas).*
- [ ] **PS-11** Emergencia: con el vehículo en movimiento solo se puede enviar AC-06 → en el panel, **Avisos** muestra la
      franja roja y «Ver en mapa» abre la ubicación.
- [ ] Biometría: cerrar sesión y entrar con huella o rostro.

**Rendimiento y seguridad (teléfono real)**

- [ ] **PR-05** Arranque en frío en un teléfono de gama media: `adb shell am start -W cl.conatacoch.app/.MainActivity`
      cinco veces (meta: menos de 3 s).
- [ ] **PR-06** Batería y CPU: 30 minutos en servicio con el GPS activo, mirando **App Inspection / Profiler** (meta: CPU
      de 5% o menos).
- [ ] **PR-07** Red lenta: en el emulador, **Extended controls → Cellular → 3G** → las pantallas muestran «Cargando» y
      mensajes de error claros, sin cerrarse.
- [ ] **PSE-07** En el navegador, abrir `http://conatacoch-api.onrender.com/admin` (sin «s») → debe redirigir a `https://`.
- [ ] **PSE-01** OWASP ZAP contra un servidor de prueba (no el de producción) y guardar el informe.

**Usabilidad (PX-01 a PX-08)**: con al menos 5 pasajeros y 3 conductores, pedirles las tareas de la Tabla de usabilidad
del informe sin ayuda, anotar si las completan y el tiempo, y al final aplicar el cuestionario SUS (meta: 68 o más).
