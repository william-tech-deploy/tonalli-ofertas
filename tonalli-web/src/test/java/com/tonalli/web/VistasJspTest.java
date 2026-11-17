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
    void publicarConFotoLaGuardaYLaSirveComoJpeg() throws Exception {
        HttpClient demo = conSesion("demo@tonalli.local", "DemoClave#2026!");
        String formulario = paginaConSesion(demo, "/ofertas/nueva");
        Matcher m = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(formulario);
        assertTrue(m.find());
        assertTrue(formulario.contains("enctype=\"multipart/form-data\""));

        // Una PNG de 1200 × 600 enviada como multipart, igual que la manda el navegador.
        java.awt.image.BufferedImage png = new java.awt.image.BufferedImage(1200, 600, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(png, "png", bytes);
        String limite = "----tonalli" + System.nanoTime();
        java.io.ByteArrayOutputStream cuerpo = new java.io.ByteArrayOutputStream();
        String[][] campos = {{"_csrf", m.group(1)}, {"titulo", "Prueba con foto"}, {"tienda", "Tienda de prueba"},
                {"precio", "10.00"}, {"precioAnterior", ""}, {"enlace", ""}, {"categoriaId", "1"}};
        for (String[] c : campos) {
            cuerpo.writeBytes(("--" + limite + "\r\nContent-Disposition: form-data; name=\"" + c[0] + "\"\r\n\r\n"
                    + c[1] + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        cuerpo.writeBytes(("--" + limite + "\r\nContent-Disposition: form-data; name=\"foto\"; filename=\"foto.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        cuerpo.writeBytes(bytes.toByteArray());
        cuerpo.writeBytes(("\r\n--" + limite + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpResponse<String> envio = demo.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/ofertas/nueva"))
                .header("Content-Type", "multipart/form-data; boundary=" + limite)
                .POST(HttpRequest.BodyPublishers.ofByteArray(cuerpo.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(302, envio.statusCode(), "Publicar con foto debe redirigir al detalle; respuesta: " + envio.body());
        String destino = envio.headers().firstValue("Location").orElseThrow();
        assertTrue(destino.matches(".*/ofertas/\\d+$"), destino);

        String detalle = paginaConSesion(demo, URI.create(destino).getPath());
        Matcher foto = Pattern.compile("src=\"(/imagenes/\\d+)\"").matcher(detalle);
        assertTrue(foto.find(), "El detalle debe mostrar la foto subida");

        // La foto se ve también sin sesión, como el catálogo, y siempre como JPEG.
        HttpResponse<byte[]> img = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + foto.group(1))).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, img.statusCode());
        assertEquals("image/jpeg", img.headers().firstValue("Content-Type").orElse(""));
        assertEquals((byte) 0xFF, img.body()[0]);
        assertEquals((byte) 0xD8, img.body()[1]);
    }

    /**
     * La página de informes dibujada por el motor JSP real. MockMvc no sirve para esto: no ejecuta las
     * JSP, así que un error de sintaxis en la plantilla o un acento roto solo se ve aquí.
     */
    @Test
    void elAdministradorVeLosSieteGraficosYElFormularioDeDescarga() throws Exception {
        HttpClient admin = conSesion("admin@tonalli.local", "CambiaEstaClave#2026");
        String html = paginaConSesion(admin, "/admin/informes");

        assertTrue(html.contains("<h1>Reportes y estadísticas</h1>"), "El título debe leerse en UTF-8");
        assertTrue(html.contains("Descuento promedio"), "Los indicadores se dibujan");
        assertEquals(7, html.split("data-url=", -1).length - 1, "La página declara siete gráficos");
        assertTrue(html.contains("/api/estadisticas/ofertas-por-mes"), "Cada gráfico apunta a su API");
        assertTrue(html.contains("/js/graficos.js"), "Y carga el script que los pinta");
        assertTrue(html.contains("Resumen estadístico"), "El desplegable ofrece los cuatro reportes");
        assertTrue(html.contains("name=\"desde\"") && html.contains("name=\"hasta\""),
                "El formulario deja elegir el periodo");
    }

    /** La ruta anterior de los gráficos sigue funcionando: ahora redirige a la sección de informes. */
    @Test
    void laRutaAnteriorDeGraficosRedirigeALosInformes() throws Exception {
        HttpClient admin = conSesion("admin@tonalli.local", "CambiaEstaClave#2026");
        HttpResponse<String> r = admin.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/admin/graficos")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(302, r.statusCode());
        assertTrue(r.headers().firstValue("Location").orElse("").endsWith("/admin/informes"),
                r.headers().firstValue("Location").orElse("(sin Location)"));
    }

    /**
     * Descarga real de un informe: comprueba las cabeceras que hacen que el navegador lo guarde en vez de
     * mostrarlo, que no lo deje en la caché del disco (lleva correos) y que el archivo empiece con el BOM.
     */
    @Test
    void elAdministradorDescargaUnInformeConLasCabecerasCorrectas() throws Exception {
        HttpClient admin = conSesion("admin@tonalli.local", "CambiaEstaClave#2026");
        HttpResponse<byte[]> csv = admin.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + puerto
                        + "/admin/informes/descargar?tipo=RESUMEN")).build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, csv.statusCode());
        assertTrue(csv.headers().firstValue("Content-Type").orElse("").startsWith("text/csv"),
                csv.headers().firstValue("Content-Type").orElse("(sin tipo)"));
        assertTrue(csv.headers().firstValue("Content-Disposition").orElse("").contains("attachment"),
                "Debe descargarse, no abrirse en el navegador");
        assertTrue(csv.headers().firstValue("Content-Disposition").orElse("").contains("tonalli-resumen"),
                csv.headers().firstValue("Content-Disposition").orElse("(sin nombre)"));
        assertTrue(csv.headers().firstValue("Cache-Control").orElse("").contains("no-store"),
                "Un informe no puede quedar guardado en el disco del navegador");
        assertEquals("nosniff", csv.headers().firstValue("X-Content-Type-Options").orElse(""));

        String contenido = new String(csv.body(), StandardCharsets.UTF_8);
        assertEquals('\uFEFF', contenido.charAt(0), "El BOM es lo que hace que Excel lea los acentos");
        assertTrue(contenido.contains("Tasa de verificación (%)"), "Y los acentos llegan bien");
    }

    /** Un tipo de informe que no existe se rechaza: la lista blanca la hace el propio enum. */
    @Test
    void unTipoDeInformeInventadoNoDevuelveArchivo() throws Exception {
        HttpClient admin = conSesion("admin@tonalli.local", "CambiaEstaClave#2026");
        HttpResponse<String> r = admin.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + puerto
                        + "/admin/informes/descargar?tipo=../../etc/passwd")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertTrue(r.statusCode() >= 400, "No puede responder 200 a un tipo inexistente: " + r.statusCode());
        assertTrue(!r.headers().firstValue("Content-Type").orElse("").startsWith("text/csv"),
                "Y menos devolver un archivo");
    }

    @Test
    void elIconoYLasIlustracionesSonPublicos() {
        assertEquals(HttpStatus.OK, http.getForEntity("/img/favicon.svg", String.class).getStatusCode());
        assertEquals(HttpStatus.OK, http.getForEntity("/favicon.ico", byte[].class).getStatusCode());
        String html = pagina("/");
        assertTrue(html.contains("/img/favicon.svg"), "La cabecera enlaza el ícono de la pestaña");
        assertTrue(html.contains("/img/categorias/"), "Las ofertas sin foto muestran la ilustración de su categoría");
    }

    @Test
    void elLoginEnModoDemoMuestraLosCorreosPeroNoLaContrasena() {
        String html = pagina("/login");
        assertTrue(html.contains("ana.martinez@tonalli.local"));
        assertTrue(!html.contains("DemoClave#2026!"));
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
