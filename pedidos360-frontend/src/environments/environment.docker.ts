export const environment = {
  production: true,

  // DEMO LOCAL con LOGIN REAL (docker compose): exige login con Microsoft Entra ID.
  // Usada solo por `docker compose up` (build con --configuration=docker).
  // NO afecta a environment.ts (local/ng serve) ni environment.prod.ts (despliegue real).
  authBypass: false,

  // MSAL se instancia (auth-config.ts / APP_INITIALIZER) con los IDs reales.
  azureClientId: 'bcb83cb2-3d6f-4715-b3e7-204237e6f53d',
  azureTenantId: '9150f81f-b0fc-436d-998d-dcb5cf883d2e',
  azureApiScope: 'api://bcb83cb2-3d6f-4715-b3e7-204237e6f53d/write-read',

  redirectUri: 'http://localhost:4200/',

  // El navegador (host) llega a los contenedores via puertos mapeados en docker-compose.
  // 18081 -> ms-productos (8081), 8082 -> ms-identidad, 8083 -> ms-carrito.
  apiProductosUrl: 'http://localhost:18081/productos',
  apiIdentidadUrl: 'http://localhost:8082/usuarios',
  apiCarritoUrl: 'http://localhost:8083',

  // En la demo local NO hay API Gateway: las dos "puertas" son la misma
  // (el propio microservicio), asi que el cambio de URL del
  // TokenInterceptor es un no-op.
  apiGatewayBase: '',
  apiGatewayCognitoBase: '',

  // === Amazon Cognito (segundo IDaaS) ===
  // User Pool + App Client creados en AWS (ver GUIA-DESPLIEGUE.txt seccion 0.2).
  // Mientras esten vacios, el formulario de Cognito en /login muestra un aviso
  // y el login con Microsoft sigue funcionando normalmente.
  cognitoRegion: 'us-east-1',
  cognitoUserPoolId: 'us-east-1_FYeXHMqv3',
  cognitoClientId: '265jk804f5bv49fm35a9mgtj9s',

  // Microservicio administrador de RabbitMQ (colas, exchanges y bindings).
  // En produccion se llama a traves del API Gateway (ruta /rabbit -> EC2:8087).
  apiRabbitUrl: 'http://localhost:8087',
};
