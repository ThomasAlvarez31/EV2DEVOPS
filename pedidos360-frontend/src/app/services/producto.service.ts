import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Producto } from '../models/producto.model';

@Injectable({ providedIn: 'root' })
export class ProductoService {

  private readonly baseUrl = environment.apiProductosUrl;

  constructor(private http: HttpClient) {}

  listar(): Observable<Producto[]> {
    return this.http.get<Producto[]>(this.baseUrl);
  }

  obtenerPorId(id: number): Observable<Producto> {
    return this.http.get<Producto>(`${this.baseUrl}/${id}`);
  }

  /** Busqueda del catalogo: nombre, categoria o ambos. */
  buscar(nombre?: string | null, categoria?: string | null): Observable<Producto[]> {
    let params = new HttpParams();
    if (nombre && nombre.trim()) {
      params = params.set('nombre', nombre.trim());
    }
    if (categoria && categoria.trim()) {
      params = params.set('categoria', categoria.trim());
    }
    return this.http.get<Producto[]>(this.baseUrl, { params });
  }

  buscarPorCategoria(categoria: string): Observable<Producto[]> {
    return this.buscar(null, categoria);
  }

  categorias(): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/categorias`);
  }
}
