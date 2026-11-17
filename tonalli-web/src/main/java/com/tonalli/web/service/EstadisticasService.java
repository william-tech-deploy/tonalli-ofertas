package com.tonalli.web.service;

import com.tonalli.web.dto.TemperaturaFiltro;
import com.tonalli.web.model.EstadoOferta;
import com.tonalli.web.model.EstadoReporte;
import com.tonalli.web.model.MotivoReporte;
import com.tonalli.web.model.Rol;
import com.tonalli.web.repository.OfertaRepository;
import com.tonalli.web.repository.ReporteRepository;
import com.tonalli.web.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Datos agregados que alimentan los gráficos de la página de informes.
 *
 * <p>Qué devuelve y por qué: cada método entrega un {@code Map<String, Number>} de rótulo a valor, y nada
 * más. Ese formato es exactamente el JSON que espera {@code graficos.js}, así que ni el controlador ni la
 * vista tienen que transformar nada. Se usa {@link LinkedHashMap} y no un {@code HashMap} porque el orden
 * de las claves es el orden de las barras del gráfico: con un mapa sin orden, el mismo gráfico saldría
 * distinto en cada recarga.
 *
 * <p>Qué no devuelve: ningún identificador ni dato de una persona. Los gráficos son conteos, así que
 * aunque la URL de la API quedara expuesta por error, no habría nada personal que filtrar. El acceso lo
 * restringe {@code SecurityConfig} a rol ADMIN para {@code /api/estadisticas/**}.
 *
 * <p>Todos los métodos son {@code @Transactional(readOnly = true)}: le dice a Hibernate que no hace falta
 * vigilar cambios en las entidades (menos trabajo por consulta) y, en una base con réplicas de lectura,
 * permite que estas consultas no toquen el servidor principal.
 */
@Service
public class EstadisticasService {

    /** Cuántos meses muestra el gráfico de publicaciones. Un año entero deja ver la estacionalidad. */
    static final int MESES_DEL_GRAFICO = 12;

    /** Los tramos del catálogo, de frío a caliente. Los límites viven en el enum, no aquí. */
    private static final TemperaturaFiltro[] TRAMOS = {
            TemperaturaFiltro.FRIA, TemperaturaFiltro.TIBIA, TemperaturaFiltro.CALIENTE};

    /**
     * Abreviaturas de los meses, escritas a mano. Se evita {@code Month.getDisplayName} con un
     * {@code Locale} porque el texto que devuelve depende de la versión del JDK y de los datos de
     * idioma instalados en el servidor: el mismo gráfico podría decir «nov» en el equipo de desarrollo
     * y «Nov.» dentro del contenedor. Con el arreglo, el rótulo es el mismo en todas partes.
     */
    private static final String[] MESES = {
            "ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"};

    private static final ZoneId ZONA = ZoneId.of("America/El_Salvador");

    private final OfertaRepository ofertas;
    private final ReporteRepository reportes;
    private final UsuarioRepository usuarios;

    public EstadisticasService(OfertaRepository ofertas, ReporteRepository reportes, UsuarioRepository usuarios) {
        this.ofertas = ofertas;
        this.reportes = reportes;
        this.usuarios = usuarios;
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

    /** Reportes por estado: ABIERTO, RESUELTO, DESCARTADO. Los estados sin reportes salen con 0. */
    @Transactional(readOnly = true)
    public Map<String, Long> reportesPorEstado() {
        Map<EstadoReporte, Long> cuenta = new LinkedHashMap<>();
        for (EstadoReporte e : EstadoReporte.values()) {
            cuenta.put(e, 0L);
        }
        for (Object[] fila : reportes.contarPorEstado()) {
            cuenta.put((EstadoReporte) fila[0], ((Number) fila[1]).longValue());
        }
        Map<String, Long> resultado = new LinkedHashMap<>();
        cuenta.forEach((estado, total) -> resultado.put(capitalizar(estado.name()), total));
        return resultado;
    }

    /** Usuarios por rol. Los roles sin nadie salen con 0, para que el gráfico no cambie de forma. */
    @Transactional(readOnly = true)
    public Map<String, Long> usuariosPorRol() {
        Map<Rol, Long> cuenta = new LinkedHashMap<>();
        for (Rol r : Rol.values()) {
            cuenta.put(r, 0L);
        }
        for (Object[] fila : usuarios.contarPorRol()) {
            cuenta.put((Rol) fila[0], ((Number) fila[1]).longValue());
        }
        Map<String, Long> resultado = new LinkedHashMap<>();
        cuenta.forEach((rol, total) -> resultado.put(rol.getEtiqueta(), total));
        return resultado;
    }

    /**
     * Ofertas publicadas en cada uno de los últimos doce meses.
     *
     * <p>El mapa se rellena primero con los doce meses en cero y después se van sumando las fechas que
     * devuelve la consulta. Hacerlo así tiene dos ventajas sobre agrupar en la base de datos: un mes sin
     * ninguna oferta aparece igual (con un hueco, el gráfico mentiría sobre la tendencia) y la consulta
     * es JPQL puro, sin funciones de fecha, que es lo único que se comporta igual en H2 y en MySQL.
     *
     * <p>Las eliminadas no cuentan: son ofertas que un moderador borró y sumarlas inflaría la actividad.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> ofertasPorMes() {
        YearMonth mesActual = YearMonth.from(LocalDate.now(ZONA));
        YearMonth primerMes = mesActual.minusMonths(MESES_DEL_GRAFICO - 1L);

        Map<String, Long> resultado = new LinkedHashMap<>();
        for (int i = 0; i < MESES_DEL_GRAFICO; i++) {
            resultado.put(rotulo(primerMes.plusMonths(i)), 0L);
        }

        Instant desde = primerMes.atDay(1).atStartOfDay(ZONA).toInstant();
        Instant hasta = mesActual.plusMonths(1).atDay(1).atStartOfDay(ZONA).toInstant();
        for (Instant creada : ofertas.fechasDeCreacion(EstadoOferta.ELIMINADA, desde, hasta)) {
            // computeIfPresent y no merge: si una fecha cayera fuera del rango no debe inventar un mes.
            resultado.computeIfPresent(rotulo(YearMonth.from(creada.atZone(ZONA))), (mes, total) -> total + 1);
        }
        return resultado;
    }

    /**
     * Cuántas ofertas activas hay en cada tramo de temperatura (frías, tibias, calientes).
     *
     * <p>Es el gráfico que mide si el consenso de la comunidad funciona: si casi todo sale tibio, nadie
     * está votando; si hay muchas frías, la gente está detectando descuentos falsos, que es justo para
     * lo que existe el sitio.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> distribucionTemperatura() {
        Map<String, Long> resultado = new LinkedHashMap<>();
        for (TemperaturaFiltro tramo : TRAMOS) {
            resultado.put(tramo.getNombre(), 0L);
        }
        for (Long temperatura : ofertas.temperaturas(EstadoOferta.ACTIVA)) {
            long valor = temperatura == null ? 0L : temperatura;
            for (TemperaturaFiltro tramo : TRAMOS) {
                if (tramo.contiene(valor)) {
                    resultado.merge(tramo.getNombre(), 1L, Long::sum);
                    break;
                }
            }
        }
        return resultado;
    }

    /** Descuento medio por categoría, en porcentaje con un decimal. Solo cuenta ofertas con precio anterior. */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> descuentoPorCategoria() {
        Map<String, BigDecimal> resultado = new LinkedHashMap<>();
        for (Object[] fila : ofertas.descuentoPromedioPorCategoria(EstadoOferta.ACTIVA)) {
            double promedio = fila[1] == null ? 0 : ((Number) fila[1]).doubleValue();
            resultado.put((String) fila[0], BigDecimal.valueOf(promedio).setScale(1, RoundingMode.HALF_UP));
        }
        return resultado;
    }

    /** Los siete gráficos en un solo objeto, para el informe de resumen en CSV. */
    @Transactional(readOnly = true)
    public Map<String, Map<String, ? extends Number>> todos() {
        Map<String, Map<String, ? extends Number>> conjunto = new LinkedHashMap<>();
        conjunto.put("Ofertas publicadas por mes", ofertasPorMes());
        conjunto.put("Ofertas activas por categoría", ofertasPorCategoria());
        conjunto.put("Temperatura de las ofertas activas", distribucionTemperatura());
        conjunto.put("Descuento promedio por categoría (%)", descuentoPorCategoria());
        conjunto.put("Usuarios por rol", usuariosPorRol());
        conjunto.put("Reportes por motivo", reportesPorMotivo());
        conjunto.put("Reportes por estado", reportesPorEstado());
        return conjunto;
    }

    /** "nov 2026". Se arma con el arreglo MESES para que el texto no dependa del idioma del servidor. */
    private static String rotulo(YearMonth mes) {
        return MESES[mes.getMonthValue() - 1] + " " + mes.getYear();
    }

    /** ABIERTO -> Abierto. Los enum se escriben en mayúsculas y en un gráfico eso grita. */
    private static String capitalizar(String nombre) {
        return nombre.charAt(0) + nombre.substring(1).toLowerCase(Locale.ROOT);
    }
}
