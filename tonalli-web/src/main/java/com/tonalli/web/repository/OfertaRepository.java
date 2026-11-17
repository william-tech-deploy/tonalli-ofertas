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

    // ----- Informes y estadísticas (sección /admin/informes) -----

    /**
     * Instantes de creación de las ofertas del rango. Devuelve solo la fecha, no la entidad, y el
     * agrupado por mes se hace en Java: las funciones de fecha de JPQL se traducen distinto en H2
     * (pruebas y dev) y en MySQL (produccion), y así la misma consulta sirve en los dos motores sin
     * depender del dialecto. El rango acota cuántas filas vuelven; si el sitio creciera mucho, este
     * conteo se movería a una consulta agregada con la función de fecha propia del motor.
     */
    @Query("select o.creadaEn from Oferta o where o.estado <> :excluido "
            + "and o.creadaEn >= :desde and o.creadaEn < :hasta")
    List<Instant> fechasDeCreacion(@Param("excluido") EstadoOferta excluido,
                                   @Param("desde") Instant desde,
                                   @Param("hasta") Instant hasta);

    /** Temperatura (suma de valor × peso) de cada oferta de un estado, incluidas las que no tienen votos (0). */
    @Query("select (select coalesce(sum(v.valor * v.peso), 0) from Voto v where v.oferta = o) "
            + "from Oferta o where o.estado = :estado")
    List<Long> temperaturas(@Param("estado") EstadoOferta estado);

    /** Descuento medio en porcentaje de las ofertas que declaran precio anterior. null si ninguna lo declara. */
    @Query("select avg((o.precioAnteriorCentavos - o.precioCentavos) * 100.0 / o.precioAnteriorCentavos) "
            + "from Oferta o where o.estado = :estado and o.precioAnteriorCentavos is not null")
    Double descuentoPromedio(@Param("estado") EstadoOferta estado);

    /** Precio medio en centavos de las ofertas de un estado. null si no hay ninguna. */
    @Query("select avg(o.precioCentavos) from Oferta o where o.estado = :estado")
    Double precioPromedio(@Param("estado") EstadoOferta estado);

    long countByEstadoAndPrecioAnteriorCentavosIsNotNull(EstadoOferta estado);

    long countByEstadoNotAndCreadaEnAfter(EstadoOferta estado, Instant desde);

    /** Descuento medio por categoría: [nombre, porcentaje]. Solo categorías con al menos una oferta con descuento. */
    @Query("select c.nombre, avg((o.precioAnteriorCentavos - o.precioCentavos) * 100.0 / o.precioAnteriorCentavos) "
            + "from Oferta o join o.categoria c "
            + "where o.estado = :estado and o.precioAnteriorCentavos is not null "
            + "group by c.nombre order by c.nombre")
    List<Object[]> descuentoPromedioPorCategoria(@Param("estado") EstadoOferta estado);

    /**
     * Filas del informe de ofertas. Es una proyección explícita: trae solo las diez columnas que salen
     * al CSV en vez de la entidad completa, así el informe no carga en memoria datos que no publica.
     */
    @Query("select o.id, o.titulo, o.tienda, c.nombre, o.precioCentavos, o.precioAnteriorCentavos, "
            + "o.estado, u.nombre, o.creadaEn, "
            + "(select coalesce(sum(v.valor * v.peso), 0) from Voto v where v.oferta = o) "
            + "from Oferta o join o.categoria c join o.autor u "
            + "where o.creadaEn >= :desde and o.creadaEn < :hasta order by o.creadaEn desc")
    List<Object[]> filasInformeOfertas(@Param("desde") Instant desde,
                                       @Param("hasta") Instant hasta,
                                       Pageable pageable);

    @Query("select c.nombre, count(o) from Oferta o join o.categoria c "
            + "where o.estado = :estado group by c.nombre order by c.nombre")
    List<Object[]> contarPorCategoria(@Param("estado") EstadoOferta estado);
}
