# CONATACOCH · App móvil (prototipo)

App Android de la Confederación Nacional de Taxis Colectivos de Chile. Proyecto universitario (INACAP).
Una sola app con dos perfiles: **pasajero** y **conductor**. La administración es un panel web (`backend/public/admin`,
HTML/CSS/JS sin framework) que entrega el mismo servidor en `/admin`, con las vistas `Admin *` de Figma.

Estado actual: la app usa el backend (`backend/server.js`) en https://conatacoch-api-i25d.onrender.com
(`API_URL` en `app/build.gradle.kts`, se cambia en `local.properties`; `API_URL=` vacío usa `data/fake`). Con servidor se guarda todo salvo los pagos:
cuentas, conductores, vehículos y documentos, asignaciones, recorridos y trazados, reservas, turnos, servicios y avisos
(PostgreSQL en Neon), y GPS, notificaciones y auditoría (MongoDB Atlas). Pagos siguen con datos de ejemplo.
- **Sin cámaras** (descartadas): el conductor tiene un panel de asientos (toca libre = sube alguien sin reserva,
  ocupado = se baja) y la lista de reservas (Subió / Eliminar). Las reservas vencen a los 3 minutos de hechas.
  Asientos libres = capacidad − ocupados − reservas activas; solo se reserva en colectivos en servicio.
  No existe el aviso AC-04 ni la pantalla de corrección de ocupación.
Variables del servidor: `backend/.env.example`. Pendientes antes de la entrega: `PENDIENTES.md`. No conectar Rutpay ni Firebase hasta que se pida.
- `data/remote/ApiCliente.kt` (HTTP + token), `ApiAuthRepository`, `ApiPasajeroRepository` (delega pagos a
  `FakePasajeroRepository`), `ApiConductorRepository`, `ServidorMapaRepository` (trazado guardado
  por la administración; si no hay, OpenStreetMap). Backend: `backend/rutas/{publico,auth,pasajero,conductor,admin}.js`.
- Conductor: el GPS del teléfono (`ui/conductor/Gps.kt`) se envía cada ~5 s mientras está en servicio, y la pantalla
  de servicio muestra el mapa de su línea siguiendo su ubicación (`MapaRecorrido(seguirUbicacion = true)`).
- Turnos = asignaciones con fecha y horario (`backend/turnos.js`, `GET/POST/DELETE /api/admin/turnos`); el conductor
  solo inicia servicio dentro de un turno vigente. `POST /api/admin/asignaciones` es el vínculo fijo conductor–vehículo.
  Sesiones revocables (tabla `sesiones`), TOTP de la administración (`backend/totp.js`). Fechas en hora de Santiago. Errores del servidor llegan como `ErrorApi` con mensaje para el usuario; los ViewModels
  los muestran con `ErrorGenerico` (nunca dejar que una llamada de red cierre la app).
Mapa real con OpenStreetMap (osmdroid, sin clave): `ui/pasajero/MapaOsm.kt`. Trazados con Overpass + OSRM
en `data/remote/OsmMapaRepository.kt`, guardados en `filesDir/trazados`.
Los 15 recorridos oficiales están en `data/local/CatalogoRecorridos.kt` (generado desde `backend/database/seed`).

## Especificación

- `docs/figma/` contiene las vistas de Figma (claro y oscuro). Son la especificación exacta:
  respetar distribución, textos y colores. Las vistas `Admin *` son del panel web, no de esta app.
- Si una pantalla no está clara o no existe en Figma, **preguntar antes de inventarla**.
- Fuera del alcance de la lista pedida no se agregan funcionalidades.
- Google, Facebook y "¿Olvidaste tu contraseña?" de Figma están ocultos: no se construyen.

## Stack

- Kotlin + Jetpack Compose + Material 3. minSdk 26, target/compile 36.
- Gradle 8.14.3 (wrapper), AGP 8.13, Kotlin 2.2.21. Versiones en `gradle/libs.versions.toml`.
- Navegación: `navigation-compose` con rutas de texto (`ui/navigation/AppNavHost.kt`).
- Biometría: `androidx.biometric` (huella, rostro o PIN/patrón del propio teléfono).

## Arquitectura (MVVM)

```
app/src/main/java/cl/conatacoch/app/
├─ AppContainer.kt      único lugar donde se eligen las implementaciones de los repositorios
├─ MainActivity.kt      FragmentActivity (BiometricPrompt la necesita)
├─ domain/
│  ├─ model/            modelos y reglas simples (Aviso.habilitado, EstadoServicio.libres)
│  ├─ repository/       interfaces: AuthRepository, PasajeroRepository, PreferenciasRepository, ConductorRepository
│  └─ validation/       RutValidator, PasswordValidator (Kotlin puro, con tests)
├─ data/fake/           Fake*Repository con datos de ejemplo en memoria
└─ ui/
   ├─ theme/            tokens de color, tipografía, tema claro/oscuro
   ├─ components/       piezas comunes (botones, campos, tarjetas, encabezados)
   ├─ navigation/       NavHost, barras inferiores y selector de perfil
   ├─ pasajero/         pantallas + PasajeroAuthViewModel + PasajeroViewModel
   └─ conductor/        pantallas + ConductorAuthViewModel + ConductorViewModel
```

- La UI solo conoce las interfaces de `domain/repository`. Para pasar a la API Django REST + WebSockets:
  crear `data/remote/*Repository` que implemente las mismas interfaces y cambiarlas en `AppContainer`.
- Los flujos de datos que cambian en vivo (reserva, servicio, historial) se exponen como `Flow`/`StateFlow`,
  pensando en WebSockets.
- Los ViewModels se crean en `AppNavHost` con alcance de la Activity y se comparten entre pantallas del mismo perfil.

## Convenciones de Kotlin

- Estilo oficial de Kotlin (`kotlin.code.style=official`), 4 espacios, coma final en listas multilínea.
- Nombres de dominio y UI en español (como en Figma); APIs de Android en inglés.
- Pantallas: `XxxScreen(vm, callbacks de navegación, modifier)`. Sin lógica de negocio en los composables.
- Estado de UI en el ViewModel (`mutableStateOf` o `StateFlow`); campos de texto locales con `rememberSaveable`.
- Validaciones de dominio en `domain/validation`, siempre con test JUnit.
- Textos en el código (no `strings.xml`) mientras la app sea solo en español.
- Cambio mínimo que resuelve el problema; sin abstracciones "para el futuro".

## Guía visual

- Colores de marca: verde `#48B749` (logo), verde oscuro `#2E7D32` (texto verde y botones sobre blanco),
  negro `#231F20`, blanco `#FFFFFF`.
- Rojo **solo** para errores y estado "lleno".
- Figma también usa ámbar (advertencias), azul (información/turno de tarde) y gris neutro;
  están como tokens en `ui/theme/Color.kt`. Acceder a ellos con `Extra.*`.
- Tema claro y oscuro desde los mismos tokens (`ui/theme/Theme.kt`). El modo oscuro se elige en la app
  (Preferencias del pasajero o Inicio de servicio del conductor); `null` sigue al sistema.
- Tipografía: escala de Material 3 con Roboto (fuente por defecto de Android).
- Áreas táctiles de mínimo 48 dp. Botones principales de 52 dp de alto.

## Comandos

```bash
./gradlew assembleDebug          # compilar APK de debug
./gradlew testDebugUnitTest      # pruebas unitarias JUnit
./gradlew installDebug           # instalar en emulador/dispositivo conectado
```

El build necesita acceso a `dl.google.com` / `maven.google.com` (Android SDK, AGP, AndroidX).

## Datos de ejemplo

- Pasajero: `camila.reyes@correo.cl` / `colectivo-demo-2026`
- Pasajero sin cuenta: "Continuar sin registrarme" (en Iniciar sesión o Crear cuenta). El invitado ve mapa,
  recorridos y detalle del colectivo; Reservar, Pagar, Historial y Avisos le piden iniciar sesión o crear cuenta,
  y al ingresar vuelve a la pantalla donde estaba (`PasajeroAuthViewModel.invitado`, `AppNavHost.pedirCuenta`).
- Conductor aprobado: `p.soto@correo.cl` / `colectivo-demo-2026`
- Un conductor recién registrado queda **pendiente de validación**.
- Registro de pasajero: nombre, correo y contraseña (15 a 128 caracteres); entra directo, sin código por correo.
- Pago: el primer intento se rechaza y el siguiente se aprueba (para ver ambas pantallas).
- Escanear QR simula leer el vehículo `ABCD-12`.
- Recorridos: se elige la línea (5003, 5011, …) en el buscador del mapa. Los colectivos de ejemplo se mueven
  sobre la ida del recorrido para simular el GPS.
- Reservas: solo una activa a la vez. Aparece en el mapa ("Cerca de tu paradero") y en Historial > Reservas,
  junto al historial (completadas, expiradas, canceladas).
- Cerrar sesión (al final de Perfil) vuelve al selector de perfil.
- Estado "Detenido / En movimiento" del conductor: se cambia tocando el chip (luego vendrá del GPS).
