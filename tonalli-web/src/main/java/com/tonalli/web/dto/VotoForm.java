package com.tonalli.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Cuerpo JSON del voto en la API: valor 1 (caliente) o -1 (frío). */
public class VotoForm {

    @Min(value = -1, message = "El valor debe ser -1 o 1.")
    @Max(value = 1, message = "El valor debe ser -1 o 1.")
    private int valor;

    public int getValor() { return valor; }
    public void setValor(int valor) { this.valor = valor; }
}
