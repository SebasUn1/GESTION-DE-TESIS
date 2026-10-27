package com.tesistrack.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** CORS_ALLOWED_ORIGINS se escribe a mano en el panel del hosting: tiene que tolerar espacios y la barra final. */
class CorsConfigTest {

    @Test
    void tomaVariosOrigenesAunqueVenganConEspaciosOBarraFinal() {
        CorsConfig config = new CorsConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", "https://tesistrack.vercel.app/ , http://localhost:5173,");

        var fuente = (UrlBasedCorsConfigurationSource) config.corsConfigurationSource();
        var cors = fuente.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/health"));

        assertThat(cors.getAllowedOrigins()).containsExactly("https://tesistrack.vercel.app", "http://localhost:5173");
    }
}
