package com.tonalli.web.service;

import com.tonalli.web.dto.Formato;
import com.tonalli.web.dto.OfertaVista;
import com.tonalli.web.dto.ReporteVista;
import com.tonalli.web.exception.OperacionInvalidaException;
import com.tonalli.web.exception.RecursoNoEncontradoException;
import com.tonalli.web.model.EstadoOferta;
import com.tonalli.web.model.EstadoReporte;
import com.tonalli.web.model.Oferta;
import com.tonalli.web.model.Reporte;
import com.tonalli.web.repository.OfertaRepository;
import com.tonalli.web.repository.ReporteRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cola de reportes y acciones de moderación. Cada acción queda en el registro de auditoría. */
@Service
public class ModeracionService {

    private static final Logger AUDITORIA = LoggerFactory.getLogger("AUDITORIA");

    private final ReporteRepository reportes;
    private final OfertaRepository ofertas;

    public ModeracionService(ReporteRepository reportes, OfertaRepository ofertas) {
        this.reportes = reportes;
        this.ofertas = ofertas;
    }

    @Transactional(readOnly = true)
    public List<ReporteVista> reportesAbiertos() {
        return reportes.findByEstadoOrderByCreadoEnAsc(EstadoReporte.ABIERTO).stream()
                .map(r -> new ReporteVista(r.getId(), r.getOferta().getId(), r.getOferta().getTitulo(),
                        r.getOferta().getEstado(), r.getMotivo().getEtiqueta(), r.getReportante().getNombre(),
                        Formato.fecha(r.getCreadoEn())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OfertaVista> ofertasOcultas() {
        return ofertas.findByEstadoOrderByCreadaEnDesc(EstadoOferta.OCULTA).stream()
                .map(o -> OfertaService.vista(o, 0L))
                .toList();
    }

    /** Resuelve un reporte: oculta la oferta (ocultar = true) o descarta el reporte (ocultar = false). */
    @Transactional
    public void resolverReporte(Long moderadorId, Long reporteId, boolean ocultar) {
        Reporte reporte = reportes.findById(reporteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Reporte no encontrado"));
        if (reporte.getEstado() != EstadoReporte.ABIERTO) {
            throw new OperacionInvalidaException("Ese reporte ya fue resuelto.");
        }
        if (ocultar) {
            Oferta oferta = reporte.getOferta();
            if (oferta.getEstado() == EstadoOferta.ACTIVA) {
                oferta.setEstado(EstadoOferta.OCULTA);
            }
            reporte.setEstado(EstadoReporte.RESUELTO);
        } else {
            reporte.setEstado(EstadoReporte.DESCARTADO);
        }
        AUDITORIA.info("moderador={} reporte={} accion={}", moderadorId, reporteId, ocultar ? "OCULTAR" : "DESCARTAR");
    }

    /** Restaura (ACTIVA) o elimina (ELIMINADA) una oferta que está oculta. */
    @Transactional
    public void cambiarEstadoOferta(Long moderadorId, Long ofertaId, EstadoOferta nuevo) {
        if (nuevo != EstadoOferta.ACTIVA && nuevo != EstadoOferta.ELIMINADA) {
            throw new OperacionInvalidaException("Acción no permitida.");
        }
        Oferta oferta = ofertas.findById(ofertaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Oferta no encontrada"));
        if (oferta.getEstado() != EstadoOferta.OCULTA) {
            throw new OperacionInvalidaException("Solo se pueden restaurar o eliminar ofertas ocultas.");
        }
        oferta.setEstado(nuevo);
        AUDITORIA.info("moderador={} oferta={} accion={}", moderadorId, ofertaId, nuevo);
    }
}
