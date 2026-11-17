package com.tonalli.web.service;

import com.tonalli.web.dto.Formato;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * Arma el texto de un archivo CSV. Se usa en cada informe de {@link InformeService}.
 *
 * <p>No es un detalle de formato: un CSV se abre en Excel, y Excel no es un visor de texto sino un
 * intérprete. De ahí las tres reglas que aplica esta clase.
 *
 * <p><b>1. Inyección de fórmulas (OWASP, «CSV Injection»).</b> Si una celda empieza con {@code =},
 * {@code +}, {@code -}, {@code @}, tabulador o retorno de carro, Excel, LibreOffice y Google Sheets la
 * tratan como fórmula y la ejecutan al abrir el archivo. Como los títulos de las ofertas y los nombres de
 * los usuarios los escribe cualquiera que se registre, un atacante podría publicar una oferta llamada
 * {@code =HYPERLINK(...)} y esperar a que un administrador descargue el informe: la fórmula correría en
 * la máquina del administrador, fuera del navegador, donde la política CSP del sitio ya no protege nada.
 * La defensa es anteponer un apóstrofo, que Excel entiende como «esto es texto». Los números no se tocan
 * (un {@code -5} de temperatura no es una fórmula), para que las columnas sigan sirviendo para calcular.
 *
 * <p><b>2. Comillas y separadores (RFC 4180).</b> Una celda que contenga comilla doble, coma o salto de
 * línea va entre comillas y sus comillas internas se duplican. Sin esto, un título con una coma corre la
 * columna y desplaza todas las demás: el informe queda mal y, peor, deja de ser auditable.
 *
 * <p><b>3. BOM UTF-8.</b> Excel en Windows no detecta UTF-8 por sí solo: sin la marca de orden de bytes
 * al inicio lee el archivo con la codificación local del sistema y los acentos aparecen rotos.
 *
 * <p>Cómo aplicarlo: se crea el escritor con las cabeceras, se agrega una fila por registro con
 * {@link #fila(Object...)} y se cierra con {@link #bytes()}. No es un bean de Spring ni se comparte entre
 * peticiones: cada informe crea el suyo, así no hay estado compartido entre hilos.
 */
public final class CsvEscritor {

    /** Marca de orden de bytes. Le dice a Excel que el archivo es UTF-8. */
    private static final char BOM = '﻿';

    /** RFC 4180 pide CRLF, y es además lo que esperan Excel y el Bloc de notas en Windows. */
    private static final String FIN_LINEA = "\r\n";

    /** Caracteres con los que una hoja de cálculo interpreta la celda como fórmula. */
    private static final String DISPARADORES_DE_FORMULA = "=+-@";

    /**
     * Un número con signo y decimales opcionales. Lo que coincide con esto no se neutraliza. El separador
     * decimal es el punto porque es el único que escribe este proyecto ({@code toPlainString} y
     * {@code String.format} con {@code Locale.ROOT}): aceptar también la coma sería inútil, porque una
     * celda con coma se entrecomilla de todos modos.
     */
    private static final Pattern NUMERO = Pattern.compile("-?\\d+(\\.\\d+)?");

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("America/El_Salvador"));

    private final StringBuilder texto = new StringBuilder();

    /** Crea el archivo y escribe la fila de cabeceras. */
    public CsvEscritor(String... cabeceras) {
        texto.append(BOM);
        fila((Object[]) cabeceras);
    }

    /** Agrega una fila. Devuelve el propio escritor para poder encadenar llamadas. */
    public CsvEscritor fila(Object... celdas) {
        for (int i = 0; i < celdas.length; i++) {
            if (i > 0) {
                texto.append(',');
            }
            texto.append(celda(celdas[i]));
        }
        texto.append(FIN_LINEA);
        return this;
    }

    /** Fila vacía, para separar bloques dentro del informe de resumen. */
    public CsvEscritor linea() {
        texto.append(FIN_LINEA);
        return this;
    }

    /** El archivo terminado. UTF-8 es la codificación que anuncia el BOM y la que declara el controlador. */
    public byte[] bytes() {
        return texto.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Fecha en hora de El Salvador, en formato ordenable (aaaa-mm-dd). */
    public static String fecha(Instant instante) {
        return instante == null ? "" : FECHA.format(instante);
    }

    /** Centavos a dólares con dos decimales y punto decimal, sin separador de miles ni símbolo de moneda. */
    public static String dolares(Integer centavos) {
        return centavos == null ? "" : Formato.dolares(centavos).toPlainString();
    }

    /** Sí/No en lugar de true/false: el informe lo lee una persona, no un programa. */
    public static String siNo(boolean valor) {
        return valor ? "Sí" : "No";
    }

    /**
     * Prepara una celda: quita bytes nulos, neutraliza la fórmula si hace falta y pone comillas si el
     * contenido las necesita. Es visible para las pruebas, que comprueban justo estos tres casos.
     */
    static String celda(Object valor) {
        String crudo = valor == null ? "" : String.valueOf(valor);
        String seguro = neutralizar(crudo.replace("\0", ""));
        if (necesitaComillas(seguro)) {
            return '"' + seguro.replace("\"", "\"\"") + '"';
        }
        return seguro;
    }

    private static String neutralizar(String crudo) {
        if (crudo.isEmpty() || NUMERO.matcher(crudo).matches()) {
            return crudo;
        }
        char primero = crudo.charAt(0);
        boolean peligroso = DISPARADORES_DE_FORMULA.indexOf(primero) >= 0 || primero == '\t' || primero == '\r';
        return peligroso ? "'" + crudo : crudo;
    }

    /**
     * La comilla doble, la coma y los saltos de línea los exige el RFC 4180. El punto y coma y el
     * tabulador no, pero Excel usa uno u otro como separador según la configuración regional del equipo,
     * y entrecomillarlos evita que el archivo se parta mal en una máquina con otra región.
     */
    private static boolean necesitaComillas(String texto) {
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '"' || c == ',' || c == ';' || c == '\t' || c == '\n' || c == '\r') {
                return true;
            }
        }
        return false;
    }
}
