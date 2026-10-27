package com.tesistrack.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Los documentos de las entregas: el tipo sale de los bytes, no de la extensión
 * (Decisión 25), y solo el grupo los sube.
 */
class ArchivosTest extends IntegracionTest {

    Usuario profe, alumna;
    long entrega;

    @BeforeEach
    void escenario() {
        profe = profesor("Profe Archivos");
        alumna = estudiante("Alumna Archivos");
        long tesis = crearTesisOk(alumna, "Con archivos", crearClase(profe, "Archivos").get("codigo").asString());
        long hito = post("/api/proyectos/" + tesis + "/hitos", profe, "{\"nombre\": \"Informe\"}").get("id").asLong();
        entrega = post("/api/hitos/" + hito + "/entregas", alumna, "{\"archivoNombre\": \"informe.pdf\"}").get("id").asLong();
    }

    @Test
    void unPdfRealSeReconoceYSeDescargaIgual() {
        byte[] pdf = "%PDF-1.4\n1 0 obj << >> endobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
        Respuesta r = subir("PUT", "/api/entregas/" + entrega + "/archivo", alumna, pdf, "informe.pdf");
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.get("archivoTipo").asString()).isEqualTo("application/pdf");
        assertThat(r.get("tieneArchivo").asBoolean()).isTrue();
        assertThat(bajar("/api/entregas/" + entrega + "/archivo", profe)).as("el profesor lo baja intacto").isEqualTo(pdf);
    }

    @Test
    void unArchivoDisfrazadoDePdfQuedaComoBinarioYNoSePrevisualiza() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        Respuesta r = subir("PUT", "/api/entregas/" + entrega + "/archivo", alumna, html, "trampa.pdf");
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.get("archivoTipo").asString()).isEqualTo("application/octet-stream");
    }

    @Test
    void elProfesorNoSubeArchivosPorElGrupo() {
        byte[] pdf = "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
        assertThat(subir("PUT", "/api/entregas/" + entrega + "/archivo", profe, pdf, "x.pdf").status()).isEqualTo(403);
    }
}
