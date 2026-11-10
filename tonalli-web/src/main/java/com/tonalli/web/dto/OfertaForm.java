package com.tonalli.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Datos que el usuario puede enviar al publicar una oferta. No incluye autor, estado ni fecha:
 * esos los decide el servidor (evita la asignación masiva de campos).
 */
public class OfertaForm {

    @NotBlank(message = "Escribe un título.")
    @Size(max = 120, message = "El título admite hasta 120 caracteres.")
    private String titulo;

    @NotBlank(message = "Escribe la tienda.")
    @Size(max = 80, message = "La tienda admite hasta 80 caracteres.")
    private String tienda;

    @NotNull(message = "Escribe el precio.")
    @DecimalMin(value = "0.01", message = "El precio debe ser mayor que cero.")
    @DecimalMax(value = "9999999.99", message = "El precio es demasiado alto.")
    @Digits(integer = 7, fraction = 2, message = "Usa como máximo dos decimales.")
    private BigDecimal precio;

    @DecimalMin(value = "0.01", message = "El precio anterior debe ser mayor que cero.")
    @DecimalMax(value = "9999999.99", message = "El precio anterior es demasiado alto.")
    @Digits(integer = 7, fraction = 2, message = "Usa como máximo dos decimales.")
    private BigDecimal precioAnterior;

    @NotNull(message = "Elige una categoría.")
    private Long categoriaId;

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getTienda() { return tienda; }
    public void setTienda(String tienda) { this.tienda = tienda; }
    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }
    public BigDecimal getPrecioAnterior() { return precioAnterior; }
    public void setPrecioAnterior(BigDecimal precioAnterior) { this.precioAnterior = precioAnterior; }
    public Long getCategoriaId() { return categoriaId; }
    public void setCategoriaId(Long categoriaId) { this.categoriaId = categoriaId; }
}
