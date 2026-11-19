// Cliente de la API del servidor. La sesión vive en sessionStorage: se borra al cerrar la pestaña.
const CLAVE = 'conatacoch.sesion';

export const sesion = {
  leer() {
    try { return JSON.parse(sessionStorage.getItem(CLAVE)); } catch { return null; }
  },
  guardar(datos) { sessionStorage.setItem(CLAVE, JSON.stringify(datos)); },
  borrar() { sessionStorage.removeItem(CLAVE); },
};

/** Error con el mensaje para el usuario que manda el servidor. */
export class ErrorApi extends Error {
  constructor(status, mensaje) {
    super(mensaje);
    this.status = status;
  }
}

let alVencer = () => {};
/** Qué hacer cuando el servidor responde 401 con una sesión iniciada (vencida, revocada o por inactividad). */
export function siVenceLaSesion(fn) { alVencer = fn; }

async function pedir(metodo, ruta, cuerpo, { comoArchivo = false } = {}) {
  const s = sesion.leer();
  const cabeceras = {};
  if (s?.token) cabeceras.Authorization = `Bearer ${s.token}`;
  if (cuerpo !== undefined) cabeceras['Content-Type'] = 'application/json';
  let r;
  try {
    r = await fetch(ruta, { method: metodo, headers: cabeceras, body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo) });
  } catch {
    throw new ErrorApi(0, 'No hay conexión con el servidor. Revisa tu internet e inténtalo de nuevo.');
  }
  if (r.status === 401 && s?.token) {
    alVencer();
    throw new ErrorApi(401, 'Tu sesión se cerró. Vuelve a iniciar sesión.');
  }
  if (!r.ok) {
    const datos = await r.json().catch(() => ({}));
    throw new ErrorApi(r.status, datos.error || 'Ocurrió un error en el servidor. Inténtalo de nuevo.');
  }
  if (comoArchivo) return r.blob();
  if (r.status === 204) return null;
  return r.json();
}

export const api = {
  get: (ruta) => pedir('GET', ruta),
  post: (ruta, cuerpo = {}) => pedir('POST', ruta, cuerpo),
  put: (ruta, cuerpo = {}) => pedir('PUT', ruta, cuerpo),
  delete: (ruta) => pedir('DELETE', ruta),
  archivo: (ruta) => pedir('GET', ruta, undefined, { comoArchivo: true }),
};
