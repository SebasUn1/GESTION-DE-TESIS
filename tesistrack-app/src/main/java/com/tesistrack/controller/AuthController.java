package com.tesistrack.controller;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tesistrack.service.LimitadorConsultas;

import jakarta.servlet.http.HttpServletRequest;

import com.tesistrack.dto.AsesoriasPrivadasRequest;
import com.tesistrack.dto.AuthResponse;
import com.tesistrack.dto.LoginRequest;
import com.tesistrack.dto.RegisterRequest;
import com.tesistrack.dto.PerfilDto;
import com.tesistrack.model.User;
import com.tesistrack.service.AuthService;
import com.tesistrack.service.PerfilService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    static final int INTENTOS_LOGIN_POR_CUENTA = 10;
    static final int INTENTOS_LOGIN_POR_IP = 60;

    private final AuthService authService;
    private final PerfilService perfilService;
    private final LimitadorConsultas limitador;

    public AuthController(
            AuthService authService,
            PerfilService perfilService,
            LimitadorConsultas limitador) {
        this.authService = authService;
        this.perfilService = perfilService;
        this.limitador = limitador;
    }

    /**
     * Avisa si un correo ya tiene cuenta, para no hacer completar todo el registro
     * y fallar al final.
     *
     * Va con límite de frecuencia por IP: el dato ya se filtraba al enviar el
     * registro, pero sin límite alguien podría enumerar los correos de la
     * plataforma a gran escala. Ver {@link LimitadorConsultas}.
     */
    @GetMapping("/existe")
    public Map<String, Boolean> existe(@RequestParam String email, HttpServletRequest request) {
        limitador.registrarUso("existe:" + request.getRemoteAddr());
        return Map.of("existe", authService.existeEmail(email));
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /**
     * Sin límite, alguien podía probar contraseñas contra una cuenta sin parar. Hay
     * dos topes: por cuenta <i>desde una IP</i>, que frena a quien ataca a una
     * persona, y por IP, más holgado, que frena a una máquina que recorre muchas
     * cuentas. Holgado porque un aula entera sale a internet con la misma IP.
     *
     * <p>El primero no es solo "por cuenta" a propósito: así, cualquiera que supiera
     * un correo podía dejar a esa persona sin entrar mandando intentos fallidos.
     * Ahora el que queda frenado es el que prueba, no la cuenta.
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String mensaje = "Demasiados intentos de ingreso. Esperá un minuto y probá de nuevo.";
        String ip = http.getRemoteAddr();
        limitador.registrarUso("login-ip:" + ip, INTENTOS_LOGIN_POR_IP, mensaje);
        limitador.registrarUso("login:" + User.normalizarEmail(request.email()) + "|" + ip,
            INTENTOS_LOGIN_POR_CUENTA, mensaje);
        return authService.login(request);
    }

    @GetMapping("/me")
    public PerfilDto me(Authentication authentication) {
        return perfilService.perfil(authentication);
    }

    /** "¿Das asesorías privadas, fuera de una clase?" Solo el asesor. */
    @PutMapping("/me/asesorias-privadas")
    public PerfilDto asesoriasPrivadas(
            @Valid @RequestBody AsesoriasPrivadasRequest request, Authentication authentication) {
        return perfilService.cambiarAsesoriasPrivadas(request.valor(), authentication);
    }
}
