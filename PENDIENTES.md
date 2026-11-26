# Pendientes antes de la entrega (09-12-2026)

Cambios que se hacen al final, cuando todo esté probado. Al terminar cada uno, marcarlo con `[x]`.

## 1. Dirección del servidor

El servidor nuevo quedó en **https://conatacoch-api-i25d.onrender.com** (el nombre `conatacoch-api` ya estaba usado en Render).
Mientras tanto, para probar la app: en `local.properties` poner `API_URL=https://conatacoch-api-i25d.onrender.com`.

Cambiar `conatacoch-api.onrender.com` por `conatacoch-api-i25d.onrender.com` en:

- [ ] `app/build.gradle.kts` (dirección por defecto de `API_URL`)
- [ ] `app/src/main/java/cl/conatacoch/app/ui/navigation/AppNavHost.kt` (comentario del panel web)
- [ ] `README.md`
- [ ] `CLAUDE.md`
- [ ] Informe Final (anexos de instalación y manual del panel)

## 2. Cuentas y servicios

- [x] Configurar cron-job.org con `https://conatacoch-api-i25d.onrender.com/api/salud` cada 10 minutos.
- [ ] Primer ingreso al panel web y alta del TOTP con la app autenticadora.
- [ ] Opcional: en Render, en `DATABASE_URL`, cambiar `sslmode=require` por `sslmode=verify-full` para quitar el aviso
      «SECURITY WARNING» de los logs. Hoy no afecta: la conexión ya va cifrada.
- [ ] Después de la entrega: cambiar la contraseña del usuario de MongoDB Atlas (se compartió en un chat).
- [ ] Revisar de vez en cuando el espacio usado en Neon (plan gratis: 0,5 GB). El registro ya limita a 20 cuentas por red por hora.

## 3. Pruebas en el PC y el teléfono

Las automáticas ya están hechas y sus resultados están en el informe. Faltan estas, que necesitan teléfono o emulador.
Cada una lleva el ID de su caso en el informe: anota el resultado («Aprobada» o qué pasó) y saca la captura indicada.

**Preparación**

1. En Android Studio: **Device Manager** → crea un emulador Pixel 8 con API 35 (necesita la virtualización activa en
   el BIOS). Mejor aún, conecta tu teléfono por USB con la **depuración USB** activada.
2. Instala la app: `./gradlew installDebug` (con `API_URL` en `local.properties`; espera ~1 minuto si Render estaba dormido).
3. Abre el panel web en el navegador del PC para preparar datos: `https://conatacoch-api-i25d.onrender.com/admin`.
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
- [ ] **PSE-07** En el navegador, abrir `http://conatacoch-api-i25d.onrender.com/admin` (sin «s») → debe redirigir a `https://`.
- [ ] **PSE-01** OWASP ZAP contra un servidor de prueba (no el de producción) y guardar el informe.

**Usabilidad (PX-01 a PX-08)**: con al menos 5 pasajeros y 3 conductores, pedirles las tareas de la Tabla de usabilidad
del informe sin ayuda, anotar si las completan y el tiempo, y al final aplicar el cuestionario SUS (meta: 68 o más).
