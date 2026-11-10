package com.tonalli.web.api;

import com.tonalli.web.dto.OfertaForm;
import com.tonalli.web.dto.OfertaVista;
import com.tonalli.web.dto.VotoForm;
import com.tonalli.web.model.Rol;
import com.tonalli.web.security.UsuarioActual;
import com.tonalli.web.service.OfertaService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST de ofertas (semanas 11-14). Usa exactamente los mismos servicios que las páginas JSP,
 * así que las reglas de negocio y de seguridad son idénticas. La autenticación es la sesión del
 * navegador; las peticiones que cambian datos deben enviar el token CSRF en la cabecera X-CSRF-TOKEN.
 */
@RestController
@RequestMapping("/api/ofertas")
public class OfertaApiController {

    private final OfertaService ofertas;

    public OfertaApiController(OfertaService ofertas) {
        this.ofertas = ofertas;
    }

    @GetMapping
    public Page<OfertaVista> listar(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) Long categoria,
                                    @RequestParam(defaultValue = "0") int pagina) {
        return ofertas.listar(q, categoria, pagina);
    }

    @GetMapping("/{id}")
    public OfertaVista detalle(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual usuario) {
        boolean esModerador = usuario != null && usuario.getRol() != Rol.USER;
        return ofertas.obtener(id, esModerador);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@Valid @RequestBody OfertaForm form,
                                                     @AuthenticationPrincipal UsuarioActual usuario) {
        Long id = ofertas.crear(usuario.getId(), form);
        return ResponseEntity.created(URI.create("/api/ofertas/" + id)).body(Map.of("id", id));
    }

    @PostMapping("/{id}/voto")
    public Map<String, Long> votar(@PathVariable Long id, @Valid @RequestBody VotoForm form,
                                   @AuthenticationPrincipal UsuarioActual usuario) {
        return Map.of("temperatura", ofertas.votar(usuario.getId(), id, form.getValor()));
    }
}
