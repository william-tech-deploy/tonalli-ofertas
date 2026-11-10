package com.tonalli.web.web;

import com.tonalli.web.dto.AlertaForm;
import com.tonalli.web.dto.NombreCategoriaForm;
import com.tonalli.web.exception.OperacionInvalidaException;
import com.tonalli.web.security.UsuarioActual;
import com.tonalli.web.service.CuentaService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Alertas y propuestas de categorías del usuario conectado. */
@Controller
public class CuentaController {

    private final CuentaService cuenta;

    public CuentaController(CuentaService cuenta) {
        this.cuenta = cuenta;
    }

    @GetMapping("/alertas")
    public String alertas(@AuthenticationPrincipal UsuarioActual usuario, Model model) {
        model.addAttribute("alertas", cuenta.alertas(usuario.getId()));
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new AlertaForm());
        }
        return "alertas";
    }

    @PostMapping("/alertas")
    public String crearAlerta(@Valid @ModelAttribute("form") AlertaForm form, BindingResult errores,
                              @AuthenticationPrincipal UsuarioActual usuario, Model model) {
        if (!errores.hasErrors()) {
            try {
                cuenta.crearAlerta(usuario.getId(), form);
                return "redirect:/alertas";
            } catch (OperacionInvalidaException e) {
                model.addAttribute("error", e.getMessage());
            }
        }
        model.addAttribute("alertas", cuenta.alertas(usuario.getId()));
        return "alertas";
    }

    @PostMapping("/alertas/{id}/eliminar")
    public String eliminarAlerta(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual usuario) {
        cuenta.eliminarAlerta(usuario.getId(), id);
        return "redirect:/alertas";
    }

    @GetMapping("/propuestas")
    public String propuestas(@AuthenticationPrincipal UsuarioActual usuario, Model model) {
        model.addAttribute("propuestas", cuenta.propuestas(usuario.getId()));
        model.addAttribute("form", new NombreCategoriaForm());
        return "propuestas";
    }

    @PostMapping("/propuestas")
    public String proponer(@Valid @ModelAttribute("form") NombreCategoriaForm form, BindingResult errores,
                           @AuthenticationPrincipal UsuarioActual usuario, Model model) {
        if (!errores.hasErrors()) {
            try {
                cuenta.proponer(usuario.getId(), form);
                return "redirect:/propuestas";
            } catch (OperacionInvalidaException e) {
                model.addAttribute("error", e.getMessage());
            }
        }
        model.addAttribute("propuestas", cuenta.propuestas(usuario.getId()));
        return "propuestas";
    }

    @PostMapping("/propuestas/{id}/apoyar")
    public String apoyar(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual usuario,
                         RedirectAttributes flash) {
        cuenta.apoyar(usuario.getId(), id);
        flash.addFlashAttribute("ok", "Apoyo registrado.");
        return "redirect:/propuestas";
    }
}
