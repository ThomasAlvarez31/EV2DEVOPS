import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CarritoService } from '../../services/carrito.service';
import { ToastService } from '../../services/toast.service';
import { Carrito, ItemCarrito } from '../../models/carrito.model';
import {
  ETIQUETAS_METODO_PAGO,
  EstadoPago,
  MetodoPago,
  Pedido
} from '../../models/pedido.model';
import { IMAGEN_POR_DEFECTO, mensajeDeError } from '../../shared/utilidades';

type Resultado = 'aprobado' | 'rechazado' | 'fallo';

interface TarjetaDemo {
  numero: string;
  titular: string;
  vencimiento: string;
  cvv: string;
}

/**
 * Checkout en dos pasos, sin duplicar la generacion del pedido:
 *   1) POST /carrito/checkout        -> crea el pedido (PENDIENTE_DE_PAGO)
 *   2) POST /pedidos/{id}/pago       -> pago SIMULADO (aprobado / rechazado)
 *
 * Los datos de la tarjeta NO se envian al backend: solo se usan en el
 * navegador para simular la experiencia. No se almacena ningun dato bancario.
 */
@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './checkout.component.html'
})
export class CheckoutComponent implements OnInit {

  carrito: Carrito | null = null;
  cargando = true;
  error: string | null = null;

  metodoPago: MetodoPago | null = null;
  tarjeta: TarjetaDemo = { numero: '', titular: '', vencimiento: '', cvv: '' };
  simularRechazo = false;

  procesando = false;
  pedido: Pedido | null = null;
  resultado: Resultado | null = null;
  errorPago: string | null = null;

  readonly metodos: MetodoPago[] = ['TARJETA_CREDITO', 'TARJETA_DEBITO', 'TRANSFERENCIA'];
  readonly etiquetas = ETIQUETAS_METODO_PAGO;
  readonly imagenDefecto = IMAGEN_POR_DEFECTO;

  constructor(
    private carritoService: CarritoService,
    private toastService: ToastService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.carritoService.obtenerCarrito().subscribe({
      next: (carrito) => {
        this.carrito = carrito;
        this.cargando = false;
        if (carrito.items.length === 0 && !this.pedido) {
          this.error = 'Tu carrito está vacío. Agrega productos antes de continuar.';
        }
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
    return this.items.reduce((acc, i) => acc + i.precioUnitario * i.cantidad, 0);
  }

  get esTarjeta(): boolean {
    return this.metodoPago === 'TARJETA_CREDITO' || this.metodoPago === 'TARJETA_DEBITO';
  }

  get formularioTarjetaValido(): boolean {
    const digitos = this.tarjeta.numero.replace(/\s/g, '');
    const vence = this.tarjeta.vencimiento.trim();
    return digitos.length === 16
      && this.tarjeta.titular.trim().length >= 3
      && /^\d{2}\/\d{2}$/.test(vence)
      && this.tarjeta.cvv.trim().length === 3;
  }

  get puedePagar(): boolean {
    if (this.procesando || !this.metodoPago) {
      return false;
    }
    if (this.esTarjeta && !this.formularioTarjetaValido) {
      return false;
    }
    return this.total > 0 || !!this.pedido;
  }

  formatearNumero(): void {
    const digitos = this.tarjeta.numero.replace(/\D/g, '').slice(0, 16);
    this.tarjeta.numero = digitos.replace(/(.{4})/g, '$1 ').trim();
  }

  formatearVencimiento(): void {
    const digitos = this.tarjeta.vencimiento.replace(/\D/g, '').slice(0, 4);
    this.tarjeta.vencimiento = digitos.length > 2
      ? `${digitos.slice(0, 2)}/${digitos.slice(2)}`
      : digitos;
  }

  confirmar(): void {
    if (this.procesando) {
      return; // evita confirmaciones multiples accidentales
    }
    if (!this.metodoPago) {
      this.errorPago = 'Selecciona un método de pago para continuar.';
      return;
    }
    if (this.esTarjeta && !this.formularioTarjetaValido) {
      this.errorPago = 'Revisa los datos de la tarjeta (son de prueba).';
      return;
    }

    this.errorPago = null;

    if (this.pedido) {
      // El pedido ya existe (reintento tras un fallo de red): solo falta pagar.
      this.procesarPago(this.pedido.id);
      return;
    }

    this.procesando = true;
    this.carritoService.checkout(this.metodoPago).subscribe({
      next: (pedido) => {
        this.pedido = pedido;
        this.carritoService.obtenerCarrito().subscribe({ next: (c) => (this.carrito = c), error: () => {} });
        this.procesarPago(pedido.id);
      },
      error: (err) => {
        this.procesando = false;
        this.resultado = 'fallo';
        this.errorPago = mensajeDeError(err, 'No se pudo registrar el pedido.');
        this.toastService.error(this.errorPago);
      }
    });
  }

  private procesarPago(pedidoId: number): void {
    this.procesando = true;

    this.carritoService.pagar(pedidoId, this.simularRechazo).subscribe({
      next: (pedido) => {
        this.pedido = pedido;
        this.procesando = false;
        this.resultado = pedido.estadoPago === 'APROBADO' ? 'aprobado' : 'rechazado';

        if (this.resultado === 'aprobado') {
          this.toastService.exito(`Pago aprobado. Tu pedido #${pedido.id} está confirmado.`);
        } else {
          this.toastService.error(`Pago rechazado. El pedido #${pedido.id} quedó cancelado.`);
          this.carritoService.obtenerCarrito().subscribe({ next: (c) => (this.carrito = c), error: () => {} });
        }
      },
      error: (err) => {
        this.procesando = false;
        this.resultado = 'fallo';
        this.errorPago = mensajeDeError(err, 'No se pudo procesar el pago.');
        this.toastService.error(this.errorPago);
      }
    });
  }

  reiniciar(): void {
    this.resultado = null;
    this.errorPago = null;
    this.pedido = null;
    this.metodoPago = null;
    this.tarjeta = { numero: '', titular: '', vencimiento: '', cvv: '' };
    this.simularRechazo = false;
    this.ngOnInit();
  }

  estadoDePago(pedido: Pedido): EstadoPago {
    return pedido.estadoPago;
  }
}
