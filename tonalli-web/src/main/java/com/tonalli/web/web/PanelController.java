package com.tonalli.web.web;

import com.tonalli.web.security.UsuarioActual;
import com.tonalli.web.service.PanelService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Panel "Mi cuenta" de cualquier usuario con sesión. SecurityConfig ya exige estar autenticado para /cuenta
 * (cae en anyRequest().authenticated()); el id del usuario sale siempre de la sesión.
 */
@Controller
public class PanelController {

    private final PanelService panel;

    public PanelController(PanelService panel) {
        this.panel = panel;
    }

    @GetMapping("/cuenta")
    public String cuenta(@AuthenticationPrincipal UsuarioActual actual, Model model) {
        model.addAttribute("perfil", panel.perfil(actual.getId()));
        model.addAttribute("ofertas", panel.misOfertas(actual.getId()));
        return "cuenta";
    }

    /** POST con token CSRF: un enlace o una imagen en otra página no pueden retirar ofertas por el usuario. */
    @PostMapping("/cuenta/ofertas/{id}/retirar")
    public String retirar(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual actual,
                          RedirectAttributes flash) {
        panel.retirar(actual.getId(), id);
        flash.addFlashAttribute("ok", "Oferta retirada del catálogo.");
        return "redirect:/cuenta";
    }
}
