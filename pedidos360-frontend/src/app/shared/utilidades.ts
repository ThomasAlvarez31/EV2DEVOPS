import { Producto } from '../models/producto.model';

/** Imagen usada cuando un producto no tiene imagenUrl en el backend. */
export const IMAGEN_POR_DEFECTO =
  'https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=600&q=80';

/** Devuelve la imagen del producto o la imagen por defecto. */
export function imagenDe(producto: Producto): string {
  return producto.imagenUrl?.trim() ? producto.imagenUrl : IMAGEN_POR_DEFECTO;
}

/**
 * Extrae un mensaje legible de una respuesta de error HTTP.
 * Los microservicios devuelven el mensaje como texto plano en `error`.
 */
export function mensajeDeError(err: any, porDefecto: string): string {
  if (typeof err?.error === 'string' && err.error.trim()) {
    return err.error;
  }
  if (typeof err?.error?.message === 'string' && err.error.message.trim()) {
    return err.error.message;
  }
  if (err?.status === 0) {
    return 'No hay conexión con el servidor. Verifica que los microservicios estén activos.';
  }
  return err?.message || porDefecto;
}
