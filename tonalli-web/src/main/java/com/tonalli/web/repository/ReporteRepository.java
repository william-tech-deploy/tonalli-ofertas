package com.tonalli.web.repository;

import com.tonalli.web.model.EstadoReporte;
import com.tonalli.web.model.Reporte;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReporteRepository extends JpaRepository<Reporte, Long> {

    @EntityGraph(attributePaths = {"oferta", "reportante"})
    List<Reporte> findByEstadoOrderByCreadoEnAsc(EstadoReporte estado);

    long countByEstado(EstadoReporte estado);

    boolean existsByReportanteIdAndOfertaId(Long reportanteId, Long ofertaId);

    // ----- Informes y estadísticas -----

    @Query("select r.estado, count(r) from Reporte r group by r.estado")
    List<Object[]> contarPorEstado();

    /**
     * Filas del informe de moderación. A propósito no incluye quién reportó: el informe mide el trabajo
     * de moderación, no señala usuarios, y menos datos personales dentro de un archivo descargable es
     * menos superficie de fuga (minimización de datos).
     */
    @Query("select r.id, o.titulo, r.motivo, r.estado, r.creadoEn from Reporte r join r.oferta o "
            + "where r.creadoEn >= :desde and r.creadoEn < :hasta order by r.creadoEn desc")
    List<Object[]> filasInformeModeracion(@Param("desde") Instant desde,
                                          @Param("hasta") Instant hasta,
                                          Pageable pageable);

    @Query("select r.motivo, count(r) from Reporte r group by r.motivo")
    List<Object[]> contarPorMotivo();
}
