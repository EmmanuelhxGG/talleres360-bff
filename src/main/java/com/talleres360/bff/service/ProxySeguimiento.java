package com.talleres360.bff.service;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/** Solo reenvía lecturas explícitas y construye los encabezados desde la identidad validada. */
@Service
public class ProxySeguimiento {
  private final RestClient client;
  private final String clave;

  public ProxySeguimiento(
      RestClient ordersClient, @Value("${app.services.internal-key}") String clave) {
    this.client = ordersClient;
    this.clave = clave;
  }

  public ResponseEntity<byte[]> consultar(
      String base,
      HttpServletRequest request,
      JwtAuthenticationToken identidad,
      Set<String> rolesPermitidos) {
    String rol =
        java.util.List.of("Admin", "Operador", "Cliente").stream()
            .filter(rolesPermitidos::contains)
            .filter(
                r ->
                    identidad.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_" + r)))
            .findFirst()
            .orElse(null);
    if (rol == null)
      return error(
          HttpStatus.FORBIDDEN, "Tu cuenta no tiene permisos para consultar esta información.");
    String correo = null;
    if (rol.equals("Cliente")) {
      for (String claim : new String[] {"preferred_username", "email", "upn"}) {
        String valor = identidad.getToken().getClaimAsString(claim);
        if (valor != null && !valor.isBlank()) {
          correo = valor;
          break;
        }
      }
      if (correo == null)
        return error(
            HttpStatus.FORBIDDEN, "No pudimos identificar tu cuenta. Inicia sesión nuevamente.");
    }
    if (clave == null || clave.isBlank()) return noDisponible();
    URI uri =
        URI.create(
            base.replaceAll("/+$", "")
                + request.getRequestURI()
                + (request.getQueryString() == null ? "" : "?" + request.getQueryString()));
    try {
      var consulta =
          client.get().uri(uri).header("X-Internal-Key", clave).header("X-Actor-Role", rol);
      if (correo != null) consulta.header("X-Customer-Email", correo);
      return consulta.exchange(
          (req, res) -> {
            if (res.getStatusCode().is5xxServerError() || res.getStatusCode().value() == 401)
              return noDisponible();
            if (res.getStatusCode().is4xxClientError())
              return error(
                  HttpStatus.valueOf(res.getStatusCode().value()),
                  "No se pudo consultar la información. Revisa los filtros y tus permisos.");
            HttpHeaders headers = new HttpHeaders();
            if (res.getHeaders().getContentType() != null)
              headers.setContentType(res.getHeaders().getContentType());
            headers.setCacheControl("no-store");
            return ResponseEntity.status(res.getStatusCode())
                .headers(headers)
                .body(res.getBody().readAllBytes());
          });
    } catch (ResourceAccessException ex) {
      return noDisponible();
    }
  }

  private static ResponseEntity<byte[]> noDisponible() {
    return error(
        HttpStatus.SERVICE_UNAVAILABLE, "No pudimos cargar la información. Inténtalo nuevamente.");
  }

  private static ResponseEntity<byte[]> error(HttpStatus estado, String mensaje) {
    return ResponseEntity.status(estado)
        .contentType(MediaType.APPLICATION_JSON)
        .cacheControl(CacheControl.noStore())
        .body(("{\"detail\":\"" + mensaje + "\"}").getBytes(StandardCharsets.UTF_8));
  }
}
