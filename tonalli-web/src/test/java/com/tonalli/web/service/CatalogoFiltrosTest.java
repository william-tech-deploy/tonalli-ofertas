package com.tonalli.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonalli.web.dto.FiltroOfertas;
import com.tonalli.web.dto.OfertaVista;
import com.tonalli.web.dto.OrdenOferta;
import com.tonalli.web.dto.TemperaturaFiltro;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Filtros del catálogo sobre las ofertas de ejemplo del perfil dev. */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class CatalogoFiltrosTest {

    @Autowired private OfertaService ofertas;

    private FiltroOfertas filtro() {
        return new FiltroOfertas();
    }

    private List<OfertaVista> resultado(FiltroOfertas f) {
        return ofertas.listar(f).getContent();
    }

    @Test
    void sinFiltrosDevuelveLaPrimeraPaginaDelCatalogo() {
        assertEquals(OfertaService.TAMANO_PAGINA, resultado(filtro()).size());
    }

    @Test
    void elRangoDePreciosDejaFueraLoQueNoCabe() {
        FiltroOfertas f = filtro();
        f.setPrecioMin(new BigDecimal("10"));
        f.setPrecioMax(new BigDecimal("100"));
        List<OfertaVista> lista = resultado(f);
        assertFalse(lista.isEmpty());
        assertTrue(lista.stream().allMatch(o -> o.getPrecio().compareTo(new BigDecimal("10")) >= 0
                && o.getPrecio().compareTo(new BigDecimal("100")) <= 0));
    }

    @Test
    void unRangoAlRevesSeEntiendeIgual() {
        FiltroOfertas alReves = filtro();
        alReves.setPrecioMin(new BigDecimal("100"));
        alReves.setPrecioMax(new BigDecimal("10"));
        FiltroOfertas normal = filtro();
        normal.setPrecioMin(new BigDecimal("10"));
        normal.setPrecioMax(new BigDecimal("100"));
        assertEquals(ofertas.listar(normal).getTotalElements(), ofertas.listar(alReves).getTotalElements());
    }

    @Test
    void soloConDescuentoDejaFueraLasQueNoTienenPrecioAnterior() {
        FiltroOfertas f = filtro();
        f.setSoloConDescuento(true);
        List<OfertaVista> lista = resultado(f);
        assertFalse(lista.isEmpty());
        assertTrue(lista.stream().allMatch(o -> o.getPrecioAnterior() != null));
    }

    @Test
    void ordenarPorPrecioFuncionaEnLosDosSentidos() {
        FiltroOfertas sube = filtro();
        sube.setOrden(OrdenOferta.PRECIO_ASC);
        List<OfertaVista> ascendente = resultado(sube);
        for (int i = 1; i < ascendente.size(); i++) {
            assertTrue(ascendente.get(i - 1).getPrecio().compareTo(ascendente.get(i).getPrecio()) <= 0);
        }
        FiltroOfertas baja = filtro();
        baja.setOrden(OrdenOferta.PRECIO_DESC);
        List<OfertaVista> descendente = resultado(baja);
        for (int i = 1; i < descendente.size(); i++) {
            assertTrue(descendente.get(i - 1).getPrecio().compareTo(descendente.get(i).getPrecio()) >= 0);
        }
        assertTrue(ascendente.get(0).getPrecio().compareTo(descendente.get(0).getPrecio()) < 0);
    }

    @Test
    void ordenarPorTemperaturaPoneArribaLasMasVotadas() {
        FiltroOfertas f = filtro();
        f.setOrden(OrdenOferta.TEMPERATURA);
        List<OfertaVista> lista = resultado(f);
        for (int i = 1; i < lista.size(); i++) {
            assertTrue(lista.get(i - 1).getTemperatura() >= lista.get(i).getTemperatura());
        }
    }

    @Test
    void ordenarPorDescuentoPoneArribaElMayorPorcentaje() {
        FiltroOfertas f = filtro();
        f.setOrden(OrdenOferta.DESCUENTO);
        List<OfertaVista> lista = resultado(f);
        assertTrue(lista.get(0).getDescuento() != null);
        for (int i = 1; i < lista.size(); i++) {
            int anterior = lista.get(i - 1).getDescuento() == null ? -1 : lista.get(i - 1).getDescuento();
            int actual = lista.get(i).getDescuento() == null ? -1 : lista.get(i).getDescuento();
            assertTrue(anterior >= actual, "El descuento debe ir de mayor a menor");
        }
    }

    @Test
    void elFiltroDeTemperaturaSeparaTibiasDeFrias() {
        FiltroOfertas tibias = filtro();
        tibias.setTemperatura(TemperaturaFiltro.TIBIA);
        assertTrue(resultado(tibias).stream().allMatch(o -> o.getTemperatura() >= 0 && o.getTemperatura() <= 99));

        FiltroOfertas frias = filtro();
        frias.setTemperatura(TemperaturaFiltro.FRIA);
        List<OfertaVista> lista = resultado(frias);
        assertFalse(lista.isEmpty(), "Los datos de ejemplo incluyen ofertas frías");
        assertTrue(lista.stream().allMatch(o -> o.getTemperatura() < 0));
    }

    @Test
    void losFiltrosSeCombinanEntreSi() {
        FiltroOfertas f = filtro();
        f.setQ("samsung");
        f.setSoloConDescuento(true);
        f.setPrecioMax(new BigDecimal("800"));
        f.setOrden(OrdenOferta.PRECIO_DESC);
        List<OfertaVista> lista = resultado(f);
        assertFalse(lista.isEmpty());
        assertTrue(lista.stream().allMatch(o -> o.getTitulo().toLowerCase().contains("samsung")
                && o.getPrecioAnterior() != null
                && o.getPrecio().compareTo(new BigDecimal("800")) <= 0));
    }

    @Test
    void unOrdenDesconocidoEnLaUrlNoRompeNada() {
        assertEquals(OrdenOferta.RECIENTES, OrdenOferta.desdeTexto("'; drop table ofertas; --"));
        assertEquals(TemperaturaFiltro.TODAS, TemperaturaFiltro.desdeTexto(null));
    }
}
