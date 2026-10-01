package com.talleres360.bff;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hito H5: sin token 401, con token pero rol equivocado 403.
 * El 200 se comprueba end-to-end con el compose (aqui no hay ms-orders levantado).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityRulesTest {

	@Autowired
	private MockMvc mvc;

	private static SimpleGrantedAuthority scope() {
		return new SimpleGrantedAuthority("SCOPE_access_as_user");
	}

	private static SimpleGrantedAuthority role(String role) {
		return new SimpleGrantedAuthority("ROLE_" + role);
	}

	@Test
	void sinTokenDevuelve401() throws Exception {
		mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
	}

	@Test
	void clienteNoPuedeCrearOrdenes() throws Exception {
		mvc.perform(post("/api/orders")
						.with(jwt().authorities(scope(), role("Cliente")))
						.contentType("application/json")
						.content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void clienteNoPuedeConsultarTodasLasOrdenes() throws Exception {
		mvc.perform(get("/api/orders").with(jwt().authorities(scope(), role("Cliente"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void operadorNoPuedeEliminarOrdenes() throws Exception {
		mvc.perform(delete("/api/orders/1").with(jwt().authorities(scope(), role("Operador"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void tokenSinElScopeDeLaApiEsRechazado() throws Exception {
		mvc.perform(get("/api/orders").with(jwt().authorities(role("Admin"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void rutaNoMapeadaSeDeniega() throws Exception {
		mvc.perform(get("/api/otra-cosa").with(jwt().authorities(scope(), role("Admin"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void operadorNoPuedeModificarCatalogoNiVerReportes() throws Exception {
		mvc.perform(post("/api/products").with(jwt().authorities(scope(), role("Operador")))
				.contentType("application/json").content("{}")).andExpect(status().isForbidden());
		mvc.perform(get("/api/reports/sales").with(jwt().authorities(scope(), role("Operador"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void clienteNoPuedeLeerCatalogo() throws Exception {
		mvc.perform(get("/api/products").with(jwt().authorities(scope(), role("Cliente"))))
				.andExpect(status().isForbidden());
	}
}
