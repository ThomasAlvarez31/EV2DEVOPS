import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { Subject } from 'rxjs';
import { filter, takeUntil } from 'rxjs/operators';
import { MsalBroadcastService, MsalService } from '@azure/msal-angular';
import { EventMessage, EventType, InteractionStatus } from '@azure/msal-browser';
import { environment } from '../../../environments/environment';
import { CognitoService } from '../../core/cognito.service';
import { CarritoService } from '../../services/carrito.service';
import { Toast, ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, RouterLinkActive],
  templateUrl: './navbar.component.html'
})
export class NavbarComponent implements OnInit, OnDestroy {

  isAutenticado = false;
  nombreUsuario: string | null = null;
  cantidadCarrito = 0;
  terminoBusqueda = '';
  menuAbierto = false;
  campanaAbierta = false;
  notificaciones: Toast[] = [];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private msalService: MsalService,
    private msalBroadcastService: MsalBroadcastService,
    private cognitoService: CognitoService,
    private carritoService: CarritoService,
    public toastService: ToastService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.actualizarEstadoCuenta();

    this.carritoService.cantidadCarrito
      .pipe(takeUntil(this.destroy$))
      .subscribe((cantidad) => (this.cantidadCarrito = cantidad));

    this.toastService.notificaciones
      .pipe(takeUntil(this.destroy$))
      .subscribe((lista) => (this.notificaciones = lista));

    // Contador inicial del carrito (desde ahi se actualiza solo con cada operacion).
    this.cargarCantidad();

    // Cambios de la sesion de Cognito (entrar/salir con cuenta propia).
    this.cognitoService.sesion$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.actualizarEstadoCuenta());

    // SOLO PRUEBA LOCAL (authBypass): no se suscribe a eventos de MSAL.
    if (environment.authBypass) {
      return;
    }

    this.msalBroadcastService.msalSubject$
      .pipe(
        filter((msg: EventMessage) =>
          msg.eventType === EventType.LOGIN_SUCCESS || msg.eventType === EventType.LOGOUT_SUCCESS
        ),
        takeUntil(this.destroy$)
      )
      .subscribe(() => this.actualizarEstadoCuenta());

    this.msalBroadcastService.inProgress$
      .pipe(filter((s) => s === InteractionStatus.None), takeUntil(this.destroy$))
      .subscribe(() => this.actualizarEstadoCuenta());
  }

  buscar(formulario: NgForm): void {
    const termino = this.terminoBusqueda.trim();
    this.menuAbierto = false;
    this.router.navigate(['/catalogo'], { queryParams: termino ? { q: termino } : {} });
    formulario.resetForm();
    this.terminoBusqueda = '';
  }

  alternarCampana(): void {
    this.campanaAbierta = !this.campanaAbierta;
  }

  limpiarNotificaciones(): void {
    this.toastService.limpiarNotificaciones();
  }

  /**
   * Va a la pantalla de acceso: ahi la persona elige entre Microsoft
   * Entra ID o una cuenta propia de Amazon Cognito.
   */
  login(): void {
    this.router.navigateByUrl('/login');
  }

  /** Sale del IDaaS con el que se haya entrado. */
  logout(): void {
    if (environment.authBypass) {
      this.router.navigateByUrl('/');
      return;
    }
    if (this.cognitoService.tieneSesion()) {
      this.cognitoService.cerrarSesion();
      this.router.navigateByUrl('/');
      return;
    }
    this.msalService.logoutRedirect();
  }

  cerrarSesionLocal(): void {
    this.menuAbierto = false;
    this.logout();
  }

  private cargarCantidad(): void {
    this.carritoService.obtenerCarrito().subscribe({ error: () => {} });
  }

  private actualizarEstadoCuenta(): void {
    if (environment.authBypass) {
      this.isAutenticado = true;
      this.nombreUsuario = 'Usuario Local';
      return;
    }
    // Sesion de Amazon Cognito (cuenta propia de la tienda).
    const sesionCognito = this.cognitoService.sesion;
    if (this.cognitoService.tieneSesion() && sesionCognito) {
      this.isAutenticado = true;
      this.nombreUsuario = sesionCognito.nombre || sesionCognito.email;
      return;
    }

    const cuentas = this.msalService.instance.getAllAccounts();
    this.isAutenticado = cuentas.length > 0;
    this.nombreUsuario = cuentas[0]?.name ?? null;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
