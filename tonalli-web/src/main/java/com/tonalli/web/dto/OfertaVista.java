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
    private final Long imagenId;

    public OfertaVista(Long id, String titulo, String tienda, String categoria, String autor, Long autorId,
                       int precioCentavos, Integer precioAnteriorCentavos, long temperatura,
                       String creada, EstadoOferta estado) {
        this(id, titulo, tienda, categoria, autor, autorId, precioCentavos, precioAnteriorCentavos, temperatura,
                creada, estado, null, null);
    }

    public OfertaVista(Long id, String titulo, String tienda, String categoria, String autor, Long autorId,
                       int precioCentavos, Integer precioAnteriorCentavos, long temperatura,
                       String creada, EstadoOferta estado, String enlace, Long imagenId) {
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
        this.imagenId = imagenId;
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
    public Long getImagenId() { return imagenId; }
    public boolean isTieneFoto() { return imagenId != null; }

    /**
     * Ruta de la imagen que se muestra: la foto subida o, si no hay, la ilustración de su categoría.
     * Las categorías creadas por votación usan una ilustración genérica.
     */
    public String getImagenUrl() {
        if (imagenId != null) {
            return "/imagenes/" + imagenId;
        }
        String clave = java.text.Normalizer.normalize(categoria == null ? "" : categoria, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
        return switch (clave) {
            case "supermercado", "tecnologia", "ropa" -> "/img/categorias/" + clave + ".svg";
            default -> "/img/categorias/otra.svg";
        };
    }
}
