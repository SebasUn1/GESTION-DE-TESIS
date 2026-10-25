package com.tesistrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.tesistrack.model.Role;
import com.tesistrack.model.User;

import io.jsonwebtoken.JwtException;

/** Sin una clave de verdad la aplicación no arranca, y un token ajeno no sirve. */
class JwtServiceTest {

    private static final String CLAVE = "una-clave-de-prueba-de-treinta-y-dos-caracteres";

    @Test
    void sinClaveONiConUnaCortaNoArranca() {
        assertThatThrownBy(() -> new JwtService("", 60_000))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> new JwtService(null, 60_000)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService("corta", 60_000)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void elTokenLlevaElCorreoYElRol() {
        JwtService jwt = new JwtService(CLAVE, 60_000);
        var claims = jwt.parseClaims(jwt.generateToken(usuario()));
        assertThat(claims.getSubject()).isEqualTo("ana@utec.pe");
        assertThat(claims.get("role", String.class)).isEqualTo("ASESOR");
    }

    @Test
    void unTokenFirmadoConOtraClaveSeRechaza() {
        String ajeno = new JwtService("otra-clave-distinta-tambien-de-32-caracteres", 60_000).generateToken(usuario());
        JwtService jwt = new JwtService(CLAVE, 60_000);
        assertThatThrownBy(() -> jwt.parseClaims(ajeno)).isInstanceOf(JwtException.class);
    }

    @Test
    void unTokenVencidoSeRechaza() {
        JwtService vencido = new JwtService(CLAVE, -1_000);
        String token = vencido.generateToken(usuario());
        assertThatThrownBy(() -> vencido.parseClaims(token)).isInstanceOf(JwtException.class);
    }

    private static User usuario() {
        User u = new User();
        u.setName("Ana");
        u.setEmail("ana@utec.pe");
        u.setRole(Role.ASESOR);
        return u;
    }
}
