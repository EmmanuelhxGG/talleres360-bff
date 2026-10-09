package com.talleres360.bff.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

@RestController
public class ReportsProxyController {
  private final RestClient client;
  private final String reportUrl;
  private final String internalKey;

  public ReportsProxyController(
      RestClient ordersClient,
      @Value("${app.services.report-url}") String reportUrl,
      @Value("${app.services.internal-key}") String internalKey) {
    this.client = ordersClient;
    this.reportUrl = reportUrl;
    this.internalKey = internalKey;
  }

  @GetMapping("/api/reports/**")
  public ResponseEntity<byte[]> proxy(HttpServletRequest request) throws IOException {
    URI target =
        URI.create(
            reportUrl
                + request.getRequestURI()
                + (request.getQueryString() == null ? "" : "?" + request.getQueryString()));
    return client
        .get()
        .uri(target)
        .header("X-Internal-Key", internalKey)
        .exchange(
            (req, res) -> {
              HttpHeaders headers = new HttpHeaders();
              Optional.ofNullable(res.getHeaders().getContentType())
                  .ifPresent(headers::setContentType);
              return ResponseEntity.status(res.getStatusCode())
                  .headers(headers)
                  .body(res.getBody().readAllBytes());
            });
  }
}
