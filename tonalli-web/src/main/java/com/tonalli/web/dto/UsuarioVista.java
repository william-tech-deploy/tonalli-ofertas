package com.tonalli.web.dto;

import com.tonalli.web.model.Rol;

/** Datos de un usuario para el panel de administración. No incluye el hash de la contraseña. */
public class UsuarioVista {

    private final Long id;
    private final String nombre;
    private final String correo;
    private final Rol rol;
    private final boolean verificado;

    public UsuarioVista(Long id, String nombre, String correo, Rol rol, boolean verificado) {
        this.id = id;
        this.nombre = nombre;
        this.correo = correo;
        this.rol = rol;
        this.verificado = verificado;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getCorreo() { return correo; }
    public Rol getRol() { return rol; }
    public boolean isVerificado() { return verificado; }
}
