package com.talleres360.bff.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * El BFF es un Resource Server: no emite tokens, solo valida los que emite Entra ID. La firma, el
 * issuer y la audiencia se validan con la configuracion de application.yml (issuer-uri +
 * audiences). Aqui se define quien puede llamar a cada endpoint.
 */
@Configuration
public class SecurityConfig {

  /** Scope delegado que expone el registro api-fullstack en Entra ID. */
  private static final String SCOPE = "SCOPE_access_as_user";

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.cors(Customizer.withDefaults())
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.GET, "/api/notifications")
                    .access(scopeAndRoles("Operador", "Cliente"))
                    .requestMatchers(HttpMethod.GET, "/api/audit", "/api/audit/events")
                    .access(scopeAndRoles("Admin"))
                    .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**")
                    .access(scopeAndRoles("Admin", "Operador"))
                    .requestMatchers(HttpMethod.POST, "/api/products")
                    .access(scopeAndRoles("Admin"))
                    .requestMatchers(HttpMethod.PUT, "/api/products/**")
                    .access(scopeAndRoles("Admin"))
                    .requestMatchers(HttpMethod.GET, "/api/reports/**")
                    .access(scopeAndRoles("Admin"))
                    // El cliente agenda y consulta exclusivamente sus propias solicitudes.
                    .requestMatchers(HttpMethod.GET, "/api/appointments", "/api/appointments/**")
                    .access(scopeAndRoles("Cliente"))
                    .requestMatchers(HttpMethod.POST, "/api/appointments")
                    .access(scopeAndRoles("Cliente"))
                    // Las ordenes operativas completas contienen datos de todos los clientes.
                    .requestMatchers(HttpMethod.GET, "/api/orders", "/api/orders/**")
                    .access(scopeAndRoles("Admin", "Operador"))
                    // Crear y modificar ordenes (incluido el cambio de estado): taller
                    .requestMatchers(HttpMethod.POST, "/api/orders", "/api/orders/**")
                    .access(scopeAndRoles("Admin", "Operador"))
                    .requestMatchers(HttpMethod.PUT, "/api/orders/**")
                    .access(scopeAndRoles("Admin", "Operador"))
                    // Eliminar: solo administrador
                    .requestMatchers(HttpMethod.DELETE, "/api/orders/**")
                    .access(scopeAndRoles("Admin"))
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

    return http.build();
  }

  /** Exige el scope delegado y ademas uno de los app roles del token. */
  private AuthorizationManager<RequestAuthorizationContext> scopeAndRoles(String... roles) {
    return AuthorizationManagers.allOf(
        AuthorityAuthorizationManager.hasAuthority(SCOPE),
        AuthorityAuthorizationManager.hasAnyRole(roles));
  }

  /**
   * Por defecto Spring solo mapea el claim "scp" a SCOPE_*. Entra ID manda los app roles en el
   * claim "roles", asi que los agregamos como ROLE_Admin / ROLE_Operador / ROLE_Cliente.
   */
  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          Collection<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
          List<String> roles = jwt.getClaimAsStringList("roles");
          if (roles != null) {
            roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
          }
          return authorities;
        });
    return converter;
  }
}
