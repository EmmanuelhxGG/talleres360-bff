package com.talleres360.bff.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

@RestController
public class ProductsProxyController {
	private final RestClient ordersClient;
	private final String ordersUrl;
	public ProductsProxyController(RestClient ordersClient, @Value("${app.services.orders-url}") String ordersUrl) {
		this.ordersClient = ordersClient; this.ordersUrl = ordersUrl;
	}
	@GetMapping("/api/products")
	public ResponseEntity<byte[]> findAll() {
		return ordersClient.get().uri(URI.create(ordersUrl + "/api/products")).exchange((request, response) -> {
			HttpHeaders headers = new HttpHeaders();
			Optional.ofNullable(response.getHeaders().getContentType()).ifPresent(headers::setContentType);
			try { return ResponseEntity.status(response.getStatusCode()).headers(headers).body(response.getBody().readAllBytes()); }
			catch (IOException exception) { throw new IllegalStateException("No fue posible leer la respuesta del catálogo", exception); }
		}, false);
	}
}
