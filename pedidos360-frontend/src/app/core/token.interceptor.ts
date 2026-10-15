import { Location } from '@angular/common';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import {
  MSAL_INTERCEPTOR_CONFIG,
  MsalBroadcastService,
  MsalInterceptor,
  MsalInterceptorConfiguration,
  MsalService
} from '@azure/msal-angular';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { CognitoService } from './cognito.service';

/**
 * Interceptor de tokens para los DOS IDaaS y las DOS puertas de entrada
 * (sin crear gateways nuevos):
 *
 *  - Sesion de Microsoft Entra ID -> se comporta como el MsalInterceptor de
 *    siempre: JWT de Microsoft hacia la API de LOGICA (st3wqbtin7), que es
 *    la que tiene el JWT Authorizer en el API Gateway.
 *
 *  - Sesion de Amazon Cognito  -> se envia el ID token de Cognito y la URL
 *    se reescribe al MISMO gateway que sirve el frontend en HTTPS
 *    (n27vhcx1n0), que no tiene authorizer: ahi el token lo validan los
 *    microservicios (MultiIssuerJwtDecoder acepta Azure y Cognito). Sin el
 *    cambio de URL, el authorizer de la API de logica rechazaria el token
 *    de Cognito con 401.
 *
 * De este modo una sola llamada a HTTP viaja con el token correcto y por la
 * puerta de enlace correcta segun con que cuenta se haya iniciado sesion.
 */
@Injectable()
export class TokenInterceptor extends MsalInterceptor {

  /** Prefijos de URL que deben llevar Authorization. */
  private readonly basesApi: string[];

  constructor(
    @Inject(MSAL_INTERCEPTOR_CONFIG) msalInterceptorConfig: MsalInterceptorConfiguration,
    authService: MsalService,
    location: Location,
    msalBroadcastService: MsalBroadcastService,
    private readonly cognito: CognitoService
  ) {
    super(msalInterceptorConfig, authService, location, msalBroadcastService);
    this.basesApi = [
      environment.apiProductosUrl,
      environment.apiIdentidadUrl,
      environment.apiCarritoUrl,
      environment.apiRabbitUrl,
      environment.apiGatewayBase,
      environment.apiGatewayCognitoBase
    ].filter((url): url is string => !!url);
  }

  override intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const tokenCognito = this.cognito.token();
    if (tokenCognito && this.esApi(req.url)) {
      return next.handle(
        req.clone({
          url: this.paraApiDeCognito(req.url),
          setHeaders: { Authorization: `Bearer ${tokenCognito}` }
        })
      );
    }
    return super.intercept(req, next);
  }

  private esApi(url: string): boolean {
    return this.basesApi.some((base) => url.startsWith(base));
  }

  /**
   * Cambia la API PRINCIPAL por la API SECUNDARIA cuando hay una. Si no hay
   * una segunda API configurada (o la URL no pertenece a la API principal)
   * la URL queda igual.
   */
  private paraApiDeCognito(url: string): string {
    const principal = environment.apiGatewayBase;
    const secundaria = environment.apiGatewayCognitoBase;
    if (!principal || !secundaria || !url.startsWith(principal)) {
      return url;
    }
    return secundaria + url.slice(principal.length);
  }
}
