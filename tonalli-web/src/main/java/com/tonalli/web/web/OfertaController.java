package com.tonalli.web.web;

import com.tonalli.web.dto.ComentarioForm;
import com.tonalli.web.dto.OfertaForm;
import com.tonalli.web.dto.ReporteForm;
import com.tonalli.web.exception.OperacionInvalidaException;
import com.tonalli.web.model.MotivoReporte;
import com.tonalli.web.model.Rol;
import com.tonalli.web.security.UsuarioActual;
import com.tonalli.web.service.OfertaService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Detalle, publicación, votos, comentarios y reportes. El usuario sale siempre de la sesión
 * (@AuthenticationPrincipal): ningún formulario trae un "usuarioId" que alguien pudiera falsificar.
 */
@Controller
public class OfertaController {

    private final OfertaService ofertas;

    public OfertaController(OfertaService ofertas) {
        this.ofertas = ofertas;
    }

    @GetMapping("/ofertas/{id}")
    public String detalle(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual usuario, Model model) {
        boolean esModerador = usuario != null && usuario.getRol() != Rol.USER;
        model.addAttribute("oferta", ofertas.obtener(id, esModerador));
        model.addAttribute("comentarios", ofertas.comentarios(id));
        model.addAttribute("comentarioForm", new ComentarioForm());
        model.addAttribute("reporteForm", new ReporteForm());
        model.addAttribute("motivos", MotivoReporte.values());
        return "oferta";
    }

    @GetMapping("/ofertas/nueva")
    public String nueva(Model model) {
        model.addAttribute("form", new OfertaForm());
        model.addAttribute("categorias", ofertas.categorias());
        return "oferta-nueva";
    }

    @PostMapping("/ofertas/nueva")
    public String crear(@Valid @ModelAttribute("form") OfertaForm form, BindingResult errores,
                        @AuthenticationPrincipal UsuarioActual usuario, Model model) {
        if (!errores.hasErrors()) {
            try {
                Long id = ofertas.crear(usuario.getId(), form);
                return "redirect:/ofertas/" + id;
            } catch (OperacionInvalidaException e) {
                model.addAttribute("error", e.getMessage());
            }
        }
        model.addAttribute("categorias", ofertas.categorias());
        return "oferta-nueva";
    }

    @PostMapping("/ofertas/{id}/voto")
    public String votar(@PathVariable Long id, @RequestParam int valor,
                        @AuthenticationPrincipal UsuarioActual usuario, RedirectAttributes flash) {
        try {
            ofertas.votar(usuario.getId(), id, valor);
        } catch (OperacionInvalidaException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ofertas/" + id;
    }

    @PostMapping("/ofertas/{id}/comentarios")
    public String comentar(@PathVariable Long id, @Valid @ModelAttribute("comentarioForm") ComentarioForm form,
                           BindingResult errores, @AuthenticationPrincipal UsuarioActual usuario,
                           RedirectAttributes flash) {
        if (errores.hasErrors()) {
            flash.addFlashAttribute("error", errores.getAllErrors().get(0).getDefaultMessage());
        } else {
            try {
                ofertas.comentar(usuario.getId(), id, form.getTexto());
            } catch (OperacionInvalidaException e) {
                flash.addFlashAttribute("error", e.getMessage());
            }
        }
        return "redirect:/ofertas/" + id;
    }

    @PostMapping("/ofertas/{id}/reportes")
    public String reportar(@PathVariable Long id, @Valid @ModelAttribute("reporteForm") ReporteForm form,
                           BindingResult errores, @AuthenticationPrincipal UsuarioActual usuario,
                           RedirectAttributes flash) {
        if (errores.hasErrors()) {
            flash.addFlashAttribute("error", "Elige un motivo para el reporte.");
        } else {
            try {
                ofertas.reportar(usuario.getId(), id, form.getMotivo());
                flash.addFlashAttribute("ok", "Gracias, revisaremos tu reporte.");
            } catch (OperacionInvalidaException e) {
                flash.addFlashAttribute("error", e.getMessage());
            }
        }
        return "redirect:/ofertas/" + id;
    }
}
