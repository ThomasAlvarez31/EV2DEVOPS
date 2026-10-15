import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { CarritoService } from '../../services/carrito.service';
import { ToastService } from '../../services/toast.service';
import { Carrito, ItemCarrito } from '../../models/carrito.model';
import { IMAGEN_POR_DEFECTO, mensajeDeError } from '../../shared/utilidades';

@Component({
  selector: 'app-carrito',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './carrito.component.html'
})
export class CarritoComponent implements OnInit {

  carrito: Carrito | null = null;
  cargando = false;
  error: string | null = null;
  actualizando: number | null = null;

  constructor(
    private carritoService: CarritoService,
    private toastService: ToastService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.cargarCarrito();
  }

  cargarCarrito(): void {
    this.cargando = true;
    this.error = null;

    this.carritoService.obtenerCarrito().subscribe({
      next: (data) => {
        this.carrito = data;
        this.cargando = false;
      },
      error: (err) => {
        this.error = mensajeDeError(err, 'No se pudo cargar el carrito.');
        this.cargando = false;
      }
    });
  }

  get items(): ItemCarrito[] {
    return this.carrito?.items ?? [];
  }

  get total(): number {
    return this.items.reduce((acc, item) => acc + item.precioUnitario * item.cantidad, 0);
  }

  get cantidadTotal(): number {
    return this.items.reduce((acc, item) => acc + item.cantidad, 0);
  }

  cambiarCantidad(item: ItemCarrito, delta: number): void {
    const nueva = item.cantidad + delta;
    if (nueva <= 0) {
      this.eliminarItem(item);
      return;
    }
    this.actualizarCantidad(item, nueva);
  }

  actualizarCantidad(item: ItemCarrito, cantidad: number): void {
    if (cantidad <= 0 || this.actualizando === item.id) {
      return;
    }
    this.actualizando = item.id;

    this.carritoService.actualizarCantidad(item.id, cantidad).subscribe({
      next: (data) => {
        this.carrito = data;
        this.actualizando = null;
      },
      error: (err) => {
        this.toastService.error(mensajeDeError(err, 'No se pudo actualizar la cantidad.'));
        this.actualizando = null;
        this.cargarCarrito();
      }
    });
  }

  eliminarItem(item: ItemCarrito): void {
    if (this.actualizando === item.id) {
      return;
    }
    this.actualizando = item.id;

    this.carritoService.eliminarItem(item.id).subscribe({
      next: (data) => {
        this.carrito = data;
        this.actualizando = null;
        this.toastService.info(`Se eliminó "${item.nombreProducto}" del carrito.`);
      },
      error: (err) => {
        this.toastService.error(mensajeDeError(err, 'No se pudo eliminar el ítem.'));
        this.actualizando = null;
      }
    });
  }

  irAlCheckout(): void {
    if (this.items.length === 0) {
      this.toastService.error('Tu carrito está vacío: agrega productos antes de continuar.');
      return;
    }
    this.router.navigate(['/checkout']);
  }

  readonly imagenDefecto = IMAGEN_POR_DEFECTO;
}
