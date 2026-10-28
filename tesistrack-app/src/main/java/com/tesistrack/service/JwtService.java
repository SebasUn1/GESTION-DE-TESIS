package com.tesistrack.service;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.tesistrack.model.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    /** HS256 pide una clave de al menos 256 bits. */
    static final int LARGO_MINIMO_SECRETO = 32;

    public JwtService(
        @Value("${app.jwt.secret}") String secret,
        @Value("${app.jwt.expiration-ms}") long expirationMs
    ) {
        // Sin valor por defecto a propósito: un default en el código está publicado en
        // GitHub, y con él cualquiera firma un token con el correo de otro y entra como
        // esa persona. Mejor que la aplicación no arranque a que arranque así.
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < LARGO_MINIMO_SECRETO) {
            throw new IllegalStateException(
                "Falta la variable de entorno JWT_SECRET (mínimo " + LARGO_MINIMO_SECRETO + " caracteres). "
                + "En desarrollo, arrancá con el perfil dev: ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
            .subject(user.getEmail())
            .claims(Map.of("role", user.getRole().name(), "name", user.getName()))
            .issuedAt(now)
            .expiration(expiry)
            .signWith(signingKey)
            .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
