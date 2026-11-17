package com.tonalli.web.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Utilidades de formato y saneamiento que comparten servicios y vistas. */
public final class Formato {

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/El_Salvador"));

    private Formato() {
    }

    public static String fecha(Instant instante) {
        return instante == null ? "" : FECHA.format(instante);
    }

    /** 1250 centavos -> 12.50 dólares. */
    public static BigDecimal dolares(int centavos) {
        return BigDecimal.valueOf(centavos, 2);
    }

    /** 12.5 dólares -> 1250 centavos (redondeo comercial). Lanza ArithmeticException si no cabe en un int. */
    public static int centavos(BigDecimal dolares) {
        return dolares.setScale(2, RoundingMode.HALF_UP).movePointRight(2).intValueExact();
    }

    /**
     * Prepara el texto de una búsqueda: quita los comodines de LIKE (% y _), la barra invertida y los
     * caracteres de control, y lo limita a 60 caracteres. Nunca devuelve null.
     */
    public static String limpiarBusqueda(String q) {
        if (q == null) {
            return "";
        }
        String limpio = q.replaceAll("[%_\\\\\\p{Cntrl}]", " ").trim();
        return limpio.length() > 60 ? limpio.substring(0, 60) : limpio;
    }

    /** Enlace opcional: recorta espacios y convierte el texto vacío en null (la columna queda sin valor). */
    public static String enlaceONulo(String enlace) {
        if (enlace == null) {
            return null;
        }
        String limpio = enlace.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    /** Recorta espacios y elimina caracteres de control (excepto salto de línea) de un texto libre. */
    public static String limpiarTexto(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replaceAll("[\\p{Cntrl}&&[^\\n]]", "").trim();
    }
}
