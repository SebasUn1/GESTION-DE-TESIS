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
    void elLoginFrenaAQuienPruebaClavesSinDejarAfueraALaPersonaReal() {
        Usuario victima = estudiante("Vera Victima");
        String malo = "{\"email\": \"%s\", \"password\": \"adivinando\"}".formatted(victima.email());
        String bueno = "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(victima.email(), CLAVE);
        String atacante = "203.0.113.66";

        for (int i = 0; i < 10; i++) {
            assertThat(postDesde(atacante, "/api/auth/login", malo).status()).isEqualTo(401);
        }
        assertThat(postDesde(atacante, "/api/auth/login", bueno).status())
            .as("el atacante queda frenado aunque acierte, hasta que pase el minuto").isEqualTo(429);

        assertThat(postDesde("198.51.100.20", "/api/auth/login", bueno).status())
            .as("la dueña de la cuenta, desde su IP, sigue entrando").isEqualTo(200);
    }

    @Test
    void unaMismaIpNoPuedeRecorrerCuentasSinFin() {
        String ip = "203.0.113.77";
        int frenadas = 0;
        for (int i = 0; i < 61; i++) {
            String intento = "{\"email\": \"%s\", \"password\": \"x\"}".formatted(correoUnico("barrido"));
            if (postDesde(ip, "/api/auth/login", intento).status() == 429) {
                frenadas++;
            }
        }
        assertThat(frenadas).as("el intento 61 desde la misma IP se corta").isEqualTo(1);
    }
}
