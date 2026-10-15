import { APP_INITIALIZER, ApplicationConfig, importProvidersFrom } from '@angular/core';
import { provideRouter } from '@angular/router';
import { HTTP_INTERCEPTORS, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import {
  IPublicClientApplication
} from '@azure/msal-browser';
import {
  MsalModule,
  MsalService,
  MsalGuard,
  MsalBroadcastService,
  MSAL_INSTANCE,
  MSAL_GUARD_CONFIG,
  MSAL_INTERCEPTOR_CONFIG
} from '@azure/msal-angular';

import { TokenInterceptor } from './core/token.interceptor';
import { routes } from './app.routes';
import { environment } from '../environments/environment';
import {
  MSALInstanceFactory,
  MSALGuardConfigFactory,
  MSALInterceptorConfigFactory
} from './core/auth-config';

/**
 * MSAL requiere inicializarse (handshake interno) antes de poder usarse.
 * Si se omite este paso, el login/logout falla de forma silenciosa o
 * intermitente, especialmente justo después de recargar la página.
 */
export function initializeMsal(instance: IPublicClientApplication): () => Promise<void> {
  return () => instance.initialize().then(() => {
    return instance.handleRedirectPromise()
      .catch((error) => {
        // Si el redirect de Azure falla (p. ej. URI registrado como Web y no como SPA),
        // no bloquear el arranque de Angular: se muestra la app y el error en consola.
        console.error('Error procesando la respuesta de autenticación de Azure:', error);
        instance.setActiveAccount(null);
        return null;
      })
      .then(() => {
        const accounts = instance.getAllAccounts();
        if (accounts.length > 0) {
          instance.setActiveAccount(accounts[0]);
        }
      });
  });
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(withInterceptorsFromDi()),
    importProvidersFrom(MsalModule),

    { provide: MSAL_INSTANCE, useFactory: MSALInstanceFactory },
    { provide: MSAL_GUARD_CONFIG, useFactory: MSALGuardConfigFactory },
    { provide: MSAL_INTERCEPTOR_CONFIG, useFactory: MSALInterceptorConfigFactory },

    // Adjunta el token a cada request HTTP según el IDaaS usado:
    //  - sesión de Cognito  -> ID token de Cognito
    //  - sesión de Microsoft -> JWT de Azure AD (protectedResourceMap)
    // SOLO PRUEBA LOCAL (authBypass=true): no se registra (no hay token).
    ...(environment.authBypass ? [] : [
      { provide: HTTP_INTERCEPTORS, useClass: TokenInterceptor, multi: true }
    ]),

    MsalService,
    MsalGuard,
    MsalBroadcastService,

    {
      provide: APP_INITIALIZER,
      useFactory: initializeMsal,
      deps: [MSAL_INSTANCE],
      multi: true
    }
  ]
};
