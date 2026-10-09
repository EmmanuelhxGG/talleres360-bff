# Talleres360 BFF

Entrada autenticada desarrollada con Java 17, Spring Boot 3.5.6 y Spring Security OAuth2 Resource Server. Puerto **8080**. Rama **`bff-emmanuel`**. Repositorio: [EmmanuelhxGG/talleres360-bff](https://github.com/EmmanuelhxGG/talleres360-bff). Documentación del código al 6 de octubre de 2026.

El BFF valida tokens de Microsoft Entra ID, autoriza por scope/rol, deriva la identidad y reenvía peticiones. No tiene base propia ni calcula precios, stock o ventas.

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
`RABBIT_ADMIN_URL` (8086) y `KAFKA_ADMIN_URL` (8087). El BFF ofrece
`/api/messaging/rabbit/**` y `/api/messaging/kafka/**` únicamente para Admin,
con JWT y scope válidos; deriva la credencial interna en el servidor.
Nunca acepta una URL destino enviada por el navegador. RabbitMQ/Kafka no reciben
conexiones directas del frontend. El comprobante PDF se consulta en
`/api/notifications/{id}/receipt`, con autorización por rol y propietario.

## Archivos principales

Las rutas Java parten de `src/main/java/com/talleres360/bff/`.

| Ruta | Responsabilidad |
| --- | --- |
| `config/SecurityConfig.java` | JWT, scope y permisos por método/ruta/rol. |
| `config/CorsConfig.java` | CORS. |
| `config/OrdersClientConfig.java` | Cliente HTTP con conexión de 3 segundos y lectura de 10 segundos. |
| `controller/OrdersProxyController.java` | Órdenes; actor y rol obtenidos del JWT. |
| `controller/AppointmentsProxyController.java` | Solicitudes; correo del cliente obtenido del JWT. |
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
| `GET/POST /api/appointments`, `GET /api/appointments/**` | Cliente |
| `GET /api/orders`, `GET /api/orders/**` | Operador, Admin |
| `POST /api/orders`, `PUT /api/orders/**` | Operador, Admin |
| `DELETE /api/orders/**` | Admin |
| `GET /api/products`, `GET /api/products/**` | Operador, Admin |
| `POST /api/products`, `PUT /api/products/**` | Admin |
| `GET /api/reports/**` | Admin |
| `GET /api/notifications` | Operador (tickets), Cliente (solo correos propios) |
| `GET /api/audit`, `GET /api/audit/events` | Admin, solo lectura |

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
INTERNAL_API_KEY=<CLAVE_INTERNA_COMPARTIDA>
CORS_ALLOWED_ORIGINS=http://localhost:5173
SECURITY_LOG_LEVEL=INFO
```

Las URL son bases, sin `/api`, `/dev` ni rutas de recursos. La clave coincide con los cinco micros. Varios orígenes CORS se separan con coma; incluir esquema y puerto exactos.

### Notificaciones y auditoría

La matriz sigue el caso: Notificaciones para Operador/Cliente; Auditoría para Admin según la sección específica de la pantalla `/audit`. La tabla general menciona Auditor, pero no se incorpora un cuarto rol en Azure sin una definición consistente.

El BFF construye `X-Actor-Role` y, para Cliente, `X-Customer-Email` a partir del token validado. Ignora las cabeceras de identidad y clave interna del navegador. Notificaciones aplica el filtro de propietario en la base, incluso cuando se solicita otro `orderId`. Admin no tiene acceso público a notificaciones y las rutas internas y de reintento no se publican.

`GET /api/reports/audit` se conserva para el dashboard, pero se dirige al servicio de Auditoría; `/api/reports/sales` continúa en Reportería. Las consultas nuevas no se cachean. Ante caídas o credenciales internas rechazadas devuelven un mensaje personalizado sin revelar URLs ni respuestas técnicas.

API Gateway mantiene como integración el BFF. Si utiliza `ANY /{proxy+}` o una ruta equivalente, las nuevas rutas pasan por esa integración. Si utiliza rutas explícitas, añadir `GET /api/notifications`, `GET /api/audit` y `GET /api/audit/events`, con el mismo JWT authorizer. No publicar `/internal/**`. Este cambio de código no modifica la configuración de AWS.

La revisión local del 8 de octubre verificó el flujo de los cinco servicios con bases H2 descartables y JWT simulados: creación, aceptación, informe, entrega, stock, ventas, notificaciones y auditoría. No certifica un login real en Azure ni el despliegue AWS. Los archivos de pruebas no forman parte de esta versión.

Compose lee `.env` y pasa sus valores al contenedor. Java/Maven directo no carga ese archivo automáticamente: exporta las variables en la terminal. Dentro de Docker, `localhost` identifica el propio contenedor. Si los micros se ejecutan en el host de Docker Desktop, puede usarse `http://host.docker.internal:8081`, `:8082` y `:8083`.

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

Gateway debe reenviar `/dev/api/orders` como `/api/orders`. Configura rutas base y subrutas de orders, appointments, products y reports. Una ruta `/api/products` por sí sola no cubre `/api/products/{id}`.

El autorizador JWT utiliza issuer v2, audiencia de la API y scope delegado. CORS permite el origen real del frontend, métodos `GET, POST, PUT, DELETE, OPTIONS` y encabezados `authorization, content-type`. El preflight OPTIONS no exige token.

Los Security Groups permiten al BFF llegar a 8081–8083; Catálogo y Reportería también reciben Órdenes. PostgreSQL no se publica. El puerto 8080 es destino HTTP del Gateway en esta distribución: JWT no cifra por sí mismo ese tramo HTTP. SSH se limita a las IP autorizadas y los micros no se abren a Internet.

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
