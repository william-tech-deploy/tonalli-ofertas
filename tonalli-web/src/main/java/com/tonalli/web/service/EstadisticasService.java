package com.tonalli.web.service;

import com.tonalli.web.model.EstadoOferta;
import com.tonalli.web.model.MotivoReporte;
import com.tonalli.web.repository.OfertaRepository;
import com.tonalli.web.repository.ReporteRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Datos agregados para los gráficos del administrador. */
@Service
public class EstadisticasService {

    private final OfertaRepository ofertas;
    private final ReporteRepository reportes;

    public EstadisticasService(OfertaRepository ofertas, ReporteRepository reportes) {
        this.ofertas = ofertas;
        this.reportes = reportes;
    }

    /** Ofertas activas por categoría: nombre -> cantidad. */
    @Transactional(readOnly = true)
    public Map<String, Long> ofertasPorCategoria() {
        Map<String, Long> resultado = new LinkedHashMap<>();
        for (Object[] fila : ofertas.contarPorCategoria(EstadoOferta.ACTIVA)) {
            resultado.put((String) fila[0], ((Number) fila[1]).longValue());
        }
        return resultado;
    }

    /** Reportes por motivo: etiqueta -> cantidad. Incluye los motivos sin reportes, con 0. */
    @Transactional(readOnly = true)
    public Map<String, Long> reportesPorMotivo() {
        Map<MotivoReporte, Long> cuenta = new LinkedHashMap<>();
        for (MotivoReporte m : MotivoReporte.values()) {
            cuenta.put(m, 0L);
        }
        for (Object[] fila : reportes.contarPorMotivo()) {
            cuenta.put((MotivoReporte) fila[0], ((Number) fila[1]).longValue());
        }
        Map<String, Long> resultado = new LinkedHashMap<>();
        cuenta.forEach((motivo, total) -> resultado.put(motivo.getEtiqueta(), total));
        return resultado;
    }
}
