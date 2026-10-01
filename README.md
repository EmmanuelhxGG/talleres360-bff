# Talleres360 — BFF

Para montar backend, BFF, Security Groups y API Gateway desde cero en otra cuenta AWS, consulta la [guía de despliegue del repositorio backend](../talleres360-backend/DESPLIEGUE_EC2_API_GATEWAY.md).

Spring Boot/Java 17. Es la entrada autenticada a los microservicios: verifica el access token v2 de Microsoft Entra ID (issuer, audiencia, scope y rol) y reenvía órdenes/agendamientos a orders, inventario a catalog y dashboard/auditoría a report. No crea órdenes ni calcula ventas: cada micro conserva esa responsabilidad.

```text
Frontend → API Gateway → BFF :8080 → orders :8081
                                  → catalog :8082
                                  → report :8083
```

## Entra ID y permisos

La API registrada en Entra debe exponer `api://<API_CLIENT_ID>/access_as_user`, emitir tokens v2 (`requestedAccessTokenVersion: 2`) y definir roles con valores exactos `Cliente`, `Operador` y `Admin`. El token debe llevar `aud = API_CLIENT_ID`, issuer `https://login.microsoftonline.com/<ENTRA_TENANT_ID>/v2.0`, scope `access_as_user` y uno de esos roles. El README del frontend explica los registros SPA/API y asignación de usuarios. El BFF extrae el correo/rol del token para la auditoría; no confía en un encabezado de actor enviado por el navegador.

| Ruta | Acceso |
| --- | --- |
| `GET/POST /api/appointments`, `GET /api/appointments/availability` | Cliente |
| `GET/POST/PUT /api/orders/**` | Operador y Admin |
| `DELETE /api/orders/**` | Solo Admin |
| `GET /api/products/**` | Operador y Admin |
| `POST /api/products`, `PUT /api/products/{id}` | Solo Admin |
| `GET /api/reports/**` | Solo Admin |

Todos requieren además el scope delegado. El Operador puede crear una orden, aceptar o cancelar solicitudes, registrar diagnóstico, trabajo, fecha estimada, mano de obra y repuestos, y entregar sin aprobación administrativa. Admin puede hacer esas operaciones, gestionar productos/precios/stock, consultar ventas y auditoría e intervenir en estados con un motivo de al menos 10 caracteres. La autorización efectiva está en `src/main/java/com/talleres360/bff/config/SecurityConfig.java`.

## Archivos y configuración

`compose.yml` construye el BFF. `src/main/resources/application.yml` lee las variables. `ProductsProxyController.java` y `ReportsProxyController.java` reenvían las rutas nuevas e incorporan la clave interna del servidor; `OrdersProxyController.java` deriva actor y rol del JWT antes de llamar a orders. `SecurityConfig.java` contiene los permisos efectivos; las vistas del frontend solo ocultan controles, no autorizan. Nunca aceptes `X-Actor-*` o `X-Internal-Key` aportados por el navegador como identidad o permiso.

Para el stack completo en una PC usa `talleres360-backend/infra/apps/compose.yml` con ambos repositorios como carpetas hermanas; el BFF local queda en `http://localhost:8080`. Configura `INTERNAL_API_KEY` en `talleres360-backend/infra/apps/.env`; el Compose la pasa a todos los servicios. Si levantas el BFF solo en Docker Desktop y los micros corren en el host, usa `host.docker.internal` con puertos 8081–8083 en las tres URL. `localhost` dentro del contenedor es el propio contenedor.

## Despliegue en tu EC2 BFF

1. Arranca primero los tres micros en la EC2 backend (`infra/ms/compose.yml`). Copia su **IP privada**.
2. En la EC2 BFF, clona la rama `bff-emmanuel` de tu fork o verifica `git branch --show-current` y `git status` antes de `git pull --ff-only`. Usa tu propio PEM: `ssh -i <ruta-llave.pem> ec2-user@<DNS-de-tu-EC2-BFF>`. No subas la llave a Git.
3. En la raíz de `talleres360-bff/`, copia `.env.example` a `.env` y completa:

   ```dotenv
   ENTRA_TENANT_ID=<Id-del-tenant>
   API_CLIENT_ID=<Id-de-la-API-registrada>
   ORDERS_URL=http://<IP-privada-backend>:8081
   CATALOG_URL=http://<IP-privada-backend>:8082
   REPORT_URL=http://<IP-privada-backend>:8083
   INTERNAL_API_KEY=<misma-clave-aleatoria-larga-de-infra-ms>
   CORS_ALLOWED_ORIGINS=http://localhost:5173
   SECURITY_LOG_LEVEL=INFO
   ```

   Las URL internas **no** incluyen `/dev` ni `/api`. La clave es un secreto: debe coincidir con `infra/ms/.env` y no se publica. Si cambia la IP privada, actualiza las tres URL y recrea BFF.
4. Ejecuta `docker compose config`, `docker compose up -d --build`, `docker compose ps` y `docker compose logs --tail=100 bff`. Para actualizar una versión nueva: `git pull --ff-only` en la misma rama y `docker compose up -d --build`.
5. La EC2 backend abre 8081–8083 **solo al Security Group del BFF**; las bases PostgreSQL no publican puertos. BFF publica 8080 al destino de API Gateway; limita SSH 22 a tu IP. Una integración HTTP pública BFF↔Gateway no proporciona aislamiento de red/TLS extremo a extremo: para producción prefiere VPC Link + balanceador privado, con mayor costo y configuración.

Si trasladas el sistema a **otra cuenta AWS**, no reutilices la URL anterior de API Gateway ni supongas que una IP privada antigua seguirá sirviendo. Vuelve a configurar la integración y rutas en Gateway, cambia `VITE_API_BASE_URL` del frontend y actualiza las tres URL internas de este `.env`. Si backend y BFF están en cuentas/VPC distintas, primero conecta sus VPC (por ejemplo, peering con rutas y reglas de red en ambos lados); sin eso, las IP privadas no son alcanzables. El README del backend distingue los dos escenarios y explica las restricciones de Security Group. Los IDs de Entra no cambian por mover AWS si sigues usando el mismo tenant y los mismos registros SPA/API.

## API Gateway y CORS

`VITE_API_BASE_URL` del frontend toma, por ejemplo, `https://<api-id>.execute-api.<region>.amazonaws.com/dev`. Gateway debe pasar `/dev/api/orders` al BFF como `/api/orders`. Configura rutas base y subrutas para `/api/orders`, `/api/appointments`, `/api/products` y `/api/reports/{proxy+}` (o un proxy equivalente), incluida escritura POST/PUT de productos y las subrutas de estado/informe de órdenes. Una ruta base como `/api/products` no cubre por sí sola `/api/products/{id}`; comprueba ambos casos. Las rutas normales llevan autorizador JWT con issuer v2, audiencia del ID de la API y scope `access_as_user`; `OPTIONS` de preflight queda sin auth. CORS: origen exacto del frontend, métodos `GET, POST, PUT, DELETE, OPTIONS` y encabezados `authorization, content-type`.

Prueba desde PowerShell:

```powershell
curl.exe -i -X OPTIONS "https://<api-id>.execute-api.<region>.amazonaws.com/dev/api/products" -H "Origin: http://localhost:5173" -H "Access-Control-Request-Method: POST" -H "Access-Control-Request-Headers: authorization,content-type"
```

Espera 200/204 y `access-control-allow-origin` correcto. Si hay 403 con `Invalid CORS request`, revisa ruta y preflight; 401 con token apunta a issuer/audience/firma, 403 con token válido a scope/rol o autorizador Gateway. Sin token, `curl -i http://localhost:8080/api/products` debe devolver 401.

Pruebas: `./mvnw test` (Windows: `mvnw.cmd test`). La disponibilidad de agenda todavía no calcula ocupación real. Se probaron los controladores y la compilación, pero no una llamada integral contra las EC2 con esta versión: comprueba autorización con cuentas Cliente, Operador y Admin después de desplegar. No expongas el token, el PEM ni `INTERNAL_API_KEY` en capturas o commits.
