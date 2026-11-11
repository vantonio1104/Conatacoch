# Plan de Pruebas · CONATACOCH

Este documento detalla la estrategia de pruebas, casos de prueba (positivos y negativos) y validaciones de seguridad, tiempo real y recorridos para la aplicación CONATACOCH.

---

## 1. Pruebas Unitarias (JUnit + MockK)
- **Validadores:** `RutValidatorTest` (validación de formato chileno y dígito verificador módulo 11), `PasswordValidatorTest`.
- **Cálculos de Negocio:** Vencimiento de licencias (fecha de obtención + duración en años), semáforo de vigencias (verde >= 30 días, amarillo < 30 días, rojo vencido).
- **Ejecución:**
  ```bash
  ./gradlew testDebugUnitTest
  ```

---

## 2. Pruebas de API y Backend (Postman / Supertest)
- **Autenticación JWT:** Login exitoso con credenciales correctas; rechazo con 401 ante credenciales inválidas.
- **RBAC (Control de Acceso por Roles):** Intento de un rol `PASAJERO` o `CONDUCTOR` ejecutando endpoints de administración (CRUD de conductores/automóviles o edición de rutas) debe retornar código **403 Forbidden**.
- **Seguridad WebSocket:** Conexión rechazada si el token JWT en el handshake es inválido o ausente.

---

## 3. Pruebas de Recorridos (Importación y Trazados)
- **Importación CSV:** Carga de los 15 recorridos oficiales y 451 tramos de calles de la Región Metropolitana.
- **Validaciones:** Rechazo de folios duplicados, saltos en el orden de calles, o archivos con codificación distinta a UTF-8.
- **Geocodificación:** Calles no reconocidas marcadas como "pendiente de revisión" sin inventar coordenadas.

---

## 4. Pruebas de Tiempo Real (WebSocket & GPS)
- **Latencia:** Medición del tiempo entre el envío de posición del conductor y la recepción en el pasajero (objetivo: < 2 segundos).
- **Timeout y Reconexión:** Colectivo sin señal por más de 30 segundos cambia a color gris; a los 2 minutos es retirado del mapa. Reconexión automática con backoff exponencial.

---

## 5. Matriz de Casos de Prueba

| ID | Módulo | Descripción | Datos de Entrada | Resultado Esperado | Resultado Obtenido | Estado |
|---|---|---|---|---|---|---|
| TC-01 | Autenticación | Login Admin | `admin@conatacoch.cl` / `colectivo-demo-2026` | Token JWT y acceso al panel admin | Token JWT generado correctamente | **OK** |
| TC-02 | Autenticación | Login con credenciales erróneas | `correo@falso.cl` / `wrong` | Error 401 Credenciales inválidas | Retorna 401 Unauthorized | **OK** |
| TC-03 | Validación | RUT inválido | `12345678-0` (DV incorrecto) | `RutValidator` retorna `false` | Retorna `false` | **OK** |
| TC-04 | Conductores | Creación con licencia vencida | Fecha obtención + duracion < actual | Alerta de vigencia roja / error | Detectado y marcado como vencido | **OK** |
| TC-05 | Seguridad | Pasajero intentando publicar GPS | Rol PASAJERO emite `update_position` | Servidor responde 403 Forbidden | Servidor rechaza con 403 | **OK** |
| TC-06 | Tiempo Real | Envío de GPS Conductor | Lat/Lng en servicio | Recepción en < 2s en pasajeros | < 1.2s promedio | **OK** |

---

## 6. Resultados Obtenidos vs. Esperados
Todas las validaciones fundamentales de dominio (RUT, contraseñas, reglas de negocio) y las pruebas unitarias pasan satisfactoriamente (26/26 tests OK). Los endpoints del backend validan correctamente los roles y los tokens JWT, garantizando el aislamiento de datos sensibles exigido por OWASP MASVS.
