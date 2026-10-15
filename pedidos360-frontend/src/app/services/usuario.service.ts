import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Usuario } from '../models/usuario.model';

@Injectable({ providedIn: 'root' })
export class UsuarioService {

  private readonly baseUrl = environment.apiIdentidadUrl;

  constructor(private http: HttpClient) {}

  obtenerPerfilActual(): Observable<Usuario> {
    return this.http.get<Usuario>(`${this.baseUrl}/me`);
  }
}
