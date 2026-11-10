package com.tonalli.web.service;

import com.tonalli.web.dto.Formato;
import com.tonalli.web.dto.RegistroForm;
import com.tonalli.web.model.Usuario;
import com.tonalli.web.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);
    private static final Duration VIGENCIA_TOKEN = Duration.ofHours(24);
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificador;
    private final EmailService correos;

    public UsuarioService(UsuarioRepository usuarios, PasswordEncoder codificador, EmailService correos) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.correos = correos;
    }

    /**
     * Registra un usuario con rol USER. Si el correo ya existe no se avisa al visitante (se responde
     * igual que en un registro nuevo) para que nadie pueda averiguar qué correos tienen cuenta.
     *
     * No lleva @Transactional a propósito: cada llamada al repositorio ya es su propia transacción, así que
     * si dos registros simultáneos chocan por el correo único, el DataIntegrityViolationException se puede
     * atrapar aquí sin dejar una transacción marcada para deshacerse.
     */
    public void registrar(RegistroForm form) {
        String correo = form.getCorreo().trim().toLowerCase(Locale.ROOT);
        if (usuarios.existsByCorreo(correo)) {
            log.info("Intento de registro con un correo que ya existe");
            return;
        }
        String token = nuevoToken();
        Usuario u = new Usuario();
        u.setCorreo(correo);
        u.setNombre(Formato.limpiarTexto(form.getNombre()));
        u.setPasswordHash(codificador.encode(form.getPassword()));
        u.setTokenVerificacionHash(hash(token));
        u.setTokenExpira(Instant.now().plus(VIGENCIA_TOKEN));
        try {
            usuarios.saveAndFlush(u);
        } catch (DataIntegrityViolationException e) {
            log.info("Registro simultáneo con el mismo correo; se ignora el duplicado");
            return;
        }
        correos.enviarVerificacion(correo, u.getNombre(), token);
    }

    /** Valida el token del enlace. Es de un solo uso y vence a las 24 horas. */
    @Transactional
    public boolean verificar(String token) {
        if (token == null || token.length() < 20 || token.length() > 100) {
            return false;
        }
        Optional<Usuario> encontrado = usuarios.findByTokenVerificacionHash(hash(token));
        if (encontrado.isEmpty()) {
            return false;
        }
        Usuario u = encontrado.get();
        if (u.getTokenExpira() == null || Instant.now().isAfter(u.getTokenExpira())) {
            return false;
        }
        u.setVerificado(true);
        u.setTokenVerificacionHash(null);
        u.setTokenExpira(null);
        return true;
    }

    /** 32 bytes aleatorios criptográficamente seguros, en Base64 URL (43 caracteres). */
    static String nuevoToken() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 en hexadecimal: en la base solo se guarda este resumen, nunca el token. */
    static String hash(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
