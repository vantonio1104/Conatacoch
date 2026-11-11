# Guía de Instalación y Configuración · CONATACOCH

Esta guía detalla los requisitos, configuración de entorno y pasos para poner en marcha tanto el servidor backend como la aplicación Android en desarrollo y demostración.

## 1. Requisitos Previos
- **Android Studio** (Koala / Jellyfish o superior con JDK 17+).
- **Node.js** (v18 o superior) y **npm**.
- **Docker y Docker Compose** (opcional, para levantar PostgreSQL y MongoDB con un solo comando).
- Dispositivo Android físico o emulador Android (API 26 o superior).

---

## 2. Configuración del Servidor Backend (Node.js + Express + Socket.IO)

1. Ubícate en la carpeta del backend:
   ```bash
   cd backend
   ```
2. Instala las dependencias:
   ```bash
   npm install
   ```
3. Configura las variables de entorno creando un archivo `.env`:
   ```env
   PORT=3000
   JWT_SECRET=conatacoch_secret_key_2026
   ```
4. Inicia el servidor en modo desarrollo:
   ```bash
   npm run dev
   ```

### Ejecución con Docker Compose (Opcional)
Si prefieres levantar servicios auxiliares con Docker:
```bash
docker-compose up -d
```

---

## 3. Carga de Datos Reales (Seed)
Los archivos con los 15 recorridos oficiales y 451 segmentos de calles de la Región Metropolitana se encuentran en:
- `backend/database/seed/rutas.csv`
- `backend/database/seed/ruta_calles.csv`
- `backend/database/seed/conductores_template.csv`
- `backend/database/seed/vehiculos_template.csv`

Puedes importarlos a la base de datos relacional ejecutando el script DDL ubicado en `backend/database/schema.sql`.

---

## 4. Configuración y Ejecución de la Aplicación Android

1. Abre el proyecto en **Android Studio**.
2. Sincroniza Gradle (`File > Sync Project with Gradle Files`).
3. Para conectar la app Android al backend ejecutándose en tu PC:
   - **Desde el Emulador Android:** La IP por defecto para acceder al localhost del PC es `http://10.0.2.2:3000`.
   - **Desde un Dispositivo Físico:** Conecta tu teléfono a la misma red Wi-Fi y configura la URL base con la IP local de tu PC (ej. `http://192.168.1.50:3000`).
4. Compila e instala la app en el emulador o dispositivo:
   ```bash
   ./gradlew assembleDebug
   ```

## 5. Demostración de las Tres Vistas (Administrador, Conductor, Pasajero)
- **Cuentas de Acceso:**
  - **Administrador:** `admin@conatacoch.cl` / `colectivo-demo-2026`
  - **Conductor:** `p.soto@correo.cl` / `colectivo-demo-2026` (Vehículo patente `ABCD-12`, Ruta `235003`)
  - **Pasajero:** `camila.reyes@correo.cl` / `colectivo-demo-2026` (o modo invitado)
- Para probar el tiempo real (GPS del conductor reflejado en el pasajero y administrador):
  1. Inicia sesión como **Conductor**, cambia el estado a **"En servicio"**.
  2. Inicia sesión como **Pasajero** o **Administrador** en otro emulador/dispositivo y selecciona la línea correspondiente para ver el movimiento en el mapa interactivo con osmdroid y cálculo de ETA.
