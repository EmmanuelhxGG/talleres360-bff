package com.talleres360.bff.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

/** Administración solo para Admin; nunca acepta un host destino proporcionado por el cliente. */
@RestController
public class MensajeriaProxyController {
  private final RestClient cliente;
  private final String rabbit, kafka, clave;

  public MensajeriaProxyController(
      RestClient ordersClient,
      @Value("${app.services.rabbit-admin-url}") String rabbit,
      @Value("${app.services.kafka-admin-url}") String kafka,
      @Value("${app.services.internal-key}") String clave) {
    var tiempos = new org.springframework.http.client.SimpleClientHttpRequestFactory();
    tiempos.setConnectTimeout(3000);
    tiempos.setReadTimeout(20000);
    this.cliente = RestClient.builder().requestFactory(tiempos).build();
    this.rabbit = rabbit;
    this.kafka = kafka;
    this.clave = clave;
  }

  @RequestMapping(
      value = "/api/messaging/{sistema}/**",
      method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
  public ResponseEntity<byte[]> administrar(
      @PathVariable String sistema,
      HttpServletRequest solicitud,
      JwtAuthenticationToken identidad,
      @RequestBody(required = false) byte[] cuerpo) {
    if (identidad.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_Admin")))
      return error(HttpStatus.FORBIDDEN);
    String base =
        switch (sistema) {
          case "rabbit" -> rabbit;
          case "kafka" -> kafka;
          default -> null;
        };
    if (base == null) return error(HttpStatus.NOT_FOUND);
    if (clave.isBlank()) return error(HttpStatus.SERVICE_UNAVAILABLE);
    if (cuerpo != null && cuerpo.length > 16384) return error(HttpStatus.PAYLOAD_TOO_LARGE);
    try {
      var pedido =
          cliente
              .method(HttpMethod.valueOf(solicitud.getMethod()))
              .uri(URI.create(base.replaceAll("/+$", "") + solicitud.getRequestURI()))
              .header("X-Internal-Key", clave)
              .header("X-Actor-Role", "Admin");
      if (cuerpo != null) pedido.contentType(MediaType.APPLICATION_JSON).body(cuerpo);
      return pedido.exchange(
          (req, res) -> {
            if (res.getStatusCode().isError())
              return error(
                  res.getStatusCode().is4xxClientError()
                      ? HttpStatus.BAD_REQUEST
                      : HttpStatus.SERVICE_UNAVAILABLE);
            return ResponseEntity.status(res.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noStore())
                .body(res.getBody().readAllBytes());
          });
    } catch (org.springframework.web.client.RestClientException error) {
      return error(HttpStatus.SERVICE_UNAVAILABLE);
    }
  }

  private ResponseEntity<byte[]> error(HttpStatus estado) {
    return ResponseEntity.status(estado)
        .contentType(MediaType.APPLICATION_JSON)
        .cacheControl(CacheControl.noStore())
        .body(
            "{\"detail\":\"No se pudo realizar la operación de mensajería. Revisa los datos y tus permisos.\"}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
}
