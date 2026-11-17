package com.tonalli.web.dto;

import com.tonalli.web.model.Rol;

/**
 * Datos que el usuario ve de sí mismo en "Mi cuenta". Es un objeto de vista, no la entidad:
 * así nunca llegan a la página el hash de la contraseña ni el token de verificación.
 */
public class PerfilVista {

    private final String nombre;
    private final String correo;
    private final Rol rol;
    private final int karma;
    private final int pesoVoto;
    private final String miembroDesde;

    public PerfilVista(String nombre, String correo, Rol rol, int karma, int pesoVoto, String miembroDesde) {
        this.nombre = nombre;
        this.correo = correo;
        this.rol = rol;
        this.karma = karma;
        this.pesoVoto = pesoVoto;
        this.miembroDesde = miembroDesde;
    }

    public String getNombre() { return nombre; }
    public String getCorreo() { return correo; }
    public Rol getRol() { return rol; }
    public int getKarma() { return karma; }
    public int getPesoVoto() { return pesoVoto; }
    public String getMiembroDesde() { return miembroDesde; }

    /** Nombre del rol para mostrar, en español. */
    public String getRolTexto() {
        return switch (rol) {
            case ADMIN -> "Administrador";
            case MODERADOR -> "Moderador";
            case USER -> "Usuario";
        };
    }
}
