package com.tesistrack.integracion;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base de los tests de integración: la aplicación entera en un puerto aleatorio,
 * contra un PostgreSQL en Docker ({@link ContenedorPostgres}), y llamadas HTTP de
 * verdad con su JWT. Prueba lo mismo que un cliente: rutas, seguridad, validación,
 * JSON y base de datos juntos.
 *
 * <p>Cada test crea sus propios usuarios con correos únicos, así que no dependen del
 * orden ni se pisan entre sí aunque compartan la base.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(ContenedorPostgres.class)
public abstract class IntegracionTest {

    protected static final String CLAVE = "password123";

    private static final AtomicInteger SECUENCIA = new AtomicInteger();
    protected static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int puerto;

    @Autowired
    protected JdbcTemplate jdbc;

    /** Un usuario registrado, con su token. */
    protected record Usuario(Long id, String nombre, String email, String token) {
    }

    /** Una respuesta: el código y el cuerpo (null si no vino JSON). */
    protected record Respuesta(int status, JsonNode cuerpo) {
        public JsonNode get(String campo) {
            return cuerpo.get(campo);
        }
    }

    /** Un correo que ningún otro test usa. */
    protected static String correoUnico(String nombre) {
        return nombre.toLowerCase().replace(' ', '.') + "." + SECUENCIA.incrementAndGet() + "."
            + UUID.randomUUID().toString().substring(0, 8) + "@test.tesistrack";
    }

    protected Usuario registrar(String nombre, String rol) {
        String email = correoUnico(nombre);
        Respuesta r = post("/api/auth/register", null, """
            {"name": "%s", "email": "%s", "password": "%s", "role": "%s", "aceptaPolitica": true}
            """.formatted(nombre, email, CLAVE, rol));
        if (r.status() != 200 && r.status() != 201) {
            throw new IllegalStateException("No se pudo registrar a " + nombre + ": " + r);
        }
        return new Usuario(r.get("user").get("id").asLong(), nombre, email, r.get("token").asString());
    }

    protected Usuario estudiante(String nombre) {
        return registrar(nombre, "ESTUDIANTE");
    }

    protected Usuario profesor(String nombre) {
        return registrar(nombre, "ASESOR");
    }

    /** El coordinador no se puede registrar por la API: se promueve por SQL, como en producción. */
    protected Usuario coordinador(String nombre) {
        Usuario u = registrar(nombre, "ESTUDIANTE");
        jdbc.update("update users set role = 'COORDINADOR' where id = ?", u.id());
        Respuesta login = post("/api/auth/login", null,
            "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(u.email(), CLAVE));
        return new Usuario(u.id(), u.nombre(), u.email(), login.get("token").asString());
    }

    protected Respuesta get(String ruta, Usuario u) {
        return enviar("GET", ruta, u, null);
    }

    protected Respuesta post(String ruta, Usuario u, String json) {
        return enviar("POST", ruta, u, json);
    }

    protected Respuesta put(String ruta, Usuario u, String json) {
        return enviar("PUT", ruta, u, json);
    }

    protected Respuesta patch(String ruta, Usuario u, String json) {
        return enviar("PATCH", ruta, u, json);
    }

    protected Respuesta delete(String ruta, Usuario u) {
        return enviar("DELETE", ruta, u, null);
    }

    /** Una clase nueva del profesor: {@code id}, {@code nombre}, {@code codigo}. */
    protected JsonNode crearClase(Usuario profesor, String nombre) {
        Respuesta r = post("/api/areas", profesor, "{\"nombre\": " + str(nombre + " " + UUID.randomUUID().toString().substring(0, 6)) + "}");
        if (r.status() != 201) {
            throw new IllegalStateException("No se pudo crear la clase: " + r);
        }
        return r.cuerpo();
    }

    /** Crea la tesis, entrando a la clase con el código si viene uno. */
    protected Respuesta crearTesis(Usuario estudiante, String titulo, String codigo) {
        return post("/api/proyectos", estudiante, """
            {"titulo": %s, "codigoInvitacion": %s}
            """.formatted(str(titulo), str(codigo)));
    }

    protected long crearTesisOk(Usuario estudiante, String titulo, String codigo) {
        Respuesta r = crearTesis(estudiante, titulo, codigo);
        if (r.status() != 201) {
            throw new IllegalStateException("No se pudo crear la tesis: " + r);
        }
        return r.get("id").asLong();
    }

    /** Sube un archivo como lo hace el navegador: multipart con el campo {@code archivo}. */
    protected Respuesta subir(String metodo, String ruta, Usuario u, byte[] contenido, String nombre) {
        String limite = UUID.randomUUID().toString();
        ByteArrayOutputStream cuerpo = new ByteArrayOutputStream();
        cuerpo.writeBytes(("--" + limite + "\r\nContent-Disposition: form-data; name=\"archivo\"; filename=\""
            + nombre + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        cuerpo.writeBytes(contenido);
        cuerpo.writeBytes(("\r\n--" + limite + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
            .header("Content-Type", "multipart/form-data; boundary=" + limite)
            .method(metodo, HttpRequest.BodyPublishers.ofByteArray(cuerpo.toByteArray()));
        return ejecutar(b, u);
    }

    /** Los bytes crudos de una respuesta, para comparar una descarga con lo subido. */
    protected byte[] bajar(String ruta, Usuario u) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).GET();
        if (u != null) {
            b.header("Authorization", "Bearer " + u.token());
        }
        try {
            return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofByteArray()).body();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** Escapa un texto para meterlo en un JSON escrito a mano. */
    protected static String str(String texto) {
        return texto == null ? "null" : JSON.writeValueAsString(texto);
    }

    private Respuesta enviar(String metodo, String ruta, Usuario u, String json) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
            .header("Content-Type", "application/json")
            .method(metodo, json == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json));
        return ejecutar(b, u);
    }

    private Respuesta ejecutar(HttpRequest.Builder b, Usuario u) {
        if (u != null) {
            b.header("Authorization", "Bearer " + u.token());
        }
        try {
            HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode cuerpo = null;
            if (r.body() != null && !r.body().isBlank()) {
                try {
                    cuerpo = JSON.readTree(r.body());
                } catch (RuntimeException e) {
                    cuerpo = null;
                }
            }
            return new Respuesta(r.statusCode(), cuerpo);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
