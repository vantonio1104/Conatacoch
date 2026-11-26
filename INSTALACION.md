# Instalación y despliegue · CONATACOCH

Cómo poner en marcha el servidor, la base de datos, el panel web de administración y la app Android.

```
App Android ─┐                                  ┌─▶ PostgreSQL (Neon)   cuentas, conductores, vehículos, turnos, reservas
             ├─HTTPS─▶ Servidor Node (Render) ──┤
Panel web  ──┘         también entrega /admin   └─▶ MongoDB (Atlas)      GPS, notificaciones, auditoría
cron-job.org ── cada 10 min ──▶ /api/salud  (para que Render no duerma el servidor)
```

Todos los servicios son gratis y **ninguno pide tarjeta**. Necesitas una cuenta de GitHub (sirve para entrar a los demás).

| Servicio | Qué hace | ¿Se apaga? |
|---|---|---|
| **Neon** | PostgreSQL | Se suspende tras 5 min sin uso y despierta solo en menos de un segundo. Los datos nunca se pierden. |
| **MongoDB Atlas (M0)** | MongoDB | No se apaga. |
| **Render (Free)** | Corre `backend/server.js` y el panel web | Se duerme tras 15 min sin visitas; cron-job.org lo mantiene despierto. |
| **cron-job.org** | Visita el servidor cada 10 min | No. |

## 1. Requisitos

| Para | Qué necesitas |
|---|---|
| Usar la app | Teléfono Android 8.0 (API 26) o superior, con internet y GPS |
| Usar el panel web | Navegador actual y una app autenticadora (Google Authenticator, Microsoft Authenticator u otra) |
| Compilar la app | Android Studio con JDK 17 o superior, o el SDK de Android por línea de comandos |
| Correr el servidor en tu PC | Node.js 20 o superior, npm y una base PostgreSQL (MongoDB es opcional) |
| Publicar | Cuentas gratuitas en GitHub, Neon, MongoDB Atlas, Render y cron-job.org |

Tiempo estimado para publicar por primera vez: 30–45 minutos.

## 2. Base de datos y servidor en la nube

### 2.1 PostgreSQL en Neon

1. Entra a <https://neon.com> → **Sign up** (con GitHub o Google).
2. Crea un proyecto: nombre `conatacoch`, región **AWS São Paulo (sa-east-1)** (la más cercana a Chile) y la versión
   de Postgres que venga por defecto.
3. En el panel del proyecto, botón **Connect**. Copia la cadena de conexión (empieza con `postgresql://` y termina con
   `?sslmode=require`). Es tu **DATABASE_URL**.

> No hay que crear tablas ni cargar datos: al arrancar, el servidor crea las tablas y carga los 15 recorridos
> oficiales, sus calles y los datos de ejemplo (`backend/database/schema.sql` y `backend/database/seed/*.csv`).
> Se puede ejecutar muchas veces: nunca borra datos.

### 2.2 MongoDB en Atlas

1. Entra a <https://www.mongodb.com/cloud/atlas/register> y crea una cuenta.
2. Primero crea un proyecto: **Create new project** → nombre `conatacoch` → **Next** → **Create Project**.
   Sin proyecto no aparece la opción de crear el cluster.
3. Dentro del proyecto: **Create** (o **Build a Cluster**) → **Free (M0)**, proveedor AWS, región
   **São Paulo (sa-east-1)** → **Create Deployment**. Si São Paulo no está en el plan gratis, usa la región más cercana.
4. Crea un **usuario de base de datos** (Atlas suele crear uno solo al crear el cluster: sirve) y anota usuario y contraseña (solo letras y números, para evitar problemas en la URL).
5. Candado 🔒 (**Security**) → **Database & Network Access** → **IP Access List** (en versiones antiguas: **Network Access**)
   → **Add IP Address** → **Allow Access from Anywhere** (`0.0.0.0/0`) → **Confirm**. Render cambia de IP; la
   seguridad la dan el usuario y la contraseña.
6. Pantalla del cluster → **Connect** → **Drivers** → **Node.js** → copia la cadena `mongodb+srv://...` y reemplaza `<db_password>` por la
   contraseña. Es tu **MONGODB_URI**.

### 2.3 Repositorio en GitHub

Render instala el servidor desde GitHub. Con **GitHub Desktop** (<https://desktop.github.com>): **File → Add local
repository** → carpeta `Conatacoch` → **Publish repository** (puede ser privado).

> `.gitignore` ya evita que se suban `local.properties`, `.env` y `node_modules`. **Nunca** subas las cadenas de
> conexión ni contraseñas al repositorio.

### 2.4 Servidor en Render

1. Entra a <https://render.com> con GitHub → **New + → Blueprint** → elige el repositorio (Render lee `render.yaml`).
2. Completa las variables:
   - `DATABASE_URL` → la cadena de Neon.
   - `MONGODB_URI` → la cadena de Atlas.
   - **`ADMIN_PASSWORD`** → contraseña real del administrador `admin@conatacoch.cl`, de 15 a 128 caracteres. Sin ella
     queda la contraseña de prueba, y el panel es público: **defínela siempre**.
   - `JWT_SECRET` lo genera Render solo.
3. **Apply / Deploy**. En **Logs** deberías ver:
   ```
   [mongo] conectado
   [postgres] base de datos lista: 15 recorridos, 30 vehículos
   Servidor CONATACOCH en el puerto 10000
   ```
4. Abre `https://TU-SERVIDOR.onrender.com/api/salud/db`. Debe responder `{"ok":true,"postgres":true,"mongo":true, ...}`.

### 2.5 Que no se duerma: cron-job.org

En <https://cron-job.org> crea un cronjob a `https://TU-SERVIDOR.onrender.com/api/salud` **cada 10 minutos**.
Render da 750 horas gratis al mes (un mes encendido son 744 h). `/api/salud` no toca la base de datos, así Neon puede
dormir cuando nadie usa la app.

## 3. App Android

1. Abre el proyecto en **Android Studio** y espera la sincronización de Gradle.
2. La app ya apunta a `https://conatacoch-api.onrender.com` (en `app/build.gradle.kts`). Para usar otro servidor,
   agrega en `local.properties` (raíz del proyecto) y sincroniza Gradle:
   ```properties
   API_URL=https://OTRO-SERVIDOR.onrender.com
   ```
   - Desde el emulador, con el servidor en tu PC: `API_URL=http://10.0.2.2:3000`.
   - Desde un teléfono en la misma WiFi: `API_URL=http://IP-DE-TU-PC:3000` (por ejemplo `http://192.168.1.50:3000`).
   - Con `API_URL=` (vacío) la app usa datos de ejemplo en memoria, sin internet.
3. Compila, prueba e instala:
   ```bash
   ./gradlew assembleDebug        # APK en app/build/outputs/apk/debug/app-debug.apk
   ./gradlew testDebugUnitTest    # pruebas JUnit
   ./gradlew installDebug         # instala en el emulador o teléfono conectado
   ```
   Sin Android Studio, basta el SDK de línea de comandos: `sdk.dir=/ruta/al/sdk` en `local.properties`.
4. Para instalar el APK a mano: cópialo al teléfono, permite «Instalar apps desconocidas» y ábrelo.

## 4. Panel web de administración

- Dirección: `https://conatacoch-api.onrender.com/admin` (o `http://localhost:3000/admin` con el servidor en tu PC).
- Se entra con correo, contraseña y el código de 6 dígitos de una app autenticadora. **La primera vez** el panel muestra
  un código QR y una clave: regístralos en la app autenticadora y confirma con un código.
- Los administradores nuevos se crean en **Cuentas**: reciben una contraseña temporal (se muestra una sola vez) que
  deben cambiar al entrar.
- La sesión se cierra tras 30 minutos sin actividad y dura como máximo 8 horas.
- Si un administrador pierde su teléfono, hoy su TOTP se restablece solo desde el SQL Editor de Neon:
  `UPDATE usuarios SET totp_secreto = NULL, totp_activo = FALSE WHERE email = 'correo@conatacoch.cl';`

## 5. Servidor en tu PC (opcional)

```bash
cd backend
cp .env.example .env      # completa DATABASE_URL, JWT_SECRET, ADMIN_PASSWORD y, si lo usas, MONGODB_URI
npm install
npm run dev               # http://localhost:3000/api/salud/db y http://localhost:3000/admin
```

Las pruebas del servidor y del panel están en `PLAN_DE_PRUEBAS.md`.

## 6. Mapa real (OpenStreetMap, gratis y sin clave)

El mapa usa **OpenStreetMap** con **osmdroid** en la app y **Leaflet** en el panel; no necesita clave ni tarjeta.
Si la administración no guardó un trazado para un recorrido, la app lo calcula así:

1. Toma las calles de `ruta_calles.csv` (copiadas en `data/local/CatalogoRecorridos.kt`).
2. Con **Overpass API** busca esas calles y el punto donde se cruza cada par de calles seguidas.
3. Con **OSRM** une esos cruces siguiendo las calles, y guarda el resultado en el teléfono: la primera vez tarda unos
   segundos y después aparece al instante, incluso sin internet.

Overpass y OSRM son servidores públicos de prueba: si están lentos, el mapa muestra «No se pudo cargar el recorrido»
con **Reintentar**. Antes de presentar, abre cada recorrido una vez. Si un tramo se dibuja raro, revisa que el nombre
de la calle del CSV coincida con OpenStreetMap, corrígelo y regenera el catálogo con
`python3 backend/database/seed/generar_catalogo.py`.

## 7. Qué queda guardado

| Dato | Dónde |
|---|---|
| Cuentas (contraseñas con bcrypt), sesiones revocables y TOTP de la administración | PostgreSQL · `usuarios`, `roles`, `sesiones` |
| Conductores: datos, licencia, estado y fotos de la licencia | PostgreSQL · `conductores`, `licencia_fotos` |
| Los 15 recorridos, sus calles, trazados y tarifas | PostgreSQL · `rutas`, `ruta_calles`, `ruta_trazados`, `operadores` |
| Vehículos, documentos (permiso, revisión técnica, SOAP) y vínculo con recorrido y conductor | PostgreSQL · `automoviles`, `permisos_circulacion`, `revisiones_tecnicas`, `seguros_obligatorios`, `asignaciones` |
| Turnos con fecha, horario, vehículo y recorrido (sin turno = día libre) | PostgreSQL · `turnos` |
| Reservas (una activa por pasajero; vencen a los 3 minutos) | PostgreSQL · `reservas` |
| Servicios (inicio y fin) y asientos ocupados que marca el conductor | PostgreSQL · `servicios` |
| Avisos de un toque del conductor (AC-01, 02, 03, 05 y 06) con su ubicación | PostgreSQL · `avisos` |
| GPS del conductor en servicio (se borra solo a las 24 h) | MongoDB · `posiciones_vehiculos` |
| Notificaciones (se borran a los 30 días) | MongoDB · `notificaciones` |
| Registro de acciones (quién hizo qué y cuándo) | MongoDB · `auditoria` |

Los vehículos **no tienen cámaras**: asientos libres = capacidad − ocupados − reservas activas. Solo se reserva en
colectivos **en servicio**. Los **pagos** aún son de ejemplo (falta conectar Rutpay). Mientras un vehículo no envíe GPS,
la app del pasajero simula su movimiento sobre el recorrido.

## 8. Demostración

- **Cuentas de prueba** (las crea el servidor; la contraseña de prueba la entrega el equipo y no se publica):
  `camila.reyes@correo.cl` (pasajero), `p.soto@correo.cl` (conductor, vehículo `ABCD-12`, línea 5003) y
  `admin@conatacoch.cl` (administración, en el panel web). El pasajero también puede entrar sin cuenta.
- **Aprobar a un conductor recién registrado:** panel → **Validar conductores** → recorrido y vehículo → **Aprobar y vincular**.
- **Turnos:** panel → **Asignar rutas** o **Salidas y turnos**. El conductor solo inicia servicio dentro de un turno
  vigente (o 15 minutos antes). Pedro Soto recibe turnos de 06:00 a 22:00 los próximos 14 días.
- **Tiempo real:** el conductor toca **Iniciar servicio**; en otro teléfono o emulador, el pasajero elige la línea en el
  mapa y ve el colectivo moverse; en el panel, **Mapa en vivo**.
- **Ver los datos:** Neon → **SQL Editor** (`SELECT email, nombre FROM usuarios;`); Atlas → **Browse Collections**.

## 9. Actualizar el servidor

Render publica solo cada cambio que llega a la rama `main` de GitHub: haz **Commit** y **Push**, espera «Deploy live»
en **Events** (2–4 minutos) y revisa `/api/salud/db`. Al arrancar, el servidor agrega las tablas y columnas nuevas sin
borrar datos. Para volver atrás, publica de nuevo un commit anterior desde Render.

## 10. Problemas comunes

| Síntoma | Causa y solución |
|---|---|
| La app muestra «No se pudo conectar con el servidor» | Revisa `API_URL` (con `https://` y sin `/` al final) y abre `/api/salud` en el navegador. |
| La primera acción tarda ~1 minuto | Render estaba dormido. Revisa que el cronjob esté activo y apunte a `/api/salud`. |
| En Logs de Render: `password authentication failed` | La `DATABASE_URL` está mal copiada. Cópiala de nuevo desde Neon → Connect. |
| `"mongo": false` en `/api/salud/db` | Revisa la `MONGODB_URI` (contraseña sin `<` `>`) y que Atlas permita `0.0.0.0/0`. |
| El panel dice «Código incorrecto» | La hora del teléfono debe estar en automático; tras 5 fallos hay que esperar 15 minutos. |
| Neon dice que se acabó la cuota del mes | El plan gratis trae 100 horas de cómputo al mes; se reinicia cada mes. |
