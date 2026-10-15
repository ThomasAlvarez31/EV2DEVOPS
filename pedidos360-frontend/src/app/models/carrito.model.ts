export interface ItemCarrito {
  id: number;
  productoId: number;
  nombreProducto: string;
  precioUnitario: number;
  cantidad: number;
  imagenUrl?: string | null;
}

export interface Carrito {
  id: number;
  usuarioId: string;
  estado: 'ACTIVO' | 'FINALIZADO';
  fechaCreacion: string;
  items: ItemCarrito[];
}

export interface AgregarItemRequest {
  productoId: number;
  nombreProducto: string;
  precioUnitario: number;
  cantidad: number;
}
