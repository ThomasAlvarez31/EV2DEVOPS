export const environment = {
  production: true,

  // false = login con Microsoft Entra ID (producción). Ver environment.ts (local).
  authBypass: false,

  // === Datos del registro de la app en Azure Entra ID ===
  azureClientId: 'bcb83cb2-3d6f-4715-b3e7-204237e6f53d',
  azureTenantId: '9150f81f-b0fc-436d-998d-dcb5cf883d2e',
  azureApiScope: 'api://bcb83cb2-3d6f-4715-b3e7-204237e6f53d/write-read',

  // URL pública del frontend. Azure SOLO acepta HTTPS para URIs que no sean
  // localhost: por eso se usa el API Gateway (n27vhcx1n0) que SI termina TLS.
  // Debe coincidir EXACTO con el "URI de redirección" registrado en Azure
  // (plataforma SPA) y con el <base href> del build (= /desarrollo/).
  redirectUri: 'https://n27vhcx1n0.execute-api.us-east-1.amazonaws.com/desarrollo/',

  // URL(s) del AWS API Gateway que enruta hacia cada microservicio en EC2.
  //
  // === UNA sola API de logica + el gateway del frontend ya existente ===
  // (NO se crean gateways nuevos).
  //
  // apiGatewayBase = st3wqbtin7: la API de LOGICA donde estan las rutas
  //   /productos /usuarios /carrito /pedidos /ordenes /despachos /emails
  //   /rabbit /monitoreo. Tiene el JWT Authorizer de Microsoft Entra ID
  //   (issuer del tenant + audience = azureApiScope) y su CORS ya acepta el
  //   origen del frontend. TODAS las sesiones de Microsoft pasan por aqui.
  //
  // apiGatewayCognitoBase = n27vhcx1n0: el MISMO gateway que ya sirve el
  //   frontend en HTTPS (ruta ANY /desarrollo/{proxy+} -> EC2:80 -> nginx).
  //   Ese gateway NO tiene authorizer, asi que deja pasar las llamadas hechas
  //   con sesion de Amazon Cognito; ahi el JWT lo validan los microservicios
  //   (MultiIssuerJwtDecoder acepta Azure y Cognito). Si se mandara un token
  //   de Cognito a la API de logica, el authorizer lo rechazaria con 401.
  //   El TokenInterceptor reescribe la URL sola cuando hay sesion de Cognito.
  apiGatewayBase: 'https://st3wqbtin7.execute-api.us-east-1.amazonaws.com/desarrollo',
  apiGatewayCognitoBase: 'https://n27vhcx1n0.execute-api.us-east-1.amazonaws.com/desarrollo',

  apiProductosUrl: 'https://st3wqbtin7.execute-api.us-east-1.amazonaws.com/desarrollo/productos',
  apiIdentidadUrl: 'https://st3wqbtin7.execute-api.us-east-1.amazonaws.com/desarrollo/usuarios',
  apiCarritoUrl: 'https://st3wqbtin7.execute-api.us-east-1.amazonaws.com/desarrollo/carrito',

  // === Amazon Cognito (segundo IDaaS) ===
  // User Pool + App Client creados en AWS (ver GUIA-DESPLIEGUE.txt seccion 0.2).
  // Mientras esten vacios, el formulario de Cognito en /login muestra un aviso
  // y el login con Microsoft sigue funcionando normalmente.
  cognitoRegion: 'us-east-1',
  cognitoUserPoolId: 'us-east-1_FYeXHMqv3',
  cognitoClientId: '265jk804f5bv49fm35a9mgtj9s',

  // Microservicio administrador de RabbitMQ (colas, exchanges y bindings).
  // En produccion se llama a traves del API Gateway (ruta /rabbit -> EC2:8087).
  apiRabbitUrl: 'https://st3wqbtin7.execute-api.us-east-1.amazonaws.com/desarrollo/rabbit',
};
