-- DDL PostgreSQL para CONATACOCH
-- Requerimiento 4a: Base de Datos Relacional

CREATE TABLE roles (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(50) UNIQUE NOT NULL -- ADMIN, CONDUCTOR, PASAJERO
);

CREATE TABLE usuarios (
    id SERIAL PRIMARY KEY,
    email VARCHAR(150) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    rol_id INT REFERENCES roles(id) NOT NULL,
    activo BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE operadores (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL,
    contacto VARCHAR(100)
);

CREATE TABLE rutas (
    id SERIAL PRIMARY KEY,
    folio VARCHAR(50) UNIQUE NOT NULL,
    linea VARCHAR(100) NOT NULL,
    tipo_servicio VARCHAR(50) NOT NULL,
    operador_id INT REFERENCES operadores(id),
    flota_vigente INT CHECK (flota_vigente >= 0) NOT NULL,
    origen_comuna VARCHAR(100) NOT NULL,
    origen_calle VARCHAR(150) NOT NULL,
    destino_comuna VARCHAR(100) NOT NULL,
    destino_calle VARCHAR(150) NOT NULL,
    comunas_que_atraviesa TEXT
);

CREATE TABLE ruta_calles (
    id SERIAL PRIMARY KEY,
    ruta_folio VARCHAR(50) REFERENCES rutas(folio) ON DELETE CASCADE,
    sentido VARCHAR(20) CHECK (sentido IN ('IDA', 'REGRESO')) NOT NULL,
    orden INT NOT NULL,
    calle VARCHAR(150) NOT NULL,
    comuna VARCHAR(100) NOT NULL,
    UNIQUE (ruta_folio, sentido, orden)
);

CREATE TABLE ruta_trazados (
    id SERIAL PRIMARY KEY,
    ruta_folio VARCHAR(50) REFERENCES rutas(folio) ON DELETE CASCADE,
    sentido VARCHAR(20) CHECK (sentido IN ('IDA', 'REGRESO')) NOT NULL,
    geometria TEXT NOT NULL, -- GeoJSON o Polilínea codificada
    version INT DEFAULT 1,
    estado VARCHAR(50) CHECK (estado IN ('ACTIVO', 'PENDIENTE_REVISION')) DEFAULT 'ACTIVO',
    fecha_actualizacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE conductores (
    id SERIAL PRIMARY KEY,
    usuario_id INT REFERENCES usuarios(id) UNIQUE,
    nombre_completo VARCHAR(200) NOT NULL,
    rut VARCHAR(20) UNIQUE NOT NULL,
    direccion TEXT NOT NULL,
    fono_celular VARCHAR(25) NOT NULL,
    fono_domicilio VARCHAR(25),
    fecha_incorporacion DATE CHECK (fecha_incorporacion <= CURRENT_DATE) NOT NULL,
    tipo_licencia VARCHAR(10) CHECK (tipo_licencia IN ('A2', 'A3')) NOT NULL,
    municipio_licencia VARCHAR(100) NOT NULL,
    fecha_obtencion_licencia DATE NOT NULL,
    duracion_licencia_anos INT NOT NULL,
    fecha_vencimiento_licencia DATE NOT NULL -- Calculada: obtencion + duracion
);

CREATE TABLE automoviles (
    id SERIAL PRIMARY KEY,
    patente VARCHAR(15) UNIQUE NOT NULL,
    marca VARCHAR(50) NOT NULL,
    modelo VARCHAR(50) NOT NULL,
    ano INT CHECK (ano >= 1990 AND ano <= EXTRACT(YEAR FROM CURRENT_DATE) + 1) NOT NULL,
    combustible VARCHAR(30) CHECK (combustible IN ('bencina', 'diésel', 'eléctrico', 'híbrido', 'gas')) NOT NULL,
    activo BOOLEAN DEFAULT TRUE
);

CREATE TABLE permisos_circulacion (
    id SERIAL PRIMARY KEY,
    automovil_id INT REFERENCES automoviles(id) ON DELETE CASCADE,
    comuna VARCHAR(100) NOT NULL,
    fecha_otorgacion DATE NOT NULL,
    fecha_vencimiento DATE NOT NULL,
    CHECK (fecha_vencimiento > fecha_otorgacion)
);

CREATE TABLE revisiones_tecnicas (
    id SERIAL PRIMARY KEY,
    automovil_id INT REFERENCES automoviles(id) ON DELETE CASCADE,
    planta_revision VARCHAR(150) NOT NULL,
    comuna VARCHAR(100) NOT NULL,
    fecha_vencimiento DATE NOT NULL
);

CREATE TABLE seguros_obligatorios (
    id SERIAL PRIMARY KEY,
    automovil_id INT REFERENCES automoviles(id) ON DELETE CASCADE,
    compania VARCHAR(100) NOT NULL,
    prima NUMERIC(12,2) CHECK (prima > 0) NOT NULL,
    fecha_vencimiento DATE NOT NULL
);

CREATE TABLE asignaciones (
    id SERIAL PRIMARY KEY,
    conductor_id INT REFERENCES conductores(id) ON DELETE CASCADE,
    automovil_id INT REFERENCES automoviles(id) ON DELETE CASCADE,
    ruta_folio VARCHAR(50) REFERENCES rutas(folio) ON DELETE CASCADE,
    fecha_asignacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    activo BOOLEAN DEFAULT TRUE
);

-- Índices en RUT y Patente
CREATE INDEX idx_conductores_rut ON conductores(rut);
CREATE INDEX idx_automoviles_patente ON automoviles(patente);
