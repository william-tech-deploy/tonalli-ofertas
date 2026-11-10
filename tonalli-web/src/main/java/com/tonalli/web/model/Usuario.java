package com.tonalli.web.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * Usuario del sistema. La contraseña nunca se guarda: solo su hash BCrypt.
 * El token de verificación de correo tampoco se guarda en claro: solo su SHA-256.
 */
@Entity
@Table(name = "usuarios",
        uniqueConstraints = @UniqueConstraint(name = "uk_usuarios_correo", columnNames = "correo"))
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String correo;

    @Column(nullable = false, length = 60)
    private String nombre;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol = Rol.USER;

    @Column(nullable = false)
    private int karma = 0;

    @Column(nullable = false)
    private boolean verificado = false;

    @Column(name = "token_verificacion_hash", length = 64)
    private String tokenVerificacionHash;

    @Column(name = "token_expira")
    private Instant tokenExpira;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn = Instant.now();

    public Long getId() { return id; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }
    public int getKarma() { return karma; }
    public void setKarma(int karma) { this.karma = karma; }
    public boolean isVerificado() { return verificado; }
    public void setVerificado(boolean verificado) { this.verificado = verificado; }
    public String getTokenVerificacionHash() { return tokenVerificacionHash; }
    public void setTokenVerificacionHash(String tokenVerificacionHash) { this.tokenVerificacionHash = tokenVerificacionHash; }
    public Instant getTokenExpira() { return tokenExpira; }
    public void setTokenExpira(Instant tokenExpira) { this.tokenExpira = tokenExpira; }
    public Instant getCreadoEn() { return creadoEn; }
}
