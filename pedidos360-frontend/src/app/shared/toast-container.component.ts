import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Toast, ToastService } from '../services/toast.service';

/** Contenedor global de notificaciones visuales (mensajes temporales). */
@Component({
  selector: 'app-toast-container',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="toast-stack" role="status" aria-live="polite">
      <div class="toast-item" *ngFor="let t of toasts" [ngClass]="'toast-' + t.tipo">
        <span class="toast-icon">{{ iconoDe(t) }}</span>
        <span class="toast-texto">{{ t.texto }}</span>
        <button class="toast-cerrar" (click)="cerrar(t.id)" aria-label="Cerrar">&times;</button>
      </div>
    </div>
  `
})
export class ToastContainerComponent {

  toasts: Toast[] = [];

  constructor(private toastService: ToastService) {
    this.toastService.toasts.subscribe((lista) => (this.toasts = lista));
  }

  cerrar(id: number): void {
    this.toastService.cerrar(id);
  }

  iconoDe(t: Toast): string {
    return t.tipo === 'exito' ? '\u2713' : t.tipo === 'error' ? '\u2715' : '\u2139';
  }
}
