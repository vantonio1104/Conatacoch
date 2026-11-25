// Servidor REST + WebSocket de CONATACOCH.
//  - PostgreSQL (Neon): usuarios, conductores, recorridos, vehículos, reservas, turnos, servicios y avisos.
//  - MongoDB (Atlas): GPS de los vehículos, ocupación, notificaciones y auditoría.
// Variables de entorno: ver .env.example e INSTALACION.md.
require('dotenv').config();
const path = require('path');
const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const jwt = require('jsonwebtoken');
const cors = require('cors');

const mongo = require('./db/mongo');
const { inicializarBaseDeDatos } = require('./db/init');
const { JWT_SECRET, ErrorHttp, fechaIso } = require('./util');

const app = express();
const server = http.createServer(app);
// CORS: la app Android no lo necesita; solo se abre a los orígenes web que se configuren (CORS_ORIGIN).
const origenes = (process.env.CORS_ORIGIN || '').split(',').map((o) => o.trim()).filter(Boolean);
const opcionesCors = { origin: origenes.length ? origenes : false };
const io = new Server(server, { cors: opcionesCors });
app.locals.io = io;

app.set('trust proxy', 1); // Render pone un proxy delante: así req.ip es la IP real del cliente
app.use(cors(opcionesCors));
// El registro de conductor trae las 2 fotos de la licencia (hasta 1 MB cada una, en base64).
app.use('/api/auth/registro-conductor', express.json({ limit: '3mb' }));
// Los trazados de los recorridos pueden pesar algunos cientos de KB.
app.use(express.json({ limit: '2mb' }));

// Panel web de administración: archivos estáticos con una política de seguridad estricta (sin scripts en línea).
const CSP_PANEL = [
  "default-src 'self'", "script-src 'self'", "style-src 'self'", "img-src 'self' data: blob: https://tile.openstreetmap.org",
  "connect-src 'self'", "font-src 'self'", "object-src 'none'", "base-uri 'none'", "form-action 'self'", "frame-ancestors 'none'",
].join('; ');
app.use('/admin', (req, res, next) => {
  res.set({ 'Content-Security-Policy': CSP_PANEL, 'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'no-referrer' });
  next();
}, express.static(path.join(__dirname, 'public', 'admin')));
// Leaflet (mapa global del panel) se entrega desde aquí, no desde un CDN, para mantener la CSP estricta.
app.use('/admin/vendor/leaflet', express.static(path.join(__dirname, 'node_modules', 'leaflet', 'dist')));

app.use(require('./rutas/publico').router);
app.use(require('./rutas/auth'));
app.use(require('./rutas/pasajero').router);
app.use(require('./rutas/conductor').router);
app.use(require('./rutas/admin').router);
app.use(require('./rutas/reportes').router);

// --- Errores ---

app.use((req, res) => res.status(404).json({ error: 'Ruta no encontrada' }));
app.use((err, req, res, next) => { // eslint-disable-line no-unused-vars
  if (err instanceof ErrorHttp) return res.status(err.status).json({ error: err.message });
  if (err.type === 'entity.parse.failed') return res.status(400).json({ error: 'JSON inválido' });
  if (err.type === 'entity.too.large') return res.status(413).json({ error: 'Datos demasiado grandes' });
  console.error(err);
  res.status(500).json({ error: 'Error interno del servidor' });
});

// --- WebSocket: posición en vivo de los colectivos ---

io.use((socket, next) => {
  const token = socket.handshake.auth.token;
  if (!token) return next(new Error('Autenticación WebSocket requerida'));
  jwt.verify(token, JWT_SECRET, (err, decoded) => {
    if (err) return next(new Error('Token WebSocket inválido'));
    socket.user = decoded;
    next();
  });
});

io.on('connection', (socket) => {
  socket.on('subscribe_route', (folio) => socket.join(`route_${folio}`));
  socket.on('unsubscribe_route', (folio) => socket.leave(`route_${folio}`));
});

// --- Arranque ---

async function iniciar() {
  try {
    await mongo.conectarMongo();
  } catch (err) {
    console.error('[mongo] no se pudo conectar (el servidor sigue sin MongoDB):', err.message);
  }
  await inicializarBaseDeDatos();
  const PORT = process.env.PORT || 3000;
  server.listen(PORT, '0.0.0.0', () => console.log(`Servidor CONATACOCH en el puerto ${PORT}`));
}

if (require.main === module) {
  iniciar().catch((err) => {
    console.error('No se pudo iniciar el servidor:', err);
    process.exit(1);
  });
}

module.exports = { app, server, iniciar, fechaIso };
