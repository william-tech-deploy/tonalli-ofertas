package com.tonalli.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonalli.web.dto.ArchivoDescarga;
import com.tonalli.web.dto.EstadisticasGenerales;
import com.tonalli.web.dto.RangoFechas;
import com.tonalli.web.dto.TipoInforme;
import com.tonalli.web.model.EstadoOferta;
import com.tonalli.web.model.Oferta;
import com.tonalli.web.repository.OfertaRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Pruebas de los informes y de los datos de los gráficos, con la base H2 del perfil dev y los datos de
 * ejemplo que carga DemoData.
 *
 * <p>Usa su propia base ({@code tonalli-informes}) porque una de las pruebas cambia el título de una
 * oferta para comprobar la neutralización de fórmulas, y no debe alterar lo que ven las demás pruebas.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:tonalli-informes;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("dev")
class InformeServiceTest {

    private static final Long ADMIN = 1L;

    @Autowired
    private InformeService informes;

    @Autowired
    private EstadisticasService estadisticas;

    @Autowired
    private OfertaRepository ofertas;

    /** Rango amplio, para que los informes incluyan todos los datos de ejemplo. */
    private static RangoFechas todoElPeriodo() {
        RangoFechas r = new RangoFechas();
        r.setDesde(LocalDate.now().minusYears(2));
        r.setHasta(LocalDate.now());
        return r;
    }

    private static String texto(ArchivoDescarga archivo) {
        return new String(archivo.getContenido(), StandardCharsets.UTF_8);
    }

    @Test
    void losIndicadoresCuadranEntreSi() {
        EstadisticasGenerales g = informes.generales();

        assertEquals(ofertas.countByEstado(EstadoOferta.ACTIVA), g.getOfertasActivas());
        assertEquals(g.getOfertasActivas() + g.getOfertasOcultas(), g.getOfertasTotales());
        assertTrue(g.getUsuarios() > 0, "Los datos de ejemplo crean usuarios");
        assertTrue(g.getUsuariosVerificados() <= g.getUsuarios());
        assertTrue(g.getOfertasConDescuento() <= g.getOfertasActivas());
        assertTrue(g.getTasaVerificacion().compareTo(BigDecimal.ZERO) >= 0
                && g.getTasaVerificacion().compareTo(new BigDecimal("100")) <= 0,
                "La tasa de verificación es un porcentaje: " + g.getTasaVerificacion());
    }

    @Test
    void sinUsuariosNiOfertasLosPromediosNoDividenEntreCero() {
        // No se vacía la base: se comprueba el camino de guarda con un objeto construido a mano.
        EstadisticasGenerales vacio = new EstadisticasGenerales(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        assertEquals(BigDecimal.ZERO, vacio.getTasaVerificacion());
        assertEquals(BigDecimal.ZERO, vacio.getVotosPorOferta());
    }

    @Test
    void losCuatroInformesSeGeneranConBomYNombrePropio() {
        RangoFechas rango = todoElPeriodo();

        for (TipoInforme tipo : TipoInforme.values()) {
            ArchivoDescarga archivo = informes.generar(ADMIN, tipo, rango);
            String contenido = texto(archivo);

            assertTrue(archivo.getNombre().startsWith(tipo.getArchivo()), archivo.getNombre());
            assertTrue(archivo.getNombre().endsWith(".csv"), archivo.getNombre());
            assertTrue(archivo.getNombre().contains(rango.getDesdeUsado().toString()),
                    "El nombre lleva el periodo, para no confundir dos descargas: " + archivo.getNombre());
            assertEquals('﻿', contenido.charAt(0), "Todo informe empieza con el BOM");
            assertTrue(contenido.contains("\r\n"), "Las filas se separan con CRLF");
            assertFalse(archivo.getNombre().contains("/") || archivo.getNombre().contains(".."),
                    "El nombre del archivo no puede llevar rutas");
        }
    }

    @Test
    void elInformeDeOfertasTraeUnaFilaPorOfertaDelPeriodo() {
        String contenido = texto(informes.generar(ADMIN, TipoInforme.OFERTAS, todoElPeriodo()));

        assertTrue(contenido.contains("Id,Título,Tienda,Categoría"), "Debe llevar la fila de cabeceras");
        long filas = contenido.lines().filter(l -> !l.isBlank()).count() - 1;
        assertEquals(ofertas.count(), filas,
                "Una fila por oferta de los datos de ejemplo, más la cabecera");
    }

    @Test
    void elInformeDeUsuariosNoFiltraLaContrasenaNiElTokenDeVerificacion() {
        String contenido = texto(informes.generar(ADMIN, TipoInforme.USUARIOS, todoElPeriodo()));

        assertTrue(contenido.contains("Correo verificado"));
        assertFalse(contenido.contains("$2a$") || contenido.contains("$2b$"),
                "El informe no puede llevar hashes BCrypt");
        assertFalse(contenido.toLowerCase().contains("token"), "Ni el token de verificación de correo");
    }

    @Test
    void elInformeDeModeracionNoDiceQuienReporto() {
        String contenido = texto(informes.generar(ADMIN, TipoInforme.MODERACION, todoElPeriodo()));

        assertTrue(contenido.contains("Oferta reportada,Motivo,Estado"));
        assertFalse(contenido.contains("Reportante"), "Se omite a propósito: minimización de datos");
    }

    @Test
    void elResumenTraeLosIndicadoresYLosDatosDeLosSieteGraficos() {
        String contenido = texto(informes.generar(ADMIN, TipoInforme.RESUMEN, todoElPeriodo()));

        assertTrue(contenido.contains("Ofertas activas"));
        assertTrue(contenido.contains("Tasa de verificación (%)"));
        assertTrue(contenido.contains("Periodo consultado"));
        for (String titulo : estadisticas.todos().keySet()) {
            assertTrue(contenido.contains(titulo), "Falta el bloque del gráfico: " + titulo);
        }
    }

    @Test
    void unPeriodoSinDatosDevuelveSoloLaCabecera() {
        RangoFechas antiguo = new RangoFechas();
        antiguo.setDesde(LocalDate.of(2000, 1, 1));
        antiguo.setHasta(LocalDate.of(2000, 1, 31));

        String contenido = texto(informes.generar(ADMIN, TipoInforme.OFERTAS, antiguo));

        assertEquals(1, contenido.lines().filter(l -> !l.isBlank()).count(),
                "En un periodo sin ofertas solo sale la fila de cabeceras");
    }

    /**
     * La prueba que justifica el escritor de CSV: una oferta cuyo título es una fórmula de Excel debe
     * salir del informe como texto, no como fórmula. Es el ataque realista contra esta sección, porque
     * los títulos los escribe cualquiera que se registre y el archivo lo abre el administrador.
     */
    @Test
    void unaOfertaConTituloDeFormulaSaleNeutralizadaEnElCsv() {
        // Se toma con el método que trae categoría y autor en la misma consulta: así la entidad no
        // queda con referencias perezosas sin cargar y guardarla de vuelta es seguro.
        Oferta victima = ofertas.findByEstadoOrderByCreadaEnDesc(EstadoOferta.ACTIVA).get(0);
        String original = victima.getTitulo();
        victima.setTitulo("=HYPERLINK(\"http://malo.example\",\"Reclama tu premio\")");
        ofertas.save(victima);
        try {
            String contenido = texto(informes.generar(ADMIN, TipoInforme.OFERTAS, todoElPeriodo()));

            assertTrue(contenido.contains("\"'=HYPERLINK"),
                    "El título debe salir con apóstrofo delante, como texto");
            assertFalse(contenido.contains(",=HYPERLINK"),
                    "Nunca una celda que empiece por = sin neutralizar");
        } finally {
            victima.setTitulo(original);
            ofertas.save(victima);
        }
    }

    /**
     * El tope de filas protege la memoria del servidor, pero un archivo que se corta en silencio es peor
     * que un error: quien lo recibe lo cree completo. Se prueba el aviso con el contador a mano, porque
     * llenar la base con 5000 ofertas solo para esto haría la prueba lenta sin cubrir nada más.
     */
    @Test
    void siElInformeLlegaAlTopeDeFilasLoAvisaDentroDelArchivo() {
        CsvEscritor alTope = new CsvEscritor("Id");
        InformeService.avisarSiSeCorto(alTope, InformeService.MAX_FILAS);
        String conAviso = new String(alTope.bytes(), StandardCharsets.UTF_8);

        assertTrue(conAviso.contains("AVISO: el informe se cortó en " + InformeService.MAX_FILAS),
                "El archivo debe decir que está incompleto: " + conAviso);

        CsvEscritor corto = new CsvEscritor("Id");
        InformeService.avisarSiSeCorto(corto, InformeService.MAX_FILAS - 1);
        assertFalse(new String(corto.bytes(), StandardCharsets.UTF_8).contains("AVISO"),
                "Un informe que cabe entero no lleva aviso");
    }

    @Test
    void losInformesNormalesNoLlevanElAvisoDeCorte() {
        for (TipoInforme tipo : TipoInforme.values()) {
            String contenido = texto(informes.generar(ADMIN, tipo, todoElPeriodo()));
            assertFalse(contenido.contains("AVISO"),
                    "Los datos de ejemplo caben de sobra en el tope: " + tipo);
        }
    }

    @Test
    void losGraficosDevuelvenRotulosEnOrdenYSinHuecos() {
        Map<String, Long> meses = estadisticas.ofertasPorMes();
        assertEquals(EstadisticasService.MESES_DEL_GRAFICO, meses.size(),
                "Los doce meses aparecen siempre, aunque alguno no tenga ofertas");

        Map<String, Long> temperatura = estadisticas.distribucionTemperatura();
        assertEquals(3, temperatura.size(), "Los tres tramos del catálogo: frías, tibias y calientes");
        assertEquals(ofertas.countByEstado(EstadoOferta.ACTIVA),
                temperatura.values().stream().mapToLong(Long::longValue).sum(),
                "Cada oferta activa cae en un tramo y solo en uno");

        Map<String, Long> roles = estadisticas.usuariosPorRol();
        assertTrue(roles.containsKey("Usuario") && roles.containsKey("Administrador"),
                "Los rótulos salen en español: " + roles.keySet());

        Map<String, Long> estados = estadisticas.reportesPorEstado();
        assertTrue(estados.containsKey("Abierto"), "Los estados sin reportes también aparecen, con 0");
    }
}
