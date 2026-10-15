import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface ColaInfo {
  name: string;
  messages: number | null;
  consumers: number | null;
  durable: boolean | null;
  autoDelete: boolean | null;
}

export interface ExchangeInfo {
  name: string;
  type: string;
  durable: boolean | null;
  autoDelete: boolean | null;
}

export interface BindingInfo {
  exchange: string;
  queue: string;
  routingKey: string;
}

export interface NuevaCola {
  name: string;
  durable: boolean;
}

export interface NuevoExchange {
  name: string;
  type: string;
  durable: boolean;
  autoDelete: boolean;
}

export interface NuevoBinding {
  queue: string;
  exchange: string;
  routingKey: string;
}

/**
 * Cliente HTTP del microservicio administrador de RabbitMQ (ms-rabbit-admin).
 * Crear y borrar van por REST (la API de gestion solo se usa para listar).
 */
@Injectable({ providedIn: 'root' })
export class RabbitAdminService {

  private readonly base = environment.apiRabbitUrl;

  constructor(private http: HttpClient) {}

  colas(): Observable<ColaInfo[]> {
    return this.http.get<ColaInfo[]>(`${this.base}/queues`);
  }

  exchanges(): Observable<ExchangeInfo[]> {
    return this.http.get<ExchangeInfo[]>(`${this.base}/exchanges`);
  }

  bindings(): Observable<BindingInfo[]> {
    return this.http.get<BindingInfo[]>(`${this.base}/bindings`);
  }

  crearCola(cola: NuevaCola): Observable<ColaInfo> {
    return this.http.post<ColaInfo>(`${this.base}/queues`, cola);
  }

  borrarCola(name: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/queues/${encodeURIComponent(name)}`);
  }

  crearExchange(exchange: NuevoExchange): Observable<ExchangeInfo> {
    return this.http.post<ExchangeInfo>(`${this.base}/exchanges`, exchange);
  }

  borrarExchange(name: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/exchanges/${encodeURIComponent(name)}`);
  }

  crearBinding(binding: NuevoBinding): Observable<BindingInfo> {
    return this.http.post<BindingInfo>(`${this.base}/bindings`, binding);
  }

  borrarBinding(binding: BindingInfo): Observable<void> {
    const params = new HttpParams()
      .set('queue', binding.queue)
      .set('exchange', binding.exchange)
      .set('routingKey', binding.routingKey);
    return this.http.delete<void>(`${this.base}/bindings`, { params });
  }
}
