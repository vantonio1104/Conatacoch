# Spec: Panel web de administración (opción B)

Estado: **borrador para aprobar**. Entrega del proyecto: 09-12-2026.

## Objetivo

Que la administración de CONATACOCH trabaje desde un computador, en un navegador, con las vistas
`Admin 1` a `Admin 8` de `docs/figma/` (claro y oscuro). El panel lo entrega el mismo servidor Express en
`https://conatacoch-api.onrender.com/admin` y usa las rutas `/api/admin/*` que ya existen.

Usuario: administrador de CONATACOCH (rol `ADMIN`). Éxito: puede hacer en la web todo lo que hoy hace en el perfil
administrador de la app, más los 2 reportes PDF del protocolo, sin instalar nada.

## Pantallas propuestas

| # | Pantalla | Figma | Rutas del servidor | Diferencia con Figma |
|---|---|---|---|---|
| 1 | Inicio de sesión | Admin 1 | `POST /api/auth/login` + **TOTP nuevo** | Igual a Figma |
| 2 | Validar conductores | Admin 2 | `GET /api/conductores`, `POST /api/conductores/:id/estado`, `POST /api/admin/asignaciones`, **fotos nuevas** | Igual a Figma |
| 3 | Asignar rutas | Admin 3 | **Asignaciones con fecha y horario (cambio de base)** | Igual a Figma, con aviso de choque de horario |
| 4 | Salidas y turnos | Admin 4 | `GET/PUT /api/admin/turnos` | Turnos Mañana / Tarde / Libre, 14 días (igual que la app) |
| 5 | Avisos | Admin 5 | `GET /api/admin/avisos` | Sin AC-04 (no hay cámaras); «Ver en mapa» solo si el aviso trae ubicación |
| 6 | Cuentas | Admin 6 | **Nuevas**: listar, desactivar/reactivar, crear administrador, cerrar sesiones | Igual a Figma; «Cerrar sesiones» corrige D-13 |
| 7 | Cámaras | Admin 7 | — | **Se omite**: las cámaras se descartaron |
| 8 | Recorridos y tarifas | Admin 8 | `GET /api/rutas` + **editar tarifa nueva** | Sin la sección Recaudación (pagos simulados) |
| — | Conductores (CRUD) | sin Figma: estilo del panel | `GET/POST/PUT/DELETE /api/conductores` | Aprobado diseñarla |
| — | Automóviles y documentos (CRUD) | sin Figma: estilo del panel | `GET/POST/PUT/DELETE /api/automoviles` | Aprobado diseñarla |
| — | Reportes PDF | sin Figma: estilo del panel | `GET /api/admin/reportes/*` | Aprobado diseñarla |
| — | Resumen y semáforo, mapa global | sin Figma: estilo del panel | `GET /api/admin/resumen`, `/api/admin/flota` | Aprobado diseñarla |

Después de terminar el panel se **quita el perfil administrador de la app Android** (decisión del 19-11-2026).

## Tecnología

- HTML, CSS y JavaScript sin framework ni paso de compilación (módulos ES), en `backend/public/admin/`.
- Express los entrega con `express.static`. Sin dependencias nuevas de producción.
- Colores y tipografía: los tokens de la guía visual (`#48B749`, `#2E7D32`, `#231F20`, ámbar, azul, gris; rojo solo
  para errores, emergencia y «lleno»). Tema claro y oscuro con el botón «Modo oscuro» (se recuerda en el navegador).
- Iconos en SVG dentro del HTML; logo copiado desde `app/src/main/res/drawable/logo.png`.
- Pensado para pantallas de 1280 px o más; en pantallas chicas el menú se pliega (sin rediseño móvil).

## Seguridad

- Sesión: el mismo token JWT de hoy, guardado en `sessionStorage` (se borra al cerrar la pestaña) y enviado como
  `Authorization: Bearer`. Cierre automático tras 30 minutos sin actividad, como dice Figma — **P7**.
- Content-Security-Policy estricta para `/admin` (`script-src 'self'`, sin scripts en línea) y datos del servidor
  siempre con `textContent`, nunca con `innerHTML`, para evitar XSS.
- No se muestra «Sesión protegida con TOTP» mientras no exista TOTP.
- El panel solo funciona con `ADMIN_PASSWORD` definida en Render (se documenta en DESPLIEGUE.md).

## Comandos

```bash
cd backend
npm run dev                                   # panel en http://localhost:3000/admin
TEST_DATABASE_URL=postgresql://usuario:clave@localhost:5432/conatacoch_test npm test
```

## Estructura

```
backend/public/admin/
├─ index.html          una sola página; cada pantalla es una sección
├─ estilos.css         tokens claro/oscuro y componentes (tarjetas, tablas, chips, botones)
├─ app.js              sesión, navegación por #ruta, llamadas a la API y manejo de errores
├─ pantallas/*.js      un archivo por pantalla (conductores.js, turnos.js, …)
└─ logo.png
backend/test/panel.test.js   pruebas del panel
```

## Estilo de código

```js
// pantallas/avisos.js
export async function mostrarAvisos(api, raiz) {
  const avisos = await api.get('/api/admin/avisos');
  const filas = avisos.map((a) => fila([chip(a.codigo, a.codigo === 'AC-06'), a.titulo, a.conductor, a.patente, fecha(a.fecha)]));
  raiz.replaceChildren(tabla(['Tipo', 'Aviso', 'Conductor', 'Vehículo', 'Fecha'], filas));
}
```

Nombres en español, como en la app y en Figma. Un error de la API se muestra en la pantalla con su mensaje y nunca
deja el panel en blanco.

## Pruebas

- **Servidor (node:test + Supertest):** `/admin` entrega el HTML con la cabecera CSP; cada ruta nueva (cuentas,
  tarifa) con sus casos de rol (403 a no administradores), validación (400) y éxito.
- **Navegador (Playwright, ya instalado en el entorno de trabajo):** un recorrido de humo: iniciar sesión, aprobar un
  conductor pendiente, guardar un turno, descargar los 2 PDF, cerrar sesión. Contra servidor local y base de pruebas,
  nunca contra producción — **P8**.
- **Manual:** cada pantalla en claro y oscuro comparada con Figma, uso solo con teclado y foco visible.

## Límites

- **Siempre:** seguir textos, distribución y colores de Figma; correr las pruebas antes de cada commit; probar contra
  la base de pruebas.
- **Preguntar antes:** rutas o columnas nuevas en el servidor, dependencias nuevas, pantallas sin Figma, quitar la
  administración de la app Android.
- **Nunca:** mostrar funciones que no existen (TOTP, cámaras, recaudación real) como si funcionaran; guardar secretos en
  el repositorio; probar contra la base de producción.

## Criterios de éxito

1. Un administrador entra en `/admin`, usa cada pantalla aprobada y cierra sesión; un pasajero o conductor recibe
   «Correo o contraseña incorrectos» o 403 y no ve el panel.
2. Cada pantalla coincide con su vista de Figma en claro y oscuro, salvo las diferencias aprobadas en esta spec.
3. Los 2 reportes PDF se descargan desde el panel.
4. Tras 30 minutos sin actividad, el panel vuelve al inicio de sesión.
5. `npm test` pasa completo (las 28 pruebas actuales y las nuevas) y la prueba de humo en navegador pasa.
6. El informe final se actualiza: Tabla 10 («Panel de administración»), manual de uso, pruebas y capturas.

## Decisiones tomadas (19-11-2026)

- Se construyen: TOTP, Cuentas, editar tarifa y fotos de licencia. Recaudación no (pagos simulados).
- Las pantallas sin Figma se diseñan con el estilo del panel.
- Asignaciones con fecha y horario, con detección de choque como en Admin 3.
- El perfil administrador sale de la app Android cuando el panel esté listo.

## Preguntas abiertas

- **P10. Fotos de licencia, quién las sube:** el conductor en el registro de la app (cambio en Android: elegir foto o
  cámara) o el administrador desde el panel. Se guardan en PostgreSQL (Render no conserva archivos en disco), solo
  JPEG o PNG de hasta 1 MB cada una, y solo el rol ADMIN las ve.
- **P11. Alta del TOTP:** no hay vista en Figma para configurarlo. Propuesta: la primera vez que el administrador entra,
  el panel le muestra la clave y un código QR para registrarla en su app autenticadora y le pide un código para
  confirmar. Se implementa con el módulo crypto de Node (RFC 6238), sin librería; el QR necesita una librería pequeña
  o se muestra solo la clave para escribirla a mano.
- **P12. Asignaciones y turnos:** con fecha y horario, la asignación pasa a ser lo que define cuándo trabaja cada
  conductor. Propuesta: «Salidas y turnos» muestra esas mismas asignaciones por semana (Mañana 06:00–14:00 y Tarde
  14:00–22:00 se vuelven horarios rápidos) y desaparece la tabla `turnos` aparte. El conductor solo inicia servicio
  dentro de una asignación vigente para ese momento.
- **P7. Duración del token del administrador:** propuesta de 8 horas, más cierre a los 30 minutos sin actividad.
- **P8. Playwright** como dependencia de desarrollo para la prueba de humo en el navegador.
- **Plazo:** el alcance completo son unos 8 a 10 días de trabajo; cabe antes del 09-12-2026, con poco margen.
