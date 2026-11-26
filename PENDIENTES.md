# Pendientes antes de la entrega (09-12-2026)

Todo lo que falta, en un solo lugar. Al terminar cada punto, marcarlo con `[x]`.

## 1. Dirección del servidor

- [x] Todo apunta a **https://conatacoch-api-i25d.onrender.com**: la app (dirección por defecto de `API_URL`, ya no hace
      falta ponerla en `local.properties`), `README.md`, `CLAUDE.md` y el informe.

## 2. Cuentas y servicios

- [x] Configurar cron-job.org con `https://conatacoch-api-i25d.onrender.com/api/salud` cada 10 minutos.
- [x] Primer ingreso al panel web y alta del TOTP con la app autenticadora.
- [ ] Revisar de vez en cuando el espacio usado en Neon (plan gratis: 0,5 GB). El registro ya limita a 20 cuentas por red por hora.
- [ ] Opcional: en Render, en `DATABASE_URL`, cambiar `sslmode=require` por `sslmode=verify-full` para quitar el aviso
      «SECURITY WARNING» de los logs. Hoy no afecta: la conexión ya va cifrada.

## 3. Pruebas que faltan para el informe

Las automáticas ya están hechas y sus resultados están en el informe. Faltan estas, marcadas como «Pendiente (equipo)»
en las tablas de pruebas. Por cada una anota el ID, el resultado («Aprobada» o qué pasó) y la captura indicada; con eso
se actualiza el informe.

**Preparación**

1. En Android Studio: **Device Manager** → crea un emulador Pixel 8 con API 35 (necesita la virtualización activa en
   el BIOS). Mejor aún, conecta tu teléfono por USB con la **depuración USB** activada.
2. Instala la app: `./gradlew installDebug` (con `API_URL` en `local.properties`; espera ~1 minuto si Render estaba dormido).
3. Abre el panel web en el navegador del PC para preparar datos: `https://conatacoch-api-i25d.onrender.com/admin`.
4. Para probar en tiempo real hacen falta dos dispositivos (emulador + teléfono, o dos emuladores): uno pasajero y uno conductor.

**3.1 Pasajero (Tabla de pruebas integrales)**

- [x] **PS-01** Registro y acceso contra el servidor: «Crear cuenta» con contraseña de 15 caracteres o más → entra directo al mapa.
- [x] **PS-02** Ver colectivos: en el buscador elegir la línea 5003 → se ven el recorrido y los colectivos en servicio; al
      tocar uno, el detalle muestra tiempo estimado y asientos. 📷 *Captura 1: lista (Figura 28).*
- [x] **PS-03** Reserva y abordaje: reservar un asiento → aparece «Cerca de tu paradero» (listo, 📷 Figura 29). **Falta:**
      en el conductor, tocar «Subió» → la reserva pasa a completada en Historial.
- [x] **PS-04** Reserva vencida: reservar y esperar 3 minutos sin tocar «Subió» → pasa a «Expirada» en Historial y
      llega la notificación.
- [x] **PS-15** Preferencias: en Perfil desactivar «Retrasos del colectivo»; el conductor envía «Voy con retraso»
      (AC-01) → el pasajero **no** ve esa notificación. Activarla y repetir → sí la ve.

**3.2 Conductor (Tabla de pruebas integrales)**

- [x] **PS-07** Registro y validación: registrarse con las dos fotos de la licencia (cámara del teléfono) → queda
      pendiente; en el panel, **Validar conductores** muestra las fotos legibles → «Aprobar y vincular».
- [x] **PS-08** Turnos: en el panel asignar un turno de hoy → en la app, la tarjeta «Tu asignación de hoy» muestra
      línea, patente y horario. Sin turno hoy, el botón «Iniciar servicio» queda deshabilitado.
- [x] **PS-09** Servicio y fuera de servicio: «Iniciar servicio» → el colectivo aparece en el mapa del pasajero y en
      **Mapa en vivo** del panel; con una reserva activa, «Fuera de servicio» → la reserva se cancela y el pasajero recibe
      el aviso. 📷 *Captura 3 (panel de asientos y reservas).*
- [x] **PS-11** Emergencia: con el vehículo en movimiento solo se puede enviar AC-06 → en el panel, **Avisos** muestra la
      franja roja y «Ver en mapa» abre la ubicación.
- [x] Biometría (sin ID): cerrar sesión y entrar con huella o rostro.

- [ ] Opcional: repetir la captura del conductor (Figura 30) con la APK nueva, que ya no corta «Línea 5003» en la cabecera.

**3.3 Rendimiento en el teléfono (Tabla de pruebas de rendimiento)**

- [x] **PR-05** Repetir en un teléfono físico de gama media: `adb shell am start -W cl.conatacoch.app/.MainActivity`
      cinco veces (meta: menos de 3 s). Hoy está aprobada solo con el emulador.
- [x] **PR-06** 30 minutos en servicio **con la pantalla de servicio abierta** y el GPS activo, mirando
      **App Inspection / Profiler** (meta: CPU de 5% o menos).
- [x] **PR-07** Red lenta: en el emulador, **Extended controls → Cellular → 3G** → las pantallas muestran «Cargando» y
      mensajes de error claros, sin cerrarse.

**3.4 Servidor y servicios**

- [x] **PI-11** Índices TTL en MongoDB Atlas: 24 h en `posiciones_vehiculos` y 30 días en `notificaciones` (Figuras 45 y 46).
- [x] **PSE-07** `http://conatacoch-api-i25d.onrender.com/admin` redirige a `https://`.
- [x] **PSE-01** OWASP ZAP contra una copia local con base de pruebas (no Render). Informes en `docs/pruebas/`;
      resumen en la Tabla 41 del informe. Se corrigieron D-24 a D-26.

**3.5 Usabilidad: queda para el piloto**

- [x] **PX-01 a PX-08** y el cuestionario SUS pasan a «Pendiente (piloto)» en el informe: se harán con pasajeros y
      conductores reales de las líneas.

**3.6 Carga masiva**

- [x] **PR-02, PR-03 y PR-04** quedaron como «Pendiente (piloto)» en el informe, igual que PR-08: el plan gratis de
      Render no soporta esa carga y no se prueba contra el servidor en uso.

## 3.7 Marcas amarillas del informe (resueltas)

- [x] Pipeline de GitHub Actions: `.github/workflows/pruebas.yml` (servidor, panel y app en cada cambio a `main`).
- [x] Política de privacidad: redactada en el informe (sección «Política de privacidad propuesta»). Para el piloto:
      que CONATACOCH la apruebe y agregar en la app el aviso y la casilla de consentimiento en el registro.
- [x] Prueba con TalkBack: pasa al piloto, junto con la usabilidad.

## 4. Revisión de código: puntos no corregidos

No bloquean la entrega. Cada uno se corrige o se justifica en el informe.

- [ ] Eliminar un conductor borra su cuenta y, en cascada, sus servicios y turnos: se pierde el historial. Mejor
      desactivar la cuenta y dejar «Eliminar» solo para registros rechazados.
- [ ] El turno solo se revisa al iniciar el servicio: el conductor puede seguir en servicio después de que termina.
- [ ] La huella del conductor se pide después de la contraseña y no está ligada a nada (no agrega seguridad). El token
      vive solo en memoria: cada vez que se abre la app hay que iniciar sesión, y cada inicio deja una sesión de 30 días.
- [ ] La velocidad la informa el propio teléfono; el servidor no la calcula entre dos posiciones (un GPS falso pasa).
- [ ] Menores del servidor: el límite de intentos y el de registros viven en memoria (se reinician cuando Render
      reinicia el servidor); el inicio de sesión responde más rápido si el correo no existe; el WebSocket no revisa si la
      sesión fue cerrada; la API envía `X-Powered-By: Express` y no envía HSTS; índice de `avisos(creado_en)` duplicado
      en `backend/database/schema.sql`.
- [ ] Menores de la app: las pruebas JUnit cubren solo validadores y preferencias (no los ViewModels);
      `isMinifyEnabled = false` en release; si se pide una APK para instalar fuera de Android Studio, firmarla (release).
- [ ] Revisión contra Figma incompleta: faltó comparar el modo oscuro y las pantallas de pago aprobado y rechazado.

## 5. Después de la entrega

- [ ] Cambiar la contraseña del usuario de MongoDB Atlas (se compartió en un chat).
