package com.tonalli.web.repository;

import com.tonalli.web.model.EstadoReporte;
import com.tonalli.web.model.Reporte;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReporteRepository extends JpaRepository<Reporte, Long> {

    @EntityGraph(attributePaths = {"oferta", "reportante"})
    List<Reporte> findByEstadoOrderByCreadoEnAsc(EstadoReporte estado);

    boolean existsByReportanteIdAndOfertaId(Long reportanteId, Long ofertaId);

    @Query("select r.motivo, count(r) from Reporte r group by r.motivo")
    List<Object[]> contarPorMotivo();
}
