import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { ToastService } from '../../services/toast.service';
import {
  BindingInfo,
  ColaInfo,
  ExchangeInfo,
  NuevaCola,
  NuevoBinding,
  NuevoExchange,
  RabbitAdminService
} from '../../services/rabbit-admin.service';
import { mensajeDeError } from '../../shared/utilidades';

/**
 * Consola web de RabbitMQ dentro de la tienda.
 *
 * Todo lo que hace esta vista pasa por ms-rabbit-admin (API REST):
 *   GET/POST/DELETE /queues, /exchanges y /bindings.
 * Sirve para mostrar en la presentacion el ciclo completo:
 * crear la cola -> publicar un pedido -> ver el mensaje entrar/salir -> DLQ.
 */
@Component({
  selector: 'app-rabbitmq',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './rabbitmq.component.html'
})
export class RabbitmqComponent implements OnInit {

  colas: ColaInfo[] = [];
  exchanges: ExchangeInfo[] = [];
  bindings: BindingInfo[] = [];

  cargando = false;
  error: string | null = null;

  nuevaCola: NuevaCola = { name: '', durable: true };
  nuevoExchange: NuevoExchange = { name: '', type: 'direct', durable: true, autoDelete: false };
  nuevoBinding: NuevoBinding = { queue: '', exchange: '', routingKey: '' };

  readonly tiposExchange = ['direct', 'topic', 'fanout', 'headers'];

  constructor(
    private rabbit: RabbitAdminService,
    private toast: ToastService
  ) {}

  ngOnInit(): void {
    this.recargar();
  }

  recargar(): void {
    this.cargando = true;
    this.error = null;

    forkJoin({
      colas: this.rabbit.colas(),
      exchanges: this.rabbit.exchanges(),
      bindings: this.rabbit.bindings()
    }).subscribe({
      next: (resultado) => {
        this.colas = resultado.colas;
        this.exchanges = resultado.exchanges;
        this.bindings = resultado.bindings;
        this.cargando = false;
      },
      error: (err) => {
        this.error = mensajeDeError(err,
          'No se pudo consultar ms-rabbit-admin. Verifica que el servicio y RabbitMQ estén activos.');
        this.cargando = false;
      }
    });
  }

  // ------------------------------------------------------------------ colas

  crearCola(): void {
    if (!this.nuevaCola.name.trim()) {
      this.toast.error('Escribe el nombre de la cola.');
      return;
    }
    this.rabbit.crearCola({ name: this.nuevaCola.name.trim(), durable: this.nuevaCola.durable })
      .subscribe({
        next: (cola) => {
          this.toast.exito(`Cola "${cola.name}" creada.`);
          this.nuevaCola = { name: '', durable: true };
          this.recargar();
        },
        error: (err) => this.toast.error(mensajeDeError(err, 'No se pudo crear la cola.'))
      });
  }

  borrarCola(cola: ColaInfo): void {
    if (!confirm(`¿Borrar la cola "${cola.name}"? Los mensajes que tenga se pierden.`)) {
      return;
    }
    this.rabbit.borrarCola(cola.name).subscribe({
      next: () => {
        this.toast.exito(`Cola "${cola.name}" eliminada.`);
        this.recargar();
      },
      error: (err) => this.toast.error(mensajeDeError(err, 'No se pudo eliminar la cola.'))
    });
  }

  // -------------------------------------------------------------- exchanges

  crearExchange(): void {
    if (!this.nuevoExchange.name.trim()) {
      this.toast.error('Escribe el nombre del exchange.');
      return;
    }
    this.rabbit.crearExchange({
      name: this.nuevoExchange.name.trim(),
      type: this.nuevoExchange.type,
      durable: this.nuevoExchange.durable,
      autoDelete: this.nuevoExchange.autoDelete
    }).subscribe({
      next: (exchange) => {
        this.toast.exito(`Exchange "${exchange.name}" creado.`);
        this.nuevoExchange = { name: '', type: 'direct', durable: true, autoDelete: false };
        this.recargar();
      },
      error: (err) => this.toast.error(mensajeDeError(err, 'No se pudo crear el exchange.'))
    });
  }

  borrarExchange(exchange: ExchangeInfo): void {
    if (!confirm(`¿Borrar el exchange "${exchange.name}"?`)) {
      return;
    }
    this.rabbit.borrarExchange(exchange.name).subscribe({
      next: () => {
        this.toast.exito(`Exchange "${exchange.name}" eliminado.`);
        this.recargar();
      },
      error: (err) => this.toast.error(mensajeDeError(err, 'No se pudo eliminar el exchange.'))
    });
  }

  // --------------------------------------------------------------- bindings

  crearBinding(): void {
    const binding = {
      queue: this.nuevoBinding.queue.trim(),
      exchange: this.nuevoBinding.exchange.trim(),
      routingKey: this.nuevoBinding.routingKey.trim()
    };
    if (!binding.queue || !binding.exchange || !binding.routingKey) {
      this.toast.error('Cola, exchange y routing key son obligatorios.');
      return;
    }
    this.rabbit.crearBinding(binding).subscribe({
      next: () => {
        this.toast.exito(`Binding ${binding.exchange} -> ${binding.queue} creado.`);
        this.nuevoBinding = { queue: '', exchange: '', routingKey: '' };
        this.recargar();
      },
      error: (err) => this.toast.error(mensajeDeError(err, 'No se pudo crear el binding.'))
    });
  }

  borrarBinding(binding: BindingInfo): void {
    if (!confirm(`¿Borrar el binding ${binding.exchange} -> ${binding.queue} (${binding.routingKey})?`)) {
      return;
    }
    this.rabbit.borrarBinding(binding).subscribe({
      next: () => {
        this.toast.exito('Binding eliminado.');
        this.recargar();
      },
      error: (err) => this.toast.error(mensajeDeError(err, 'No se pudo eliminar el binding.'))
    });
  }

  /** Filtra las colas "internas" de RabbitMQ para que la lista sea legible. */
  get colasVisibles(): ColaInfo[] {
    return [...this.colas].sort((a, b) => a.name.localeCompare(b.name));
  }

  get exchangesVisibles(): ExchangeInfo[] {
    return [...this.exchanges]
      .filter((e) => !e.name.startsWith('amq.'))
      .sort((a, b) => a.name.localeCompare(b.name));
  }
}
