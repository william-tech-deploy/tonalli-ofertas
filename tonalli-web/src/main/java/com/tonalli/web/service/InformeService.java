package com.tonalli.web.service;

import com.tonalli.web.dto.ArchivoDescarga;
import com.tonalli.web.dto.EstadisticasGenerales;
import com.tonalli.web.dto.RangoFechas;
import com.tonalli.web.dto.TipoInforme;
import com.tonalli.web.model.EstadoOferta;
import com.tonalli.web.model.EstadoReporte;
import com.tonalli.web.model.MotivoReporte;
import com.tonalli.web.model.Rol;
import com.tonalli.web.repository.ComentarioRepository;
import com.tonalli.web.repository.OfertaRepository;
import com.tonalli.web.repository.ReporteRepository;
import com.tonalli.web.repository.UsuarioRepository;
import com.tonalli.web.repository.VotoRepository;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Genera los informes descargables del administrador y los indicadores de la página de informes.
 *
 * <p>Por qué un servicio aparte de {@link EstadisticasService}: aquel entrega los datos de los gráficos
 * (mapas de rótulo a número que se sirven como JSON); este arma archivos. Son dos responsabilidades con
 * reglas distintas —uno se consume desde JavaScript, el otro se abre en Excel— y mezclarlas obligaría a
 * que el servicio de los gráficos conociera el formato CSV, que no es asunto suyo.
 *
 * <p><b>Tope de filas.</b> Cada informe pide a la base como máximo {@link #MAX_FILAS} registros, con un
 * {@link Pageable}. El límite está en la consulta y no en el bucle que escribe el CSV: así la base nunca
 * manda más de lo que se va a usar. Sin este tope, pedir un informe sería una forma trivial de agotar la
 * memoria del servidor, porque el archivo se arma completo antes de enviarse.
 *
 * <p><b>Auditoría.</b> Toda descarga queda registrada en el log AUDITORIA con quién la pidió, qué tipo y
 * qué periodo. El informe de usuarios contiene correos, así que tiene que poder responderse después la
 * pregunta de quién se llevó qué datos y cuándo.
 */
@Service
public class InformeService {

    private static final Logger AUDITORIA = LoggerFactory.getLogger("AUDITORIA");

    /** Tope de filas por informe. Con el catálogo de un proyecto académico sobra; es la red de seguridad. */
    public static final int MAX_FILAS = 5_000;

    private static final DateTimeFormatter FECHA_ARCHIVO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final OfertaRepository ofertas;
    private final UsuarioRepository usuarios;
    private final ReporteRepository reportes;
    private final VotoRepository votos;
    private final ComentarioRepository comentarios;
    private final EstadisticasService estadisticas;

    public InformeService(OfertaRepository ofertas, UsuarioRepository usuarios, ReporteRepository reportes,
                          VotoRepository votos, ComentarioRepository comentarios, EstadisticasService estadisticas) {
        this.ofertas = ofertas;
        this.usuarios = usuarios;
        this.reportes = reportes;
        this.votos = votos;
        this.comentarios = comentarios;
        this.estadisticas = estadisticas;
    }

    /**
     * Indicadores que encabezan la página.
     *
     * <p>Casi todos los indicadores son un {@code count} o un {@code avg} resuelto por la base: devuelve
     * un número y ninguna entidad se carga en memoria. La excepción es la temperatura promedio, que llega
     * como una lista de un número por oferta activa y se promedia en Java, porque la temperatura no es una
     * columna sino la suma de los votos y anidar ese cálculo dentro de un {@code avg} no se traduce igual
     * en H2 y en MySQL. Esa misma lista es la que usa el histograma, así que se paga una sola vez; el día
     * que el catálogo activo sea grande, el camino es guardar la temperatura en una columna mantenida al
     * votar y entonces el promedio también sale de un {@code avg}.
     */
    @Transactional(readOnly = true)
    public EstadisticasGenerales generales() {
        long activas = ofertas.countByEstado(EstadoOferta.ACTIVA);
        long ocultas = ofertas.countByEstado(EstadoOferta.OCULTA);
        Double descuento = ofertas.descuentoPromedio(EstadoOferta.ACTIVA);
        Double precio = ofertas.precioPromedio(EstadoOferta.ACTIVA);

        List<Long> temperaturas = ofertas.temperaturas(EstadoOferta.ACTIVA);
        double temperaturaMedia = temperaturas.isEmpty() ? 0
                : temperaturas.stream().mapToLong(t -> t == null ? 0L : t).average().orElse(0);

        return new EstadisticasGenerales(
                activas + ocultas,
                activas,
                ocultas,
                ofertas.countByEstadoAndPrecioAnteriorCentavosIsNotNull(EstadoOferta.ACTIVA),
                ofertas.countByEstadoNotAndCreadaEnAfter(EstadoOferta.ELIMINADA,
                        Instant.now().minus(30, ChronoUnit.DAYS)),
                descuento == null ? 0 : descuento,
                precio == null ? 0 : (int) Math.round(precio),
                temperaturaMedia,
                usuarios.count(),
                usuarios.countByVerificado(true),
                votos.count(),
                comentarios.count(),
                reportes.count(),
                reportes.countByEstado(EstadoReporte.ABIERTO));
    }

    /**
     * Arma el informe pedido y lo devuelve listo para descargar.
     *
     * <p>El {@code switch} sobre el enum no lleva rama {@code default}: al ser exhaustivo, si mañana se
     * agrega un tipo de informe el compilador marca este método como error en vez de dejar que en
     * ejecución se devuelva un archivo vacío.
     */
    @Transactional(readOnly = true)
    public ArchivoDescarga generar(Long adminId, TipoInforme tipo, RangoFechas rango) {
        Pageable tope = PageRequest.of(0, MAX_FILAS);
        Instant desde = rango.desdeInstante();
        Instant hasta = rango.hastaInstante();

        byte[] contenido = switch (tipo) {
            case OFERTAS -> informeOfertas(desde, hasta, tope);
            case USUARIOS -> informeUsuarios(desde, hasta, tope);
            case MODERACION -> informeModeracion(desde, hasta, tope);
            case RESUMEN -> informeResumen(rango);
        };

        AUDITORIA.info("admin={} informe={} desde={} hasta={} bytes={}",
                adminId, tipo, rango.getDesdeUsado(), rango.getHastaUsado(), contenido.length);

        String nombre = tipo.getArchivo()
                + "_" + FECHA_ARCHIVO.format(rango.getDesdeUsado())
                + "_" + FECHA_ARCHIVO.format(rango.getHastaUsado()) + ".csv";
        return new ArchivoDescarga(nombre, contenido);
    }

    private byte[] informeOfertas(Instant desde, Instant hasta, Pageable tope) {
        CsvEscritor csv = new CsvEscritor("Id", "Título", "Tienda", "Categoría", "Precio USD",
                "Precio anterior USD", "Descuento %", "Estado", "Autor", "Temperatura", "Publicada");
        List<Object[]> filas = ofertas.filasInformeOfertas(desde, hasta, tope);
        for (Object[] f : filas) {
            int precio = ((Number) f[4]).intValue();
            Integer anterior = f[5] == null ? null : ((Number) f[5]).intValue();
            csv.fila(f[0],
                    f[1],
                    f[2],
                    f[3],
                    CsvEscritor.dolares(precio),
                    CsvEscritor.dolares(anterior),
                    porcentajeDescuento(precio, anterior),
                    ((EstadoOferta) f[6]).name(),
                    f[7],
                    f[9] == null ? 0 : f[9],
                    CsvEscritor.fecha((Instant) f[8]));
        }
        avisarSiSeCorto(csv, filas.size());
        return csv.bytes();
    }

    private byte[] informeUsuarios(Instant desde, Instant hasta, Pageable tope) {
        CsvEscritor csv = new CsvEscritor("Id", "Nombre", "Correo", "Rol", "Correo verificado", "Karma", "Registro");
        List<Object[]> filas = usuarios.filasInformeUsuarios(desde, hasta, tope);
        for (Object[] f : filas) {
            csv.fila(f[0],
                    f[1],
                    f[2],
                    ((Rol) f[3]).getEtiqueta(),
                    CsvEscritor.siNo(Boolean.TRUE.equals(f[4])),
                    f[5],
                    CsvEscritor.fecha((Instant) f[6]));
        }
        avisarSiSeCorto(csv, filas.size());
        return csv.bytes();
    }

    private byte[] informeModeracion(Instant desde, Instant hasta, Pageable tope) {
        CsvEscritor csv = new CsvEscritor("Id", "Oferta reportada", "Motivo", "Estado", "Fecha del reporte");
        List<Object[]> filas = reportes.filasInformeModeracion(desde, hasta, tope);
        for (Object[] f : filas) {
            csv.fila(f[0],
                    f[1],
                    ((MotivoReporte) f[2]).getEtiqueta(),
                    ((EstadoReporte) f[3]).name(),
                    CsvEscritor.fecha((Instant) f[4]));
        }
        avisarSiSeCorto(csv, filas.size());
        return csv.bytes();
    }

    /**
     * Informe de resumen: los indicadores y, debajo, los datos de cada gráfico.
     *
     * <p>Es el archivo que se entrega cuando alguien pide «el reporte del sitio»: una sola hoja con todo
     * lo que la página muestra en pantalla, de modo que el documento se sostiene solo sin necesidad de
     * capturas. Va en dos columnas (indicador, valor) porque así cada bloque se lee seguido y se puede
     * ordenar o graficar en Excel sin reorganizar nada.
     */
    private byte[] informeResumen(RangoFechas rango) {
        EstadisticasGenerales g = generales();
        CsvEscritor csv = new CsvEscritor("Indicador", "Valor");

        csv.fila("Informe generado", CsvEscritor.fecha(Instant.now()));
        csv.fila("Periodo consultado", rango.getDesdeUsado() + " a " + rango.getHastaUsado());
        csv.linea();

        csv.fila("Ofertas (activas y ocultas)", g.getOfertasTotales());
        csv.fila("Ofertas activas", g.getOfertasActivas());
        csv.fila("Ofertas ocultas por moderación", g.getOfertasOcultas());
        csv.fila("Ofertas con descuento declarado", g.getOfertasConDescuento());
        csv.fila("Ofertas de los últimos 30 días", g.getOfertasUltimos30Dias());
        csv.fila("Descuento promedio (%)", g.getDescuentoPromedio());
        csv.fila("Precio promedio (USD)", g.getPrecioPromedio());
        csv.fila("Temperatura promedio", g.getTemperaturaPromedio());
        csv.fila("Usuarios registrados", g.getUsuarios());
        csv.fila("Usuarios con correo verificado", g.getUsuariosVerificados());
        csv.fila("Tasa de verificación (%)", g.getTasaVerificacion());
        csv.fila("Votos emitidos", g.getVotos());
        csv.fila("Votos por oferta activa", g.getVotosPorOferta());
        csv.fila("Comentarios publicados", g.getComentarios());
        csv.fila("Reportes recibidos", g.getReportesTotales());
        csv.fila("Reportes sin revisar", g.getReportesAbiertos());

        for (Map.Entry<String, Map<String, ? extends Number>> grafico : estadisticas.todos().entrySet()) {
            csv.linea();
            csv.fila(grafico.getKey(), "");
            grafico.getValue().forEach((rotulo, valor) -> csv.fila(rotulo, valor));
        }
        return csv.bytes();
    }

    /**
     * Escribe un aviso al final si el informe llegó al tope de filas.
     *
     * <p>Por qué hace falta: el tope protege la memoria del servidor, pero un archivo que se corta en
     * silencio es peor que un error, porque quien lo recibe lo cree completo y saca conclusiones de datos
     * incompletos sin saberlo. El aviso va en su propia fila al final, separado por una línea en blanco,
     * para que no se confunda con un registro.
     */
    static void avisarSiSeCorto(CsvEscritor csv, int filasEscritas) {
        if (filasEscritas < MAX_FILAS) {
            return;
        }
        csv.linea();
        csv.fila("AVISO: el informe se cortó en " + MAX_FILAS
                + " filas y puede haber más datos en este periodo. Acota el rango de fechas para verlos.");
    }

    /** Porcentaje de descuento con un decimal, o vacío si la oferta no declara precio anterior. */
    private static String porcentajeDescuento(int precio, Integer anterior) {
        if (anterior == null || anterior <= 0) {
            return "";
        }
        double porcentaje = (anterior - precio) * 100.0 / anterior;
        return String.format(Locale.ROOT, "%.1f", porcentaje);
    }
}
