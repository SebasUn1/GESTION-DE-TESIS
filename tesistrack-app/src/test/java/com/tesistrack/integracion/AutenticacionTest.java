package com.tesistrack.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Registro, login y token: lo primero que toca cualquier persona. */
class AutenticacionTest extends IntegracionTest {

    @Test
    void elCoordinadorNoSePuedeAutoasignarAlRegistrarse() {
        Respuesta r = post("/api/auth/register", null, """
            {"name": "Quiero Ver Todo", "email": "%s", "password": "%s", "role": "COORDINADOR", "aceptaPolitica": true}
            """.formatted(correoUnico("coord"), CLAVE));
        assertThat(r.status()).isEqualTo(400);
    }

    @Test
    void sinAceptarLaPoliticaNoSeCreaLaCuenta() {
        Respuesta r = post("/api/auth/register", null, """
            {"name": "Sin Consentimiento", "email": "%s", "password": "%s", "role": "ESTUDIANTE", "aceptaPolitica": false}
            """.formatted(correoUnico("politica"), CLAVE));
        assertThat(r.status()).isEqualTo(400);
    }

    @Test
    void elCorreoNoDistingueMayusculasNiEspacios() {
        Usuario u = estudiante("Eva Mayusculas");
        Respuesta duplicado = post("/api/auth/register", null, """
            {"name": "Eva Otra", "email": "%s", "password": "%s", "role": "ESTUDIANTE", "aceptaPolitica": true}
            """.formatted(u.email().toUpperCase(), CLAVE));
        assertThat(duplicado.status()).isEqualTo(400);

        Respuesta login = post("/api/auth/login", null,
            "{\"email\": \"  %s \", \"password\": \"%s\"}".formatted(u.email().toUpperCase(), CLAVE));
        assertThat(login.status()).isEqualTo(200);
        assertThat(login.get("user").get("id").asLong()).isEqualTo(u.id());
    }

    @Test
    void unaClaveIncorrectaOUnTokenFalsoDan401() {
        Usuario u = estudiante("Luis Clave");
        assertThat(post("/api/auth/login", null,
            "{\"email\": \"%s\", \"password\": \"otra-clave\"}".formatted(u.email())).status()).isEqualTo(401);
        assertThat(get("/api/auth/me", new Usuario(u.id(), u.nombre(), u.email(), "no.es.un-token")).status()).isEqualTo(401);
        assertThat(get("/api/auth/me", u).get("email").asString()).isEqualTo(u.email());
    }

    @Test
    void elLoginSeFrenaDespuesDeDiezIntentosALaMismaCuenta() {
        Usuario victima = estudiante("Vera Victima");
        String malo = "{\"email\": \"%s\", \"password\": \"adivinando\"}".formatted(victima.email());
        for (int i = 0; i < 10; i++) {
            assertThat(post("/api/auth/login", null, malo).status()).isEqualTo(401);
        }
        Respuesta frenado = post("/api/auth/login", null,
            "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(victima.email(), CLAVE));
        assertThat(frenado.status()).as("ni con la clave correcta, hasta que pase el minuto").isEqualTo(429);

        Usuario otra = estudiante("Otra Cuenta");
        assertThat(post("/api/auth/login", null,
            "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(otra.email(), CLAVE)).status())
            .as("el freno es por cuenta: los demás siguen entrando").isEqualTo(200);
    }
}
