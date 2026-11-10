package com.tonalli.web.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FormatoTest {

    @Test
    void convierteCentavosYDolares() {
        assertEquals(new BigDecimal("12.50"), Formato.dolares(1250));
        assertEquals(1250, Formato.centavos(new BigDecimal("12.5")));
        assertEquals(1, Formato.centavos(new BigDecimal("0.005"))); // redondeo comercial hacia arriba
    }

    @Test
    void centavosFueraDeRangoLanzaExcepcion() {
        assertThrows(ArithmeticException.class, () -> Formato.centavos(new BigDecimal("99999999999.99")));
    }

    @Test
    void limpiarBusquedaQuitaComodinesYLimitaLargo() {
        assertEquals("100 desc", Formato.limpiarBusqueda("100%_desc").replaceAll("\\s+", " "));
        assertEquals("", Formato.limpiarBusqueda(null));
        assertEquals(60, Formato.limpiarBusqueda("a".repeat(200)).length());
        assertEquals("a b", Formato.limpiarBusqueda("a\u0001b"));
    }

    @Test
    void limpiarTextoQuitaControlesPeroConservaSaltoDeLinea() {
        assertEquals("hola\nmundo", Formato.limpiarTexto("  hola\u0007\nmundo  "));
        assertEquals("", Formato.limpiarTexto(null));
    }
}
