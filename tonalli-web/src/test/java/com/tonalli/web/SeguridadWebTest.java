package com.tonalli.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de la seguridad de punta a punta, con la cadena de filtros real y la base H2 del perfil dev.
 * MockMvc no ejecuta el motor JSP: se comprueba el estado HTTP, las redirecciones y las cabeceras.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SeguridadWebTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void elCatalogoEsPublicoYLlevaCabecerasDeSeguridad() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("script-src 'self'")));
    }

    @Test
    void laApiPublicaDeOfertasNoPideSesion() throws Exception {
        mvc.perform(get("/api/ofertas").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void sinSesionElPanelDeAdminRedirigeAlLogin() throws Exception {
        mvc.perform(get("/admin/usuarios"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void sinSesionLaApiDeEscrituraResponde401() throws Exception {
        mvc.perform(post("/api/ofertas").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void unPostSinTokenCsrfSeRechaza() throws Exception {
        mvc.perform(post("/ofertas/1/voto").param("valor", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void unUsuarioComunNoEntraAlPanelDeAdmin() throws Exception {
        mvc.perform(get("/admin/usuarios")).andExpect(status().isForbidden());
        mvc.perform(get("/moderacion")).andExpect(status().isForbidden());
        mvc.perform(get("/api/estadisticas/ofertas-por-categoria")).andExpect(status().isForbidden());
    }

    @Test
    void elRegistroConContrasenaCortaMuestraElError() throws Exception {
        mvc.perform(post("/registro").with(csrf())
                        .param("nombre", "Ana")
                        .param("correo", "ana@correo.com")
                        .param("password", "corta")
                        .param("confirmar", "corta"))
                .andExpect(status().isOk())
                .andExpect(view().name("registro"))
                .andExpect(model().attributeHasFieldErrors("form", "password"));
    }

    @Test
    void unLoginIncorrectoVuelveAlFormularioConErrorGenerico() throws Exception {
        mvc.perform(post("/login").with(csrf())
                        .param("correo", "admin@tonalli.local")
                        .param("password", "incorrecta-incorrecta"))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void unLoginCorrectoEntraAlSitio() throws Exception {
        mvc.perform(post("/login").with(csrf())
                        .param("correo", "admin@tonalli.local")
                        .param("password", "CambiaEstaClave#2026"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void tras5FallosLaCuentaSeBloqueaAunqueLaContrasenaSeaCorrecta() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/login").with(csrf())
                    .param("correo", "demo@tonalli.local")
                    .param("password", "mal-mal-mal-mal"));
        }
        mvc.perform(post("/login").with(csrf())
                        .param("correo", "demo@tonalli.local")
                        .param("password", "DemoClave#2026!"))
                .andExpect(redirectedUrl("/login?error"));
    }
}
