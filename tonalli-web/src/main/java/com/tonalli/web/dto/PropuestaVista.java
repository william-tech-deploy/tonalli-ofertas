package com.tonalli.web.dto;

public class PropuestaVista {

    private final Long id;
    private final String nombre;
    private final String autor;
    private final int apoyos;
    private final boolean yaApoyada;

    public PropuestaVista(Long id, String nombre, String autor, int apoyos, boolean yaApoyada) {
        this.id = id;
        this.nombre = nombre;
        this.autor = autor;
        this.apoyos = apoyos;
        this.yaApoyada = yaApoyada;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getAutor() { return autor; }
    public int getApoyos() { return apoyos; }
    public boolean isYaApoyada() { return yaApoyada; }
}
