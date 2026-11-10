package com.tonalli.web.dto;

import com.tonalli.web.model.EstadoOferta;
import java.math.BigDecimal;

/**
 * Lo que ven las vistas y la API de una oferta. Es una clase con getters (no un record) porque
 * Jakarta EL 5 de Tomcat 10.1 lee propiedades por getters en las JSP.
 * Nunca se exponen las entidades directamente: así no se filtran campos internos ni hay carga perezosa en la vista.
 */
public class OfertaVista {

    private final Long id;
    private final String titulo;
    private final String tienda;
    private final String categoria;
    private final String autor;
    private final Long autorId;
    private final BigDecimal precio;
    private final BigDecimal precioAnterior;
    private final Integer descuento;
    private final long temperatura;
    private final String creada;
    private final EstadoOferta estado;
    private final String enlace;

    public OfertaVista(Long id, String titulo, String tienda, String categoria, String autor, Long autorId,
                       int precioCentavos, Integer precioAnteriorCentavos, long temperatura,
                       String creada, EstadoOferta estado) {
        this(id, titulo, tienda, categoria, autor, autorId, precioCentavos, precioAnteriorCentavos, temperatura,
                creada, estado, null);
    }

    public OfertaVista(Long id, String titulo, String tienda, String categoria, String autor, Long autorId,
                       int precioCentavos, Integer precioAnteriorCentavos, long temperatura,
                       String creada, EstadoOferta estado, String enlace) {
        this.id = id;
        this.titulo = titulo;
        this.tienda = tienda;
        this.categoria = categoria;
        this.autor = autor;
        this.autorId = autorId;
        this.precio = Formato.dolares(precioCentavos);
        this.precioAnterior = precioAnteriorCentavos == null ? null : Formato.dolares(precioAnteriorCentavos);
        if (precioAnteriorCentavos != null && precioAnteriorCentavos > precioCentavos) {
            this.descuento = (int) Math.round(100.0 * (precioAnteriorCentavos - precioCentavos) / precioAnteriorCentavos);
        } else {
            this.descuento = null;
        }
        this.temperatura = temperatura;
        this.creada = creada;
        this.estado = estado;
        this.enlace = enlace;
    }

    public Long getId() { return id; }
    public String getTitulo() { return titulo; }
    public String getTienda() { return tienda; }
    public String getCategoria() { return categoria; }
    public String getAutor() { return autor; }
    public Long getAutorId() { return autorId; }
    public BigDecimal getPrecio() { return precio; }
    public BigDecimal getPrecioAnterior() { return precioAnterior; }
    public Integer getDescuento() { return descuento; }
    public long getTemperatura() { return temperatura; }
    public String getCreada() { return creada; }
    public EstadoOferta getEstado() { return estado; }
    public String getEnlace() { return enlace; }
}
