package com.tesistrack.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/**
 * El ciclo de corrección completo, que es para lo que existe el sistema: entrega,
 * el profesor observa, sube la versión corregida y se aprueba.
 */
class CicloEntregaTest extends IntegracionTest {

    @Test
    void entregaObservacionVersionCorregidaYAprobada() {
        Usuario profe = profesor("Profe Ciclo");
        JsonNode clase = crearClase(profe, "Ciclo");
        post("/api/areas/" + clase.get("id").asLong() + "/actividades", profe,
            "{\"nombre\": \"Hito 1: Plan\", \"fechaLimite\": \"2099-12-31\"}");
        Usuario b = estudiante("Beto Ciclo");
        long tesis = crearTesisOk(b, "Mi tema", clase.get("codigo").asString());

        assertThat(get("/api/proyectos/" + tesis + "/dashboard", b).get("semaforo").asString()).isEqualTo("VERDE");

        long hito = get("/api/proyectos/" + tesis + "/hitos", b).cuerpo().get(0).get("id").asLong();
        Respuesta v1 = post("/api/hitos/" + hito + "/entregas", b,
            "{\"archivoNombre\": \"plan_v1.pdf\", \"comentario\": \"primera\"}");
        assertThat(v1.status()).isEqualTo(201);
        assertThat(v1.get("version").asInt()).isEqualTo(1);
        assertThat(v1.get("estado").asString()).isEqualTo("EN_REVISION");
        assertThat(hitoEstado(tesis, b)).as("entregar deja el hito esperando revisión").isEqualTo("ENTREGADO");

        Respuesta obs = post("/api/entregas/" + v1.get("id").asLong() + "/observaciones", profe,
            "{\"descripcion\": \"Falta justificar la muestra\"}");
        assertThat(obs.status()).isEqualTo(201);
        assertThat(hitoEstado(tesis, b)).as("observar devuelve el hito").isEqualTo("OBSERVADO");
        JsonNode trasObservar = get("/api/proyectos/" + tesis + "/dashboard", b).cuerpo();
        assertThat(trasObservar.get("semaforo").asString()).as("observado sin subsanar = en riesgo").isEqualTo("AMARILLO");
        JsonNode pendiente = trasObservar.get("observacionesPendientes").get(0);
        assertThat(pendiente.get("hitoId").asLong()).as("la observación lleva al hito para corregir").isEqualTo(hito);
        assertThat(get("/api/hitos/" + hito + "/entregas", b).cuerpo().get(0).get("estado").asString()).isEqualTo("OBSERVADA");

        Respuesta v2 = post("/api/hitos/" + hito + "/entregas", b,
            "{\"archivoNombre\": \"plan_v2.pdf\", \"comentario\": \"corregida\"}");
        assertThat(v2.get("version").asInt()).as("el backend numera las versiones").isEqualTo(2);
        assertThat(hitoEstado(tesis, b)).as("el retorno OBSERVADO → ENTREGADO").isEqualTo("ENTREGADO");
        assertThat(patch("/api/observaciones/" + obs.get("id").asLong() + "/estado", profe, "{\"estado\": \"RESUELTA\"}").status())
            .isEqualTo(200);
        assertThat(patch("/api/entregas/" + v2.get("id").asLong() + "/estado", profe, "{\"estado\": \"APROBADA\"}").get("estado").asString())
            .isEqualTo("APROBADA");
        assertThat(patch("/api/hitos/" + hito + "/estado", profe, "{\"estado\": \"COMPLETADO\"}").status()).isEqualTo(200);

        JsonNode fin = get("/api/proyectos/" + tesis + "/dashboard", b).cuerpo();
        assertThat(fin.get("observacionesPendientes").size()).isZero();
        assertThat(fin.get("semaforo").asString()).isEqualTo("VERDE");
        JsonNode versiones = get("/api/hitos/" + hito + "/entregas", b).cuerpo();
        assertThat(versiones.findValuesAsString("estado"))
            .as("la v1 queda observada para siempre en el historial")
            .containsExactly("OBSERVADA", "APROBADA");
        assertThat(patch("/api/entregas/" + v2.get("id").asLong() + "/estado", b, "{\"estado\": \"APROBADA\"}").status())
            .as("el estudiante no se aprueba solo").isEqualTo(403);
    }

    private String hitoEstado(long tesis, Usuario u) {
        return get("/api/proyectos/" + tesis + "/hitos", u).cuerpo().get(0).get("estado").asString();
    }
}
