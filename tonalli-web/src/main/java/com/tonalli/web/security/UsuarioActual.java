package com.tonalli.web.security;

import com.tonalli.web.model.Rol;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Usuario autenticado que vive en la sesión. Lleva el id para no consultar la base en cada petición.
 *
 * Nota de diseño: "credenciales no vencidas" se usa para representar "correo verificado". Spring Security
 * evalúa esa condición DESPUÉS de comprobar la contraseña, así que solo quien conoce la contraseña
 * correcta se entera de que le falta verificar el correo (no se filtra qué cuentas existen).
 */
public class UsuarioActual implements UserDetails, CredentialsContainer {

    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String correo;
    private final String nombre;
    private String passwordHash;
    private final Rol rol;
    private final boolean correoVerificado;

    public UsuarioActual(Long id, String correo, String nombre, String passwordHash, Rol rol, boolean correoVerificado) {
        this.id = id;
        this.correo = correo;
        this.nombre = nombre;
        this.passwordHash = passwordHash;
        this.rol = rol;
        this.correoVerificado = correoVerificado;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public Rol getRol() { return rol; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()));
    }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public String getUsername() { return correo; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return correoVerificado; }

    @Override
    public boolean isEnabled() { return true; }

    /** Spring llama a este método al terminar el login: el hash no debe quedarse en la sesión. */
    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof UsuarioActual u && Objects.equals(id, u.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
