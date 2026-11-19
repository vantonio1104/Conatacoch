# Plan: Panel web de administración

Basado en `SPEC-panel-web.md` (aprobada el 19-11-2026). Entrega: 09-12-2026.

## Componentes y dependencias

```
Sesiones revocables (servidor) ──┬──> Panel base (login, menú, modo oscuro, inactividad)
                                 │        ├──> TOTP
                                 │        ├──> Validar conductores ──> Fotos de licencia (servidor + Android)
                                 │        ├──> Asignaciones con fecha y horario ──> Salidas y turnos
                                 │        ├──> Avisos · Cuentas · Recorridos y tarifas
                                 │        └──> Pantallas sin Figma (resumen, CRUD, reportes, mapa)
                                 └──> Quitar administración de la app Android (al final)
```

Las sesiones van primero porque el panel, el TOTP, «Cerrar sesiones» y el cierre por inactividad dependen de ellas.

## Decisiones técnicas

1. **Sesiones en PostgreSQL.** Tabla nueva `sesiones` (id, usuario, creada, último uso, revocada). El token lleva el id
   de la sesión; `autenticar` revisa que siga activa y actualiza el último uso como máximo una vez por minuto. Esto da
   «Cerrar sesiones», la columna «Sesiones activas» de Figma, el cierre del administrador a los 30 minutos sin
   actividad (también en el servidor, no solo en el navegador) y cierra el defecto D-13. Costo: una consulta más por
   solicitud.
2. **Asignaciones con fecha y horario: se reutiliza la tabla `turnos`.** Ya tiene conductor, fecha, vehículo,
   recorrido, hora de inicio y de fin, que es justo lo que pide Admin 3. En vez de crear una tabla nueva y borrar
   `turnos`, se quita la restricción de un turno por día y se agrega el control de choque de horario. `asignaciones`
   queda solo como el vínculo fijo conductor–vehículo de «Aprobar y vincular». Así hay una sola tabla que define cuándo
   trabaja cada conductor, que es el objetivo de P12, con menos cambios. **Requiere tu visto bueno.**
3. **Sin turnos automáticos.** Hoy el servidor inventa turnos con un ciclo de 6 días (`generarTurnos`). Con
   asignaciones explícitas, ese ciclo se elimina: el conductor trabaja solo cuando la administración lo asigna.
   **Requiere tu visto bueno.**
4. **TOTP en dos pasos.** El inicio de sesión del administrador devuelve un desafío de 5 minutos; el token de 8 horas
   se entrega solo con el código correcto. Los intentos fallidos usan el mismo límite que la contraseña.
5. **Fotos en PostgreSQL** (`licencia_fotos`, tipo bytea). Solo JPEG o PNG, revisando los primeros bytes, de hasta
   1 MB cada una. Solo el rol ADMIN las descarga. En Android se usa el selector de fotos del sistema, que no pide
   permisos.
6. **Ubicación de los avisos.** Se agregan latitud y longitud a `avisos`, tomadas de la última posición del vehículo,
   para el enlace «Ver en mapa».
7. **Mapa en el panel con Leaflet 1.9.4**, servido desde el mismo servidor (no desde un CDN externo, para mantener la
   CSP estricta), con mosaicos de OpenStreetMap y su atribución. Dependencia nueva: **requiere tu visto bueno.**
8. **Nuevos administradores.** Figma solo pide nombre y correo, y no hay correo saliente. Propuesta: el panel muestra
   una única vez una contraseña temporal para entregarla en persona; al entrar, el nuevo administrador configura su
   TOTP y debe cambiar la contraseña (pantalla sin Figma, con el estilo del panel). **Requiere tu visto bueno.**
9. **Dependencias nuevas:** `qrcode` (producción, solo para el QR del TOTP), `leaflet` (producción, ver punto 7) y
   `@playwright/test` (desarrollo).

## Orden y puntos de control

| Fase | Tareas | Punto de control |
|---|---|---|
| A. Acceso seguro | T1 a T3 | Un administrador entra con contraseña y TOTP, la sesión se cierra a los 30 min y «Cerrar sesiones» funciona. Pruebas en verde. |
| B. Modelo de trabajo | T4 a T7 | Validar, vincular, asignar con choque de horario, ver la semana; el conductor solo inicia servicio en su horario. |
| C. Resto de Figma | T8 a T10 | Avisos, Cuentas y Tarifas iguales a Figma (claro y oscuro). |
| D. Sin Figma | T11 a T13 | Resumen, CRUD de conductores y automóviles, reportes PDF y mapa global. |
| E. Cierre | T14 a T16 | App sin perfil administrador, prueba de humo completa, documentación e informe actualizados. |

Se puede trabajar en paralelo: T5 (Android) mientras avanza la fase B en el servidor, y las pantallas de la fase D
entre sí.

## Riesgos

| Riesgo | Mitigación |
|---|---|
| El cambio de turnos rompe lo que usa la app del conductor (`/api/conductor/turnos`, iniciar servicio) | Se mantiene el formato de respuesta de esas rutas y se agregan pruebas antes de cambiarlas |
| Los cambios de Android no se pueden compilar en este entorno | Cambios chicos y aislados (registro con fotos, quitar administración); tú compilas al final de T5 y T14 |
| Plazo de 8 a 10 días de trabajo | Fases ordenadas por valor: si falta tiempo, se recorta la fase D (mapa global primero) |
| Base de producción con datos | Cambios de esquema con `IF NOT EXISTS` y sin borrar datos; nunca se prueba contra producción |
| Render gratuito se apaga tras 15 minutos | Ya documentado; el panel muestra «Conectando…» mientras despierta |
