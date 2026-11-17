package com.tonalli.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonalli.web.model.Oferta;
import com.tonalli.web.repository.OfertaRepository;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
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

    @LocalServerPort
    private int puerto;

    /**
     * Inicia sesión como lo haría un navegador: pide /login, toma el token CSRF del formulario y lo envía con
     * las credenciales. Devuelve un cliente que guarda la cookie de sesión. Contraseñas del perfil dev.
     */
    private HttpClient conSesion(String correo, String password) throws Exception {
        HttpClient cliente = HttpClient.newBuilder().cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        String formulario = cliente.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/login")).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        Matcher m = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(formulario);
        assertTrue(m.find(), "El formulario de login debe traer el token CSRF");
        String cuerpo = "correo=" + URLEncoder.encode(correo, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8)
                + "&_csrf=" + URLEncoder.encode(m.group(1), StandardCharsets.UTF_8);
        HttpResponse<String> login = cliente.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(302, login.statusCode());
        assertTrue(!login.headers().firstValue("Location").orElse("").contains("error"), "El login debe ser correcto");
        return cliente;
    }

    private String paginaConSesion(HttpClient cliente, String ruta) throws Exception {
        HttpResponse<String> r = cliente.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode(), "La página " + ruta + " debe responder 200");
        return r.body();
    }

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
    void elUsuarioVeSuCuentaYNoElEnlaceDeAdministracion() throws Exception {
        HttpClient demo = conSesion("demo@tonalli.local", "DemoClave#2026!");
        String html = paginaConSesion(demo, "/cuenta");
        assertTrue(html.contains("<h1>Mi cuenta</h1>"));
        assertTrue(html.contains("Mis ofertas"));
        assertTrue(html.contains("Retirar"), "Sus ofertas activas se pueden retirar");
        assertTrue(!html.contains("Administración"), "Un usuario normal no ve el enlace al panel de administración");
    }

    @Test
    void elAdministradorVeSuPanelYSuDistintivo() throws Exception {
        HttpClient admin = conSesion("admin@tonalli.local", "CambiaEstaClave#2026");
        String html = paginaConSesion(admin, "/admin");
        assertTrue(html.contains("<h1>Administración</h1>"));
        assertTrue(html.contains("Usuarios registrados"));
        assertTrue(html.contains("chip-rol rol-ADMIN"), "La cabecera muestra el distintivo de administrador");
        assertTrue(paginaConSesion(admin, "/admin/usuarios").contains("← Administración"));
    }

    @Test
    void elMenuSinSesionNoMuestraLosPaneles() {
        String html = pagina("/");
        assertTrue(!html.contains("Mi cuenta") && !html.contains("Administración"),
                "Los enlaces a los paneles solo aparecen con sesión");
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
