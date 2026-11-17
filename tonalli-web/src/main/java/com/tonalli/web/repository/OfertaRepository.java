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
    /**
     * Búsqueda del catálogo con todos los filtros. Los opcionales se anulan con "(:x is null or ...)":
     * así una sola consulta sirve para cualquier combinación, siempre con parámetros (nunca SQL armado a mano).
     * El orden simple (fecha o precio) lo pone el Pageable; la temperatura y el descuento necesitan los
     * dos métodos de abajo, porque se calculan y no son columnas.
     */
    @EntityGraph(attributePaths = {"categoria", "autor"})
    @Query("""
            select o from Oferta o where o.estado = :estado
            and lower(o.titulo) like lower(concat('%', :q, '%'))
            and (:categoriaId is null or o.categoria.id = :categoriaId)
            and o.precioCentavos >= :precioMin
            and o.precioCentavos <= :precioMax
            and (:conDescuento is null or o.precioAnteriorCentavos is not null)
            and (:tempMin is null or (select coalesce(sum(v.valor * v.peso), 0) from Voto v where v.oferta = o) >= :tempMin)
            and (:tempMax is null or (select coalesce(sum(v.valor * v.peso), 0) from Voto v where v.oferta = o) <= :tempMax)
            """)
    Page<Oferta> buscarConFiltros(@Param("estado") EstadoOferta estado,
                                @Param("q") String q,
                                @Param("categoriaId") Long categoriaId,
                                @Param("precioMin") int precioMin,
                                @Param("precioMax") int precioMax,
                                @Param("conDescuento") Boolean conDescuento,
                                @Param("tempMin") Long tempMin,
                                @Param("tempMax") Long tempMax,
                                Pageable pageable);

    /** Igual que el anterior, ordenado por temperatura: la suma de valor × peso de sus votos. */
    @EntityGraph(attributePaths = {"categoria", "autor"})
    @Query("""
            select o from Oferta o where o.estado = :estado
            and lower(o.titulo) like lower(concat('%', :q, '%'))
            and (:categoriaId is null or o.categoria.id = :categoriaId)
            and o.precioCentavos >= :precioMin
            and o.precioCentavos <= :precioMax
            and (:conDescuento is null or o.precioAnteriorCentavos is not null)
            and (:tempMin is null or (select coalesce(sum(v.valor * v.peso), 0) from Voto v where v.oferta = o) >= :tempMin)
            and (:tempMax is null or (select coalesce(sum(v.valor * v.peso), 0) from Voto v where v.oferta = o) <= :tempMax)
            order by (select coalesce(sum(v.valor * v.peso), 0) from Voto v where v.oferta = o) desc, o.creadaEn desc
            """)
    Page<Oferta> buscarPorTemperatura(@Param("estado") EstadoOferta estado,
                                @Param("q") String q,
                                @Param("categoriaId") Long categoriaId,
                                @Param("precioMin") int precioMin,
                                @Param("precioMax") int precioMax,
                                @Param("conDescuento") Boolean conDescuento,
                                @Param("tempMin") Long tempMin,
                                @Param("tempMax") Long tempMax,
                                Pageable pageable);

    /**
     * Igual, ordenado por porcentaje de descuento. Las ofertas sin precio anterior valen -1 y quedan al final.
     * La validación exige precio anterior mayor que el actual, así que nunca se divide entre cero.
     */
    @EntityGraph(attributePaths = {"categoria", "autor"})
    @Query("""
            select o from Oferta o where o.estado = :estado
            and lower(o.titulo) like lower(concat('%', :q, '%'))
            and (:categoriaId is null or o.categoria.id = :categoriaId)
            and o.precioCentavos >= :precioMin
            and o.precioCentavos <= :precioMax
            and (:conDescuento is null or o.precioAnteriorCentavos is not null)
            and (:tempMin is null or (select coalesce(sum(v.valor * v.peso), 0) from Voto v where v.oferta = o) >= :tempMin)
            and (:tempMax is null or (select coalesce(sum(v.valor * v.peso), 0) from Voto v where v.oferta = o) <= :tempMax)
            order by coalesce((o.precioAnteriorCentavos - o.precioCentavos) * 100.0 / o.precioAnteriorCentavos, -1) desc,
                     o.creadaEn desc
            """)
    Page<Oferta> buscarPorDescuento(@Param("estado") EstadoOferta estado,
                                @Param("q") String q,
                                @Param("categoriaId") Long categoriaId,
                                @Param("precioMin") int precioMin,
                                @Param("precioMax") int precioMax,
                                @Param("conDescuento") Boolean conDescuento,
                                @Param("tempMin") Long tempMin,
                                @Param("tempMax") Long tempMax,
                                Pageable pageable);

    long countByAutorIdAndCreadaEnAfter(Long autorId, Instant desde);

    /** Ofertas de un autor, sin las eliminadas, de la más nueva a la más antigua (panel "Mi cuenta"). */
    @EntityGraph(attributePaths = {"categoria", "autor"})
    List<Oferta> findByAutorIdAndEstadoNotOrderByCreadaEnDesc(Long autorId, EstadoOferta estado, Pageable pageable);

    long countByEstado(EstadoOferta estado);

    boolean existsByCategoriaId(Long categoriaId);

    @Query("select c.nombre, count(o) from Oferta o join o.categoria c "
            + "where o.estado = :estado group by c.nombre order by c.nombre")
    List<Object[]> contarPorCategoria(@Param("estado") EstadoOferta estado);
}
