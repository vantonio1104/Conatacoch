# CONATACOCH · App móvil (prototipo)

App Android de la Confederación Nacional de Taxis Colectivos de Chile. Proyecto universitario (INACAP).
Una sola app con dos perfiles: **pasajero** y **conductor**. El panel web de administración va en otro repositorio.

Estado actual: prototipo con **datos de ejemplo**, sin backend. No conectar servicios reales
(Rutpay, Firebase, Google Maps ni la API) hasta que se pida.

## Especificación

- `docs/figma/` contiene las vistas de Figma (claro y oscuro). Son la especificación exacta:
  respetar distribución, textos y colores. Las vistas `Admin *` son del panel web, no de esta app.
- Si una pantalla no está clara o no existe en Figma, **preguntar antes de inventarla**.
- Fuera del alcance de la lista pedida no se agregan funcionalidades.

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
- Conductor aprobado: `p.soto@correo.cl` / `colectivo-demo-2026`
- Un conductor recién registrado queda **pendiente de validación**.
- Código de verificación: cualquier código de 6 dígitos.
- Pago: el primer intento se rechaza y el siguiente se aprueba (para ver ambas pantallas).
- Escanear QR simula leer el vehículo `ABCD-12`.
- Estado "Detenido / En movimiento" del conductor: se cambia tocando el chip (luego vendrá del GPS).
