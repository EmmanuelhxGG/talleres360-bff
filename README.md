# Talleres360 BFF

## Actualización: identidad de las bandejas (28-10-2026)

Cliente, Operador y Admin con scope pueden consultar `/api/notifications` y
`/api/notifications/solicitudes`. Por defecto el ámbito es personal: se obtiene el
correo del JWT para los tres roles, nunca de un encabezado del navegador.
`ambito=operativo` queda reservado a Operador. El rol Admin no concede lectura
global de notificaciones. Las demás autorizaciones no se amplían.

El cambio de estado de productos usa `PUT /api/products/{id}/estado`, a través del
proxy existente y solo Admin. Los códigos seguros de disponibilidad de Órdenes
se conservan en las respuestas de compras; no se reemplazan por éxito.


Entrada autenticada desarrollada con Java 17, Spring Boot 3.5.6 y Spring Security OAuth2 Resource Server. Puerto **8080**. Rama **`bff-emmanuel`**. Repositorio: [EmmanuelhxGG/talleres360-bff](https://github.com/EmmanuelhxGG/talleres360-bff). Documentación actualizada el **22 de octubre de 2026**. Las plantillas no contienen credenciales ni direcciones de una cuenta AWS concreta.

El BFF valida tokens de Microsoft Entra ID, autoriza por scope/rol, deriva la identidad y reenvía peticiones. No tiene base propia ni calcula precios, stock o ventas.

Las notificaciones incluyen `GET /api/notifications/solicitudes` para agrupar los
avisos por solicitud. Esta ruta exige el mismo scope y permisos de Cliente u
Operador que el listado individual. El correo del cliente se obtiene del JWT;
el navegador no puede indicar otra identidad para consultar sus avisos.

## Registro, catálogo y compras

`GET /api/catalogo` permite a los roles autorizados consultar productos sin cantidades
de inventario. `GET /api/products` y su CRUD son exclusivos de Admin. El BFF
construye `X-Actor-Role` desde las autoridades del JWT, nunca desde un encabezado
proporcionado por el navegador. Agendamiento y su historial envían también la
credencial interna a Órdenes. Las respuestas de inventario no se almacenan en caché.

- `POST /api/registros` requiere JWT y scope `access_as_user`, sin exigir aún rol Cliente.
  Solo admite `@duocuc.cl` y obtiene su correo del token. Si se declara un correo en
  el cuerpo debe coincidir con esa identidad; no se permite solicitar por otra persona.
  Valida nombre, dominio, tamaño del cuerpo y límite de intentos por sujeto autenticado.
  La ruta continúa privada en BFF y API Gateway: no resuelve el alta de una identidad
  que todavía no puede autenticarse en el tenant.
  `GET /api/registros` requiere Admin. El alta y el rol se aprueban manualmente en Entra.
- `GET/POST /api/compras` permite Cliente, Operador y Admin con correo `@duocuc.cl` o
  `@tu-tenant.onmicrosoft.com` en el JWT. La misma política se aplica al
  agendamiento y seguimiento del cliente; el alta nueva se restringe a Duoc.
  El token debe pertenecer al issuer y
  audiencia configurados; no se admiten otros tenants por el dominio del correo.
  El BFF deriva destinatario y nombre del token y no confía en identidad enviada por
  React. El carrito contiene solo UUID, productos y cantidades, sin RUT/teléfono/taller.
- `POST /api/orders` y `DELETE /api/orders/**` se deniegan a todos los roles.
  Las solicitudes nacen del carrito. Solo Operador gestiona estados e informes;
  Admin consulta las órdenes sin modificarlas.
- Órdenes asigna una única sede desde el servidor. Ya no se envían regiones ni sucursales.
- Cliente, Operador y Admin consultan el catálogo de venta y categorías. Solo Admin puede
  crear, editar o dar de baja productos y categorías mediante `/api/products`
  y `/api/categorias`.
- Los comprobantes nuevos son texto en el correo. `/api/notifications/{id}/receipt`
  conserva la descarga autorizada de documentos históricos, no genera PDF nuevos.

Todas las rutas de datos de API Gateway, incluido **POST /api/registros**, mantienen
el authorizer JWT. No crear excepciones públicas para registro. Un usuario todavía
no habilitado en el tenant debe gestionar su acceso con la persona responsable de
Entra ID. La aplicación no invita usuarios ni crea aprobaciones nativas en Entra.

## Arquitectura

```text
Frontend local → API Gateway → BFF :8080
                                 ├── Órdenes :8081, EC2 propia
                                 ├── Catálogo :8082, EC2 propia
                                 ├── Reportería :8083, EC2 propia
                                 ├── Notificaciones :8084, EC2 propia
                                 └── Auditoría :8085, EC2 propia
```

Cada destino usa su IP privada en la misma VPC. Este repositorio se construye de forma independiente del antiguo backend agrupado.

La administración de mensajería usa destinos privados separados:
`RABBIT_ADMIN_URL` (8086). El BFF ofrece
`/api/messaging/rabbit/**` únicamente para Admin,
con JWT y scope válidos; deriva la credencial interna en el servidor.
Nunca acepta una URL destino enviada por el navegador. RabbitMQ no recibe
conexiones directas del frontend. Los correos nuevos llevan el comprobante en
texto, sin PDF ni adjuntos. `/api/notifications/{id}/receipt` se conserva solo para
comprobantes históricos, con autorización por rol y propietario.

Kafka y su ruta administrativa fueron retirados el 21 de octubre de 2026.
Reportería y Auditoría conservan las mismas rutas de consulta y reciben sus
eventos desde el outbox HTTP de Órdenes. El frontend no necesita otras URL.

## Archivos principales

Las rutas Java parten de `src/main/java/com/talleres360/bff/`.

| Ruta | Responsabilidad |
| --- | --- |
| `config/SecurityConfig.java` | JWT, scope y permisos por método/ruta/rol. |
| `config/CorsConfig.java` | CORS. |
| `config/OrdersClientConfig.java` | Cliente HTTP con conexión de 3 segundos y lectura de 10 segundos. |
| `controller/OrdersProxyController.java` | Órdenes; actor y rol obtenidos del JWT. |
| `controller/AppointmentsProxyController.java` | Solicitudes; correo del cliente obtenido del JWT. |
| `controller/ComprasProxyController.java` | Compras propias; correo y nombre del JWT, nombre codificado en UTF-8/Base64 para cabeceras HTTP. |
| `controller/RegistroProxyController.java` | Solicitudes locales de acceso institucional, sin crear identidades en Entra. |
| `controller/ProductsProxyController.java` | Catálogo con clave interna del servidor. |
| `controller/ReportsProxyController.java` | Lectura de ventas con clave interna. |
| `controller/SeguimientoProxyController.java`, `service/ProxySeguimiento.java` | Notificaciones y auditoría: rutas explícitas, rol/correo derivados del JWT y errores personalizados. |
| `src/main/resources/application.yml` | Puerto, issuer, audiencia, destinos y CORS desde variables. |
| `.env.example`, `compose.yml`, `Dockerfile` | Configuración y construcción independiente. |

Los proxies preservan estado y cuerpo del micro, incluidos errores de negocio. Las reglas de órdenes, stock y ventas permanecen en sus respectivos servicios.

## Entra ID y permisos

Se usa un registro SPA y otro API del mismo tenant. El BFF utiliza tenant e ID de la API, no el ID SPA ni un client secret.

| Elemento | Configuración del proyecto |
| --- | --- |
| Issuer | `https://login.microsoftonline.com/<ENTRA_TENANT_ID>/v2.0` |
| Audiencia | `<API_CLIENT_ID>` |
| Scope delegado | `access_as_user` |
| Valores de roles | `Cliente`, `Operador`, `Admin`, respetando mayúsculas. |
| Scope solicitado por SPA | `api://<API_CLIENT_ID>/access_as_user` |

El registro API se configura para tokens v2. Los roles se asignan a usuarios en su aplicación empresarial. Debe enviarse un **access token** para la API, no el ID token de la sesión.

Todos estos accesos requieren además token válido y scope:

| Método y ruta | Roles |
| --- | --- |
| `POST /api/registros` | JWT y scope válidos; identidad institucional, sin exigir todavía rol Cliente |
| `GET /api/registros` | Admin |
| `GET/POST /api/compras` | Cliente, Operador, Admin; solo sus compras propias |
| `GET/POST /api/appointments`, `GET /api/appointments/**` | Cliente |
| `GET /api/orders`, `GET /api/orders/**` | Operador, Admin |
| `POST /api/orders` | Denegado |
| `PUT /api/orders/**` | Operador |
| `DELETE /api/orders/**` | Denegado |
| `GET /api/products`, `GET /api/products/**` | Admin |
| `POST /api/products`, `PUT/DELETE /api/products/**` | Admin |
| `GET /api/categorias` | Cliente, Operador, Admin |
| `POST /api/categorias`, `PUT/DELETE /api/categorias/**` | Admin |
| `GET /api/reports/**` | Admin |
| `GET /api/notifications` | Operador (seguimiento de correos), Cliente (solo avisos propios) |
| `GET /api/audit`, `GET /api/audit/events`, `GET /api/audit/solicitudes` | Admin, solo lectura |
| `GET /api/notifications/{id}/receipt` | Admin, Operador o Cliente; el servicio comprueba acceso al documento histórico |
| `/api/messaging/rabbit/**` | Admin |

Las demás rutas se deniegan. `/api/orders/{id}/stock` ya está cubierto por el proxy de órdenes. Los endpoints `/internal/stock-reservations`, `/internal/stock-consumptions` y `/internal/events` son de comunicación entre servidores, no del navegador.

El correo se deriva de `preferred_username`, `email` o `upn`. Para solicitudes se envía `X-Customer-Email`; para órdenes, `X-Actor-Email` y `X-Actor-Role`. No se adopta la identidad enviada por el navegador en esas cabeceras. Catálogo y Reportería reciben `X-Internal-Key` desde la configuración del BFF.

## Crear y completar .env

Crea `.env` junto a `compose.yml`, usando `.env.example` como plantilla. Sustituye los marcadores:

```dotenv
ENTRA_TENANT_ID=<ID_DEL_TENANT>
API_CLIENT_ID=<ID_DEL_REGISTRO_API>
ORDERS_URL=http://<IP_PRIVADA_EC2_ORDENES>:8081
CATALOG_URL=http://<IP_PRIVADA_EC2_CATALOGO>:8082
REPORT_URL=http://<IP_PRIVADA_EC2_REPORTES>:8083
NOTIFICATIONS_URL=http://<IP_PRIVADA_EC2_NOTIFICACIONES>:8084
AUDIT_URL=http://<IP_PRIVADA_EC2_AUDITORIA>:8085
RABBIT_ADMIN_URL=http://<IP_PRIVADA_EC2_RABBIT>:8086
INTERNAL_API_KEY=<CLAVE_INTERNA_COMPARTIDA>
CORS_ALLOWED_ORIGINS=http://localhost:5173
SECURITY_LOG_LEVEL=INFO
```

Las URL son bases, sin `/api`, `/dev` ni rutas de recursos. La clave coincide con los cinco micros. Varios orígenes CORS se separan con coma; incluir esquema y puerto exactos.

### Notificaciones y auditoría

Notificaciones corresponde a Operador/Cliente y Auditoría a Admin. Los roles implementados son `Cliente`, `Operador` y `Admin`; no se requiere un cuarto rol para usar estas pantallas.

El BFF construye `X-Actor-Role` y, para Cliente, `X-Customer-Email` a partir del token validado. Ignora las cabeceras de identidad y clave interna del navegador. Notificaciones aplica el filtro de propietario en la base, incluso cuando se solicita otro `orderId`. Admin no tiene acceso público a notificaciones y las rutas internas y de reintento no se publican.

`GET /api/reports/audit` se conserva para el dashboard, pero se dirige al servicio de Auditoría; `/api/reports/sales` continúa en Reportería. Las consultas nuevas no se cachean. Ante caídas o credenciales internas rechazadas devuelven un mensaje personalizado sin revelar URLs ni respuestas técnicas.

`GET /api/audit/solicitudes` devuelve solicitudes agrupadas y paginadas, con
`orderId`, cantidad de `eventos` y `ultimaActividad`. Aplica los mismos filtros que
la consulta de eventos. Al desplegar una solicitud, el frontend consulta
`GET /api/audit?orderId=...` para cargar su historial. Ambas rutas exigen JWT,
scope y rol Admin; no se publican escrituras ni cambia el acceso de otros roles.

API Gateway mantiene como integración el BFF. Si utiliza `ANY /{proxy+}` o una ruta equivalente, las nuevas rutas pasan por esa integración. Si utiliza rutas explícitas, añadir `GET /api/notifications`, `GET /api/audit` y `GET /api/audit/events`, con el mismo JWT authorizer. No publicar `/internal/**`. Este cambio de código no modifica la configuración de AWS.

Las comprobaciones locales con JWT firmados de prueba validan el contrato y los permisos, pero no sustituyen una sesión institucional real. El despliegue permite comprobar por separado los destinos internos y el rechazo sin token; un HTTP 401 confirma protección, no una compra autenticada completa.

Compose lee `.env` y pasa sus valores al contenedor. Java/Maven directo no carga ese archivo automáticamente: exporta las variables en la terminal. Dentro de Docker, `localhost` identifica el propio contenedor. Las URL de los cinco micros y del administrador Rabbit deben ser alcanzables desde BFF; en EC2 se utilizan destinos privados de la misma VPC.

## Construir y ejecutar

La EC2 necesita Git, Docker Engine, Buildx y Compose. El Dockerfile incluye la compilación; no exige Java/Maven en el host.

Desde la raíz, después de completar `.env`:

```bash
docker buildx version
docker compose version
docker compose config --quiet
docker compose up -d --build
docker compose ps
docker compose logs --tail=100 bff
```

El contenedor `ms-talleres360-bff` publica `8080:8080` y usa `restart: unless-stopped`. Requiere conectividad con Entra ID y con las IP privadas de los micros.

Con JDK 17 y variables exportadas, fuera de Docker:

```bash
./mvnw spring-boot:run
```

En PowerShell: `./mvnw.cmd spring-boot:run`.

## API Gateway y red

El destino de Gateway es el BFF. El frontend utiliza:

```text
https://<API_ID>.execute-api.<REGION>.amazonaws.com/dev
```

Gateway debe reenviar `/dev/api/orders` como `/api/orders`, sin conservar el prefijo del stage. La distribución usa `ANY /{proxy+}` hacia BFF, protegido con JWT, y preflight CORS sin token. Esta ruta cubre compras, registros, órdenes, agendamientos, productos, categorías, reportes, seguimiento y administración Rabbit. Si se usan rutas explícitas en otra instalación, también deben cubrir esas bases y sus subrutas. El BFF sigue denegando las rutas no autorizadas: el comodín no publica `/internal/**`.

El autorizador JWT utiliza issuer v2, audiencia de la API y scope delegado. CORS permite el origen real del frontend, métodos `GET, POST, PUT, DELETE, OPTIONS` y encabezados `authorization, content-type`. El preflight OPTIONS no exige token.

Los Security Groups permiten al BFF llegar a los cinco micros (8081–8085) y al administrador Rabbit (8086). Órdenes llega a Catálogo, Reportería y Auditoría; Notificaciones recibe sus comandos por RabbitMQ. Los cinco microservicios comparten la base PostgreSQL `talleres360` en la EC2 de Órdenes. El puerto 5432 admite únicamente sus orígenes privados autorizados, con un usuario por servicio y permisos sobre sus propias tablas; el BFF no accede a SQL. El puerto 8080 es destino HTTP del Gateway en esta distribución: JWT no cifra por sí mismo ese tramo HTTP. SSH se limita a orígenes autorizados y los puertos de los micros no se abren a Internet.

| Cambio de dirección | Qué modificar |
| --- | --- |
| IP privada de un micro | Su URL en este `.env` y recrear BFF. |
| Dirección pública del BFF | Integración de Gateway. |
| Gateway/stage | `VITE_API_BASE_URL` del frontend. |
| Tenant/registro API | Variables BFF, frontend y autorizador Gateway. |

Mover AWS no obliga a cambiar Entra si se conservan tenant y registros.

## Comprobación y diagnóstico

Sin token debe responder 401:

```bash
curl -i http://localhost:8080/api/orders
```

Preflight desde PowerShell, reemplazando la URL:

```powershell
curl.exe -i -X OPTIONS "https://<API_ID>.execute-api.<REGION>.amazonaws.com/dev/api/orders" -H "Origin: http://localhost:5173" -H "Access-Control-Request-Method: GET" -H "Access-Control-Request-Headers: authorization,content-type"
```

Espera 200/204 con el origen permitido. Esto comprueba CORS, no el circuito autenticado completo.

| Resultado | Revisar |
| --- | --- |
| 401 | Token, issuer, audiencia y vencimiento. |
| 403 | Scope, roles y permisos en Gateway/BFF. |
| Invalid CORS request | Origen, preflight y ruta sin prefijo de stage. |
| Error de conexión | URL privada, puerto, grupo de seguridad y contenedor destino. |
| 409 de orden | Regla de negocio del micro; conservar el error. |

Empaquetado:

```bash
./mvnw -DskipTests package
```

En PowerShell: `./mvnw.cmd '-DskipTests' package`. El empaquetado no ejecuta pruebas funcionales.

## Actualizar y proteger configuración

Comprueba rama y cambios locales. Con el código publicado y sin conflictos:

```bash
git pull --ff-only origin bff-emmanuel
docker compose up -d --build
```

Reiniciar no descarga Git ni reconstruye imágenes. No publicar `.env`, PEM, tokens o clave interna. Mantener `SECURITY_LOG_LEVEL=INFO` para operación normal. Esta documentación no modifica ni certifica una configuración AWS concreta.

Si el despliegue ya utiliza archivos Compose adicionales, conserva los mismos
`-f` al reconstruir y recrear. Consulta `docker compose ls` antes de modificarlo.
No reemplaces un `.env` existente con `.env.example`. Los ejemplos usan marcadores
para tenant, aplicaciones, hosts y claves; nunca publiques los valores de tu cuenta.
