# Guía de Instalación y Configuración · CONATACOCH

Esta guía detalla los requisitos, configuración de entorno y pasos para poner en marcha tanto el servidor backend como la aplicación Android en desarrollo y demostración.

## 1. Requisitos Previos
- **Android Studio** (Koala / Jellyfish o superior con JDK 17+).
- **Node.js** (v18 o superior) y **npm**.
- **Docker y Docker Compose** (opcional, para levantar PostgreSQL y MongoDB con un solo comando).
- Dispositivo Android físico o emulador Android (API 26 o superior).

---

> **Base de datos real y servidor 24/7:** sigue `DESPLIEGUE.md` (Neon + MongoDB Atlas + Render, gratis).
> Lo que sigue en esta sección es solo para correr el servidor en tu PC.

## 2. Configuración del Servidor Backend (Node.js + Express + Socket.IO)

1. Ubícate en la carpeta del backend:
   ```bash
   cd backend
   ```
2. Instala las dependencias:
   ```bash
   npm install
   ```
3. Copia `backend/.env.example` como `backend/.env` y completa `DATABASE_URL` (PostgreSQL) y `MONGODB_URI` (MongoDB).
   Las tablas y los datos iniciales se crean solos al arrancar.
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

## 3.1 Mapa real (OpenStreetMap, gratis y sin clave)

El mapa del pasajero usa **OpenStreetMap** con la librería **osmdroid**. No necesita clave, cuenta ni tarjeta.
Los 15 recorridos oficiales se dibujan sobre las calles reales así:

1. La app toma las calles de `ruta_calles.csv` (copiadas en `data/local/CatalogoRecorridos.kt`).
2. Con **Overpass API** busca esas calles en OpenStreetMap y el punto exacto donde se cruza cada par de calles seguidas.
3. Con **OSRM** une esos cruces siguiendo las calles (ruta en auto).
4. El resultado se guarda en el teléfono: la primera vez que eliges un recorrido tarda unos segundos;
   después aparece al instante, incluso sin internet.

Requisitos: que el teléfono o emulador tenga internet. No hay que configurar nada más.

Notas:
- Overpass y OSRM son servidores públicos gratuitos pensados para pruebas. Si alguno está lento o caído,
  el mapa muestra "No se pudo cargar el recorrido" con un botón **Reintentar**.
- Antes de presentar, abre cada recorrido una vez para que quede guardado en el teléfono.
- Si un tramo se dibuja raro, casi siempre es porque el nombre de la calle en el CSV no coincide con el de
  OpenStreetMap (puedes revisarlo en openstreetmap.org). Corrige el CSV y regenera el catálogo con
  `python3 backend/database/seed/generar_catalogo.py`, y luego borra los datos de la app para recalcular.
- Para una app en producción, el backend debería calcular los trazados una vez y guardarlos en la base de datos.

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
  - **Administrador:** `admin@conatacoch.cl`
  - **Conductor:** `p.soto@correo.cl` (Vehículo patente `ABCD-12`, Ruta `235003`)
  - **Pasajero:** `camila.reyes@correo.cl` (o modo invitado)
  - Contraseña: la contraseña de prueba la entrega el equipo (no se publica en el repositorio).
- Para probar el tiempo real (GPS del conductor reflejado en el pasajero y administrador):
  1. Inicia sesión como **Conductor**, cambia el estado a **"En servicio"**.
  2. Inicia sesión como **Pasajero** o **Administrador** en otro emulador/dispositivo y selecciona la línea correspondiente para ver el movimiento en el mapa interactivo con osmdroid y cálculo de ETA.
