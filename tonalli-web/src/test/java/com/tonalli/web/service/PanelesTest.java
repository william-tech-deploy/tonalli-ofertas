package com.tonalli.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.tonalli.web.dto.OfertaVista;
import com.tonalli.web.dto.ResumenAdmin;
import com.tonalli.web.exception.RecursoNoEncontradoException;
import com.tonalli.web.model.EstadoOferta;
import com.tonalli.web.model.Oferta;
import com.tonalli.web.model.Usuario;
import com.tonalli.web.repository.OfertaRepository;
import com.tonalli.web.repository.UsuarioRepository;
import com.tonalli.web.security.UsuarioActual;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Panel "Mi cuenta" y panel de administración, con los datos de ejemplo del perfil dev.
 * Cada prueba se revierte sola (@Transactional), así no altera a las demás.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class PanelesTest {

    @Autowired private PanelService panel;
    @Autowired private AdminService admin;
    @Autowired private OfertaRepository ofertaRepo;
    @Autowired private UsuarioRepository usuarioRepo;
    @Autowired private MockMvc mvc;

    private Usuario demo() { return usuarioRepo.findByCorreo("demo@tonalli.local").orElseThrow(); }
    private Usuario adminUsuario() { return usuarioRepo.findByCorreo("admin@tonalli.local").orElseThrow(); }

    private UsuarioActual sesionDe(Usuario u) {
        return new UsuarioActual(u.getId(), u.getCorreo(), u.getNombre(), u.getPasswordHash(), u.getRol(), true);
    }

    private Oferta ofertaActivaDe(Usuario u) {
        return ofertaRepo.findAll().stream()
                .filter(o -> o.getAutor().getId().equals(u.getId()) && o.getEstado() == EstadoOferta.ACTIVA)
                .findFirst().orElseThrow();
    }

    @Test
    void elPerfilMuestraLosDatosDelPropioUsuario() {
        var perfil = panel.perfil(demo().getId());
        assertEquals("demo@tonalli.local", perfil.getCorreo());
        assertEquals("Usuario", perfil.getRolTexto());
        assertEquals(OfertaService.pesoPorKarma(demo().getKarma()), perfil.getPesoVoto());
    }

    @Test
    void misOfertasSoloTraeLasDelUsuario() {
        List<OfertaVista> mias = panel.misOfertas(demo().getId());
        assertFalse(mias.isEmpty());
        assertTrue(mias.stream().allMatch(o -> o.getAutorId().equals(demo().getId())));
    }

    @Test
    void elAutorRetiraSuOfertaYDesapareceDeSuPanel() {
        Long id = ofertaActivaDe(demo()).getId();
        panel.retirar(demo().getId(), id);
        assertEquals(EstadoOferta.ELIMINADA, ofertaRepo.findById(id).orElseThrow().getEstado());
        assertTrue(panel.misOfertas(demo().getId()).stream().noneMatch(o -> o.getId().equals(id)));
    }

    @Test
    void nadiePuedeRetirarUnaOfertaAjenaNiSiquieraUnAdministrador() {
        Long id = ofertaActivaDe(demo()).getId();
        assertThrows(RecursoNoEncontradoException.class, () -> panel.retirar(adminUsuario().getId(), id));
        assertEquals(EstadoOferta.ACTIVA, ofertaRepo.findById(id).orElseThrow().getEstado());
    }

    @Test
    void elResumenCuentaLoMismoQueLaBase() {
        ResumenAdmin r = admin.resumen();
        assertEquals(usuarioRepo.count(), r.getUsuarios());
        assertEquals(ofertaRepo.countByEstado(EstadoOferta.ACTIVA), r.getOfertasActivas());
        assertTrue(r.getAdministradores() >= 1);
    }

    @Test
    void miCuentaSeAbreConLaSesionDelUsuario() throws Exception {
        mvc.perform(get("/cuenta").with(user(sesionDe(demo()))))
                .andExpect(status().isOk())
                .andExpect(view().name("cuenta"))
                .andExpect(model().attributeExists("perfil", "ofertas"));
    }

    @Test
    void elAdministradorVeSuPanelDeInicio() throws Exception {
        mvc.perform(get("/admin").with(user(sesionDe(adminUsuario()))))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-inicio"))
                .andExpect(model().attributeExists("resumen"));
    }

    @Test
    void retirarSinTokenCsrfSeRechaza() throws Exception {
        Long id = ofertaActivaDe(demo()).getId();
        mvc.perform(post("/cuenta/ofertas/" + id + "/retirar").with(user(sesionDe(demo()))))
                .andExpect(status().isForbidden());
        assertEquals(EstadoOferta.ACTIVA, ofertaRepo.findById(id).orElseThrow().getEstado());
    }

    @Test
    void retirarConTokenVuelveAMiCuenta() throws Exception {
        Long id = ofertaActivaDe(demo()).getId();
        mvc.perform(post("/cuenta/ofertas/" + id + "/retirar").with(user(sesionDe(demo()))).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals(EstadoOferta.ELIMINADA, ofertaRepo.findById(id).orElseThrow().getEstado());
    }
}
