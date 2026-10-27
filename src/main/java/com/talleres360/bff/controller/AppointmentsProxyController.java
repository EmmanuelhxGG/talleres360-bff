package com.talleres360.bff.controller;

import com.talleres360.bff.service.CorreoAutenticado;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AppointmentsProxyController {

  private static final String CUSTOMER_EMAIL_HEADER = "X-Customer-Email";
  private final RestClient ordersClient;
  private final String ordersUrl;
  private final String internalKey;

  public AppointmentsProxyController(
      RestClient ordersClient, @Value("${app.services.orders-url}") String ordersUrl,
      @Value("${app.services.internal-key}") String internalKey) {
    this.ordersClient = ordersClient;
    this.ordersUrl = ordersUrl;
    this.internalKey = internalKey;
  }

  @RequestMapping(
      path = {"/api/appointments", "/api/appointments/**"},
      method = {RequestMethod.GET, RequestMethod.POST})
  public ResponseEntity<byte[]> proxy(
      HttpServletRequest request,
      @RequestBody(required = false) byte[] body,
      JwtAuthenticationToken authentication)
      throws IOException {
    URI target =
        URI.create(
            ordersUrl
                + request.getRequestURI()
                + (request.getQueryString() != null ? "?" + request.getQueryString() : ""));

    RestClient.RequestBodySpec spec =
        ordersClient
            .method(HttpMethod.valueOf(request.getMethod()))
            .uri(target)
            .header("X-Internal-Key", internalKey)
            .header(CUSTOMER_EMAIL_HEADER, authenticatedEmail(authentication));

    Optional.ofNullable(request.getContentType())
        .ifPresent(contentType -> spec.contentType(MediaType.parseMediaType(contentType)));
    if (body != null && body.length > 0) spec.body(body);

    return spec.exchange(
        (req, res) -> {
          HttpHeaders headers = new HttpHeaders();
          headers.setCacheControl("no-store");
          Optional.ofNullable(res.getHeaders().getContentType()).ifPresent(headers::setContentType);
          return ResponseEntity.status(res.getStatusCode())
              .headers(headers)
              .body(res.getBody().readAllBytes());
        });
  }

  private String authenticatedEmail(JwtAuthenticationToken authentication) {
    return CorreoAutenticado.desde(authentication.getToken()).orElseThrow(() ->
        new ResponseStatusException(HttpStatus.UNAUTHORIZED,
            "La sesión no contiene un correo permitido"));
  }
}
