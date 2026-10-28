package com.tesistrack.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Mensajes privados: solo entre quienes comparten una tesis (Decisión 28). */
class MensajesTest extends IntegracionTest {

    @Test
    void elEstudianteYSuProfesorSeEscribenYLosNoLeidosSeCuentan() {
        Usuario profe = profesor("Profe Mensajes");
        Usuario alumno = estudiante("Alumno Mensajes");
        crearTesisOk(alumno, "Con chat", crearClase(profe, "Chat").get("codigo").asString());

        assertThat(post("/api/mensajes/con/" + profe.id(), alumno, "{\"texto\": \"¿Revisó mi capítulo?\"}").status())
            .isEqualTo(201);
        assertThat(get("/api/mensajes/no-leidos", profe).get("total").asInt()).isEqualTo(1);
        assertThat(get("/api/mensajes/con/" + alumno.id(), profe).cuerpo().toString()).contains("¿Revisó mi capítulo?");

        assertThat(put("/api/mensajes/con/" + alumno.id() + "/leidos", profe, null).status()).isEqualTo(204);
        assertThat(get("/api/mensajes/no-leidos", profe).get("total").asInt()).isZero();
    }

    @Test
    void nadieLeEscribeAUnDesconocidoNiElCoordinadorParticipa() {
        Usuario profe = profesor("Profe Ajeno Chat");
        Usuario alumno = estudiante("Alumno Sin Tesis Comun");
        assertThat(post("/api/mensajes/con/" + profe.id(), alumno, "{\"texto\": \"hola\"}").status()).isEqualTo(403);
        assertThat(get("/api/mensajes/con/" + profe.id(), alumno).status()).isEqualTo(403);

        Usuario coord = coordinador("Coord Chat");
        assertThat(get("/api/mensajes/contactos", coord).status()).isEqualTo(403);
        assertThat(get("/api/mensajes/no-leidos", coord).get("total").asInt()).isZero();
    }
}
