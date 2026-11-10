package com.tonalli.web.repository;

import com.tonalli.web.model.EstadoOferta;
import com.tonalli.web.model.Oferta;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfertaRepository extends JpaRepository<Oferta, Long> {

    /** El EntityGraph trae categoría y autor en la misma consulta y evita el problema N+1. */
    @EntityGraph(attributePaths = {"categoria", "autor"})
    @Query("select o from Oferta o where o.estado = :estado "
            + "and lower(o.titulo) like lower(concat('%', :q, '%'))")
    Page<Oferta> buscar(@Param("estado") EstadoOferta estado, @Param("q") String q, Pageable pageable);

    @EntityGraph(attributePaths = {"categoria", "autor"})
    @Query("select o from Oferta o where o.estado = :estado and o.categoria.id = :categoriaId "
            + "and lower(o.titulo) like lower(concat('%', :q, '%'))")
    Page<Oferta> buscarPorCategoria(@Param("estado") EstadoOferta estado,
                                    @Param("categoriaId") Long categoriaId,
                                    @Param("q") String q,
                                    Pageable pageable);

    @EntityGraph(attributePaths = {"categoria", "autor"})
    @Query("select o from Oferta o where o.id = :id")
    Optional<Oferta> cargarConDetalle(@Param("id") Long id);

    @EntityGraph(attributePaths = {"categoria", "autor"})
    List<Oferta> findByEstadoOrderByCreadaEnDesc(EstadoOferta estado);

    @EntityGraph(attributePaths = {"categoria", "autor"})
    @Query("select o from Oferta o where o.estado = :estado "
            + "and lower(o.titulo) like lower(concat('%', :q, '%')) and o.precioCentavos <= :maximo "
            + "order by o.creadaEn desc")
    List<Oferta> coincidencias(@Param("estado") EstadoOferta estado,
                               @Param("q") String q,
                               @Param("maximo") int maximo,
                               Pageable pageable);

    long countByAutorIdAndCreadaEnAfter(Long autorId, Instant desde);

    boolean existsByCategoriaId(Long categoriaId);

    @Query("select c.nombre, count(o) from Oferta o join o.categoria c "
            + "where o.estado = :estado group by c.nombre order by c.nombre")
    List<Object[]> contarPorCategoria(@Param("estado") EstadoOferta estado);
}
