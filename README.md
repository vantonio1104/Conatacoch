# Conatacoch · App móvil y Sistema de Gestión

Proyecto universitario (INACAP) para la Confederación Nacional de Taxis Colectivos de Chile (CONATACOCH):

- **App Android** (Kotlin + Jetpack Compose + Material 3) para **pasajeros** y **conductores**.
- **Panel web de administración** en `https://conatacoch-api.onrender.com/admin` (lo entrega el mismo servidor;
  código en `backend/public/admin`). Acceso con correo, contraseña y código TOTP de una app autenticadora.
- **Servidor** Node.js + Express (`backend/`), PostgreSQL en Neon y MongoDB Atlas, publicado en Render.

- Vistas de Figma: `docs/figma/`
- Contexto, convenciones y guía visual: `CLAUDE.md`

## 1. Identificación de la Necesidad (Problemática y Solución)

### a) Problemática
- Control manual y disperso de documentos de conductores y vehículos (licencias, permisos de circulación, revisiones técnicas, seguros obligatorios).
- Vencimientos de documentos no detectados a tiempo, generando riesgos legales y operativos.
- Recorridos de líneas de colectivos (La Cisterna, San Ramón, La Granja y La Florida, Región Metropolitana) sin registro centralizado ni estandarizado.
- Falta de trazabilidad y de información en tiempo real para el pasajero sobre la ubicación y tiempo estimado de llegada (ETA) de los colectivos.

### b) Cómo la solución lo resuelve
- **Registro centralizado:** Base de datos relacional y no relacional que unifica conductores, vehículos, asignaciones y trazados de rutas.
- **Semáforo de vencimientos:** Indicadores visuales claros (verde vigente, amarillo < 30 días, rojo vencido) y control de vigencias.
- **Validaciones automáticas:** Control riguroso de formatos (RUT chileno, patentes, licencias A2/A3, fechas y primas).
- **Recorridos actualizables:** Conversión automática de calles a polilíneas reales mediante geocodificación y ruteo, con control de versiones e importación masiva CSV.
- **Mapa en vivo y Vistas por Rol:** Visualización en tiempo real de la flota para Administrador, Conductor y Pasajero, con restricciones de seguridad basadas en roles (RBAC).
- **Panel de administración:** validar conductores, asignar turnos con fecha y horario, avisos, cuentas, tarifas,
  mantenedores de conductores y automóviles, mapa en vivo y los 2 reportes PDF del protocolo.

## Cuentas de prueba (Prototipo / Demo)
- Pasajero: `camila.reyes@correo.cl`
- Conductor aprobado: `p.soto@correo.cl`
- Administración: `admin@conatacoch.cl`

Por seguridad, la contraseña de prueba la entrega el equipo (no se publica en el repositorio). En producción, la del administrador se cambia con la variable `ADMIN_PASSWORD` de Render.
La primera vez que un administrador entra al panel, configura su segundo factor (TOTP) escaneando un código QR.

## Compilar y probar
```bash
./gradlew assembleDebug        # APK de debug
./gradlew testDebugUnitTest    # pruebas JUnit
cd backend && npm run dev      # servidor y panel en http://localhost:3000/admin
```
Pruebas del servidor y del panel: `PLAN_DE_PRUEBAS.md`.
