package com.tonalli.web.dto;

public class CategoriaVista {

    private final Long id;
    private final String nombre;
    private final boolean base;

    public CategoriaVista(Long id, String nombre, boolean base) {
        this.id = id;
        this.nombre = nombre;
        this.base = base;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public boolean isBase() { return base; }
}
