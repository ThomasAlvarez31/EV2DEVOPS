import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MsalService } from '@azure/msal-angular';
import { CognitoService } from '../../core/cognito.service';
import { environment } from '../../../environments/environment';

type ModoCognito = 'entrar' | 'crear' | 'confirmar';

/**
 * Pantalla de acceso con los DOS IDaaS del proyecto:
 *
 *  1. Microsoft Entra ID (MSAL)  -> botón "Iniciar sesión con Microsoft".
 *  2. Amazon Cognito             -> formulario para crear una cuenta propia
 *                                    o entrar con email y contraseña.
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './login.component.html'
})
export class LoginComponent {

  /** true mientras se redirige a Microsoft: evita dobles clics. */
  iniciando = false;
  /** true mientras Cognito procesa crear/entrar/confirmar. */
  procesando = false;
  error: string | null = null;
  aviso: string | null = null;

  modo: ModoCognito = 'entrar';
  email = '';
  password = '';
  nombre = '';
  codigo = '';

  private readonly returnUrl: string;

  constructor(
    private msalService: MsalService,
    private router: Router,
    private route: ActivatedRoute,
    public cognito: CognitoService
  ) {
    this.returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') || '/catalogo';
  }

  get estaAutenticado(): boolean {
    // SOLO PRUEBA LOCAL (authBypass): se muestra como autenticado sin consultar.
    if (environment.authBypass) {
      return true;
    }
    if (this.cognito.tieneSesion()) {
      return true;
    }
    return this.msalService.instance.getAllAccounts().length > 0;
  }

  // ------------------------------------------------------------ Microsoft

  login(): void {
    if (this.iniciando) {
      return;
    }
    this.error = null;

    // SOLO PRUEBA LOCAL (authBypass): se entra directo a la tienda.
    if (environment.authBypass) {
      this.router.navigateByUrl('/catalogo');
      return;
    }

    this.iniciando = true;
    try {
      this.msalService.loginRedirect({ scopes: ['User.Read'] });
    } catch (err: any) {
      this.iniciando = false;
      this.error = err?.message || 'No se pudo iniciar la sesión con Microsoft. Intenta nuevamente.';
    }
  }

  // ------------------------------------------------------------- Cognito

  cambiarModo(modo: ModoCognito): void {
    this.modo = modo;
    this.error = null;
    this.aviso = null;
  }

  enviarFormulario(): void {
    // El formulario se reutiliza: en modo "confirmar" envía el código.
    if (this.modo === 'confirmar') {
      this.confirmarCuenta();
      return;
    }
    this.error = null;
    this.aviso = null;

    if (!this.cognito.configurado) {
      this.error = 'Cognito aún no está configurado: carga el User Pool y el App Client en '
        + 'environment.prod.ts (ver GUIA-DESPLIEGUE.txt, sección 0.2).';
      return;
    }
    if (!this.email.trim() || !this.password) {
      this.error = 'Ingresa tu email y tu contraseña.';
      return;
    }
    if (this.modo === 'crear' && !this.nombre.trim()) {
      this.error = 'Indica tu nombre para crear la cuenta.';
      return;
    }

    this.procesando = true;
    const accion = this.modo === 'crear'
      ? this.cognito.registrar(this.email.trim(), this.password, this.nombre.trim())
          .then((requiereCodigo) => {
            if (requiereCodigo) {
              this.modo = 'confirmar';
              this.aviso = 'Cuenta creada. Revisa tu correo e ingresa el código de verificación.';
              return;
            }
            return this.entrarEnCognito();
          })
      : this.entrarEnCognito();

    accion
      .catch((err) => this.tratarError(err))
      .finally(() => (this.procesando = false));
  }

  confirmarCuenta(): void {
    this.error = null;
    if (!this.codigo.trim()) {
      this.error = 'Ingresa el código de verificación.';
      return;
    }
    this.procesando = true;
    this.cognito
      .confirmar(this.email.trim(), this.codigo.trim())
      .then(() => {
        this.aviso = 'Cuenta verificada. Ya puedes iniciar sesión.';
        this.modo = 'entrar';
        this.codigo = '';
      })
      .catch((err) => this.tratarError(err))
      .finally(() => (this.procesando = false));
  }

  salirDeCognito(): void {
    this.cognito.cerrarSesion();
    this.modo = 'entrar';
  }

  // --------------------------------------------------------------- internos

  private entrarEnCognito(): Promise<void> {
    return this.cognito.iniciarSesion(this.email.trim(), this.password).then(() => {
      this.router.navigateByUrl(this.returnUrl);
    });
  }

  private tratarError(err: any): void {
    this.error = this.mensajeDeCognito(err);
  }

  private mensajeDeCognito(err: any): string {
    const codigo = err?.code || err?.name || '';
    switch (codigo) {
      case 'UserNotFoundException':
        return 'No existe ninguna cuenta con ese email.';
      case 'NotAuthorizedException':
        return 'Email o contraseña incorrectos.';
      case 'UsernameExistsException':
        return 'Ya existe una cuenta con ese email. Inicia sesión en su lugar.';
      case 'UserNotConfirmedException':
        this.modo = 'confirmar';
        this.aviso = 'La cuenta espera el código de verificación enviado por Cognito.';
        return 'Confirma tu cuenta con el código que llegó a tu correo.';
      case 'CodeMismatchException':
        return 'El código de verificación no es correcto.';
      case 'ExpiredCodeException':
        return 'El código de verificación expiró. Solicita uno nuevo.';
      case 'InvalidPasswordException':
      case 'PasswordComplexityException':
        return err?.message || 'La contraseña no cumple la política del User Pool.';
      case 'LimitExceededException':
      case 'TooManyRequestsException':
        return 'Demasiados intentos. Espera unos minutos y vuelve a intentarlo.';
      case 'InvalidParameterException':
        return err?.message || 'Revisa los datos ingresados.';
      default:
        return err?.message || 'No se pudo completar la operación en Cognito.';
    }
  }
}
