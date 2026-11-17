package com.tonalli.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Pruebas del escritor de CSV. No levantan Spring: la clase no tiene dependencias, así que estas pruebas
 * corren en milisegundos y se pueden ejecutar tantas veces como haga falta.
 *
 * <p>Lo que se comprueba es exactamente lo que el escritor promete en su documentación: que una celda no
 * pueda convertirse en fórmula de Excel, que las comas y las comillas no corran las columnas, y que el
 * archivo empiece con el BOM que necesita Excel para leer los acentos.
 */
class CsvEscritorTest {

    @Test
    void elArchivoEmpiezaConElBomYSeparaLasFilasConCrLf() {
        byte[] archivo = new CsvEscritor("Id", "Título").fila(1, "Camisa").bytes();
        String texto = new String(archivo, StandardCharsets.UTF_8);

        assertEquals('﻿', texto.charAt(0), "Sin BOM, Excel en Windows rompe los acentos");
        assertEquals("﻿Id,Título\r\n1,Camisa\r\n", texto);
    }

    /**
     * El caso que da sentido a toda la clase: alguien publica una oferta cuyo título es una fórmula y
     * espera a que un administrador descargue el informe para que se ejecute en su equipo.
     */
    @Test
    void unTituloQueEsUnaFormulaSeNeutralizaConApostrofo() {
        String celda = CsvEscritor.celda("=HYPERLINK(\"http://malo.example\",\"Haz clic\")");

        assertTrue(celda.startsWith("\"'="), "La celda debe quedar entrecomillada y empezar por apóstrofo: " + celda);
    }

    @Test
    void losCincoDisparadoresDeFormulaSeNeutralizan() {
        String[] peligrosos = {"=1+1", "+34", "@SUM(A1)", "-2+3+cmd|' /C calc'!A0", "\tcon tabulador",
                "\rcon retorno"};
        for (String peligroso : peligrosos) {
            assertTrue(CsvEscritor.celda(peligroso).replace("\"", "").startsWith("'"),
                    "Debía neutralizarse: " + peligroso);
        }
    }

    /**
     * Un número negativo no es una fórmula, y la temperatura de una oferta puede serlo. Si se le pusiera
     * apóstrofo, Excel lo leería como texto y las columnas del informe dejarían de poder sumarse.
     */
    @Test
    void losNumerosNoSeTocan() {
        for (String numero : new String[] {"-5", "-12.50", "0", "34", "1250"}) {
            assertEquals(numero, CsvEscritor.celda(numero), "Un número no debe llevar apóstrofo");
        }
    }

    @Test
    void unaComaDentroDeUnaCeldaNoCorreLasColumnas() {
        assertEquals("\"Pollo, arroz y ensalada\"", CsvEscritor.celda("Pollo, arroz y ensalada"));
    }

    @Test
    void lasComillasSeDuplicanComoPideElRfc4180() {
        assertEquals("\"Dijo \"\"está barato\"\"\"", CsvEscritor.celda("Dijo \"está barato\""));
    }

    @Test
    void unSaltoDeLineaDentroDeLaCeldaQuedaEntrecomillado() {
        assertEquals("\"linea uno\nlinea dos\"", CsvEscritor.celda("linea uno\nlinea dos"));
    }

    @Test
    void elPuntoYComaTambienSeEntrecomilla() {
        // No lo exige el RFC, pero Excel lo usa como separador en algunas regiones.
        assertEquals("\"uno;dos\"", CsvEscritor.celda("uno;dos"));
    }

    @Test
    void unValorNuloSaleComoCeldaVacia() {
        assertEquals("", CsvEscritor.celda(null));
    }

    @Test
    void losBytesNulosSeDescartan() {
        assertEquals("abc", CsvEscritor.celda("a\0b\0c"));
    }

    @Test
    void losAyudantesDeFormatoEscribenLoQueLeeUnaPersona() {
        assertEquals("12.50", CsvEscritor.dolares(1250));
        assertEquals("", CsvEscritor.dolares(null));
        assertEquals("Sí", CsvEscritor.siNo(true));
        assertEquals("No", CsvEscritor.siNo(false));
        assertEquals("", CsvEscritor.fecha(null));
    }

    @Test
    void laFechaSaleEnHoraDeElSalvadorYEnFormatoOrdenable() {
        // 2026-11-17T06:00:00Z son las 00:00 del 17 de noviembre en El Salvador (UTC-6).
        assertEquals("2026-11-17 00:00", CsvEscritor.fecha(java.time.Instant.parse("2026-11-17T06:00:00Z")));
    }

    @Test
    void lineaDejaUnaFilaVaciaParaSepararBloques() {
        String texto = new String(new CsvEscritor("A").linea().fila("B").bytes(), StandardCharsets.UTF_8);
        assertEquals("﻿A\r\n\r\nB\r\n", texto);
    }
}
