import { Injectable } from '@angular/core';
import {
  AuthenticationDetails,
  CognitoUser,
  CognitoUserAttribute,
  CognitoUserPool,
  CognitoUserSession
} from 'amazon-cognito-identity-js';
import { BehaviorSubject } from 'rxjs';
import { environment } from '../../environments/environment';

export interface SesionCognito {
  email: string;
  nombre: string;
  /** ID token (trae email/name) que se envia como Bearer a los backends. */
  token: string;
  /** Epoch en milisegundos: momento de expiracion del ID token. */
  expiraEn: number;
}

const CLAVE_SESION = 'pedidos360.sesion.cognito';

/**
 * Login con cuentas propias de la tienda creadas en Amazon Cognito.
 *
 * Convenciones:
 *  - Username = email del usuario.
 *  - El token que viaja a los backends es el ID token (trae "email" y "name",
 *    que son los claims que usan ms-identidad y ms-carrito; el decoder
 *    multi-emisor de los backends acepta Microsoft Entra ID y Cognito).
 *  - La sesion se guarda en localStorage para que al recargar la pagina
 *    el navbar y el interceptor sigan sabiendo quien esta dentro.
 */
@Injectable({ providedIn: 'root' })
export class CognitoService {

  readonly region = environment.cognitoRegion;
  readonly poolId = environment.cognitoUserPoolId;
  readonly clientId = environment.cognitoClientId;

  /** false => todavia no se cargo el User Pool (el form muestra un aviso). */
  readonly configurado = !!(this.poolId && this.clientId);

  private readonly pool: CognitoUserPool | null;
  private readonly estado = new BehaviorSubject<SesionCognito | null>(null);

  readonly sesion$ = this.estado.asObservable();

  constructor() {
    this.pool = this.configurado
      ? new CognitoUserPool({ UserPoolId: this.poolId, ClientId: this.clientId })
      : null;

    const guardada = this.leerGuardada();
    if (guardada) {
      this.estado.next(guardada);
    }
    // Si el token esta por vencer, Cognito lo renueva solo con el refresh token.
    this.refrescarSilenciosamente();
  }

  get sesion(): SesionCognito | null {
    return this.estado.value;
  }

  /** true si hay una sesion de Cognito vigente. */
  tieneSesion(): boolean {
    return this.token() !== null;
  }

  /** ID token vigente (null si no hay sesion o expiro). */
  token(): string | null {
    const actual = this.estado.value;
    if (!actual) {
      return null;
    }
    if (actual.expiraEn && actual.expiraEn < Date.now()) {
      return null;
    }
    return actual.token;
  }

  /** Crea la cuenta. Resuelve con true si hay que pedir codigo de verificacion. */
  registrar(email: string, password: string, nombre: string): Promise<boolean> {
    if (!this.pool) {
      return Promise.reject(new Error('Cognito no esta configurado en este entorno.'));
    }
    const atributos = [
      new CognitoUserAttribute({ Name: 'email', Value: email }),
      new CognitoUserAttribute({ Name: 'name', Value: nombre || email })
    ];

    return new Promise((resolve, reject) => {
      this.pool!.signUp(email, password, atributos, [], (err, resultado) => {
        if (err) {
          reject(err);
          return;
        }
        resolve(!resultado?.userConfirmed);
      });
    });
  }

  /** Confirma la cuenta con el codigo enviado por Cognito (si el pool lo exige). */
  confirmar(email: string, codigo: string): Promise<void> {
    const usuario = this.nuevoUsuario(email);
    return new Promise((resolve, reject) => {
      usuario.confirmRegistration(codigo, true, (err) => (err ? reject(err) : resolve()));
    });
  }

  /** Inicia sesion con email y password. */
  iniciarSesion(email: string, password: string): Promise<SesionCognito> {
    if (!this.pool) {
      return Promise.reject(new Error('Cognito no esta configurado en este entorno.'));
    }
    const usuario = this.nuevoUsuario(email);
    const detalles = new AuthenticationDetails({ Username: email, Password: password });

    return new Promise((resolve, reject) => {
      usuario.authenticateUser(detalles, {
        onSuccess: (sesion) => resolve(this.publicar(usuario, sesion)),
        onFailure: (err) => reject(err),
        newPasswordRequired: () =>
          reject(this.errorPropio('NewPasswordRequired', 'Debes definir una contraseña nueva antes de entrar.'))
      });
    });
  }

  /** Cierra la sesion de Cognito y borra el token del navegador. */
  cerrarSesion(): void {
    const usuario = this.usuarioActualDeLaSesion();
    if (usuario) {
      try {
        usuario.signOut();
      } catch {
        // Si el usuario ya no existe en el pool, alcanza con borrar la sesion local.
      }
    }
    localStorage.removeItem(CLAVE_SESION);
    this.estado.next(null);
  }

  // ------------------------------------------------------------------ internos

  private nuevoUsuario(email: string): CognitoUser {
    return new CognitoUser({ Username: email, Pool: this.pool! });
  }

  private usuarioActualDeLaSesion(): CognitoUser | null {
    const guardada = this.leerGuardada();
    if (!guardada || !this.pool) {
      return null;
    }
    return new CognitoUser({ Username: guardada.email, Pool: this.pool });
  }

  private publicar(usuario: CognitoUser, sesion: CognitoUserSession): SesionCognito {
    const claims = sesion.getIdToken().decodePayload();
    const token: SesionCognito = {
      email: (claims['email'] as string) || usuario.getUsername(),
      nombre: (claims['name'] as string) || (claims['cognito:username'] as string) || (claims['email'] as string) || usuario.getUsername(),
      token: sesion.getIdToken().getJwtToken(),
      expiraEn: sesion.getIdToken().getExpiration() * 1000
    };
    localStorage.setItem(CLAVE_SESION, JSON.stringify(token));
    this.estado.next(token);
    return token;
  }

  private leerGuardada(): SesionCognito | null {
    try {
      const crudo = localStorage.getItem(CLAVE_SESION);
      if (!crudo) {
        return null;
      }
      const datos = JSON.parse(crudo) as SesionCognito;
      if (!datos?.token || (datos.expiraEn && datos.expiraEn < Date.now())) {
        localStorage.removeItem(CLAVE_SESION);
        return null;
      }
      return datos;
    } catch {
      return null;
    }
  }

  /** Intenta renovar el token guardado sin molestar al usuario. */
  private refrescarSilenciosamente(): void {
    const usuario = this.usuarioActualDeLaSesion();
    if (!usuario) {
      return;
    }
    try {
      usuario.getSession((err: any, sesion: CognitoUserSession | null) => {
        if (err || !sesion || !sesion.isValid()) {
          if (err) {
            this.cerrarSesion();
          }
          return;
        }
        this.publicar(usuario, sesion);
      });
    } catch {
      // Sesion local invalida: se ignora, el interceptor seguira con el token vigente.
    }
  }

  /** Errores de Cognito llegan como objects {code, message}; se normalizan. */
  private errorPropio(code: string, message: string): Error & { code?: string } {
    return Object.assign(new Error(message), { code });
  }
}
