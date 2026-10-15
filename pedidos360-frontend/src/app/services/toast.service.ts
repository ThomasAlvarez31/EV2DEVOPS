import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export type TipoToast = 'exito' | 'error' | 'info';

export interface Toast {
  id: number;
  tipo: TipoToast;
  texto: string;
}

/**
 * Notificaciones visuales inmediatas de la interfaz (agregar al carrito,
 * compra confirmada, pago aprobado/rechazado, errores del checkout).
 *
 * IMPORTANTE: son mensajes en pantalla, no un sistema persistente de
 * notificaciones. El historial se conserva solo mientras la pestana esta
 * abierta y alimenta la campana de notificaciones de la barra superior.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {

  private contador = 0;
  private readonly historial: Toast[] = [];
  private readonly toasts$ = new BehaviorSubject<Toast[]>([]);
  private readonly notificaciones$ = new BehaviorSubject<Toast[]>([]);

  readonly toasts = this.toasts$.asObservable();
  readonly notificaciones = this.notificaciones$.asObservable();

  mostrar(texto: string, tipo: TipoToast = 'exito'): void {
    const toast: Toast = { id: ++this.contador, tipo, texto };

    this.historial.unshift(toast);
    this.notificaciones$.next([...this.historial]);
    this.toasts$.next([...this.toasts$.value, toast]);

    setTimeout(() => this.cerrar(toast.id), 4200);
  }

  exito(texto: string): void {
    this.mostrar(texto, 'exito');
  }

  error(texto: string): void {
    this.mostrar(texto, 'error');
  }

  info(texto: string): void {
    this.mostrar(texto, 'info');
  }

  cerrar(id: number): void {
    this.toasts$.next(this.toasts$.value.filter((t) => t.id !== id));
  }

  limpiarNotificaciones(): void {
    this.historial.length = 0;
    this.notificaciones$.next([]);
  }
}
