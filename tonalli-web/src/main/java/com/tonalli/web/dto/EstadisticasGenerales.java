package com.tonalli.web.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Indicadores del sitio que encabezan la página de informes.
 *
 * <p>Por qué una clase inmutable con getters y no un {@code Map} ni un {@code record}: con getters la
 * vista JSP puede escribir {@code ${estadisticas.ofertasActivas}} (la expresión busca
 * {@code getOfertasActivas()}, que un record no genera), el compilador avisa si la vista pide un
 * indicador que no existe y, al ser todos los campos {@code final}, el objeto que viaja a la vista no se
 * puede modificar por accidente.
 *
 * <p>Aquí solo hay conteos y promedios: ni un dato personal, ni un identificador de usuario. Si mañana la
 * página se abriera a los moderadores, no habría nada que filtrar.
 */
public class EstadisticasGenerales {

    private final long ofertasTotales;
    private final long ofertasActivas;
    private final long ofertasOcultas;
    private final long ofertasConDescuento;
    private final long ofertasUltimos30Dias;
    private final double descuentoPromedio;
    private final int precioPromedioCentavos;
    private final double temperaturaPromedio;
    private final long usuarios;
    private final long usuariosVerificados;
    private final long votos;
    private final long comentarios;
    private final long reportesTotales;
    private final long reportesAbiertos;

    public EstadisticasGenerales(long ofertasTotales, long ofertasActivas, long ofertasOcultas,
                                 long ofertasConDescuento, long ofertasUltimos30Dias, double descuentoPromedio,
                                 int precioPromedioCentavos, double temperaturaPromedio, long usuarios,
                                 long usuariosVerificados, long votos, long comentarios,
                                 long reportesTotales, long reportesAbiertos) {
        this.ofertasTotales = ofertasTotales;
        this.ofertasActivas = ofertasActivas;
        this.ofertasOcultas = ofertasOcultas;
        this.ofertasConDescuento = ofertasConDescuento;
        this.ofertasUltimos30Dias = ofertasUltimos30Dias;
        this.descuentoPromedio = descuentoPromedio;
        this.precioPromedioCentavos = precioPromedioCentavos;
        this.temperaturaPromedio = temperaturaPromedio;
        this.usuarios = usuarios;
        this.usuariosVerificados = usuariosVerificados;
        this.votos = votos;
        this.comentarios = comentarios;
        this.reportesTotales = reportesTotales;
        this.reportesAbiertos = reportesAbiertos;
    }

    public long getOfertasTotales() {
        return ofertasTotales;
    }

    public long getOfertasActivas() {
        return ofertasActivas;
    }

    public long getOfertasOcultas() {
        return ofertasOcultas;
    }

    public long getOfertasConDescuento() {
        return ofertasConDescuento;
    }

    public long getOfertasUltimos30Dias() {
        return ofertasUltimos30Dias;
    }

    /** Descuento medio en porcentaje, con un decimal. */
    public BigDecimal getDescuentoPromedio() {
        return redondear(descuentoPromedio, 1);
    }

    /** Precio medio en dólares, con dos decimales. */
    public BigDecimal getPrecioPromedio() {
        return Formato.dolares(precioPromedioCentavos);
    }

    /** Temperatura media de las ofertas activas, con un decimal. */
    public BigDecimal getTemperaturaPromedio() {
        return redondear(temperaturaPromedio, 1);
    }

    public long getUsuarios() {
        return usuarios;
    }

    public long getUsuariosVerificados() {
        return usuariosVerificados;
    }

    /**
     * Porcentaje de cuentas con el correo verificado. Se calcula aquí y no en la vista porque dividir en
     * una JSP obliga a repetir en cada plantilla la comprobación de que el divisor no sea cero.
     */
    public BigDecimal getTasaVerificacion() {
        return usuarios == 0 ? BigDecimal.ZERO : redondear(usuariosVerificados * 100.0 / usuarios, 1);
    }

    public long getVotos() {
        return votos;
    }

    public long getComentarios() {
        return comentarios;
    }

    public long getReportesTotales() {
        return reportesTotales;
    }

    public long getReportesAbiertos() {
        return reportesAbiertos;
    }

    /** Votos por oferta activa: mide si la comunidad está opinando o solo publicando. */
    public BigDecimal getVotosPorOferta() {
        return ofertasActivas == 0 ? BigDecimal.ZERO : redondear((double) votos / ofertasActivas, 2);
    }

    private static BigDecimal redondear(double valor, int decimales) {
        return BigDecimal.valueOf(valor).setScale(decimales, RoundingMode.HALF_UP);
    }
}
