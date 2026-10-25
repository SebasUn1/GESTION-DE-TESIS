package com.tesistrack.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/**
 * El acceso se resuelve por pertenencia, no por rol (Decisión 7): tener rol de
 * profesor no habilita a tocar la tesis de otro. El coordinador lee todo y no
 * escribe nada (Decisión 8).
 */
class PermisosProfesorTest extends IntegracionTest {

    Usuario profe, otroProfe, alumna;
    long tesis, hito, entrega;

    @BeforeEach
    void escenario() {
        profe = profesor("Profe Dueño");
        otroProfe = profesor("Profe Ajeno");
        alumna = estudiante("Alumna Permisos");
        tesis = crearTesisOk(alumna, "Tesis con dueño", crearClase(profe, "Propia").get("codigo").asString());
        hito = post("/api/proyectos/" + tesis + "/hitos", profe, "{\"nombre\": \"Capítulo 1\"}").get("id").asLong();
        entrega = post("/api/hitos/" + hito + "/entregas", alumna, "{\"archivoNombre\": \"cap1.pdf\"}").get("id").asLong();
    }

    @Test
    void otroProfesorNoVeNiTocaUnaTesisAjena() {
        assertThat(get("/api/proyectos/" + tesis, otroProfe).status()).isEqualTo(403);
        assertThat(get("/api/proyectos/" + tesis + "/hitos", otroProfe).status()).isEqualTo(403);
        assertThat(post("/api/proyectos/" + tesis + "/hitos", otroProfe, "{\"nombre\": \"Intruso\"}").status()).isEqualTo(403);
        assertThat(patch("/api/hitos/" + hito + "/estado", otroProfe, "{\"estado\": \"COMPLETADO\"}").status()).isEqualTo(403);
        assertThat(post("/api/entregas/" + entrega + "/observaciones", otroProfe, "{\"descripcion\": \"x\"}").status()).isEqualTo(403);
        assertThat(patch("/api/entregas/" + entrega + "/estado", otroProfe, "{\"estado\": \"APROBADA\"}").status()).isEqualTo(403);
        assertThat(get("/api/proyectos", otroProfe).cuerpo().size()).isZero();
    }

    @Test
    void unHitoConEntregasNoSeBorraPeroUnoVacioSi() {
        Respuesta conEntregas = delete("/api/hitos/" + hito, profe);
        assertThat(conEntregas.status()).isEqualTo(400);
        assertThat(conEntregas.get("error").asString()).contains("entregas");

        long vacio = post("/api/proyectos/" + tesis + "/hitos", profe, "{\"nombre\": \"Borrador\"}").get("id").asLong();
        assertThat(delete("/api/hitos/" + vacio, profe).status()).isEqualTo(204);
    }

    @Test
    void elProfesorNoEntregaPorElGrupo() {
        assertThat(post("/api/hitos/" + hito + "/entregas", profe, "{\"archivoNombre\": \"yo.pdf\"}").status()).isEqualTo(403);
    }

    @Test
    void elCoordinadorLeeTodoPeroNoEscribeNada() {
        Usuario coord = coordinador("Coordinadora General");
        JsonNode todas = get("/api/proyectos", coord).cuerpo();
        assertThat(todas.findValues("id").stream().map(JsonNode::asLong)).contains(tesis);
        assertThat(get("/api/proyectos/" + tesis + "/dashboard", coord).status()).isEqualTo(200);
        assertThat(get("/api/hitos/" + hito + "/entregas", coord).status()).isEqualTo(200);

        assertThat(post("/api/proyectos/" + tesis + "/hitos", coord, "{\"nombre\": \"No\"}").status()).isEqualTo(403);
        assertThat(post("/api/hitos/" + hito + "/entregas", coord, "{\"archivoNombre\": \"no.pdf\"}").status()).isEqualTo(403);
        assertThat(post("/api/entregas/" + entrega + "/observaciones", coord, "{\"descripcion\": \"no\"}").status()).isEqualTo(403);
        assertThat(post("/api/proyectos/" + tesis + "/asesorias", coord,
            "{\"fecha\": \"2026-10-01T15:00:00Z\", \"tema\": \"No\"}").status()).isEqualTo(403);
        assertThat(post("/api/proyectos", coord, "{\"titulo\": \"Mía\"}").status()).isEqualTo(403);
    }
}
