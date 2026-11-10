package com.tonalli.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Sirve para crear una categoría (administrador) y para proponerla (usuario). */
public class NombreCategoriaForm {

    @NotBlank(message = "Escribe un nombre.")
    @Size(min = 2, max = 40, message = "El nombre debe tener entre 2 y 40 caracteres.")
    @Pattern(regexp = "^[\\p{L}0-9][\\p{L}0-9 .&-]*$",
            message = "Usa solo letras, números, espacios, punto, & o guion.")
    private String nombre;

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
}
