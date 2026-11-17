package com.tonalli.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonalli.web.model.Oferta;
import com.tonalli.web.repository.OfertaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Pruebas con el servidor Tomcat real y el motor JSP real (las demás usan MockMvc, que no dibuja los JSP).
 * Existen porque dos fallos solo aparecían en el navegador y ninguna prueba los vio:
 * el bucle de redirecciones al reenviar a /WEB-INF/jsp y los acentos rotos en los fragmentos compartidos.
 * Usa su propia base H2 para no tocar los datos de las otras pruebas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:tonalli-vistas;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("dev")
class VistasJspTest {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private OfertaRepository ofertas;

    private String pagina(String ruta) {
        ResponseEntity<String> respuesta = http.getForEntity(ruta, String.class);
        assertEquals(HttpStatus.OK, respuesta.getStatusCode(), "La página " + ruta + " debe responder 200");
        assertNotNull(respuesta.getBody());
        return respuesta.getBody();
    }

    @Test
    void elCatalogoSeDibujaSinBucleDeRedirecciones() {
        String html = pagina("/");
        assertTrue(html.contains("<h1>Ofertas</h1>"));
        assertTrue(html.contains("class=\"oferta\""), "Deben verse las ofertas de ejemplo");
    }

    @Test
    void laCabeceraYElPieConservanLosAcentos() {
        String html = pagina("/");
        assertTrue(html.contains("Iniciar sesión"), "La cabecera debe leerse en UTF-8");
        assertTrue(html.contains("Utilización de Frameworks"), "El pie debe leerse en UTF-8");
    }

    @Test
    void elFormularioDeLoginSeDibuja() {
        assertTrue(pagina("/login").contains("name=\"correo\""));
    }

    @Test
    void elDetalleEnlazaALaTiendaSinCederDatosDeLaPagina() {
        Oferta conEnlace = ofertas.findAll().stream().filter(o -> o.getEnlace() != null).findFirst().orElseThrow();
        String html = pagina("/ofertas/" + conEnlace.getId());
        assertTrue(html.contains("href=\"" + conEnlace.getEnlace() + "\""));
        assertTrue(html.contains("rel=\"noopener noreferrer nofollow ugc\""));
    }
}
