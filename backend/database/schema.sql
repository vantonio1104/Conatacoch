-- DDL PostgreSQL para CONATACOCH
-- Requerimiento 4a: Base de Datos Relacional
--
-- Se puede ejecutar varias veces sin borrar datos (IF NOT EXISTS).
-- El servidor lo ejecuta solo al arrancar (backend/db/init.js).

CREATE TABLE IF NOT EXISTS roles (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(50) UNIQUE NOT NULL -- ADMIN, CONDUCTOR, PASAJERO
);

CREATE TABLE IF NOT EXISTS usuarios (
    id SERIAL PRIMARY KEY,
    email VARCHAR(150) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nombre VARCHAR(200) NOT NULL,
    rol_id INT REFERENCES roles(id) NOT NULL,
    verificado BOOLEAN DEFAULT FALSE,
    activo BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS operadores (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(150) UNIQUE NOT NULL,
    contacto VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS rutas (
    id SERIAL PRIMARY KEY,
    folio VARCHAR(50) UNIQUE NOT NULL,
    linea VARCHAR(100) NOT NULL,
    tipo_servicio VARCHAR(100) NOT NULL,
    operador_id INT REFERENCES operadores(id),
    flota_vigente INT CHECK (flota_vigente >= 0) NOT NULL,
    origen_comuna VARCHAR(100) NOT NULL,
    origen_calle VARCHAR(150) NOT NULL,
    destino_comuna VARCHAR(100) NOT NULL,
    destino_calle VARCHAR(150) NOT NULL,
    comunas_que_atraviesa TEXT,
    tarifa INT CHECK (tarifa > 0) NOT NULL DEFAULT 800
);

CREATE TABLE IF NOT EXISTS ruta_calles (
    id SERIAL PRIMARY KEY,
    ruta_folio VARCHAR(50) REFERENCES rutas(folio) ON DELETE CASCADE,
    sentido VARCHAR(20) CHECK (sentido IN ('IDA', 'REGRESO')) NOT NULL,
    orden INT NOT NULL,
    calle VARCHAR(150) NOT NULL,
    comuna VARCHAR(100) NOT NULL,
    UNIQUE (ruta_folio, sentido, orden)
);

CREATE TABLE IF NOT EXISTS ruta_trazados (
    id SERIAL PRIMARY KEY,
    ruta_folio VARCHAR(50) REFERENCES rutas(folio) ON DELETE CASCADE,
    sentido VARCHAR(20) CHECK (sentido IN ('IDA', 'REGRESO')) NOT NULL,
    geometria TEXT NOT NULL, -- GeoJSON o Polilínea codificada
    version INT DEFAULT 1,
    estado VARCHAR(50) CHECK (estado IN ('ACTIVO', 'PENDIENTE_REVISION')) DEFAULT 'ACTIVO',
    fecha_actualizacion TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS conductores (
    id SERIAL PRIMARY KEY,
    usuario_id INT REFERENCES usuarios(id) UNIQUE,
    nombre_completo VARCHAR(200) NOT NULL,
    rut VARCHAR(20) UNIQUE NOT NULL,
    direccion TEXT NOT NULL,
    comuna VARCHAR(100),
    fono_celular VARCHAR(25) NOT NULL,
    fono_domicilio VARCHAR(25),
    fono_emergencia VARCHAR(25),
    fecha_incorporacion DATE DEFAULT CURRENT_DATE CHECK (fecha_incorporacion <= CURRENT_DATE) NOT NULL,
    tipo_licencia VARCHAR(10) CHECK (tipo_licencia IN ('A2', 'A3')) NOT NULL,
    municipio_licencia VARCHAR(100) NOT NULL,
    fecha_obtencion_licencia DATE NOT NULL,
    duracion_licencia_anos INT,
    fecha_vencimiento_licencia DATE NOT NULL CHECK (fecha_vencimiento_licencia > fecha_obtencion_licencia),
    -- Un conductor recién registrado queda PENDIENTE hasta que la administración lo valida.
    estado VARCHAR(20) CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA')) NOT NULL DEFAULT 'PENDIENTE'
);

CREATE TABLE IF NOT EXISTS automoviles (
    id SERIAL PRIMARY KEY,
    patente VARCHAR(15) UNIQUE NOT NULL,
    marca VARCHAR(50) NOT NULL,
    modelo VARCHAR(50) NOT NULL,
    ano INT CHECK (ano >= 1990) NOT NULL,
    combustible VARCHAR(30) CHECK (combustible IN ('bencina', 'diésel', 'eléctrico', 'híbrido', 'gas')) NOT NULL,
    capacidad INT CHECK (capacidad BETWEEN 1 AND 6) NOT NULL DEFAULT 4,
    activo BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS permisos_circulacion (
    id SERIAL PRIMARY KEY,
    automovil_id INT REFERENCES automoviles(id) ON DELETE CASCADE,
    comuna VARCHAR(100) NOT NULL,
    fecha_otorgacion DATE NOT NULL,
    fecha_vencimiento DATE NOT NULL,
    CHECK (fecha_vencimiento > fecha_otorgacion)
);

CREATE TABLE IF NOT EXISTS revisiones_tecnicas (
    id SERIAL PRIMARY KEY,
    automovil_id INT REFERENCES automoviles(id) ON DELETE CASCADE,
    planta_revision VARCHAR(150) NOT NULL,
    comuna VARCHAR(100) NOT NULL,
    fecha_vencimiento DATE NOT NULL
);

CREATE TABLE IF NOT EXISTS seguros_obligatorios (
    id SERIAL PRIMARY KEY,
    automovil_id INT REFERENCES automoviles(id) ON DELETE CASCADE,
    compania VARCHAR(100) NOT NULL,
    prima NUMERIC(12,2) CHECK (prima > 0) NOT NULL,
    fecha_vencimiento DATE NOT NULL
);

-- Qué vehículo opera en qué recorrido (y con qué conductor, si ya tiene).
CREATE TABLE IF NOT EXISTS asignaciones (
    id SERIAL PRIMARY KEY,
    conductor_id INT REFERENCES conductores(id) ON DELETE SET NULL,
    automovil_id INT REFERENCES automoviles(id) ON DELETE CASCADE,
    ruta_folio VARCHAR(50) REFERENCES rutas(folio) ON DELETE CASCADE,
    fecha_asignacion TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    activo BOOLEAN DEFAULT TRUE
);

-- Reservas de asiento de los pasajeros.
CREATE TABLE IF NOT EXISTS reservas (
    id SERIAL PRIMARY KEY,
    pasajero_id INT REFERENCES usuarios(id) ON DELETE CASCADE NOT NULL,
    automovil_id INT REFERENCES automoviles(id) ON DELETE CASCADE NOT NULL,
    ruta_folio VARCHAR(50) REFERENCES rutas(folio) ON DELETE CASCADE NOT NULL,
    asientos INT CHECK (asientos BETWEEN 1 AND 4) NOT NULL DEFAULT 1,
    estado VARCHAR(20) CHECK (estado IN ('ACTIVA', 'COMPLETADA', 'EXPIRADA', 'CANCELADA')) NOT NULL DEFAULT 'ACTIVA',
    creada_en TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    actualizada_en TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Índices
CREATE INDEX IF NOT EXISTS idx_conductores_rut ON conductores(rut);
CREATE INDEX IF NOT EXISTS idx_automoviles_patente ON automoviles(patente);
CREATE INDEX IF NOT EXISTS idx_reservas_pasajero ON reservas(pasajero_id, creada_en DESC);
CREATE INDEX IF NOT EXISTS idx_reservas_automovil_activa ON reservas(automovil_id) WHERE estado = 'ACTIVA';
-- Regla de negocio: un pasajero tiene como máximo una reserva activa a la vez.
CREATE UNIQUE INDEX IF NOT EXISTS uq_reserva_activa_por_pasajero ON reservas(pasajero_id) WHERE estado = 'ACTIVA';
-- Un vehículo tiene como máximo una asignación activa.
CREATE UNIQUE INDEX IF NOT EXISTS uq_asignacion_activa_por_auto ON asignaciones(automovil_id) WHERE activo;

-- ===================== v1.2: administración y conductor =====================

-- Los documentos se pueden registrar solo con su vencimiento (lo demás es opcional).
ALTER TABLE permisos_circulacion ALTER COLUMN comuna DROP NOT NULL;
ALTER TABLE permisos_circulacion ALTER COLUMN fecha_otorgacion DROP NOT NULL;
ALTER TABLE revisiones_tecnicas ALTER COLUMN planta_revision DROP NOT NULL;
ALTER TABLE revisiones_tecnicas ALTER COLUMN comuna DROP NOT NULL;
ALTER TABLE seguros_obligatorios ALTER COLUMN compania DROP NOT NULL;
ALTER TABLE seguros_obligatorios ALTER COLUMN prima DROP NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_permiso_por_auto ON permisos_circulacion(automovil_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_revision_por_auto ON revisiones_tecnicas(automovil_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_seguro_por_auto ON seguros_obligatorios(automovil_id);

-- Turnos del conductor (un turno por día). Sin fila = día libre.
CREATE TABLE IF NOT EXISTS turnos (
    id SERIAL PRIMARY KEY,
    conductor_id INT REFERENCES conductores(id) ON DELETE CASCADE NOT NULL,
    fecha DATE NOT NULL,
    ruta_folio VARCHAR(50) REFERENCES rutas(folio) ON DELETE CASCADE NOT NULL,
    automovil_id INT REFERENCES automoviles(id) ON DELETE CASCADE NOT NULL,
    hora_inicio TIME NOT NULL,
    hora_fin TIME NOT NULL,
    UNIQUE (conductor_id, fecha)
);

-- Cada vez que un conductor inicia y finaliza su servicio.
CREATE TABLE IF NOT EXISTS servicios (
    id SERIAL PRIMARY KEY,
    conductor_id INT REFERENCES conductores(id) ON DELETE CASCADE NOT NULL,
    automovil_id INT REFERENCES automoviles(id) ON DELETE CASCADE NOT NULL,
    ruta_folio VARCHAR(50) REFERENCES rutas(folio) ON DELETE CASCADE NOT NULL,
    inicio TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    fin TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_servicio_abierto_conductor ON servicios(conductor_id) WHERE fin IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_servicio_abierto_auto ON servicios(automovil_id) WHERE fin IS NULL;

-- Avisos de un toque del conductor (AC-01 a AC-06).
CREATE TABLE IF NOT EXISTS avisos (
    id SERIAL PRIMARY KEY,
    conductor_id INT REFERENCES conductores(id) ON DELETE SET NULL,
    automovil_id INT REFERENCES automoviles(id) ON DELETE SET NULL,
    ruta_folio VARCHAR(50) REFERENCES rutas(folio) ON DELETE SET NULL,
    codigo VARCHAR(10) CHECK (codigo IN ('AC-01', 'AC-02', 'AC-03', 'AC-04', 'AC-05', 'AC-06')) NOT NULL,
    titulo VARCHAR(100) NOT NULL,
    creado_en TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_avisos_fecha ON avisos(creado_en DESC);

-- ===================== v1.3: sin cámaras =====================
-- Los vehículos no tienen cámaras: el conductor marca en su panel cuántos pasajeros van a bordo.
-- Asientos libres = capacidad − ocupados − reservas activas (las reservas vencen a los 3 minutos).
ALTER TABLE servicios ADD COLUMN IF NOT EXISTS ocupados INT NOT NULL DEFAULT 0 CHECK (ocupados >= 0);

-- ===================== v1.4: turnos editables por la administración =====================
-- Un día puede quedar marcado como libre (libre = TRUE, sin vehículo ni horario).
ALTER TABLE turnos ALTER COLUMN ruta_folio DROP NOT NULL;
ALTER TABLE turnos ALTER COLUMN automovil_id DROP NOT NULL;
ALTER TABLE turnos ALTER COLUMN hora_inicio DROP NOT NULL;
ALTER TABLE turnos ALTER COLUMN hora_fin DROP NOT NULL;
ALTER TABLE turnos ADD COLUMN IF NOT EXISTS libre BOOLEAN NOT NULL DEFAULT FALSE;

-- Corrección única de los datos de ejemplo (defecto D-08): los RUT de prueba tenían un dígito verificador
-- inválido y la licencia del conductor de prueba estaba vencida desde 2020. No toca otros registros.
UPDATE conductores SET rut = '12345678-5'
 WHERE rut = '12345678-9' AND NOT EXISTS (SELECT 1 FROM conductores WHERE rut = '12345678-5');
UPDATE conductores SET rut = '9876543-3'
 WHERE rut = '9876543-K' AND NOT EXISTS (SELECT 1 FROM conductores WHERE rut = '9876543-3');
UPDATE conductores SET fecha_obtencion_licencia = '2025-03-15', duracion_licencia_anos = 4,
       fecha_vencimiento_licencia = '2029-03-15'
 WHERE rut = '12345678-5' AND fecha_vencimiento_licencia = '2020-03-15';
