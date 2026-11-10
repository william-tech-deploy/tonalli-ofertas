package com.tonalli.web.dto;

public class ComentarioVista {

    private final String autor;
    private final String texto;
    private final String creado;

    public ComentarioVista(String autor, String texto, String creado) {
        this.autor = autor;
        this.texto = texto;
        this.creado = creado;
    }

    public String getAutor() { return autor; }
    public String getTexto() { return texto; }
    public String getCreado() { return creado; }
}
