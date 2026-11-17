package com.tonalli.web.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * Pruebas del rango de fechas de los informes.
 *
 * <p>Importan porque las fechas llegan en la URL y cualquiera puede escribir lo que quiera: lo que se
 * comprueba aquí es que ninguna combinación, por absurda que sea, produzca un periodo sin límite o al
 * revés. Las fechas de las pruebas se calculan a partir de hoy para que no caduquen con el tiempo.
 */
class RangoFechasTest {

    private static final ZoneId ZONA = ZoneId.of("America/El_Salvador");

    private static RangoFechas rango(LocalDate desde, LocalDate hasta) {
        RangoFechas r = new RangoFechas();
        r.setDesde(desde);
        r.setHasta(hasta);
        return r;
    }

    @Test
    void sinFechasElPeriodoSonLosUltimos90Dias() {
        LocalDate hoy = LocalDate.now(ZONA);
        RangoFechas r = rango(null, null);

        assertEquals(hoy, r.getHastaUsado());
        assertEquals(hoy.minusDays(RangoFechas.DIAS_POR_OMISION), r.getDesdeUsado());
        assertTrue(r.isAjustado(), "Si el usuario no eligió fechas, el rango se rellenó solo");
    }

    @Test
    void unRangoNormalSeRespetaTalCual() {
        LocalDate hoy = LocalDate.now(ZONA);
        RangoFechas r = rango(hoy.minusDays(30), hoy.minusDays(10));

        assertEquals(hoy.minusDays(30), r.getDesdeUsado());
        assertEquals(hoy.minusDays(10), r.getHastaUsado());
        assertFalse(r.isAjustado(), "No había nada que corregir");
    }

    @Test
    void unRangoAlRevesSeIntercambia() {
        LocalDate hoy = LocalDate.now(ZONA);
        RangoFechas r = rango(hoy.minusDays(5), hoy.minusDays(40));

        assertEquals(hoy.minusDays(40), r.getDesdeUsado());
        assertEquals(hoy.minusDays(5), r.getHastaUsado());
        assertTrue(r.isAjustado());
    }

    @Test
    void unaFechaEnElFuturoSeRecortaAHoy() {
        LocalDate hoy = LocalDate.now(ZONA);
        RangoFechas r = rango(hoy.minusDays(7), hoy.plusYears(3));

        assertEquals(hoy, r.getHastaUsado());
        assertEquals(hoy.minusDays(7), r.getDesdeUsado());
    }

    @Test
    void unPeriodoMasLargoQueElTopeSeRecorta() {
        LocalDate hoy = LocalDate.now(ZONA);
        RangoFechas r = rango(hoy.minusDays(9000), hoy);

        assertEquals(hoy.minusDays(RangoFechas.MAX_DIAS), r.getDesdeUsado(),
                "El periodo no puede pasar del tope, para que la consulta nunca quede sin límite");
        assertEquals(hoy, r.getHastaUsado());
    }

    @Test
    void unSoloDiaCuentaCompleto() {
        LocalDate hoy = LocalDate.now(ZONA);
        RangoFechas r = rango(hoy, hoy);

        assertEquals(hoy, r.getDesdeUsado());
        assertEquals(hoy, r.getHastaUsado());
        assertEquals(Duration.ofHours(24), Duration.between(r.desdeInstante(), r.hastaInstante()),
                "El límite superior es exclusivo: 00:00 del día siguiente, así entra el día entero");
    }

    @Test
    void losInstantesSeCalculanEnHoraDeElSalvador() {
        RangoFechas r = rango(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 15));

        // El Salvador es UTC-6 todo el año (no cambia la hora): las 00:00 locales son las 06:00 UTC.
        assertEquals(Instant.parse("2026-01-15T06:00:00Z"), r.desdeInstante());
        assertEquals(Instant.parse("2026-01-16T06:00:00Z"), r.hastaInstante());
    }
}
