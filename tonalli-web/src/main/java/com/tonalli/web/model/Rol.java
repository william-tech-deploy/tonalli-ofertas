package com.tonalli.web.model;

/** Roles de la aplicación. Spring Security los usa como autoridad "ROLE_" + nombre. */
public enum Rol {

    USER("Usuario"),
    MODERADOR("Moderador"),
    ADMIN("Administrador");

    private final String etiqueta;

    Rol(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /**
     * Nombre en español, para las pantallas y los gráficos. El nombre de la constante no se traduce
     * porque Spring Security construye la autoridad como "ROLE_" + name() y la base de datos guarda ese
     * mismo texto: cambiarlo obligaría a migrar la columna y a reescribir las reglas de SecurityConfig.
     */
    public String getEtiqueta() {
        return etiqueta;
    }
}
