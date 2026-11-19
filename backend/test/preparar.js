// Prepara el entorno de pruebas: usa TEST_DATABASE_URL y se niega a correr contra otra base.
// Uso: TEST_DATABASE_URL=postgresql://.../conatacoch_test npm test
const url = process.env.TEST_DATABASE_URL;
if (!url || !/test/i.test(url)) {
  throw new Error('Define TEST_DATABASE_URL con una base de PRUEBA (su nombre debe contener "test"). Nunca la de producción.');
}
process.env.DATABASE_URL = url;
process.env.JWT_SECRET = 'clave-solo-para-pruebas';
delete process.env.MONGODB_URI; // MongoDB no es necesario: la auditoría se omite en las pruebas
module.exports = {};
