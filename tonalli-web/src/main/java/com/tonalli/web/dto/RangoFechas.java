package com.tonalli.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Rango de fechas de los informes, tal como llega del formulario ({@code <input type="date">}).
 *
 * <p>Por qué se normaliza en vez de rechazar: las fechas viajan en la URL y cualquiera puede escribir
 * {@code ?desde=1900-01-01}. En lugar de devolver un error, el rango se ajusta solo (vacío → últimos 90
 * días, invertido → se intercambia, futuro → hasta hoy, más largo que 5 años → se recorta) y la página
 * muestra el rango que de verdad se usó, así nada queda silencioso. El motivo de fondo es que ninguna
 * petición debe llegar a la base de datos pidiendo un periodo sin límite: eso convertiría la descarga de
 * un informe en una manera sencilla de agotar la memoria del servidor.
 *
 * <p>La zona horaria es fija (America/El_Salvador) porque las fechas se guardan en UTC como
 * {@link Instant}: sin fijar la zona, "17 de noviembre" significaría un periodo distinto según dónde esté
 * el servidor y los totales de un mismo día no cuadrarían entre el informe y el gráfico.
 */
public class RangoFechas {

    /** Periodo que se usa cuando el usuario no elige nada. */
    public static final int DIAS_POR_OMISION = 90;

    /** Tope del periodo, en días (5 años). Evita consultas sin límite. */
    public static final int MAX_DIAS = 1826;

    private static final ZoneId ZONA = ZoneId.of("America/El_Salvador");

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate desde;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate hasta;

    public LocalDate getDesde() {
        return desde;
    }

    public void setDesde(LocalDate desde) {
        this.desde = desde;
    }

    public LocalDate getHasta() {
        return hasta;
    }

    public void setHasta(LocalDate hasta) {
        this.hasta = hasta;
    }

    /** Primer día del periodo, ya normalizado. */
    public LocalDate getDesdeUsado() {
        return normalizar()[0];
    }

    /** Último día del periodo, ya normalizado. Entra completo en el informe. */
    public LocalDate getHastaUsado() {
        return normalizar()[1];
    }

    /** Inicio del periodo como instante: las 00:00 de ese día en El Salvador. */
    public Instant desdeInstante() {
        return getDesdeUsado().atStartOfDay(ZONA).toInstant();
    }

    /**
     * Fin del periodo como instante, exclusivo: las 00:00 del día siguiente. Se usa {@code < hasta} en vez
     * de {@code <= hasta} para que el último día cuente completo sin depender de la precisión del reloj.
     */
    public Instant hastaInstante() {
        return getHastaUsado().plusDays(1).atStartOfDay(ZONA).toInstant();
    }

    /** true si hubo que corregir lo que pidió el usuario; la página lo avisa. */
    public boolean isAjustado() {
        return !getDesdeUsado().equals(desde) || !getHastaUsado().equals(hasta);
    }

    /** [primerDía, últimoDía]. Es el único lugar donde se decide el periodo. */
    private LocalDate[] normalizar() {
        LocalDate hoy = LocalDate.now(ZONA);
        LocalDate a = hasta != null ? hasta : hoy;
        LocalDate b = desde != null ? desde : a.minusDays(DIAS_POR_OMISION);

        LocalDate fin = a.isBefore(b) ? b : a;
        LocalDate inicio = a.isBefore(b) ? a : b;

        if (fin.isAfter(hoy)) {
            fin = hoy;
        }
        if (inicio.isAfter(fin)) {
            inicio = fin;
        }
        if (ChronoUnit.DAYS.between(inicio, fin) > MAX_DIAS) {
            inicio = fin.minusDays(MAX_DIAS);
        }
        return new LocalDate[] {inicio, fin};
    }
}
