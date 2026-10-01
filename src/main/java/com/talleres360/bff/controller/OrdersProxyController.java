package com.talleres360.bff.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

/**
 * Reenvia a ms-talleres360-orders las llamadas que ya pasaron por la validacion del JWT
 * y por las reglas de rol de SecurityConfig. El BFF no duplica reglas de negocio:
 * devuelve tal cual el estado y el cuerpo del micro (incluidos los ProblemDetail 400 / 404 / 409).
 */
@RestController
public class OrdersProxyController {

	private final RestClient ordersClient;
	private final String ordersUrl;

	public OrdersProxyController(RestClient ordersClient, @Value("${app.services.orders-url}") String ordersUrl) {
		this.ordersClient = ordersClient;
		this.ordersUrl = ordersUrl;
	}

	@RequestMapping(
			path = { "/api/orders", "/api/orders/**" },
			method = { RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE })
	public ResponseEntity<byte[]> proxy(HttpServletRequest request, @RequestBody(required = false) byte[] body, JwtAuthenticationToken authentication)
			throws IOException {

		URI target = URI.create(ordersUrl + request.getRequestURI()
				+ (request.getQueryString() != null ? "?" + request.getQueryString() : ""));

		RestClient.RequestBodySpec spec = ordersClient
				.method(HttpMethod.valueOf(request.getMethod()))
				.uri(target)
				.header("X-Actor-Email", actor(authentication))
				.header("X-Actor-Role", actorRole(authentication));

		Optional.ofNullable(request.getContentType())
				.ifPresent(contentType -> spec.contentType(MediaType.parseMediaType(contentType)));

		if (body != null && body.length > 0) {
			spec.body(body);
		}

		return spec.exchange((req, res) -> {
			HttpHeaders headers = new HttpHeaders();
			Optional.ofNullable(res.getHeaders().getContentType()).ifPresent(headers::setContentType);

			return ResponseEntity.status(res.getStatusCode())
					.headers(headers)
					.body(res.getBody().readAllBytes());
		}, false);
	}

	private String actor(JwtAuthenticationToken authentication) {
		for (String claim : new String[] { "preferred_username", "email", "upn" }) {
			String value = authentication.getToken().getClaimAsString(claim);
			if (value != null && !value.isBlank()) return value;
		}
		return authentication.getName();
	}

	private String actorRole(JwtAuthenticationToken authentication) {
		var roles = authentication.getToken().getClaimAsStringList("roles");
		return roles != null && roles.contains("Admin") ? "Admin" : "Operador";
	}
}
