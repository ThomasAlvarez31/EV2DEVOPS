# Pedidos360 — E-Commerce Cloud Native

Repositorio del proyecto desarrollado para la asignatura **Desarrollo Cloud Native I (DSY1107)** — Evaluación Parcial N°1 y **Segunda Evaluación**.

Plataforma de e-commerce con arquitectura de microservicios, autenticación centralizada mediante **Azure Entra ID (MSAL)**, frontend en **Angular**, **pagos simulados** con tres métodos de pago y una capa de **eventos con RabbitMQ** que dispara en paralelo las notificaciones por correo, el **stock asíncrono**, las **órdenes de compra** y los **despachos**. La seguridad en la nube está puesta en el **AWS API Gateway** (JWT Authorizer de Microsoft Entra ID), con una segunda puerta de enlace para las sesiones de Amazon Cognito.

## Tecnologías

* Angular 17 (standalone components) + MSAL Angular
* Java 17 + Spring Boot 3.2 (microservicios)
* Spring Data JPA + H2 en memoria (por defecto; configurable a MySQL/PostgreSQL con `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`)
* RabbitMQ (eventos de negocio: correos, stock, órdenes y despachos; exchange directo + 4 colas con DLQ)
* Docker
* AWS (EC2 + API Gateway) / Microsoft Azure (Entra ID)
* Git / GitHub

## Arquitectura

```
                     ┌───────────────────────┐
                     │   Angular Frontend     │
                     │  (MSAL + Cognito)      │
                     └───────────┬───────────┘
                                 │ JWT (Bearer)
                                 ▼
                     ┌───────────────────────┐
                     │   AWS API Gateway      │
                     └───────────┬───────────┘
                                 │
        ┌────────────────────────┼────────────────────────┐
        ▼                        ▼                         ▼
┌───────────────┐      ┌───────────────┐        ┌───────────────┐
│ ms-productos   │      │ ms-identidad  │        │  ms-carrito    │
│ (puerto 8081)  │      │ (puerto 8082) │        │  (puerto 8083) │
└───────────────┘      └───────────────┘        └───────┬───────┘
   (EC2 + Spring Boot, cada uno valida el JWT           │ checkout publica
    emitido por Azure AD o Amazon Cognito)              │ eventos de correo
                                                       │
                                         ┌──────────────┴──────────────┐
                                         │ llama a POST /pagos         │
                                         ▼                             ▼
                                ┌───────────────┐            ┌─────────────────┐
                                │   ms-pagos     │            │    RabbitMQ      │
                                │ (puerto 8085)  │            │ pedidos360.emails│
                                └───────────────┘            └────────┬────────┘
                                                                      │ consume
                                                                      ▼
                                                             ┌───────────────┐
                                                             │   ms-email     │
                                                             │  (puerto 8084) │
                                                             └───────┬───────┘
                                                                     │ SMTP
                                                                     ▼
                                                                Bandeja del
                                                                cliente

                        (el mismo checkout/pago también publica en paralelo)
                                                        │
                                                        ▼
                                               ┌─────────────────┐
                                               │      Kafka       │
                                               │ pedidos360.      │
                                               │ monitoreo (log)  │
                                               └────────┬────────┘
                                                        │ consume
                                                        ▼
                                               ┌───────────────┐
                                               │ ms-monitoreo  │
                                               │  (puerto 8086)│
                                               └───────┬───────┘
                                                       │
                                                       ▼
                                              GET /monitoreo/eventos
                                              GET /monitoreo/estadisticas
```

**Cada hecho de negocio se publica dos veces**, con un propósito distinto:

| Tipo | Cuándo se publica | Destino |
|---|---|---|
| `PEDIDO_REGISTRADO` | El checkout crea el pedido (queda pendiente de pago) | RabbitMQ (correo) **y** Kafka (monitoreo) |
| `PAGO_APROBADO` | El pago simulado fue aprobado | idem |
| `PAGO_RECHAZADO` | El pago simulado fue rechazado (pedido cancelado) | idem |
| `CAMBIO_ESTADO` | Cambio de estado logístico (en preparación / enviado / entregado) | idem |

- **RabbitMQ (exchange `pedidos360.eventos.topic`, tipo directo)** → cuatro consumidores en paralelo, cada uno con su cola durable y su DLQ:

  | Cola | Routing keys | Consumidor | Qué hace |
  |---|---|---|---|
  | `pedidos360.emails` | las 4 | `ms-email` | envía la notificación por SMTP |
  | `pedidos360.stock` | `pedido.registrado`, `pago.rechazado` | `ms-productos` | descuenta / repone el stock (idempotente) |
  | `pedidos360.ordenes` | las 4 | `ms-ordenes` | crea y actualiza la orden de compra |
  | `pedidos360.despachos` | `pago.aprobado`, `pedido.cambio_estado` | `ms-despachos` | crea la guía y hace el seguimiento logístico |

  El **productor es solo `ms-carrito`** y declara únicamente el exchange; cada consumidor declara su cola, su DLQ y sus bindings con los mismos nombres (por eso no hay conflictos `PRECONDITION_FAILED`). Tras 3 intentos fallidos el mensaje va a la DLQ correspondiente (`pedidos360.<servicio>.dlq`).
- **Kafka (log)** → monitoreo: el mensaje queda guardado en el tópico `pedidos360.monitoreo` con su partición y offset; `ms-monitoreo` lo lee sin borrarlo (pueden leerlo varios consumidores).

Si un broker no está disponible, el error solo se registra en los logs: **la compra nunca falla** por RabbitMQ ni por Kafka. Detalle completo en [`KAFKA-MONITOREO.txt`](KAFKA-MONITOREO.txt).

## Estructura del repositorio

```
├── ms-productos/          # Microservicio de catálogo de productos
├── ms-identidad/          # Microservicio de perfil de usuario (vinculado al login de Azure AD)
├── ms-carrito/            # Microservicio de carrito de compras y pedidos (productor Kafka)
├── ms-pagos/              # Microservicio de pagos (decide y registra el resultado del pago)
├── ms-email/              # Microservicio de correos (consume RabbitMQ y envía por SMTP)
├── ms-monitoreo/          # Microservicio de monitoreo (consume el log de Kafka)
├── ms-rabbit-admin/       # API REST de administración de RabbitMQ (colas, exchanges, bindings)
├── ms-ordenes/            # Ordenes de compra (nace de los eventos de RabbitMQ)
├── ms-despachos/          # Despachos / guías de envío (nace de los eventos de RabbitMQ)
├── pedidos360-frontend/   # SPA en Angular con MSAL (Microsoft) + Amazon Cognito
├── docker-compose.yml     # Stack completo: rabbitmq, kafka, 9 microservicios y frontend
├── aws/cognito-pool.yaml  # Plantilla CloudFormation del User Pool de Cognito (2do login)
├── GUIA-DESPLIEGUE.txt    # Despliegue paso a paso en AWS + cambio de tenant
├── KAFKA-MONITOREO.txt    # Qué es Kafka, cómo funciona acá y cómo demostrarlo
└── ayuda.txt              # Mapa del proyecto (qué pidió la evaluación y dónde está)
```

Cada microservicio de backend sigue la misma estructura interna:
```
ms-x/
├── pom.xml
├── Dockerfile
├── .gitignore
└── src/
    ├── main/java/com/pedidos360/x/
    │   ├── XApplication.java      # Clase principal Spring Boot
    │   ├── config/SecurityConfig.java   # Valida el JWT (Azure AD o Cognito)
    │   ├── model/                 # Entidades JPA
    │   ├── repository/            # Repositorios JPA
    │   ├── service/                # Lógica de negocio
    │   └── controller/            # Endpoints REST
    ├── main/resources/application.yml
    └── test/java/...              # Pruebas unitarias básicas
```

## Endpoints principales

**ms-productos** (`/productos`)
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/productos` | Catálogo. Filtros opcionales y combinables `?nombre=` `?categoria=` (búsqueda sin distinguir mayúsculas ni tildes) |
| GET | `/productos/categorias` | Lista de categorías existentes (para los filtros del frontend) |
| GET | `/productos/{id}` | Detalle de un producto |
| POST | `/productos` | Crea un producto |
| PUT | `/productos/{id}` | Actualiza un producto |
| DELETE | `/productos/{id}` | Elimina un producto |

**ms-identidad** (`/usuarios`)
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/usuarios/me` | Perfil del usuario autenticado (se crea automáticamente la primera vez) |
| GET | `/usuarios` | Lista todos los usuarios |
| PUT | `/usuarios/{id}/rol` | Cambia el rol de un usuario |

**ms-carrito** (`/carrito`, `/pedidos`)
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/carrito` | Carrito activo del usuario autenticado |
| POST | `/carrito/items` | Agrega un producto (precio, nombre e imagen se toman de `ms-productos`) |
| PUT | `/carrito/items/{id}` | Aumenta o disminuye la cantidad (valida stock y cantidad > 0) |
| DELETE | `/carrito/items/{id}` | Elimina un ítem del carrito |
| POST | `/carrito/checkout` | Crea el pedido en `PENDIENTE_PAGO`, vacía el carrito y publica `PEDIDO_REGISTRADO` |
| POST | `/pedidos/{id}/pago` | **Pago simulado**: delega la decisión en `ms-pagos` y aplica el resultado — aprueba (`CONFIRMADO`/`APROBADO`) o rechaza (`CANCELADO`/`RECHAZADO`, restaura el carrito). Body `{simularRechazo: boolean}` |
| GET | `/pedidos` | Historial de pedidos del usuario (más reciente primero) |
| GET | `/pedidos/{id}` | Detalle de un pedido (solo el propietario, 403 si no lo es) |
| PUT | `/pedidos/{id}/estado` | Avanza el seguimiento logístico: `CONFIRMADO → EN_PREPARACION → ENVIADO → ENTREGADO` (400 si la transición no está permitida). Body `{estado: "..."}` |

**ms-pagos** (`/pagos`) — microservicio separado que decide y registra el pago; lo llama `ms-carrito` (no se expone en el API Gateway)
| Método | Ruta | Descripción |
|---|---|---|
| POST | `/pagos` | Registra (simula) el pago de un pedido: aprueba o rechaza, con un solo pago por pedido. Body `{pedidoId, usuarioId, monto, metodoPago, simularRechazo}` |
| GET | `/pagos/pedido/{id}` | Pago registrado para un pedido (auditoría/soporte). 404 si aún no existe |

**ms-ordenes** (`/ordenes`, puerto 8088) — no recibe `POST`: la orden **nace de un evento** de RabbitMQ (`pedido.registrado` → `REGISTRADA`, `pago.aprobado` → `PAGADA`, `pago.rechazado` → `CANCELADA`)
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/ordenes` | Órdenes del usuario autenticado |
| GET | `/ordenes/{id}` | Detalle (403 si no es suya, 404 si no existe) |
| GET | `/ordenes/pedido/{pedidoId}` | Orden asociada a un pedido |
| PUT | `/ordenes/{id}/estado` | Anulación manual: admite `ANULADA` desde `REGISTRADA` o `PAGADA`. Body `{estado: "ANULADA"}` |

**ms-despachos** (`/despachos`, puerto 8089) — tampoco recibe `POST`: la guía nace con `pago.aprobado` (id `P360-00001` derivado del pedido) y avanza con los cambios de estado
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/despachos` | Despachos del usuario autenticado |
| GET | `/despachos/{id}` | Detalle (403 si no es suyo, 404 si no existe) |
| GET | `/despachos/pedido/{pedidoId}` | Despacho de un pedido |
| PUT | `/despachos/{id}/estado` | `PENDIENTE → EN_RUTA → ENTREGADO` (o `CANCELADO` antes de salir). Body `{estado: "..."}` |

**ms-email** (`/emails`)
| Método | Ruta | Descripción |
|---|---|---|
| POST | `/emails/enviar` | Envío manual de un correo (pruebas/operación). El flujo normal consume la cola `pedidos360.emails` de forma asíncrona |

**ms-monitoreo** (`/monitoreo`) — lee el tópico de Kafka `pedidos360.monitoreo`
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/monitoreo/eventos` | Últimos eventos de negocio vistos (más reciente primero, hasta 500 en memoria). Filtro opcional `?tipo=PAGO_APROBADO` |
| GET | `/monitoreo/estadisticas` | Totales: `totalRecibidos`, `retenidosEnMemoria`, contador `porTipo` y `ultimoEvento` |

**ms-rabbit-admin** (`/queues`, `/exchanges`, `/bindings`) — administración del broker (puerto 8087; en prod se publica con la ruta `/rabbit` del API Gateway, que apunta al front:80 y el nginx le quita ese prefijo)
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/queues` | Colas (nombre, mensajes, consumidores, durable, arguments) |
| POST | `/queues` | Crea una cola. Body `{name, durable, autoDelete, arguments}` |
| DELETE | `/queues/{name}?ifUnused=&ifEmpty=` | Elimina una cola (404 si no existe) |
| GET | `/exchanges` | Exchanges |
| POST | `/exchanges` | Crea un exchange. Body `{name, type, durable, autoDelete, arguments}` |
| DELETE | `/exchanges/{name}?ifUnused=` | Elimina un exchange |
| GET | `/bindings` | Bindings (exchange, queue, routingKey) |
| DELETE | `/bindings?queue=&exchange=&routingKey=` | Elimina un binding |

Errores tipados: `400` validación, `404` inexistente, `409` ya existe / `405` no se puede borrar, `503` broker caído (`{status, error, message, details}`).

### Flujo asíncrono de eventos (RabbitMQ)
1. El usuario confirma su compra (`POST /carrito/checkout`) o se produce un pago / un cambio de estado logístico.
2. `ms-carrito` publica en el **exchange directo** `pedidos360.eventos.topic` un JSON con routing key
   `pedido.registrado`, `pago.aprobado`, `pago.rechazado` o `pedido.cambio_estado`. El payload (`EventoNegocio`)
   trae lo que cada consumidor necesita: `pedidoId`, `usuarioId`, `estado`, `metodoPago`, `total`, `fecha`,
   `items[]` y el bloque de correo `destino/asunto/cuerpo`.
3. El exchange reparte el mensaje a las colas enlazadas (la tabla de arriba): **`ms-email`**, **`ms-productos`**
   (stock), **`ms-ordenes`** y **`ms-despachos`** trabajan en paralelo y de forma independiente.
4. Cada consumidor usa `acknowledge-mode: manual`: si todo salió bien hace `ACK`; si falla reintenta con
   backoff exponible y, tras agotar los intentos, hace `NACK` sin requeue y el mensaje cae en **su DLQ**
   (`pedidos360.<servicio>.dlq`, declarada con `x-dead-letter-exchange: ''`).
5. Las operaciones son **idempotentes**: cada servicio lleva registro de los eventos ya procesados (o usa el
   `pedidoId` único), así que un mensaje repetido se ignora y nunca se descuenta el stock dos veces.
6. Si RabbitMQ o el SMTP no están disponibles, la compra **no falla**: el error queda registrado en los logs
   (modo degradado).

**Cómo probar los correos sin credenciales reales:**
- Sin `MAIL_USERNAME`/`MAIL_PASSWORD`: el envío falla y el mensaje queda reintentando → se ven los logs de `ms-email` y la DLQ en la consola de RabbitMQ (`http://localhost:15672`). La compra sigue funcionando.
- Envío manual rápido (no depende de RabbitMQ): `POST /emails/enviar` con `{destino, asunto, cuerpo}` y revisar el log de `ms-email`.
- Para ver los correos de verdad: crear una *app password* de Gmail en `.env` (`MAIL_USERNAME`, `MAIL_PASSWORD`) y reiniciar solo `ms-email`.

### Monitoreo de eventos (Kafka)
1. El mismo hecho de negocio que genera correo se publica también en el tópico
   `pedidos360.monitoreo` (productor: `MonitorPublisher` en `ms-carrito`, con la clave = `pedidoId`
   para que todos los eventos de un pedido caigan en la misma partición y se lean en orden).
2. `ms-monitoreo` (grupo de consumidor `ms-monitoreo`) lo consume con `@KafkaListener` y guarda los
   últimos 500 eventos en memoria junto a contadores acumulados por tipo.
3. Cada evento queda con su coordenada en el log: `topico`, `particion` y `offset`.
4. Se consulta con `GET /monitoreo/eventos` y `GET /monitoreo/estadisticas`.
5. Kafka se consume sin borrar (a diferencia de la cola de correos): el log queda y puede leerlo
   otro consumidor más adelante. Si Kafka no está disponible, solo se registra el error: la compra sigue.

**Ver en vivo:** `docker compose up -d --build` → comprar en la página → `curl http://localhost:8086/monitoreo/eventos`
y `docker compose logs -f ms-monitoreo`. Guía completa, comandos y guion de exposición en [`KAFKA-MONITOREO.txt`](KAFKA-MONITOREO.txt).

Todos los endpoints (salvo que se indique lo contrario) requieren un JWT válido en el header `Authorization: Bearer <token>`, emitido por **Microsoft Entra ID** o por **Amazon Cognito**: cada microservicio valida ambos emisores (`MultiIssuerJwtDecoder` en `config/`).

---

## ⚙️ Lo que falta configurar en Azure/AWS antes de desplegar

El código está completo y probado a nivel de compilación/lógica. Lo único que falta es **conectar con los servicios reales de Azure y AWS**, que son credenciales/infraestructura que no se pueden generar de antemano:

### 1. Registrar la app en Azure Entra ID
Sigue los mismos pasos del `tutorial-estudiantes.html` que mandó el profe:
1. Azure Portal → Microsoft Entra ID → Registros de aplicaciones → Nuevo registro (ej. `pedidos360-api`).
2. Copiar el **Application (client) ID** y el **Directory (tenant) ID**.
3. En *Exponer una API* → Agregar un ámbito → nombre `write-read` (sin barras).
4. En *Aplicaciones cliente autorizadas* → agregar el mismo Client ID y marcar `write-read`.
5. En *Certificados y secretos* → generar un secreto si hace falta (para llamadas server-to-server; no es necesario para el flujo SPA con MSAL).
6. En *Autenticación* → Agregar plataforma → **Aplicación de página única (SPA)** → poner la URI de redirección (`http://localhost:4200/` para pruebas locales, y la URL pública una vez desplegado en AWS).

### 2. Configurar el frontend (`pedidos360-frontend`)
Editar `src/environments/environment.ts` (y `environment.prod.ts` para producción) y reemplazar:
```ts
azureClientId: 'TU_CLIENT_ID_AQUI',
azureTenantId: 'TU_TENANT_ID_AQUI',
azureApiScope: 'api://TU_CLIENT_ID_AQUI/write-read',
redirectUri: 'http://localhost:4200/',           // o la URL pública HTTPS en prod
apiProductosUrl: 'http://localhost:18081/productos', // local: 2da instancia de ms-productos (o 8081 con docker compose / API Gateway en EC2)
apiIdentidadUrl: 'http://localhost:8082/usuarios',
apiCarritoUrl: 'http://localhost:8083',
apiRabbitUrl: 'http://localhost:8087',           // admin de RabbitMQ (consola /rabbitmq)
cognitoRegion: 'us-east-1',
cognitoUserPoolId: '',                           // Output del stack aws/cognito-pool.yaml
cognitoClientId: ''                              // Output del stack aws/cognito-pool.yaml
```
Con `cognitoUserPoolId`/`cognitoClientId` vacíos, `/login` muestra un aviso y solo
funciona el ingreso con Microsoft; al llenarlos se habilita **Crear cuenta** y
**Entrar** con cuenta propia de Cognito (detalle en `GUIA-DESPLIEGUE.txt` 0.2).

### 3. Configurar cada microservicio backend
Cada `ms-x/src/main/resources/application.yml` usa variables de entorno con valores por defecto. Al desplegar en EC2, definir:
- `AZURE_ISSUER_URI` → `https://login.microsoftonline.com/<TENANT_ID>/v2.0`
- `COGNITO_ISSUER_URL` → `https://cognito-idp.<REGION>.amazonaws.com/<USER_POOL_ID>` (segundo emisor aceptado; si queda vacío solo se acepta Azure)
- Base de datos → **H2 embebida** (disco en `docker-compose.yml` con los volúmenes `carrito-data` y `pagos-data`); `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` existen por si se cambia a MySQL/PostgreSQL.
- `PRODUCTOS_SERVICE_URL`, `PAGOS_SERVICE_URL` → cómo se llaman entre sí los microservicios (en docker-compose ya apuntan a los servicios `ms-productos` y `ms-pagos`).
- `RABBITMQ_HOST`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` → broker de mensajes (en docker-compose ya apuntan al servicio `rabbitmq`).
- `KAFKA_BOOTSTRAP_SERVERS` → broker de eventos para monitoreo (en docker-compose ya apunta al servicio `kafka`; por defecto `localhost:9092`).
- `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` → (**solo ms-email**) credenciales SMTP, p. ej. una app password de Gmail. **Nunca** commitearlas: usar un archivo `.env` (docker-compose las lee de ahí) o secretos del servidor.
- **Una EC2 por microservicio (topología multi-instancia)**: `docker-compose.yml` parametriza con `${VAR:-valor por defecto}` todo lo que cambia entre máquinas — `RABBITMQ_HOST/USERNAME/PASSWORD`, `RABBITMQ_DEFAULT_USER/PASS`, `RABBITMQ_MGMT_URL`, `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_ADVERTISED_HOST`, `PRODUCTOS_SERVICE_URL`, `PAGOS_SERVICE_URL` y las 9 `MS_*_URL` del frontend. **En local sin `.env` no cambia nada** (siguen los nombres de la red de Docker); en AWS cada instancia solo descomenta en su `.env` las variables de su servicio (listado explicado en `.env.example`, bloque *DESPLEGUE MULTI-EC2*). El `nginx.conf` del frontend pasó a ser **plantilla** (`/etc/nginx/templates/default.conf.template`, la procesa `envsubst` al arrancar): sus `proxy_pass` son `${MS_*_URL}`, así que para cambiar una IP basta con editar el `.env` y `docker compose up -d --no-deps frontend` (sin reconstruir). Paso a paso completo (instancias, Security Group, comandos, orden de arranque, costo) en `GUIA-DESPLIEGUE.txt` **4.3.2**, con el checklist de "dónde van las IPs" en **4.3.3**.

### 4. AWS
- Desplegar cada microservicio (`ms-productos`, `ms-identidad`, `ms-carrito`, `ms-pagos`, `ms-email`, `ms-monitoreo`, `ms-rabbit-admin`, `ms-ordenes`, `ms-despachos`) en instancias EC2 (cada uno trae su `Dockerfile`, o se puede correr el `.jar` directo con `mvn clean package` y `java -jar`).
- Crear el **User Pool de Amazon Cognito** con la plantilla `aws/cognito-pool.yaml` (CloudFormation) y copiar los `Outputs` a `environment.prod.ts` y al `.env` (`COGNITO_ISSUER_URL`).
- Desplegar un broker RabbitMQ (p. ej. un contenedor `rabbitmq:3-management` o un servicio gestionado) y apuntar `RABBITMQ_HOST` hacia él; y un broker Kafka (p. ej. `apache/kafka` o un servicio gestionado) y apuntar `KAFKA_BOOTSTRAP_SERVERS` hacia él.
- **API Gateway: dos puertas que YA existen, sin crear ninguna nueva** (seguridad en la puerta de enlace):
  - **API de lógica** (`st3wqbtin7`, con **JWT Authorizer** de Microsoft Entra ID, audience = `azureApiScope`): por aquí entra **todo lo de Microsoft**. Ya tiene `/carrito`, `/pedidos`, `/productos` y `/usuarios` (hay que **revisar** que su target diga la IP actual de cada instancia); hay que **agregar** `/ordenes/{proxy+}` (→ ms-ordenes:8088), `/despachos/{proxy+}` (→ ms-despachos:8089), `/emails/{proxy+}` (→ ms-email:8084), `/monitoreo/{proxy+}` (→ ms-monitoreo:8086) y `/rabbit/{proxy+}` (→ **front:80**, ver más abajo), copiando integración y autorización de `/carrito`.
  - **Gateway del frontend** (`n27vhcx1n0`, el que sirve el Angular en HTTPS): **no tiene authorizer**, así que por ahí entran las sesiones de **Amazon Cognito**; el token lo validan los microservicios (`MultiIssuerJwtDecoder` acepta Azure y Cognito). Su URL está en `environment.prod.ts → apiGatewayCognitoBase` y el `TokenInterceptor` cambia de puerta solo cuando la sesión es de Cognito. Su integration target es `http://<IP-front>:80` y su ruta debe ser `ANY /desarrollo/{proxy+}` (con `+`).

  **Cómo se enruta (lo que se usa es la integración directa por puerto, OPCIÓN 2 de la guía):** cada ruta del API Gateway apunta **directo a la IP y puerto de su microservicio** con el target **sin prefijo ni barra final** (`http://<IP>:8081`, `8082`, `8083`, `8084`, `8086`, `8087`, `8088`, `8089`) — la única excepción es **`/rabbit` → `http://<IP-front>:80`**, porque `ms-rabbit-admin` expone `/queues`, `/exchanges` y `/bindings` en la raíz (no existe `/rabbit/queues`) y quien le quita ese prefijo es el `nginx.conf` del frontend. Ese mismo nginx también atiende cada prefijo **con y sin `/desarrollo/` delante**, así que el gateway del frontend reenvíe el path completo o no da igual. Con esta opción hay que abrir `80`, `8081-8084` y `8086-8089` en el Security Group (nunca `8085`, `5672`, `15672` ni `9092`). La alternativa es enrutar todo a `http://<IP-front>:80` (OPCIÓN 1) y abrir solo el 80. `ms-pagos` **no necesita ruta pública**: solo lo llama `ms-carrito` (guía completa en `GUIA-DESPLIEGUE.txt` 4.1 y 4.2).
- Desplegar el frontend Angular compilado (`npm run build`, carpeta `dist/`) en S3+CloudFront, Amplify, o servirlo con el `Dockerfile`/`nginx.conf` incluido (el build de producción trae `<base href="/desarrollo/">` para publicarlo detrás del API Gateway; para servirlo desde la raíz usar `ng build --configuration=production-root`).

### 5. Ejecución local para probar antes de desplegar
```bash
# Broker de mensajes (una sola vez)
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=pedidos360 -e RABBITMQ_DEFAULT_PASS=pedidos360 \
  rabbitmq:3.13-management-alpine

# Broker de monitoreo (una sola vez; lo más simple es levantar solo ese servicio)
docker compose up -d kafka

# Cada microservicio (default AUTH_BYPASS=false => login real de Microsoft Entra ID)
cd ms-productos && mvn spring-boot:run   # puerto 8081
cd ms-identidad && mvn spring-boot:run   # puerto 8082
cd ms-carrito   && mvn spring-boot:run   # puerto 8083
cd ms-email     && mvn spring-boot:run   # puerto 8084 (MAIL_* opcionales)
cd ms-pagos     && mvn spring-boot:run   # puerto 8085 (lo llama ms-carrito al pagar)
cd ms-monitoreo && mvn spring-boot:run   # puerto 8086 (consume Kafka)
cd ms-ordenes   && mvn spring-boot:run   # puerto 8088 (consume RabbitMQ)
cd ms-despachos && mvn spring-boot:run   # puerto 8089 (consume RabbitMQ)

# Broker de monitoreo: Kafka corriendo por Docker (solo hace falta para ms-monitoreo
# y para que ms-carrito publique eventos; con docker compose up se levanta solo)

# Segunda instancia de ms-productos en 18081: es la URL que usa el
# frontend en local (pedidos360-frontend/src/environments/environment.ts)
cd ms-productos && mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=18081

# Frontend
cd pedidos360-frontend && npm install && npm start   # puerto 4200

# O todo junto con Docker
docker compose up --build
```
> En Windows (PowerShell) reemplaza `VAR=valor cmd` por `$env:VAR='valor'; cmd`.
>
> **Modo de autenticación.** El proyecto ya viene con `authBypass: false` (frontend) y
> `AUTH_BYPASS=false` (backends), es decir, **login real con Microsoft Entra ID** en local
> y en AWS: hay que iniciar sesión en `http://localhost:4200` antes de abrir el catálogo.
> Para probar **sin Azure**, levanten los backends con `AUTH_BYPASS=true` y pongan
> `authBypass: true` en `pedidos360-frontend/src/environments/environment.ts` (solo ese
> archivo local; **nunca** en `environment.prod.ts`).
>
> **Catálogo:** si la base arranca vacía se siembra sola con 8 productos de ejemplo
> (`CATALOGO_SEED=false` para apuntar a una BD con productos reales).
> Para desplegar en AWS con otro tenant/registro de Azure, ver `GUIA-DESPLIEGUE.txt`
> (sección 0.1 = archivo por archivo dónde cambiar el client ID y el tenant ID).

## Compra de prueba (demostración del flujo completo)

1. `docker compose up --build` (o los 6 procesos de arriba) y entrar a `http://localhost:4200`.
   Con `authBypass: false` hay que iniciar sesión antes de abrir el catálogo: en `/login`
   hay dos accesos, **Microsoft Entra ID** (cuenta del tenant) y **Amazon Cognito**
   (cuenta propia creada con *Crear cuenta*); si no, el guard retiene las llamadas a la API.
2. **Catálogo** → buscar/filtrar por categoría → *Agregar* 2 productos (aparece un toast y el contador de la navbar).
3. **Carrito** → subir/bajar cantidades con el stepper → verificar subtotal y total → *Continuar al checkout*.
4. **Checkout** → elegir *Tarjeta de crédito / débito / transferencia* → completar los datos de prueba
   (`4111 1111 1111 1111`, cualquier titular, `12/30`, `123`; **no se envían al backend**) → *Confirmar y pagar*.
5. Pantalla de resultado: **Pago aprobado** → el pedido pasa a `CONFIRMADO`/`APROBADO`.
6. **Mis pedidos** → expandir el pedido: fecha, método de pago, estado del pedido y del pago, detalle de productos.
7. Para ver el camino de error: activar *Modo demo: simular pago rechazado* en el checkout → el pedido queda
   `CANCELADO`/`RECHAZADO`, se muestra el aviso y **el carrito se restaura** con los mismos productos.
8. Variante por API (útil para la defensa): avanzar el estado logístico del pedido aprobado
   ```bash
   curl -X PUT http://localhost:8083/pedidos/1/estado -H "Content-Type: application/json" -d '{"estado":"EN_PREPARACION"}'
   ```
   (`CONFIRMADO → EN_PREPARACION → ENVIADO → ENTREGADO`; una transición inválida devuelve `400`).
9. **Monitoreo (Kafka):** con la compra hecha, ver el evento recién publicado:
   ```bash
   curl http://localhost:8086/monitoreo/eventos
   curl http://localhost:8086/monitoreo/estadisticas
   ```
   (detalle y comandos extra en `KAFKA-MONITOREO.txt`).
10. **Eventos de RabbitMQ → stock, órdenes y despachos:** con la misma compra los consumidores
    ya trabajaron en paralelo:
    ```bash
    docker compose logs ms-productos | grep -i stock   # descuento aplicado (o reposición si se rechazó)
    curl http://localhost:8088/ordenes/pedido/1        # orden REGISTRADA / PAGADA
    curl http://localhost:8089/despachos/pedido/1      # guía P360-00001 en PENDIENTE / EN_RUTA
    ```
    y las 4 colas + 4 DLQ del exchange `pedidos360.eventos.topic` se ven en la consola de RabbitMQ
    (`http://localhost:15672`) o en la página `/rabbitmq` del frontend.
11. **Notificaciones (Postman):** `POST http://localhost:8084/emails/enviar` con
    `{destino, asunto, cuerpo}` y `Authorization: Bearer <token>` (o `AUTH_BYPASS=true` en local);
    en AWS la misma llamada va a `<API>/desarrollo/emails/enviar`.

## Pruebas ejecutadas

| Suite | Comando | Resultado |
|---|---|---|
| `ms-productos` | `mvn test` | **35 tests, 0 fallos** |
| `ms-identidad` | `mvn test` | **8 tests, 0 fallos** |
| `ms-carrito` | `mvn test` | **38 tests, 0 fallos** |
| `ms-pagos` | `mvn test` | **14 tests, 0 fallos** |
| `ms-email` | `mvn test` | **22 tests, 0 fallos** |
| `ms-monitoreo` | `mvn test` | **12 tests, 0 fallos** |
| `ms-rabbit-admin` | `mvn test` | **44 tests, 0 fallos** |
| `ms-ordenes` | `mvn test` | **38 tests, 0 fallos** |
| `ms-despachos` | `mvn test` | **35 tests, 0 fallos** |
| Frontend | `npx ng build` | **Build exitoso** (0 errores) |

**Total: 246 pruebas, 0 fallos** (35 + 8 + 38 + 14 + 22 + 12 + 44 + 38 + 35), más el build de Angular.

Verificación manual end-to-end sobre los servicios levantados (2026-10-02): catálogo con imágenes y filtros
sin tildes, precios tomados de `ms-productos` (se ignora el precio del navegador), acumulación de ítems,
checkout `PENDIENTE_PAGO`, carrito vacío tras el checkout, **pago delegado en `ms-pagos`** (aprobado,
rechazado con carrito restaurado y registro consultable en `GET /pagos/pedido/{id}`), pago doble → `400`,
transiciones de estado y CORS con el origen desplegado.

**Verificación con `docker compose up -d --build`** (Docker Desktop + WSL2, octubre): eran 9 contenedores
(`rabbitmq`, `kafka`, los 6 microservicios y el frontend en nginx); con `ms-ordenes` y `ms-despachos` son **11**; el catálogo siembra 8 productos; la
compra corre completa sobre el stack aprobado (`CONFIRMADO` / `APROBADO` con registro en
`GET /pagos/pedido/{id}`), el pago doble devuelve `400` y el pago rechazado deja `CANCELADO` / `RECHAZADO`
con el carrito restaurado. RabbitMQ funciona end-to-end: `ms-carrito` publica y `ms-email` consume
(4 publicados / 4 entregados) y, al no haber credenciales SMTP, agota los 3 reintentos y los mensajes caen
en `pedidos360.emails.dlq`. También se verificó el modo degradado: con el broker caído los eventos se
registran como error en el log y **la compra se completa igual**.

**Verificado con login real + Kafka + correo** (2026-10-07):
- **Kafka + Monitoreo end-to-end**: `apache/kafka:3.9.0` en modo KRaft arriba y `ms-monitoreo` consumiendo
  del tópico; `GET /monitoreo/eventos` y `GET /monitoreo/estadisticas` muestran `PEDIDO_REGISTRADO`,
  `PAGO_APROBADO`, `PAGO_RECHAZADO` y `CAMBIO_ESTADO` producidos por una compra real sobre el stack.
- **Correos reales por SMTP**: con la app password de Gmail en `.env`, `ms-email` envió 4 mensajes a
  `diegoxmegalala@gmail.com` (registro, pago aprobado, registro y pago rechazado) y las colas quedaron en 0.
- **Login de Microsoft optativo en local**: el frontend en Docker publica `http://localhost:4200`
  (además del `80`) porque esa es la URI registrada en Azure; con `NG_BUILD_CONFIG=docker` en el `.env`
  compila `environment.docker.ts` (login real + URLs localhost) y los backend exigen JWT (`401` sin token).
  Sin `NG_BUILD_CONFIG` el build es de **producción** (`environment.prod.ts` = login + API Gateway).

**Monitoreo con Kafka**: las 6 pruebas de `ms-monitoreo` y las 21 de `ms-carrito` están en verde y el
`docker-compose.yml` (servicios `kafka` + `ms-monitoreo`) valida con `docker compose config`. El flujo
end-to-end con el broker levantado quedó **verificado** el 2026-10-07: comprar en la página y ver el evento
en `GET /monitoreo/eventos`; la receta exacta está en `KAFKA-MONITOREO.txt` punto 7.

## Limitaciones conocidas

- El pago es **simulado**: lo decide y registra `ms-pagos` (sin integración con pasarelas bancarias) y no se
  piden ni se almacenan datos reales de tarjeta (los campos del formulario solo viven en el navegador y no se
  envían al backend).
- Las notificaciones visuales (toasts + campana) son **temporales**: viven mientras la pestaña está abierta.
  No existe un sistema persistente de notificaciones internas.
- La base de datos por defecto es H2. En `docker-compose.yml` se usan H2 en disco con los volúmenes
  `carrito-data` y `pagos-data` para que pedidos y pagos sobrevivan a reinicios; en producción se recomienda
  MySQL/PostgreSQL.
- El **User Pool de Amazon Cognito** se crea al desplegar (plantilla `aws/cognito-pool.yaml`);
  hasta que se publiquen los `Outputs` en `environment.prod.ts` y en el `.env`, el formulario de
  Cognito en `/login` muestra un aviso y solo funciona el ingreso con Microsoft.
- **Las rutas nuevas de la API de lógica se agregan en la consola**: `/ordenes`, `/despachos`,
  `/emails` (y `/rabbit`, `/monitoreo` si faltan) se crean en el **mismo** API Gateway
  `st3wqbtin7`, copiando integración y autorización de `/carrito` — no hace falta ninguna API
  nueva. Lo mismo con la ruta `ANY /desarrollo/{proxy+}` (¡con `+`!) del gateway del frontend,
  por donde pasan las sesiones de Cognito. Ver `GUIA-DESPLIEGUE.txt` 4.2.
- `ms-monitoreo` guarda los últimos 500 eventos **en memoria**: al reiniciarse se pierden los eventos
  (los contadores vuelven a 0), pero el log completo sigue en Kafka. En producción se usaría una
  persistencia (p. ej. OpenSearch/Elasticsearch o una base de datos) si se necesita histórico.

## Integrantes

* Thomas Alvarez
* Diego Diaz
