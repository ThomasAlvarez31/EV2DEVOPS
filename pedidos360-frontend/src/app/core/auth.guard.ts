import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { MsalService } from '@azure/msal-angular';
import { environment } from '../../environments/environment';
import { CognitoService } from './cognito.service';

/**
 * Guard unico para las rutas protegidas: acepta cualquiera de los DOS
 * IDaaS (Microsoft Entra ID o Amazon Cognito).
 *
 * Si no hay sesion de ninguno, en vez de lanzar el login de Microsoft
 * automaticamente, se va a /login para que la persona elija proveedor.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  if (environment.authBypass) {
    return true;
  }

  const cognito = inject(CognitoService);
  if (cognito.tieneSesion()) {
    return true;
  }

  const msal = inject(MsalService);
  if (msal.instance.getAllAccounts().length > 0) {
    return true;
  }

  return inject(Router).createUrlTree(['/login'], {
    queryParams: { returnUrl: state.url }
  });
};
