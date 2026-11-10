package com.tonalli.web.dto;

import com.tonalli.web.model.EstadoOferta;

public class ReporteVista {

    private final Long id;
    private final Long ofertaId;
    private final String ofertaTitulo;
    private final EstadoOferta ofertaEstado;
    private final String motivo;
    private final String reportante;
    private final String creado;

    public ReporteVista(Long id, Long ofertaId, String ofertaTitulo, EstadoOferta ofertaEstado,
                        String motivo, String reportante, String creado) {
        this.id = id;
        this.ofertaId = ofertaId;
        this.ofertaTitulo = ofertaTitulo;
        this.ofertaEstado = ofertaEstado;
        this.motivo = motivo;
        this.reportante = reportante;
        this.creado = creado;
    }

    public Long getId() { return id; }
    public Long getOfertaId() { return ofertaId; }
    public String getOfertaTitulo() { return ofertaTitulo; }
    public EstadoOferta getOfertaEstado() { return ofertaEstado; }
    public String getMotivo() { return motivo; }
    public String getReportante() { return reportante; }
    public String getCreado() { return creado; }
}
