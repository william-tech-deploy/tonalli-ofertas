package com.tonalli.web.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/** Reporte de una oferta. Cada usuario puede reportar una oferta una sola vez. */
@Entity
@Table(name = "reportes",
        uniqueConstraints = @UniqueConstraint(name = "uk_reportes_reportante_oferta", columnNames = {"reportante_id", "oferta_id"}))
public class Reporte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "oferta_id", nullable = false)
    private Oferta oferta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reportante_id", nullable = false)
    private Usuario reportante;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MotivoReporte motivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoReporte estado = EstadoReporte.ABIERTO;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn = Instant.now();

    public Long getId() { return id; }
    public Oferta getOferta() { return oferta; }
    public void setOferta(Oferta oferta) { this.oferta = oferta; }
    public Usuario getReportante() { return reportante; }
    public void setReportante(Usuario reportante) { this.reportante = reportante; }
    public MotivoReporte getMotivo() { return motivo; }
    public void setMotivo(MotivoReporte motivo) { this.motivo = motivo; }
    public EstadoReporte getEstado() { return estado; }
    public void setEstado(EstadoReporte estado) { this.estado = estado; }
    public Instant getCreadoEn() { return creadoEn; }
}
