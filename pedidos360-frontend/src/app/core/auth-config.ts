import {
  IPublicClientApplication,
  PublicClientApplication,
  InteractionType,
  BrowserCacheLocation,
  LogLevel
} from '@azure/msal-browser';
import {
  MsalGuardConfiguration,
  MsalInterceptorConfiguration
} from '@azure/msal-angular';
import { environment } from '../../environments/environment';

/**
 * Instancia de MSAL configurada con los datos del registro de la app en Azure Entra ID.
 * Equivalente Angular del AuthConfig.js / msalConfig del proyecto de ejemplo del profesor.
 */
export function MSALInstanceFactory(): IPublicClientApplication {
  return new PublicClientApplication({
    auth: {
      clientId: environment.azureClientId,
      authority: `https://login.microsoftonline.com/${environment.azureTenantId}`,
      redirectUri: environment.redirectUri,
      postLogoutRedirectUri: environment.redirectUri
    },
    cache: {
      cacheLocation: BrowserCacheLocation.SessionStorage,
      storeAuthStateInCookie: false
    },
    system: {
      loggerOptions: {
        loggerCallback: () => {},
        logLevel: LogLevel.Warning,
        piiLoggingEnabled: false
      }
    }
  });
}

/**
 * Scopes solicitados al hacer login básico (perfil de Microsoft).
 */
export function MSALGuardConfigFactory(): MsalGuardConfiguration {
  return {
    interactionType: InteractionType.Redirect,
    authRequest: {
      scopes: ['User.Read']
    }
  };
}

/**
 * Mapa de recursos protegidos: para cada URL de backend, qué scope se debe
 * adjuntar como Bearer token. El interceptor (TokenInterceptor -> MsalInterceptor)
 * usa esto automáticamente en cada llamada HTTP que coincida con alguna de
 * estas URLs cuando la sesión es de Microsoft; con sesión de Cognito manda
 * el ID token de Cognito en su lugar.
 */
export function MSALInterceptorConfigFactory(): MsalInterceptorConfiguration {
  const protectedResourceMap = new Map<string, Array<string>>();

  protectedResourceMap.set(environment.apiProductosUrl, [environment.azureApiScope]);
  protectedResourceMap.set(environment.apiIdentidadUrl, [environment.azureApiScope]);
  protectedResourceMap.set(environment.apiCarritoUrl, [environment.azureApiScope]);
  protectedResourceMap.set(environment.apiRabbitUrl, [environment.azureApiScope]);

  return {
    interactionType: InteractionType.Redirect,
    protectedResourceMap
  };
}
