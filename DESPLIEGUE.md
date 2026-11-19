# Base de datos y servidor 24/7 (gratis)

Cómo queda armado:

```
App Android  ──HTTPS──▶  Servidor Node (Render)  ──▶  PostgreSQL (Neon)      cuentas, recorridos, vehículos, reservas
                                                  └──▶  MongoDB (Atlas)       GPS, ocupación por cámaras, auditoría
cron-job.org ── cada 10 min ──▶ /api/salud  (para que Render no duerma el servidor)
```

Todo es gratis y **ninguno pide tarjeta**. Necesitas una cuenta de GitHub (puedes usarla para entrar a los demás).

| Servicio | Qué hace | ¿Se apaga? |
|---|---|---|
| **Neon** | PostgreSQL | Se suspende tras 5 min sin uso y despierta solo en menos de un segundo. Los datos nunca se pierden. |
| **MongoDB Atlas (M0)** | MongoDB | No se apaga. |
| **Render (Free)** | Corre `backend/server.js` | Se duerme tras 15 min sin visitas; cron-job.org lo mantiene despierto. |
| **cron-job.org** | Visita el servidor cada 10 min | No. |

Tiempo estimado: 30–45 minutos la primera vez.

---

## 1. PostgreSQL en Neon

1. Entra a <https://neon.com> → **Sign up** (con GitHub o Google).
2. Crea un proyecto:
   - Nombre: `conatacoch`
   - Región: **AWS São Paulo (sa-east-1)** (la más cercana a Chile)
   - Postgres: la versión que venga por defecto.
3. En el panel del proyecto, botón **Connect**. Copia la cadena de conexión
   (empieza con `postgresql://` y termina con `?sslmode=require`). Guárdala: es tu **DATABASE_URL**.

> No tienes que crear tablas: el servidor las crea y carga los datos solo la primera vez que arranca.

## 2. MongoDB en Atlas

1. Entra a <https://www.mongodb.com/cloud/atlas/register> y crea una cuenta.
2. **Create a cluster** → elige **Free (M0)**, proveedor AWS, región **São Paulo (sa-east-1)** → **Create**.
3. Te pedirá crear un **usuario de base de datos**: anota el usuario y la contraseña
   (usa solo letras y números en la contraseña para evitar problemas en la URL).
4. **Network Access** (menú izquierdo) → **Add IP Address** → **Allow access from anywhere** (`0.0.0.0/0`) → Confirm.
   Render cambia de IP, por eso hay que permitir todas; la seguridad la da el usuario y la contraseña.
5. **Database** → **Connect** → **Drivers** → copia la cadena `mongodb+srv://...`
   y reemplaza `<db_password>` por la contraseña del paso 3. Esa es tu **MONGODB_URI**.

## 3. Subir el proyecto a GitHub

Render instala el servidor desde un repositorio de GitHub.

1. Instala **GitHub Desktop**: <https://desktop.github.com>.
2. **File → Add local repository** → elige la carpeta `Conatacoch`
   (si dice que no es un repositorio, toca **create a repository** ahí mismo).
3. **Publish repository**. Puede ser **privado**.

> El archivo `.gitignore` ya evita que se suban `local.properties`, `.env` y `node_modules`.
> **Nunca** subas las cadenas de conexión al repositorio.

## 4. Servidor en Render

1. Entra a <https://render.com> → **Get Started** → entra con GitHub.
2. **New + → Blueprint** → elige tu repositorio. Render lee el archivo `render.yaml` del proyecto.
3. Te pedirá dos valores:
   - `DATABASE_URL` → la cadena de Neon (paso 1).
   - `MONGODB_URI` → la cadena de Atlas (paso 2).

   `JWT_SECRET` lo genera Render solo.
4. **Apply / Deploy**. Espera unos minutos. En **Logs** deberías ver:
   ```
   [mongo] conectado
   [postgres] base de datos lista: 15 recorridos, 30 vehículos
   Servidor CONATACOCH en el puerto 10000
   ```
5. Arriba aparece la dirección del servidor, algo como `https://conatacoch-api.onrender.com`. Ábrela en el navegador
   agregando `/api/salud/db` al final. Debe responder:
   ```json
   {"ok":true,"postgres":true,"mongo":true, ...}
   ```

## 5. Que no se duerma: cron-job.org

1. Entra a <https://cron-job.org> y crea una cuenta gratis.
2. **Create cronjob**:
   - URL: `https://TU-SERVIDOR.onrender.com/api/salud`
   - Ejecutar: **cada 10 minutos**
3. Guarda.

Render da 750 horas gratis al mes, suficiente para un servidor encendido todo el mes (744 h).
`/api/salud` no toca la base de datos a propósito, así Neon puede dormir cuando nadie usa la app y no gastas su cuota.

## 6. Conectar la app

La app ya apunta a `https://conatacoch-api.onrender.com` (en `app/build.gradle.kts`). Solo ejecútala.

Para usar otro servidor, agrega en `local.properties` (raíz del proyecto):
```properties
API_URL=https://OTRO-SERVIDOR.onrender.com
```
Con `API_URL=` (vacío) la app vuelve a los datos de ejemplo en memoria (útil sin internet).
Después de cambiarlo: **File → Sync Project with Gradle Files**.

### Qué queda guardado en la base de datos

| Dato | Dónde |
|---|---|
| Cuentas de pasajeros, conductores y administración (contraseñas cifradas con bcrypt) | PostgreSQL · `usuarios`, `roles` |
| Conductores: datos, licencia, estado (pendiente / aprobado / rechazado) | PostgreSQL · `conductores` |
| Los 15 recorridos, sus calles y sus trazados (versiones) | PostgreSQL · `rutas`, `ruta_calles`, `ruta_trazados`, `operadores` |
| Vehículos, documentos (permiso, revisión técnica, SOAP) y asignación a recorrido y conductor | PostgreSQL · `automoviles`, `permisos_circulacion`, `revisiones_tecnicas`, `seguros_obligatorios`, `asignaciones` |
| Reservas (activa e historial; solo una activa por pasajero; vencen a los 3 minutos) | PostgreSQL · `reservas` |
| Turnos del conductor (los define la administración; días libres incluidos) | PostgreSQL · `turnos` |
| Servicios (cada inicio y fin de servicio) y asientos ocupados que marca el conductor | PostgreSQL · `servicios` |
| Avisos de un toque del conductor (AC-01, 02, 03, 05 y 06) | PostgreSQL · `avisos` |
| GPS del teléfono del conductor en servicio (se borra solo a las 24 h) | MongoDB · `posiciones_vehiculos` |
| Notificaciones (reserva confirmada/cancelada, nueva reserva, retraso, desvío, nueva asignación) | MongoDB · `notificaciones` |
| Registro de acciones (quién hizo qué y cuándo) | MongoDB · `auditoria` |

Los vehículos **no tienen cámaras**: asientos libres = capacidad − ocupados (los marca el conductor en su panel)
− reservas activas. Solo se puede reservar en colectivos **en servicio**. Si ves en Atlas una colección
`ocupacion_camaras` de una versión anterior, puedes borrarla: ya no se usa.

Aún son de ejemplo (no van a la base de datos): **pagos** (falta conectar Rutpay).
Mientras un vehículo no envíe GPS, la app del pasajero simula su movimiento sobre el recorrido y su tiempo de llegada.

## 7. Usar la base de datos

- **Ver los datos:** en Neon, **SQL Editor**, por ejemplo `SELECT email, nombre, created_at FROM usuarios;`.
  En Atlas, **Browse Collections**.
- **Aprobar un conductor recién registrado** (queda "pendiente de validación"): en la app, entra como
  administrador → **Conductores** → botón **Aprobar**. (También se puede en el SQL Editor de Neon:
  `UPDATE conductores SET estado = 'APROBADA' WHERE rut = '11111111-1';`)
- **Asignar un vehículo a un conductor:** administrador → **Turnos y asignación** → **Vehículo** (o en
  **Automóviles**, editando el vehículo). El conductor maneja la línea del vehículo. Sin vehículo no puede iniciar servicio.
- **Turnos:** administrador → **Turnos y asignación**: toca cada día para cambiarlo (Mañana → Tarde → Libre).
  Los días que no defines se completan solos con un ciclo de 6 días (2 de mañana, 2 libres, 2 de tarde).
- **Cuentas de prueba** (las crea el servidor la primera vez; la contraseña de prueba la entrega el equipo (no se publica en el repositorio); la del administrador se cambia con `ADMIN_PASSWORD`):
  `camila.reyes@correo.cl` (pasajero), `p.soto@correo.cl` (conductor), `admin@conatacoch.cl` (administración).

## 8. Probar el servidor en tu PC (opcional)

```bash
cd backend
cp .env.example .env      # y pega tus cadenas de Neon y Atlas
npm install
npm run dev               # http://localhost:3000/api/salud/db
```
Desde el emulador, en `local.properties`: `API_URL=http://10.0.2.2:3000`.

## Actualizar el servidor después de un cambio

Render vuelve a instalar el servidor cada vez que subes cambios a GitHub:

1. En GitHub Desktop: escribe un resumen (por ejemplo "Admin y conductor con base de datos") → **Commit to main** → **Push origin**.
2. En Render → tu servicio → **Events**: espera "Deploy live" (2–4 minutos).
3. Revisa `https://TU-SERVIDOR.onrender.com/api/salud/db`.

Al arrancar, el servidor agrega solo las tablas y columnas nuevas; **no borra datos**.

## Problemas comunes

| Síntoma | Causa y solución |
|---|---|
| La app muestra "No se pudo conectar con el servidor" | Revisa `API_URL` (debe empezar con `https://` y sin `/` al final) y abre `/api/salud` en el navegador. |
| La primera acción tarda ~1 minuto | Render estaba dormido. Revisa que el cronjob esté activo y apunte a `/api/salud`. |
| En Logs de Render: `password authentication failed` | La `DATABASE_URL` está mal copiada. Cópiala de nuevo desde Neon → Connect. |
| `"mongo": false` en `/api/salud/db` | Revisa la `MONGODB_URI` (contraseña sin `<` `>`) y que Atlas permita `0.0.0.0/0` en Network Access. |
| Neon dice que se acabó la cuota del mes | El plan gratis trae 100 horas de cómputo al mes por proyecto; con uso normal de un proyecto universitario sobra. Se reinicia cada mes. |
