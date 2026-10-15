# CHANGELOG - Pedidos360

## [1.8.0] - 2026-10-12 — Enunciado 2: eventos con RabbitMQ, órdenes, despachos y dos API Gateways
### Añadido
- **Nuevo microservicio `ms-ordenes`** (puerto 8088, `Dockerfile` con `EXPOSE 8088`): las órdenes de compra **nacen de los eventos** de RabbitMQ (no hay `POST`). Cola `pedidos360.ordenes` con las 4 routing keys y DLQ propia; `OrdenService` distingue `CREADA`/`ACTUALIZADA`/`REPETIDA`/`IGNORADA` con `pedidoId` único (idempotencia), estados `REGISTRADA` → `PAGADA` / `CANCELADA` y anulación manual por API (`PUT /ordenes/{id}/estado`, solo `ANULADA` desde `REGISTRADA` o `PAGADA`). API de solo lectura (`GET /ordenes`, `/{id}`, `/pedido/{pedidoId}`) con propiedad por usuario (403/404). Boilerplate de seguridad copiado de `ms-email`: `SecurityConfig` + `MultiIssuerJwtDecoder` (Azure + Cognito) con `app.auth.bypass`.
- **Nuevo microservicio `ms-despachos`** (puerto 8089): guías de envío que nacen con `pago.aprobado` (id `P360-00001` derivado del pedido → crearla dos veces no duplica) y avanzan `PENDIENTE → EN_RUTA → ENTREGADO` (+ `CANCELADO` antes de salir) con `despacho` terminal que no retrocede. Cola `pedidos360.despachos` con `pago.aprobado` y `pedido.cambio_estado`. Misma API de solo lectura + `PUT /despachos/{id}/estado`.
- **Stock asíncrono en `ms-productos`**: dependencia `spring-boot-starter-amqp`, cola `pedidos360.stock` (`pedido.registrado` y `pago.rechazado`), `StockListener` con reintentos y backoff exponencial y `StockService.aplicar` con resultado `APLICADO`/`REPETIDO`/`IGNORADO`. Idempotencia real con la tabla `stock_eventos_procesados` (`unique(tipo, pedido_id)`) y descuento con `Math.max(0, …)` para que nunca quede stock negativo. El producto ya no se guarda implícitamente: se llama `save()` explícito.
- **Migración del productor (`ms-carrito`) al nuevo intercambio**: `EventoNegocio` (payload enriquecido con `destino/asunto/cuerpo`, `pedidoId`, `usuarioId`, `estado`, `metodoPago`, `total`, `fecha` e `items[]`) y `EventoNegocioPublisher` reemplazan a `EmailMessage`/`EmailPublisher`; `EventosMessagingProperties` (prefijo `pedidos360.messaging.eventos`) y `RabbitConfig` que **solo declara el exchange** `pedidos360.eventos.topic` (tipo directo) y valida en el constructor que existan las 4 routing keys obligatorias (fail-fast).
- **Topología de broker declarada por consumidor** (evita `PRECONDITION_FAILED`): cada consumidor declara su cola durable + DLQ (`x-dead-letter-exchange: ''`) + bindings con los mismos nombres — `pedidos360.emails` (ms-email, 4 keys), `pedidos360.stock`, `pedidos360.ordenes` y `pedidos360.despachos`. ACK manual con `prefetch:1` y `default-requeue-rejected:false`: error de BD → reintentos con backoff y luego `NACK` a la DLQ; JSON inválido o evento repetido → `ACK`/`NACK` sin requeue según corresponda.
- **Endpoints nuevos en el nginx**: `/ordenes` → 8088, `/despachos` → 8089 y `/emails` → 8084 (para probar `POST /emails/enviar` con Postman con el JWT), de modo que el Security Group puede seguir abriendo solo el 80. Los 9 prefijos de API se atienden ahora **dos veces: con y sin `/desarrollo/` delante**, así el reverse proxy funciona igual que el SPA aunque el gateway del frontend reenvíe el path completo; las cabeceras de proxy pasaron a declararse una sola vez a nivel de `server` (nginx las hereda en todos los location).
- **Seguridad en la puerta de enlace sin crear APIs nuevas**: `environment.prod.ts` gana `apiGatewayBase` (la **API de lógica** `st3wqbtin7` que ya tenía el **JWT Authorizer** de Microsoft Entra ID, a la que se le agregan las rutas nuevas) y `apiGatewayCognitoBase` (**el mismo gateway del frontend** `n27vhcx1n0` que ya existía, sin authorizer, por donde entran las sesiones de Amazon Cognito: ahí el token lo validan los microservicios con `MultiIssuerJwtDecoder`). `core/token.interceptor.ts` reescribe la URL hacia el gateway del frontend cuando hay sesión de Cognito y solo entonces adjunta su ID token; con `''` el comportamiento es el anterior. `environment.ts` y `environment.docker.ts` traen ambas claves en `''` (en local no hay gateway).
- **`docker-compose.yml`**: `ms-ordenes` (8088, volumen `ordenes-data`) y `ms-despachos` (8089, volumen `despachos-data`) con `RABBITMQ_*`, `AZURE_ISSUER_URI`, `COGNITO_ISSUER_URL`, `AUTH_BYPASS` y `depends_on: rabbitmq`; `ms-productos` ahora también recibe `RABBITMQ_*` y espera al broker. Stack total: 11 contenedores.
- **Despliegue multi-EC2 parametrizado (una instancia por microservicio)**: `docker-compose.yml` reemplaza los valores fijos por `${VAR:-defecto}` en `RABBITMQ_HOST/USERNAME/PASSWORD` (los 6 servicios que hablan con el broker), `RABBITMQ_DEFAULT_USER/PASS`, `RABBITMQ_MGMT_URL`, `PRODUCTOS_SERVICE_URL`, `PAGOS_SERVICE_URL`, `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_ADVERTISED_LISTENERS` (con `KAFKA_ADVERTISED_HOST` para que el broker se anuncie con la IP de la instancia) y, en el servicio `frontend`, las 9 `MS_*_URL` del reverse proxy. El `nginx.conf` deja de ser configuración y pasa a ser **plantilla**: el `Dockerfile` lo copia a `/etc/nginx/templates/default.conf.template` y el entrypoint de la imagen `nginx:alpine` lo procesa con `envsubst` al arrancar (solo sustituye `MS_*_URL`; `$host`, `$uri` y `$1` quedan tal cual), de modo que cambiar una IP es un cambio de `.env` + `docker compose up -d --no-deps frontend`, sin rebuild. **En local no cambia nada**: todos los valores por defecto siguen siendo los nombres de la red de Docker. `.env.example` gana el bloque *DESPLEGUE MULTI-EC2* con el qué-pone-cada-instancia.
- **Documentación**: `GUIA-DESPLIEGUE.txt` (4.2 con rutas nuevas, 4.1 con el Security Group unificado —un solo grupo, origen "self" en los puertos internos— y 4.3 con las **dos topologías** (A: todo en una EC2; B: una EC2 por microservicio, con el paso a paso por instancia, comandos `--no-deps`, orden de arranque, advertencia de costo y **checklist de "dónde van las IPs"**), checklist del JWT Authorizer y de la segunda API, sección 5 con los curl de `POST /emails/enviar`, `GET/PUT /ordenes` y `/despachos` y las 4 colas, y **sección 8 de entrega** con `git subtree split` para separar el monorepo en un repo por microservicio), `README.md` (tabla de colas consumidoras, endpoints de los dos servicios nuevos, flujo asíncrono reescrito, 246 pruebas y limitación de la segunda API) y `ayuda.txt`.

- **Corrección del prefijo `/rabbit` en el reverse proxy** (afectaba a producción, no a local): `ms-rabbit-admin` expone `/queues`, `/exchanges` y `/bindings` en la **raíz** y el prefijo `/rabbit` no lo quitaba nadie, así que `GET /rabbit/queues` llegaba al backend como `/rabbit/queues` → 404 (tanto por el gateway del frontend como por el nginx, ya que `proxy_pass` con variable no reescribe el path). El `nginx.conf` ahora usa `location /rabbit/` (así no se come a la ruta del SPA `/rabbitmq`) con `rewrite ^/rabbit/(.*)$ /$1 break;`, y la variante `/desarrollo/rabbit` quita **ambos** prefijos. Con eso la página `/rabbitmq` del frontend administra colas saliendo por el API Gateway.

### Verificado (2026-10-12)
- `mvn test` en los **9** microservicios: **246 tests, 0 fallos** (35 + 8 + 38 + 14 + 22 + 12 + 44 + 38 + 35).
- `npx ng build`: **0 errores** (`<base href="/desarrollo/">`).
- `docker compose config -q` sin errores con los valores por defecto resueltos (RABBITMQ_HOST=rabbitmq, KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092, MS_CARRITO_URL=http://ms-carrito:8083, …) y `nginx -t` sobre el `nginx.conf` real sin errores.
- Plantilla de nginx validada en la imagen real `nginx:alpine`: con 8 IPs de ejemplo, el entrypoint ejecuta `20-envsubst-on-templates.sh` y los **18 `proxy_pass`** quedan sustituidos (`http://10.0.1.x:80xx`) mientras `listen 80`, `rewrite ^/desarrollo/(.*)$ /$1` y `Host $host` sobreviven intactos; `nginx -t` OK.
- Enrutado del reverse proxy **con verificación de paths** (27 casos) contra 9 backends eco: cada prefijo llega a su microservicio **y con la ruta correcta que espera el controlador** —incluido `/rabbit/queues` y `/desarrollo/rabbit/queues` → backend `/queues`—, las rutas SPA `/rabbitmq` y `/desarrollo/rabbitmq` **no** caen en ningún backend, y se conservan las cabeceras `Host`, `X-Real-IP` y `X-Forwarded-For`.
- Enrutado del reverse proxy probado de punta a punta con un nginx real y un backend eco por puerto: los 9 prefijos (`/productos`, `/usuarios`, `/carrito`, `/pedidos`, `/ordenes`, `/despachos`, `/emails`, `/rabbit`, `/monitoreo`) llegan al microservicio correcto **con y sin `/desarrollo/`**, y `/desarrollo/<algo que no es API>` sigue cayendo en el `index.html` del SPA.

### Pendiente (solo consola de AWS; no hay que crear APIs nuevas)
- En la API de lógica `st3wqbtin7` (ya tiene el **JWT Authorizer** de Microsoft Entra ID): añadir las rutas `/ordenes/{proxy+}`, `/despachos/{proxy+}`, `/emails/{proxy+}` (y `/rabbit`, `/monitoreo` si faltan) copiando la integración y la autorización de `/carrito`.
- En el gateway del frontend `n27vhcx1n0`: verificar que la ruta sea `ANY /desarrollo/{proxy+}` (¡con `+`!) para que pasen las rutas de API con varios segmentos; es por donde entran las sesiones de Cognito.


## [1.7.0] - 2026-10-11 — Login dual (Microsoft + Cognito), consola de RabbitMQ y HTTPS
### Añadido
- **Segundo IDaaS: Amazon Cognito** para crear cuentas propias de la tienda, en la MISMA pantalla `/login` que conserva el ingreso con Microsoft Entra ID. Nuevo `core/cognito.service.ts` (`amazon-cognito-identity-js`: pool perezoso, `registrar`/`confirmar`/`iniciarSesion`/`cerrarSesion`, sesión en `localStorage` + `sesion$`), `core/auth.guard.ts` (guard único para las dos sesiones) y `core/token.interceptor.ts` (subclase de `MsalInterceptor` que adjunta el ID token de Cognito cuando hay sesión de Cognito; si no, deja pasar el de Azure). La pestaña "Crear cuenta" muestra el campo de código de verificación.
- **`MultiIssuerJwtDecoder` en los 7 microservicios**: lee el claim `iss` sin verificar, rechaza emisores no admitidos con `JwtException("Emisor de identidad no admitido: ...")` y delega en `NimbusJwtDecoder.withIssuerLocation(...)` de forma perezosa y cacheada (Microsoft y Cognito en paralelo, normalizando barra final). Se añadió el `@Bean JwtDecoder` en los 7 `SecurityConfig` (backoff de la autoconfiguración) y la variable `app.auth.cognito-issuer` / `COGNITO_ISSUER_URL` en yml, compose y `.env.example`.
- **Claims adaptados al IDaaS**: `ms-identidad/UsuarioController` (`email`: `preferred_username`→`email`; `oid`: `oid`→`sub`; `nombre`: `name`→`cognito:username`→email) y `ms-carrito/CarritoController` (destinatario de los correos), así un token de Cognito produce el mismo usuario y los mismos correos que uno de Azure.
- **`aws/cognito-pool.yaml`** (CloudFormation): User Pool con email como nombre de usuario, verificación por código, sin MFA, política de contraseña 8+ y App Client de SPA sin secreto (SRP + password + refresh); `Outputs` = `UserPoolId`, `UserPoolClientId` y `COGNITO_ISSUER_URL`. Se crea desde la consola (no requiere AWS CLI); pasos completos en `GUIA-DESPLIEGUE.txt` sección 0.2.
- **Consola de RabbitMQ en el frontend** (`/rabbitmq`, con link en la navbar): tablas de colas, exchanges y bindings con creación y borrado, vía nuevo servicio `services/rabbit-admin.service.ts` contra `ms-rabbit-admin` (`GET/POST /queues|/exchanges|/bindings`, `DELETE`). En producción se publica detrás del API Gateway (ruta `/rabbit/{proxy+}` → EC2:8087).
- **HTTPS de producción**: `redirectUri` apunta al API Gateway `https://n27vhcx1n0.execute-api.us-east-1.amazonaws.com/desarrollo/` (Azure solo acepta HTTPS), `angular.json` compila la configuración `production` con `<base href="/desarrollo/">` (y `production-root` como respaldo para servir desde "/"), `nginx.conf` atiende también bajo `/desarrollo/*` (funciona con o sin reescritura en el gateway) y los 7 `SecurityConfig` aceptan ese origen en CORS.
- **Reverse proxy en el nginx del frontend**: `/productos`, `/usuarios`, `/carrito`, `/pedidos`, `/rabbit` y `/monitoreo` se derivan dentro de la red de Docker hacia cada microservicio, de modo que el API Gateway y el Security Group solo necesitan el puerto **80** (sin abrir 8081-8087). Validado con `nginx -t`.
- **`ms-rabbit-admin` en el stack**: servicio en `docker-compose.yml` (8087, `RABBITMQ_MGMT_URL: http://rabbitmq:15672`, `depends_on: rabbitmq`), `CATALOGO_SEED` en `ms-productos` y checklist en `GUIA-DESPLIEGUE.txt`.
- **RabbitMQ design de correos**: exchange directo `pedidos360.emails.topic` + 4 routing keys (`pedido.registrado`, `pago.aprobado`, `pago.rechazado`, `pedido.cambio_estado`), 4 `Binding` → cola `pedidos360.emails` + DLQ, ACK manual con `prefetch:1` y `default-requeue-rejected:false` (reintentos y paso a la DLQ).
- **Documentación**: `GUIA-DESPLIEGUE.txt` (checklist a 9 pasos, sección 0.2 Cognito, URIs de Azure con la URL del gateway, CORS con el origen HTTPS, rutas `/rabbit` y `/desarrollo` en el API Gateway, `.env` con `COGNITO_ISSUER_URL`, checkpoint HTTPS resuelto y errores nuevos de Cognito), `README.md` (arquitectura con los 2 IDaaS, endpoints de `ms-rabbit-admin`, configuración y limitaciones) y `ayuda.txt` (mapa de los bloques nuevos).

### Verificado (2026-10-11)
- `mvn test` en los 7 microservicios: **149 tests, 0 fallos** (13 + 8 + 36 + 14 + 22 + 12 + 44).
- `npx ng build`: **0 errores** (y `<base href="/desarrollo/">` en el `index.html` de producción).
- `docker compose config -q` sin errores.
- Sin cambios en la lógica de la compra ni en el envío de correos.

### Pendiente (depende de la cuenta de AWS)
- Crear el User Pool con `aws/cognito-pool.yaml` y copiar los `Outputs` a `environment.prod.ts` (`cognitoUserPoolId`, `cognitoClientId`) y al `.env` (`COGNITO_ISSUER_URL`); hasta entonces el formulario de Cognito en `/login` muestra un aviso y funciona solo Microsoft.
- Publicar la ruta `/rabbit/{proxy+}` → EC2:8087 en el API Gateway de las APIs (`st3wqbtin7`) y la ruta `/desarrollo` → EC2:80 en el gateway del frontend (`n27vhcx1n0`).

## [1.6.0] - 2026-10-07 — Login real, correo Gmail y stack verificado end-to-end
### Añadido
- **`environment.docker.ts`** (nuevo): perfil de build para el contenedor frontend con **login real de Microsoft Entra ID** (`authBypass: false`), `redirectUri http://localhost:4200/` y URLs de localhost (18081/8082/8083). Se elige con la variable `NG_BUILD_CONFIG=docker` del `.env`.
- **`NG_BUILD_CONFIG` en el frontend**: `Dockerfile` con `ARG NG_BUILD_CONFIG=production` y `docker-compose.yml` con `NG_BUILD_CONFIG: ${NG_BUILD_CONFIG:-production}`. Sin `.env` el build es de producción (`environment.prod.ts` = login + API Gateway) — listo para que Tomy suba a AWS sin cambios.
- **Frontend en Docker publica `80:80` y `4200:80`**: el `4200` es la URI de redirección que Azure tiene registrada para pruebas locales y el login así funciona sin tocar nada.
- **Fix de Kafka en modo KRaft**: `KAFKA_LISTENERS` sin IP (solo puerto) porque el modo KRaft de `apache/kafka:3.9.0` rechaza `0.0.0.0` como host no enrutable; ahora `ms-monitoreo` consume el tópico y el monitoreo queda end-to-end.
- **Correo real por Gmail (SMTP) configurado**: credenciales solo en el `.env` (`.env.example` documenta `MAIL_USERNAME`/`MAIL_PASSWORD`/`MAIL_FROM`); verificado enviando 4 correos reales a `diegoxmegalala@gmail.com` (pedidos registrado/aprobado/rechazado), colas en 0.
- `.env.example`, `ayuda.txt`, `GUIA-DESPLIEGUE.txt` y `README.md` actualizados con el nuevo build, los puertos 80/4200 y el estado real de lo verificado.

### Verificado (2026-10-07, stack completo de 9 contenedores)
- Kafka + monitoreo end-to-end: comprar → evento visible en `GET /monitoreo/eventos`.
- Correos reales por SMTP Gmail vía RabbitMQ → `ms-email`.
- Login real habilitado (backends devuelven 401 sin JWT; el frontend en 4200 con `authBypass: false` redirige al login de Microsoft).

## [1.5.0] - 2026-10-05 — Monitoreo con Kafka
### Añadido
- **Kafka como log de eventos de negocio (monitoreo)**, en paralelo a RabbitMQ (que sigue siendo quien entrega los correos): el tópico `pedidos360.monitoreo` recibe los mismos 4 eventos que la cola (`PEDIDO_REGISTRADO`, `PAGO_APROBADO`, `PAGO_RECHAZADO`, `CAMBIO_ESTADO`) con clave = `pedidoId` para que todos los eventos de un pedido se escriban en la misma partición y se lean en orden.
- **Productor en `ms-carrito`**: dependencia `spring-kafka`, variable `KAFKA_BOOTSTRAP_SERVERS` (por defecto `localhost:9092`) y clase `messaging/MonitorPublisher.java`, llamada desde `CarritoController` en checkout, pago aprobado, pago rechazado y cambio de estado. Si Kafka no está disponible solo se registra el error: la compra nunca falla.
- **Nuevo microservicio `ms-monitoreo`** (Spring Boot, puerto 8086, `Dockerfile` propio): consume el tópico con `@KafkaListener` (grupo de consumidor `ms-monitoreo`, JSON ilegible no detiene al consumidor), expone los últimos 500 eventos con su `topico`/`particion`/`offset` en `EventoMonitor`, mantiene contadores acumulados por tipo (`EventoStore`) y publica `GET /monitoreo/eventos?tipo=...` y `GET /monitoreo/estadisticas`; CORS y bypass igual que el resto de servicios.
- **`docker-compose.yml`**: servicio `kafka` (`apache/kafka:3.9.0` en modo KRaft, sin ZooKeeper, auto-creación de tópico, expuesto solo en `127.0.0.1:9094`), servicio `ms-monitoreo` (`depends_on: kafka`) y `KAFKA_BOOTSTRAP_SERVERS: kafka:9092` para `ms-carrito`. Total del stack: 9 contenedores.
- **6 pruebas nuevas en `ms-monitoreo`** (`EventoStoreTest` 4, `EventoConsumerTest` 2); `ms-carrito` sigue en 21. Total del proyecto: 54 pruebas en verde.
- **`KAFKA-MONITOREO.txt`**: explicación de qué es Kafka y cómo funciona acá, tabla RabbitMQ vs Kafka, diagrama de flujo, API del monitoreo con ejemplos JSON, receta de verificación paso a paso, qué quedó verificado/pendiente y guion de presentación de 60 s.
- `README.md`, `ayuda.txt` y `GUIA-DESPLIEGUE.txt` actualizados con `ms-monitoreo`, el puerto 8086, el servicio `kafka` y la sección de monitoreo.

### Limitaciones conocidas
- El flujo end-to-end con broker real (comprar y ver el evento en `GET /monitoreo/eventos`) está **pendiente de ejecutar**; lo verificado hasta ahora es la compilación, las 6 pruebas unitarias de `ms-monitoreo`, las 21 de `ms-carrito` y la validez del `docker-compose.yml`. La receta exacta queda en `KAFKA-MONITOREO.txt` punto 7.
- `ms-monitoreo` guarda los eventos en memoria (ventana de 500): al reiniciarse se pierde el histórico local, pero el log completo permanece en Kafka.

## [1.4.0] - 2026-10-01 — Segunda evaluación
### Añadido
- **Métodos de pago simulados** en `ms-carrito`: `EstadoPago` (`PENDIENTE`/`APROBADO`/`RECHAZADO`), `MetodoPago` (`TARJETA_CREDITO`/`TARJETA_DEBITO`/`TRANSFERENCIA`) y flujo de checkout en dos pasos sin duplicar pedidos: `POST /carrito/checkout` crea el pedido en `PENDIENTE_PAGO` y vacía el carrito; `POST /pedidos/{id}/pago` aprueba (`CONFIRMADO`+`APROBADO`) o rechaza (`CANCELADO`+`RECHAZADO`, restaurando el carrito). Un pago repetido devuelve 400.
- **Estados de pedido ampliados**: `PENDIENTE_PAGO`, `CONFIRMADO` (pagado), `EN_PREPARACION`, `ENVIADO`, `ENTREGADO`, `CANCELADO`; `PUT /pedidos/{id}/estado` para avanzar estados con validación de transiciones.
- **Gestión completa de cantidades del carrito**: `PUT /carrito/items/{id}` para aumentar/disminuir, validación de cantidades y de stock, y propiedad por usuario (403 si un usuario toca el carrito de otro).
- **Catálogo con filtros e imágenes**: `imagenUrl` en `Producto`, `GET /productos/categorias`, filtros combinados `?nombre=&categoria=` en `GET /productos` y datos de ejemplo con fotografías reales.
- **Tres eventos de correo** publicados por `ms-carrito`: `PEDIDO_REGISTRADO`, `PAGO_APROBADO`, `PAGO_RECHAZADO` (una sola vez por evento), con plantillas HTML en `PlantillaEmails`.
- **Reintentos y DLQ**: cola `pedidos360.emails` con `x-dead-letter-*` a `pedidos360.emails.dlq` declarada en ambos servicios, y `spring.rabbitmq.listener.simple` con 3 intentos en `ms-email`.
- **Nuevo microservicio `ms-pagos`** (Spring Boot, puerto 8085): microservicio separado que decide y registra el pago simulado (`POST /pagos`, `GET /pagos/pedido/{id}`), con entidad `Pago` (a lo sumo uno por pedido), JWT de Azure validado (ms-carrito reenvía el token), CORS y 8 pruebas unitarias. `ms-carrito` lo consume con `PagoClient` (`PAGOS_SERVICE_URL`) y solo aplica el resultado sobre el pedido; si `ms-pagos` no responde, el pago no se aprueba.
- **Frontend rediseñado como tienda comercial**: página de inicio (banner, categorías, destacados, footer), catálogo con chips de categoría y búsqueda, carrito con stepper de cantidades y resumen, **checkout con selección de método de pago** y resultados de compra, historial de pedidos filtrable y expandible, navbar con buscador/campana/contador de carrito y notificaciones visuales (toasts + campana).
- `.env.example` con todas las variables (sin credenciales) y volúmenes `carrito-data` y `pagos-data` en `docker-compose.yml` para que pedidos y pagos sobrevivan a reinicios.

### Corregido
- El evento de correo del checkout pasó de `PEDIDO_CONFIRMADO` a `PEDIDO_REGISTRADO`: con el pago simulado, el checkout ya no "confirma" la compra, solo la registra.
- `PRODUCTOS_SERVICE_URL` en `docker-compose.yml` (apuntaba a `localhost`, dentro del contenedor debe ser `http://ms-productos:8081/productos`).
- CORS de `ms-identidad` alineado con el resto de servicios (incluye el origen desplegado en `https://44.200.146.153`).
- `sourceEncoding` UTF-8 en los cuatro `pom.xml` (evita errores de compilación por tildes).
- Import de `filter` faltante en `navbar.component.ts` y ruta de importación de `toast.service` en `toast-container.component.ts` (el build de Angular fallaba).

### Cambiado
- `environment.ts` (local) queda con `authBypass: false`: el login con Microsoft Entra ID es el comportamiento por defecto también en local; `true` queda documentado como alternativa solo para pruebas sin Azure.
- `DatosDeEjemplo` (ms-productos) ya no depende de `AUTH_BYPASS`: siembra los 8 productos cuando la base arranca vacía (variable `CATALOGO_SEED`, por defecto `true`), para que el catálogo no salga en blanco en el primer despliegue con autenticación real.
- `GUIA-DESPLIEGUE.txt` ampliada con la sección **0.1 Dónde cambiar el tenant / client ID** (tabla archivo → línea exacta para un registro de Azure nuevo) y con el checklist de verificación actualizado al flujo real de checkout en dos pasos.

### Limitaciones conocidas
- El pago es **simulado**: no hay conexión con bancos ni se piden datos reales de tarjeta (los del formulario solo existen en el navegador).
- Con Docker Desktop + WSL2 instalados en el entorno local, `docker compose up -d --build` quedó verificado end-to-end (7 contenedores, compra aprobada/rechazada, RabbitMQ publica → consume → 3 reintentos → `pedidos360.emails.dlq`). Sigue sin verificarse el envío real por SMTP (falta una app password) y el login real con Microsoft Entra ID.

## [1.3.0] - 2026-09-28
### Añadido
- Nuevo microservicio **`ms-email`** (Spring Boot, puerto 8084): consume la cola durable `pedidos360.emails` en RabbitMQ y envía los correos por SMTP (`spring-boot-starter-mail`), con endpoint manual `POST /emails/enviar`, validación de JWT de Azure AD y pruebas unitarias (`EmailServiceTest`, `EmailListenerTest`).
- Integración de **RabbitMQ** en `ms-carrito`: al confirmar el checkout se publica el evento `PEDIDO_CONFIRMADO` (`{destino, asunto, cuerpo, tipo}`) en JSON. Si el broker no está disponible la compra no falla, solo se registra el error.
- Servicio `rabbitmq:3.13-management-alpine` en `docker-compose.yml` (AMQP 5672 + consola 15672) y variables `RABBITMQ_HOST/USERNAME/PASSWORD` para `ms-carrito` y `ms-email`.
- Credenciales SMTP por variables de entorno (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`), eliminando secretos del código.

## [1.2.0] - 2026-09-07
### Añadido
- Migración del frontend a **Angular**, implementando componentes modulares, enrutamiento, servicios tipados (`producto.service.ts`, `carrito.service.ts`, `usuario.service.ts`) y configuración moderna con MSAL para autenticación.
- Creación completa del microservicio **`ms-carrito`** (Spring Boot), incluyendo gestión de carritos, ítems, pedidos y modelos de persistencia JPA.
- Creación del microservicio **`ms-identidad`** (Spring Boot) para el manejo de usuarios y roles del sistema.
- Implementación completa del microservicio **`ms-productos`**, que hasta la versión anterior solo contaba con clases vacías: entidad, repositorio, servicio y controlador con endpoints CRUD funcionales.
- Implementación de pruebas unitarias para la lógica de servicios (`ProductoServiceTest`, `UsuarioServiceTest`, `CarritoServiceTest`).
- Creación de contenedores individuales mediante **Dockerfiles** para cada microservicio y el frontend, junto con la configuración de Nginx.
### Corregido
- Ruta del `application.yml` de `ms-productos`, que estaba en `src/resources/` y no era reconocida por Maven; se movió a `src/main/resources/`.
### Pendiente
- Registro de la aplicación en Azure Entra ID (App Registration, client ID, tenant ID, scope).
- Configuración de AWS API Gateway como entrypoint hacia los microservicios en EC2.
- Despliegue de los microservicios en EC2 y del frontend.
- Conexión a la base de datos cloud definitiva.

## [1.1.0] - 2026-08-24
### Añadido
- Microservicio de Productos (Java / Spring Boot): estructura inicial de clases y paquetes.
- Configuración de dependencias en pom.xml (Spring Boot 3.x).
- Esqueleto de configuración de seguridad OAuth2 con Customizer.withDefaults() actualizada a Spring Security 6.1+.
### Corregido
- Corrección de la estructura de paquetes Java (com.pedidos360.productos) y resolución de dependencias de Maven.

## [1.0.0] - 2026-08-24
### Añadido
- Estructura inicial del proyecto: frontend en React (scaffold de páginas Login, Catálogo y Carrito) y esqueleto de microservicios de Productos y Carrito en Spring Boot.
- Definición de la arquitectura objetivo: autenticación con Azure AD (MSAL), backend en Spring Boot sobre EC2, expuesto mediante AWS API Gateway.