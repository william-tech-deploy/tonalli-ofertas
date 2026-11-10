package com.tonalli.web.web;

import com.tonalli.web.exception.OperacionInvalidaException;
import com.tonalli.web.model.EstadoOferta;
import com.tonalli.web.security.UsuarioActual;
import com.tonalli.web.service.ModeracionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Panel de moderación. SecurityConfig exige rol MODERADOR o ADMIN para todo /moderacion/**. */
@Controller
public class ModeracionController {

    private final ModeracionService moderacion;

    public ModeracionController(ModeracionService moderacion) {
        this.moderacion = moderacion;
    }

    @GetMapping("/moderacion")
    public String panel(Model model) {
        model.addAttribute("reportes", moderacion.reportesAbiertos());
        model.addAttribute("ocultas", moderacion.ofertasOcultas());
        return "moderacion";
    }

    @PostMapping("/moderacion/reportes/{id}/ocultar")
    public String ocultar(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual usuario, RedirectAttributes flash) {
        return resolver(id, true, usuario, flash);
    }

    @PostMapping("/moderacion/reportes/{id}/descartar")
    public String descartar(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual usuario, RedirectAttributes flash) {
        return resolver(id, false, usuario, flash);
    }

    @PostMapping("/moderacion/ofertas/{id}/restaurar")
    public String restaurar(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual usuario, RedirectAttributes flash) {
        return cambiar(id, EstadoOferta.ACTIVA, usuario, flash);
    }

    @PostMapping("/moderacion/ofertas/{id}/eliminar")
    public String eliminar(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual usuario, RedirectAttributes flash) {
        return cambiar(id, EstadoOferta.ELIMINADA, usuario, flash);
    }

    private String resolver(Long id, boolean ocultar, UsuarioActual usuario, RedirectAttributes flash) {
        try {
            moderacion.resolverReporte(usuario.getId(), id, ocultar);
        } catch (OperacionInvalidaException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/moderacion";
    }

    private String cambiar(Long id, EstadoOferta estado, UsuarioActual usuario, RedirectAttributes flash) {
        try {
            moderacion.cambiarEstadoOferta(usuario.getId(), id, estado);
        } catch (OperacionInvalidaException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/moderacion";
    }
}
