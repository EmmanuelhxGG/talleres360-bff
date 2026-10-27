package com.talleres360.bff.service;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.security.oauth2.jwt.Jwt;

/** Política de correo común; no sustituye la validación de firma, issuer, audiencia ni rol. */
public final class CorreoAutenticado {
  private static final Pattern PERMITIDO = Pattern.compile(
      "(?i)^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@(duocuc[.]cl|[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?[.]onmicrosoft[.]com)$");

  private CorreoAutenticado() {}

  public static Optional<String> desde(Jwt identidad) {
    for (String claim : new String[] {"email", "preferred_username", "upn"}) {
      String valor = identidad.getClaimAsString(claim);
      if (valor == null) continue;
      String correo = valor.strip().toLowerCase(Locale.ROOT);
      if (correo.length() <= 150 && !correo.contains("#ext#")
          && PERMITIDO.matcher(correo).matches()) return Optional.of(correo);
    }
    return Optional.empty();
  }
}
