# Conatacoch · App móvil y Sistema de Gestión

Aplicación Android (Kotlin + Jetpack Compose + Material 3) y arquitectura de soporte para la Confederación Nacional de Taxis Colectivos de Chile (CONATACOCH). Proyecto universitario (INACAP).

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

## Cuentas de prueba (Prototipo / Demo)
- Pasajero: `camila.reyes@correo.cl` / `colectivo-demo-2026`
- Conductor aprobado: `p.soto@correo.cl` / `colectivo-demo-2026`
- Administración: 'admin@conatacoch.cl' / 'colectivo-demo-2026'

## Compilar y probar
```bash
./gradlew assembleDebug        # APK de debug
./gradlew testDebugUnitTest    # pruebas JUnit
```
