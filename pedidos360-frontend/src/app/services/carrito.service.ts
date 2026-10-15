import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { Carrito, AgregarItemRequest } from '../models/carrito.model';
import { MetodoPago, Pedido } from '../models/pedido.model';

@Injectable({ providedIn: 'root' })
export class CarritoService {

  private readonly baseUrl = environment.apiCarritoUrl;

  /** Numero de unidades en el carrito, para el contador de la barra superior. */
  private readonly cantidad$ = new BehaviorSubject<number>(0);
  readonly cantidadCarrito = this.cantidad$.asObservable();

  constructor(private http: HttpClient) {}

  obtenerCarrito(): Observable<Carrito> {
    return this.http.get<Carrito>(`${this.baseUrl}/carrito`)
      .pipe(tap((c) => this.actualizarContador(c)));
  }

  agregarItem(request: AgregarItemRequest): Observable<Carrito> {
    return this.http.post<Carrito>(`${this.baseUrl}/carrito/items`, request)
      .pipe(tap((c) => this.actualizarContador(c)));
  }

  actualizarCantidad(itemId: number, cantidad: number): Observable<Carrito> {
    return this.http.put<Carrito>(`${this.baseUrl}/carrito/items/${itemId}`, { cantidad })
      .pipe(tap((c) => this.actualizarContador(c)));
  }

  eliminarItem(itemId: number): Observable<Carrito> {
    return this.http.delete<Carrito>(`${this.baseUrl}/carrito/items/${itemId}`)
      .pipe(tap((c) => this.actualizarContador(c)));
  }

  /**
   * Paso 1 del checkout: crea el pedido (queda PENDIENTE DE PAGO) y vacia el
   * carrito. El pago se procesa en `pagar`.
   */
  checkout(metodoPago: MetodoPago): Observable<Pedido> {
    return this.http.post<Pedido>(`${this.baseUrl}/carrito/checkout`, { metodoPago })
      .pipe(tap(() => this.cantidad$.next(0)));
  }

  /** Paso 2: pago SIMULADO (aprobar o rechazar el pedido creado). */
  pagar(pedidoId: number, simularRechazo = false): Observable<Pedido> {
    return this.http.post<Pedido>(`${this.baseUrl}/pedidos/${pedidoId}/pago`, { simularRechazo });
  }

  listarPedidos(): Observable<Pedido[]> {
    return this.http.get<Pedido[]>(`${this.baseUrl}/pedidos`);
  }

  obtenerPedido(pedidoId: number): Observable<Pedido> {
    return this.http.get<Pedido>(`${this.baseUrl}/pedidos/${pedidoId}`);
  }

  private actualizarContador(carrito: Carrito): void {
    this.cantidad$.next(carrito.items.reduce((acc, i) => acc + i.cantidad, 0));
  }
}
