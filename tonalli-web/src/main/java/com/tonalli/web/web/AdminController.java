package com.tonalli.web.web;

import com.tonalli.web.dto.NombreCategoriaForm;
import com.tonalli.web.exception.OperacionInvalidaException;
import com.tonalli.web.model.Rol;
import com.tonalli.web.security.UsuarioActual;
import com.tonalli.web.service.AdminService;
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

/** Panel del administrador. SecurityConfig exige rol ADMIN para todo /admin/**. */
@Controller
public class AdminController {

    private final AdminService admin;

    public AdminController(AdminService admin) {
        this.admin = admin;
    }

    @GetMapping("/admin/usuarios")
    public String usuarios(@RequestParam(defaultValue = "0") int pagina, Model model) {
        model.addAttribute("pagina", admin.usuarios(pagina));
        model.addAttribute("roles", Rol.values());
        return "admin-usuarios";
    }

    @PostMapping("/admin/usuarios/{id}/rol")
    public String cambiarRol(@PathVariable Long id, @RequestParam Rol rol,
                             @AuthenticationPrincipal UsuarioActual actor, RedirectAttributes flash) {
        try {
            admin.cambiarRol(actor.getId(), id, rol);
            flash.addFlashAttribute("ok", "Rol actualizado.");
        } catch (OperacionInvalidaException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/usuarios";
    }

    @GetMapping("/admin/categorias")
    public String categorias(Model model) {
        cargarCategorias(model);
        model.addAttribute("form", new NombreCategoriaForm());
        return "admin-categorias";
    }

    @PostMapping("/admin/categorias")
    public String crearCategoria(@Valid @ModelAttribute("form") NombreCategoriaForm form, BindingResult errores,
                                 @AuthenticationPrincipal UsuarioActual actor, Model model) {
        if (!errores.hasErrors()) {
            try {
                admin.crearCategoria(actor.getId(), form.getNombre());
                return "redirect:/admin/categorias";
            } catch (OperacionInvalidaException e) {
                model.addAttribute("error", e.getMessage());
            }
        }
        cargarCategorias(model);
        return "admin-categorias";
    }

    @PostMapping("/admin/categorias/{id}/eliminar")
    public String eliminarCategoria(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual actor,
                                    RedirectAttributes flash) {
        try {
            admin.eliminarCategoria(actor.getId(), id);
        } catch (OperacionInvalidaException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/categorias";
    }

    @PostMapping("/admin/propuestas/{id}/aprobar")
    public String aprobar(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual actor, RedirectAttributes flash) {
        try {
            admin.aprobarPropuesta(actor.getId(), id);
        } catch (OperacionInvalidaException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/categorias";
    }

    @PostMapping("/admin/propuestas/{id}/rechazar")
    public String rechazar(@PathVariable Long id, @AuthenticationPrincipal UsuarioActual actor, RedirectAttributes flash) {
        try {
            admin.rechazarPropuesta(actor.getId(), id);
        } catch (OperacionInvalidaException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/categorias";
    }

    @GetMapping("/admin/graficos")
    public String graficos() {
        return "admin-graficos";
    }

    private void cargarCategorias(Model model) {
        model.addAttribute("categorias", admin.categorias());
        model.addAttribute("propuestas", admin.propuestasPendientes());
    }
}
