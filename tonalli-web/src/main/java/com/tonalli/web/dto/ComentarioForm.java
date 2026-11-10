package com.tonalli.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ComentarioForm {

    @NotBlank(message = "Escribe un comentario.")
    @Size(max = 500, message = "El comentario admite hasta 500 caracteres.")
    private String texto;

    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
}
