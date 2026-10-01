package com.talleres360.bff.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;

@RestController
public class ProductsProxyController {
    private final RestClient client;
    private final String catalogUrl;
    private final String internalKey;
    public ProductsProxyController(RestClient ordersClient,
            @Value("${app.services.catalog-url}") String catalogUrl,
            @Value("${app.services.internal-key}") String internalKey) {
        this.client = ordersClient; this.catalogUrl = catalogUrl; this.internalKey = internalKey;
    }
    @RequestMapping(path = {"/api/products", "/api/products/**"},
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<byte[]> proxy(HttpServletRequest request, @RequestBody(required = false) byte[] body) throws IOException {
        URI target = URI.create(catalogUrl + request.getRequestURI()
                + (request.getQueryString() == null ? "" : "?" + request.getQueryString()));
        RestClient.RequestBodySpec spec = client.method(HttpMethod.valueOf(request.getMethod()))
                .uri(target).header("X-Internal-Key", internalKey);
        Optional.ofNullable(request.getContentType()).ifPresent(type -> spec.contentType(MediaType.parseMediaType(type)));
        if (body != null && body.length > 0) spec.body(body);
        return spec.exchange((req, res) -> {
            HttpHeaders headers = new HttpHeaders();
            Optional.ofNullable(res.getHeaders().getContentType()).ifPresent(headers::setContentType);
            return ResponseEntity.status(res.getStatusCode()).headers(headers).body(res.getBody().readAllBytes());
        }, false);
    }
}
