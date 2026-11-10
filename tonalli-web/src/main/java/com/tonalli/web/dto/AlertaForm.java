package com.tonalli.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class AlertaForm {

    @NotBlank(message = "Escribe una palabra clave.")
    @Size(min = 2, max = 60, message = "La palabra clave debe tener entre 2 y 60 caracteres.")
    private String palabraClave;

    @DecimalMin(value = "0.01", message = "El precio máximo debe ser mayor que cero.")
    @DecimalMax(value = "9999999.99", message = "El precio máximo es demasiado alto.")
    @Digits(integer = 7, fraction = 2, message = "Usa como máximo dos decimales.")
    private BigDecimal precioMax;

    public String getPalabraClave() { return palabraClave; }
    public void setPalabraClave(String palabraClave) { this.palabraClave = palabraClave; }
    public BigDecimal getPrecioMax() { return precioMax; }
    public void setPrecioMax(BigDecimal precioMax) { this.precioMax = precioMax; }
}
