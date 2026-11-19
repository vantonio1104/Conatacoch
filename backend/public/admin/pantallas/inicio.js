// Inicio del panel. En la tarea T11 pasa a ser el resumen con el semáforo de vigencias.
import { el } from '../ui.js';

export async function mostrar(raiz, { sesion }) {
  raiz.replaceChildren(el('section', { class: 'tarjeta' },
    el('h2', {}, `Hola, ${sesion.leer().nombre}`),
    el('p', { class: 'secundario' }, 'Elige una sección en el menú de la izquierda.')));
}
