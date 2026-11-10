package com.tonalli.web.repository;

import com.tonalli.web.model.EstadoPropuesta;
import com.tonalli.web.model.Propuesta;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropuestaRepository extends JpaRepository<Propuesta, Long> {

    @EntityGraph(attributePaths = "autor")
    List<Propuesta> findByEstadoOrderByCreadaEnDesc(EstadoPropuesta estado);

    long countByAutorIdAndEstado(Long autorId, EstadoPropuesta estado);
}
