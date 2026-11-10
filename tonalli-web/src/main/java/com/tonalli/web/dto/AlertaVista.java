package com.tonalli.web.dto;

import java.math.BigDecimal;
import java.util.List;

/** Una alerta con las ofertas activas que coinciden con ella. */
public class AlertaVista {

    private final Long id;
    private final String palabraClave;
    private final BigDecimal precioMax;
    private final List<OfertaVista> coincidencias;

    public AlertaVista(Long id, String palabraClave, BigDecimal precioMax, List<OfertaVista> coincidencias) {
        this.id = id;
        this.palabraClave = palabraClave;
        this.precioMax = precioMax;
        this.coincidencias = coincidencias;
    }

    public Long getId() { return id; }
    public String getPalabraClave() { return palabraClave; }
    public BigDecimal getPrecioMax() { return precioMax; }
    public List<OfertaVista> getCoincidencias() { return coincidencias; }
}
