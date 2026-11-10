package com.tonalli.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tonalli.web.dto.OfertaForm;
import com.tonalli.web.exception.OperacionInvalidaException;
import com.tonalli.web.exception.RecursoNoEncontradoException;
import com.tonalli.web.model.EstadoOferta;
import com.tonalli.web.model.MotivoReporte;
import com.tonalli.web.model.Oferta;
import com.tonalli.web.model.Usuario;
import com.tonalli.web.repository.CategoriaRepository;
import com.tonalli.web.repository.OfertaRepository;
import com.tonalli.web.repository.UsuarioRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Reglas de negocio con la base H2 y los datos de ejemplo del perfil dev. Cada prueba se revierte sola. */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class OfertaFlujoTest {

    @Autowired private OfertaService ofertas;
    @Autowired private ModeracionService moderacion;
    @Autowired private OfertaRepository ofertaRepo;
    @Autowired private UsuarioRepository usuarioRepo;
    @Autowired private CategoriaRepository categoriaRepo;

    private Usuario admin() { return usuarioRepo.findByCorreo("admin@tonalli.local").orElseThrow(); }
    private Usuario demo() { return usuarioRepo.findByCorreo("demo@tonalli.local").orElseThrow(); }
    private Oferta ofertaDeDemo() { return ofertaRepo.findAll().get(0); }

    @Test
    void votarSumaYVotarIgualOtraVezLoQuita() {
        Long id = ofertaDeDemo().getId();
        assertEquals(1L, ofertas.votar(admin().getId(), id, 1));
        assertEquals(0L, ofertas.votar(admin().getId(), id, 1)); // mismo voto: se retira
    }

    @Test
    void cambiarDeOpinionRestaElVotoAnterior() {
        Long id = ofertaDeDemo().getId();
        ofertas.votar(admin().getId(), id, 1);
        assertEquals(-1L, ofertas.votar(admin().getId(), id, -1));
    }

    @Test
    void noSePuedeVotarLaPropiaOferta() {
        Long id = ofertaDeDemo().getId();
        assertThrows(OperacionInvalidaException.class, () -> ofertas.votar(demo().getId(), id, 1));
    }

    @Test
    void unValorDeVotoFueraDeRangoSeRechaza() {
        Long id = ofertaDeDemo().getId();
        assertThrows(OperacionInvalidaException.class, () -> ofertas.votar(admin().getId(), id, 5));
    }

    @Test
    void noSePuedeReportarDosVeces() {
        Long id = ofertaDeDemo().getId();
        ofertas.reportar(admin().getId(), id, MotivoReporte.SPAM);
        assertThrows(OperacionInvalidaException.class, () -> ofertas.reportar(admin().getId(), id, MotivoReporte.OTRO));
    }

    @Test
    void elPrecioAnteriorDebeSerMayorQueElActual() {
        OfertaForm form = new OfertaForm();
        form.setTitulo("Prueba");
        form.setTienda("Tienda");
        form.setPrecio(new BigDecimal("10.00"));
        form.setPrecioAnterior(new BigDecimal("10.00"));
        form.setCategoriaId(categoriaRepo.findAll().get(0).getId());
        assertThrows(OperacionInvalidaException.class, () -> ofertas.crear(admin().getId(), form));
    }

    @Test
    void unaOfertaOcultaDejaDeVerseParaElPublico() {
        Long id = ofertaDeDemo().getId();
        ofertas.reportar(admin().getId(), id, MotivoReporte.PRECIO_FALSO);
        Long reporteId = moderacion.reportesAbiertos().get(0).getId();

        moderacion.resolverReporte(admin().getId(), reporteId, true);

        assertThrows(RecursoNoEncontradoException.class, () -> ofertas.obtener(id, false));
        assertEquals(EstadoOferta.OCULTA, ofertas.obtener(id, true).getEstado()); // el moderador sí la ve
    }
}
