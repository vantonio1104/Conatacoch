# Tareas: Panel web de administración

Plan: `tasks/plan.md`. Cada tarea termina con sus pruebas en verde (`npm test` contra la base de pruebas) y un commit.

## Fase A · Acceso seguro

- [x] **T1. Sesiones revocables en el servidor**
  - Aceptación: cada inicio de sesión crea una sesión; un token de una sesión revocada o vencida responde 401; el
    token del administrador dura 8 h y su sesión se cierra tras 30 min sin uso; «cerrar sesión» revoca la actual.
    Las rutas de la app siguen funcionando igual.
  - Verificar: pruebas nuevas en `backend/test/sesiones.test.js` + las 28 actuales.
  - Archivos: `database/schema.sql`, `util.js`, `rutas/auth.js`, `test/sesiones.test.js`.
- [x] **T2. Panel base**
  - Aceptación: `/admin` entrega el panel con CSP estricta; Admin 1 sin campo TOTP todavía; menú lateral, encabezado,
    «Modo oscuro» y «Cerrar sesión»; un pasajero no entra; vuelve al inicio tras 30 min sin actividad.
  - Verificar: prueba de Supertest (HTML y cabecera CSP) y prueba de humo en Playwright (entrar y salir).
  - Archivos: `server.js`, `public/admin/{index.html,estilos.css,app.js,logo.png}`, `test/panel.test.js`,
    `test/e2e/humo.spec.js`, `package.json`.
- [ ] **T3. TOTP**
  - Aceptación: primer ingreso muestra clave y QR y pide confirmar con un código; los siguientes piden el código;
    códigos incorrectos cuentan para el límite de intentos; el panel muestra «Sesión protegida con TOTP».
  - Verificar: pruebas con códigos calculados para la hora actual, la anterior y una inválida; humo actualizado.
  - Archivos: `database/schema.sql`, `rutas/auth.js`, `totp.js`, `public/admin/app.js`, `test/totp.test.js`.

## Fase B · Modelo de trabajo

- [ ] **T4. Validar conductores (Admin 2)**
  - Aceptación: lista de pendientes y detalle con datos personales y licencia; «Rechazar»; «Aprobar y vincular» aprueba
    y vincula recorrido y vehículo en un solo paso; avisa si la licencia no es A2 o A3.
  - Verificar: prueba de integración del vínculo; revisión contra Figma en claro y oscuro.
  - Archivos: `rutas/admin.js`, `public/admin/pantallas/validar.js`, `test/api.test.js`.
- [ ] **T5. Fotos de licencia**
  - Aceptación: el registro de conductor acepta foto frente y reverso (JPEG o PNG, máx. 1 MB); solo ADMIN las ve; el
    panel las muestra en Admin 2; la app permite elegirlas en el registro.
  - Verificar: pruebas de tipo, tamaño y rol; tú compilas la app y registras un conductor con fotos.
  - Archivos: `database/schema.sql`, `rutas/auth.js`, `rutas/admin.js`, `test/fotos.test.js`,
    `ui/conductor/AuthConductorScreens.kt`, `ConductorAuthViewModel.kt`, `ApiAuthRepository.kt`, `Modelos.kt`.
- [ ] **T6. Asignaciones con fecha y horario (Admin 3)**
  - Aceptación: crear una asignación (conductor, vehículo, recorrido, fecha, desde, hasta); rechazar choques con el
    mensaje de Figma; estados Programada, Activa y Finalizada; el conductor solo inicia servicio dentro de una
    asignación vigente; se elimina el ciclo automático de 6 días.
  - Verificar: pruebas de choque (mismo conductor y mismo vehículo), de bordes de horario y de iniciar servicio fuera
    de horario.
  - Archivos: `database/schema.sql`, `turnos.js`, `rutas/admin.js`, `rutas/conductor.js`,
    `public/admin/pantallas/asignar.js`, `test/asignaciones.test.js`.
- [ ] **T7. Salidas y turnos (Admin 4)**
  - Aceptación: vista semanal por recorrido con las asignaciones de T6; «Nuevo turno» con horarios rápidos de mañana
    y tarde; semana anterior y siguiente; leyenda de colores.
  - Verificar: prueba de la ruta semanal; revisión contra Figma.
  - Archivos: `rutas/admin.js`, `public/admin/pantallas/turnos.js`, `test/asignaciones.test.js`.

## Fase C · Resto de Figma

- [ ] **T8. Avisos (Admin 5)**
  - Aceptación: franja de emergencia activa, filtros (origen, tipo, conductor, vehículo, fecha) y «Ver en mapa» con la
    ubicación guardada.
  - Archivos: `database/schema.sql`, `rutas/conductor.js`, `rutas/admin.js`, `public/admin/pantallas/avisos.js`.
- [ ] **T9. Cuentas (Admin 6)**
  - Aceptación: lista con rol, estado y sesiones activas; filtros; «Cerrar sesiones»; «Desactivar» y «Reactivar» (una
    cuenta desactivada no entra y pierde sus sesiones); «Crear administrador» con contraseña temporal y cambio
    obligatorio al entrar.
  - Archivos: `rutas/admin.js`, `rutas/auth.js`, `public/admin/pantallas/cuentas.js`, `test/cuentas.test.js`.
- [ ] **T10. Recorridos y tarifas (Admin 8)**
  - Aceptación: tabla de recorridos con tarifa y vehículos asignados hoy; «Guardar tarifa» con auditoría; la app del
    pasajero muestra la nueva tarifa. Sin la sección Recaudación.
  - Archivos: `rutas/admin.js`, `public/admin/pantallas/tarifas.js`, `test/api.test.js`.

## Fase D · Pantallas sin Figma

- [ ] **T11. Resumen y reportes PDF** (inicio del panel con semáforo de vigencias y los 2 botones de reporte).
- [ ] **T12. CRUD de conductores y de automóviles con documentos.**
- [ ] **T13. Mapa global en vivo** (Leaflet, posiciones cada 10 s).

## Fase E · Cierre

- [ ] **T14. Quitar la administración de la app Android** (selector de perfil, navegación, `ui/admin`,
  `AdminRepository` y sus pruebas). La app también llama a `POST /api/auth/logout` al cerrar sesión. Tú compilas y
  pruebas la app.
- [ ] **T15. Prueba de humo completa** en Playwright: entrar con TOTP, aprobar un conductor, asignar, ver la semana,
  cambiar una tarifa, descargar los 2 PDF, cerrar sesiones de una cuenta y salir.
- [ ] **T16. Documentación e informe:** README, INSTALACION, DESPLIEGUE, CLAUDE.md y PLAN_DE_PRUEBAS; en el informe,
  la Tabla 10, el manual de uso, las pruebas nuevas y los espacios para capturas del panel.
