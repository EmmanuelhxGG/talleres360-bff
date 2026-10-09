package com.talleres360.bff.controller;

import com.talleres360.bff.service.ProxySeguimiento;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
public class SeguimientoProxyController {
  private final ProxySeguimiento proxy;
  private final String notificaciones;
  private final String auditoria;

  public SeguimientoProxyController(
      ProxySeguimiento proxy,
      @Value("${app.services.notifications-url}") String notificaciones,
      @Value("${app.services.audit-url}") String auditoria) {
    this.proxy = proxy;
    this.notificaciones = notificaciones;
    this.auditoria = auditoria;
  }

  @GetMapping("/api/notifications")
  public ResponseEntity<byte[]> notificaciones(
      HttpServletRequest request, JwtAuthenticationToken identidad) {
    return proxy.consultar(notificaciones, request, identidad, Set.of("Operador", "Cliente"));
  }

  @GetMapping({"/api/audit", "/api/audit/events", "/api/reports/audit"})
  public ResponseEntity<byte[]> auditoria(
      HttpServletRequest request, JwtAuthenticationToken identidad) {
    return proxy.consultar(auditoria, request, identidad, Set.of("Admin"));
  }
}
