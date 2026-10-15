import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CarritoService } from '../../services/carrito.service';
import { ToastService } from '../../services/toast.service';
import {
  EstadoPedido,
  ETIQUETAS_ESTADO,
  ETIQUETAS_METODO_PAGO,
  ETIQUETAS_PAGO,
  Pedido
} from '../../models/pedido.model';
import { IMAGEN_POR_DEFECTO, mensajeDeError } from '../../shared/utilidades';

@Component({
  selector: 'app-pedidos',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './pedidos.component.html'
})
export class PedidosComponent implements OnInit {

  pedidos: Pedido[] = [];
  cargando = false;
  error: string | null = null;
  expandido: number | null = null;
  filtroEstado: EstadoPedido | 'TODOS' = 'TODOS';

  readonly estados: EstadoPedido[] =
    ['PENDIENTE_PAGO', 'CONFIRMADO', 'EN_PREPARACION', 'ENVIADO', 'ENTREGADO', 'CANCELADO'];

  readonly etiquetasEstado = ETIQUETAS_ESTADO;
  readonly etiquetasPago = ETIQUETAS_PAGO;
  readonly etiquetasMetodo = ETIQUETAS_METODO_PAGO;
  readonly imagenDefecto = IMAGEN_POR_DEFECTO;

  constructor(
    private carritoService: CarritoService,
    private toastService: ToastService
  ) {}

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.error = null;

    this.carritoService.listarPedidos().subscribe({
      next: (pedidos) => {
        this.pedidos = [...pedidos].sort((a, b) => b.id - a.id);
        this.cargando = false;
      },
      error: (err) => {
        this.error = mensajeDeError(err,
          'No se pudieron cargar tus pedidos. Verifica que ms-carrito esté activo.');
        this.cargando = false;
      }
    });
  }

  get pedidosFiltrados(): Pedido[] {
    if (this.filtroEstado === 'TODOS') {
      return this.pedidos;
    }
    return this.pedidos.filter((p) => p.estado === this.filtroEstado);
  }

  alternar(id: number): void {
    this.expandido = this.expandido === id ? null : id;
  }

  totalPagado(): number {
    return this.pedidos
      .filter((p) => p.estadoPago === 'APROBADO')
      .reduce((acc, p) => acc + p.total, 0);
  }

  contar(estado: EstadoPedido): number {
    return this.pedidos.filter((p) => p.estado === estado).length;
  }

  claseEstado(estado: EstadoPedido): string {
    switch (estado) {
      case 'CONFIRMADO': return 'badge badge-ok';
      case 'EN_PREPARACION': case 'ENVIADO': return 'badge badge-info';
      case 'ENTREGADO': return 'badge badge-accent';
      case 'CANCELADO': return 'badge badge-danger';
      default: return 'badge badge-warn';
    }
  }

  clasePago(estadoPago: string): string {
    if (estadoPago === 'APROBADO') return 'badge badge-ok';
    if (estadoPago === 'RECHAZADO') return 'badge badge-danger';
    return 'badge badge-warn';
  }

  fecha(pedido: Pedido): string {
    if (!pedido.fecha) {
      return '';
    }
    const d = new Date(pedido.fecha);
    return isNaN(d.getTime()) ? pedido.fecha : d.toLocaleString();
  }
}
