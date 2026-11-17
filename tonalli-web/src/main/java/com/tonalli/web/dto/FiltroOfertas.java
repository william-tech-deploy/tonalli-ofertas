package com.tonalli.web.dto;

import java.math.BigDecimal;

/**
 * Todo lo que el visitante eligió en el buscador. Es un objeto de entrada: el servicio lo normaliza antes
 * de consultar, así ni el controlador de páginas ni el de la API repiten esa limpieza.
 *
 * Los precios llegan en dólares (como los escribe la persona) y se convierten a centavos enteros, que es
 * como se guardan, justo antes de consultar.
 */
public class FiltroOfertas {

    private String q;
    private Long categoriaId;
    private BigDecimal precioMin;
    private BigDecimal precioMax;
    private boolean soloConDescuento;
    private TemperaturaFiltro temperatura = TemperaturaFiltro.TODAS;
    private OrdenOferta orden = OrdenOferta.RECIENTES;
    private int pagina;

    public String getQ() { return q; }
    public void setQ(String q) { this.q = q; }

    public Long getCategoriaId() { return categoriaId; }
    public void setCategoriaId(Long categoriaId) { this.categoriaId = categoriaId; }

    public BigDecimal getPrecioMin() { return precioMin; }
    public void setPrecioMin(BigDecimal precioMin) { this.precioMin = precioMin; }

    public BigDecimal getPrecioMax() { return precioMax; }
    public void setPrecioMax(BigDecimal precioMax) { this.precioMax = precioMax; }

    public boolean isSoloConDescuento() { return soloConDescuento; }
    public void setSoloConDescuento(boolean soloConDescuento) { this.soloConDescuento = soloConDescuento; }

    public TemperaturaFiltro getTemperatura() { return temperatura; }
    public void setTemperatura(TemperaturaFiltro temperatura) {
        this.temperatura = temperatura == null ? TemperaturaFiltro.TODAS : temperatura;
    }

    public OrdenOferta getOrden() { return orden; }
    public void setOrden(OrdenOferta orden) { this.orden = orden == null ? OrdenOferta.RECIENTES : orden; }

    public int getPagina() { return pagina; }
    public void setPagina(int pagina) { this.pagina = pagina; }

    /** Para que la página pueda ofrecer «Quitar filtros» solo cuando hay alguno puesto. */
    public boolean isHayFiltros() {
        return (q != null && !q.isBlank()) || categoriaId != null || precioMin != null || precioMax != null
                || soloConDescuento || temperatura != TemperaturaFiltro.TODAS || orden != OrdenOferta.RECIENTES;
    }
}
