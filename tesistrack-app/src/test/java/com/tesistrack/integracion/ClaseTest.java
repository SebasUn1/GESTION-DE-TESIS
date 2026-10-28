package com.tesistrack.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/** La clase del profesor: el código de invitación y el reparto de actividades (Decisiones 10–12). */
class ClaseTest extends IntegracionTest {

    @Test
    void lasActividadesLleganComoHitosATodasLasTesisTambienALasQueEntranDespues() {
        Usuario profe = profesor("Profe Reparto");
        JsonNode clase = crearClase(profe, "Reparto");
        long areaId = clase.get("id").asLong();
        String codigo = clase.get("codigo").asString();

        post("/api/areas/" + areaId + "/actividades", profe, "{\"nombre\": \"Planteamiento\", \"fechaLimite\": \"2099-01-31\"}");
        Usuario temprana = estudiante("Tere Temprana");
        long tesisTemprana = crearTesisOk(temprana, "Primera", codigo);
        assertThat(nombresDeHitos(tesisTemprana, temprana)).containsExactly("Planteamiento");

        post("/api/areas/" + areaId + "/actividades", profe, "{\"nombre\": \"Marco teórico\", \"fechaLimite\": \"2099-02-28\"}");
        assertThat(nombresDeHitos(tesisTemprana, temprana))
            .as("la actividad nueva llega a quien ya estaba")
            .containsExactly("Planteamiento", "Marco teórico");

        Usuario tardia = estudiante("Tito Tardio");
        long tesisTardia = crearTesisOk(tardia, "Segunda", codigo.toLowerCase());
        JsonNode hitosTardia = get("/api/proyectos/" + tesisTardia + "/hitos", tardia).cuerpo();
        assertThat(hitosTardia.findValuesAsString("nombre"))
            .as("quien entra tarde recibe las actividades que ya había, en orden")
            .containsExactly("Planteamiento", "Marco teórico");
        assertThat(hitosTardia.findValues("orden").stream().map(JsonNode::asInt)).containsExactly(1, 2);

        JsonNode tablero = get("/api/areas/" + areaId + "/tablero", profe).cuerpo();
        assertThat(tablero.get("filas").size()).isEqualTo(2);
        assertThat(tablero.get("actividades").size()).isEqualTo(2);
    }

    @Test
    void conUnCodigoQueNoExisteNoSeEntraANingunaClase() {
        Usuario alumno = estudiante("Alan Codigo");
        Respuesta r = crearTesis(alumno, "Sin clase", "TT-NOEXISTE");
        assertThat(r.status()).isEqualTo(404);
        assertThat(get("/api/proyectos", alumno).cuerpo().size()).as("no queda una tesis a medio crear").isZero();
    }

    @Test
    void elCodigoSoloLoVeElDuenoYRegenerarloInvalidaElAnterior() {
        Usuario profe = profesor("Profe Codigo");
        JsonNode clase = crearClase(profe, "Codigos");
        long areaId = clase.get("id").asLong();
        String viejo = clase.get("codigo").asString();

        String nuevo = post("/api/areas/" + areaId + "/codigo", profe, null).get("codigo").asString();
        assertThat(nuevo).isNotEqualTo(viejo).startsWith("TT-");
        assertThat(crearTesis(estudiante("Con Viejo"), "x", viejo).status()).isEqualTo(404);
        assertThat(crearTesis(estudiante("Con Nuevo"), "x", nuevo).status()).isEqualTo(201);
        assertThat(post("/api/areas/" + areaId + "/codigo", profesor("Otro Profe"), null).status()).isEqualTo(403);
    }

    private java.util.List<String> nombresDeHitos(long tesis, Usuario u) {
        return get("/api/proyectos/" + tesis + "/hitos", u).cuerpo().findValuesAsString("nombre");
    }
}
