package com.tonalli.web.web;

import com.tonalli.web.dto.ArchivoDescarga;
import com.tonalli.web.dto.RangoFechas;
import com.tonalli.web.dto.TipoInforme;
import com.tonalli.web.security.UsuarioActual;
import com.tonalli.web.service.InformeService;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Sección de informes: gráficos, estadísticas y descarga de reportes en CSV.
 *
 * <p>Vive en {@code /admin/informes}, así que {@code SecurityConfig} ya exige rol ADMIN con la regla
 * {@code .requestMatchers("/admin/**").hasRole("ADMIN")}. Esa es la razón de colgarla de {@code /admin}
 * en vez de darle una ruta propia como {@code /informes}: una ruta nueva habría necesitado su propia
 * línea en la configuración, y una regla que se olvida de escribir es un panel de administración abierto.
 *
 * <p>Sobre el nombre: en este proyecto «reporte» ya significa la denuncia que un usuario hace de una
 * oferta ({@code Reporte}, {@code MotivoReporte}, {@code /moderacion}). Para que el código no tenga dos
 * cosas distintas con el mismo nombre, las clases y la ruta usan «informe» y la pantalla se titula
 * «Reportes y estadísticas», que es como lo pide el requisito.
 */
@Controller
public class InformeController {

    private final InformeService informes;

    public InformeController(InformeService informes) {
        this.informes = informes;
    }

    /**
     * Página de informes. El rango de fechas llega como {@code @ModelAttribute} para que el formulario
     * conserve lo que el usuario eligió al recargar, y la propia clase se encarga de normalizarlo.
     */
    @GetMapping("/admin/informes")
    public String informes(@ModelAttribute("rango") RangoFechas rango, Model model) {
        model.addAttribute("estadisticas", informes.generales());
        model.addAttribute("tipos", TipoInforme.values());
        model.addAttribute("maxFilas", InformeService.MAX_FILAS);
        model.addAttribute("diasPorOmision", RangoFechas.DIAS_POR_OMISION);
        return "admin-informes";
    }

    /**
     * Descarga de un informe.
     *
     * <p>Es un GET y no un POST porque no cambia nada en el servidor: así el enlace se puede compartir y
     * volver a abrir, y no necesita token CSRF (CSRF protege de que a alguien le hagan ejecutar una
     * acción sin querer, y aquí no hay acción que ejecutar). Lo que sí lleva son tres encabezados:
     *
     * <ul>
     *   <li>{@code Content-Disposition: attachment} con el nombre del archivo. El nombre lo arma el
     *       servicio a partir del enum, nunca de texto que venga del usuario, así que no hay manera de
     *       colar un salto de línea y partir la respuesta en dos (inyección de encabezados).</li>
     *   <li>{@code Cache-Control: no-store}. El informe de usuarios lleva correos: sin esto quedaría
     *       guardado en el disco del navegador y lo vería quien usara después ese equipo.</li>
     *   <li>{@code Content-Type: text/csv;charset=UTF-8}, declarado explícitamente para que el navegador
     *       no tenga que adivinar el tipo. Spring Security ya envía además {@code X-Content-Type-Options:
     *       nosniff}, que le prohíbe adivinar.</li>
     * </ul>
     */
    @GetMapping("/admin/informes/descargar")
    public ResponseEntity<ByteArrayResource> descargar(@RequestParam TipoInforme tipo,
                                                       @ModelAttribute("rango") RangoFechas rango,
                                                       @AuthenticationPrincipal UsuarioActual actor) {
        ArchivoDescarga archivo = informes.generar(actor.getId(), tipo, rango);
        ByteArrayResource cuerpo = new ByteArrayResource(archivo.getContenido());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(archivo.getNombre()).build().toString())
                .cacheControl(CacheControl.noStore())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .contentLength(archivo.getContenido().length)
                .body(cuerpo);
    }
}
