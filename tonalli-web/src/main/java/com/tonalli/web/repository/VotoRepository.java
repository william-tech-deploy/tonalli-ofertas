package com.tonalli.web.repository;

import com.tonalli.web.model.Voto;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VotoRepository extends JpaRepository<Voto, Long> {

    Optional<Voto> findByUsuarioIdAndOfertaId(Long usuarioId, Long ofertaId);

    /** Temperatura de una oferta: suma de valor × peso. Devuelve null si todavía no tiene votos. */
    @Query("select sum(v.valor * v.peso) from Voto v where v.oferta.id = :ofertaId")
    Long temperatura(@Param("ofertaId") Long ofertaId);

    /** Temperaturas de varias ofertas en una sola consulta: [ofertaId, suma]. */
    @Query("select v.oferta.id, sum(v.valor * v.peso) from Voto v "
            + "where v.oferta.id in :ids group by v.oferta.id")
    List<Object[]> temperaturas(@Param("ids") Collection<Long> ids);
}
