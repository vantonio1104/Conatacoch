const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const jwt = require('jsonwebtoken');
const bcrypt = require('bcrypt');
const cors = require('cors');
require('dotenv').config();

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: { origin: '*' }
});

app.use(cors());
app.use(express.json());

const JWT_SECRET = process.env.JWT_SECRET || 'conatacoch_secret_key_2026';

// Middleware de Autenticación JWT para REST
function authenticateToken(req, res, next) {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];
  if (!token) return res.status(401).json({ error: 'Token requerido' });

  jwt.verify(token, JWT_SECRET, (err, user) => {
    if (err) return res.status(403).json({ error: 'Token inválido o expirado' });
    req.user = user;
    next();
  });
}

function requireRole(role) {
  return (req, res, next) => {
    if (!req.user || req.user.rol !== role) {
      return res.status(403).json({ error: 'Acceso no autorizado para este rol' });
    }
    next();
  };
}

// Mock database in-memory / connected to PostgreSQL for demo simplicity
let usuarios = [
  { id: 1, email: 'admin@conatacoch.cl', rol: 'ADMIN', passwordHash: bcrypt.hashSync('colectivo-demo-2026', 10) },
  { id: 2, email: 'p.soto@correo.cl', rol: 'CONDUCTOR', rut: '12345678-9', passwordHash: bcrypt.hashSync('colectivo-demo-2026', 10) },
  { id: 3, email: 'camila.reyes@correo.cl', rol: 'PASAJERO', passwordHash: bcrypt.hashSync('colectivo-demo-2026', 10) }
];

let conductores = [
  { id: 1, nombreCompleto: 'Pedro Soto', rut: '12345678-9', direccion: 'Av. La Marina 123', fonoCelular: '+56912345678', fonoDomicilio: '225554433', fechaIncorporacion: '2023-03-15', tipoLicencia: 'A2', municipioLicencia: 'San Ramón', fechaObtencionLicencia: '2015-03-15', duracionLicenciaAnos: 5, fechaVencimientoLicencia: '2020-03-15' }
];

let automoviles = [
  { id: 1, patente: 'ABCD-12', marca: 'Nissan', modelo: 'Versa', ano: 2022, combustible: 'bencina', conductorId: 1, rutaFolio: '235003', activo: true, revisionTecnicaVencimiento: '2027-05-15', seguroVencimiento: '2027-03-30', permisoVencimiento: '2027-04-01' }
];

let rutas = [
  { folio: '235003', linea: '5003', tipoServicio: 'Automóvil urbano', operador: 'Trans-Uruguay S.A.', flotaVigente: 30, origenComuna: 'La Cisterna', origenCalle: 'José Ureta', destinoComuna: 'La Granja', destinoCalle: 'Santa Rosa', comunasQueAtraviesa: 'La Cisterna, La Granja' }
];

// Rutas de Autenticación
app.post('/api/auth/login', async (req, res) => {
  const { email, password } = req.body;
  const usuario = usuarios.find(u => u.email === email);
  if (!usuario || !await bcrypt.compare(password, usuario.passwordHash)) {
    return res.status(401).json({ error: 'Credenciales inválidas' });
  }
  const token = jwt.sign({ id: usuario.id, email: usuario.email, rol: usuario.rol, rut: usuario.rut }, JWT_SECRET, { expiresIn: '24h' });
  res.json({ token, rol: usuario.rol, email: usuario.email });
});

// Endpoints Conductores
app.get('/api/conductores', authenticateToken, (req, res) => {
  res.json(conductores);
});

app.post('/api/conductores', authenticateToken, requireRole('ADMIN'), (req, res) => {
  const nuevo = { id: conductores.length + 1, ...req.body };
  conductores.push(nuevo);
  res.status(201).json(nuevo);
});

// Endpoints Automóviles
app.get('/api/automoviles', authenticateToken, (req, res) => {
  res.json(automoviles);
});

app.post('/api/automoviles', authenticateToken, requireRole('ADMIN'), (req, res) => {
  const nuevo = { id: automoviles.length + 1, ...req.body };
  automoviles.push(nuevo);
  res.status(201).json(nuevo);
});

// Endpoints Rutas
app.get('/api/rutas', authenticateToken, (req, res) => {
  res.json(rutas);
});

// WebSocket Connection & Real-Time Tracking
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
  console.log(`Usuario conectado via WebSocket: ${socket.user.email} (${socket.user.rol})`);

  // Suscripción a sala de ruta (Pasajero o Admin)
  socket.on('subscribe_route', (folio) => {
    socket.join(`route_${folio}`);
    console.log(`Socket ${socket.id} se suscribió a ruta_${folio}`);
  });

  socket.on('unsubscribe_route', (folio) => {
    socket.leave(`route_${folio}`);
  });

  // Conductor envía su posición GPS
  socket.on('update_position', (data) => {
    if (socket.user.rol !== 'CONDUCTOR') {
      return socket.emit('error', { message: 'Solo los conductores pueden publicar posiciones (403)' });
    }
    const { rutaFolio, lat, lng, rumbo, velocidad, sentido } = data;
    const posicionActualizada = {
      rutConductor: socket.user.rut,
      lat,
      lng,
      rumbo,
      velocidad,
      sentido,
      timestamp: Date.now()
    };
    // Retransmitir a todos los pasajeros y admins suscritos a esta ruta
    io.to(`route_${rutaFolio}`).emit('position_update', posicionActualizada);
  });

  socket.on('disconnect', () => {
    console.log(`Usuario desconectado: ${socket.id}`);
  });
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, '0.0.0.0', () => {
  console.log(`Servidor CONATACOCH ejecutándose en puerto ${PORT}`);
});
