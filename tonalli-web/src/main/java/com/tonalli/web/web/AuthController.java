package com.tonalli.web.web;

import com.tonalli.web.dto.RegistroForm;
import com.tonalli.web.service.UsuarioService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Registro, verificación de correo y página de login (el POST de /login lo procesa Spring Security). */
@Controller
public class AuthController {

    /** BCrypt solo lee los primeros 72 bytes: una contraseña más larga se rechaza en vez de truncarse en silencio. */
    private static final int MAX_BYTES_PASSWORD = 72;

    private final UsuarioService usuarios;

    public AuthController(UsuarioService usuarios) {
        this.usuarios = usuarios;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/registro")
    public String registro(Model model) {
        model.addAttribute("form", new RegistroForm());
        return "registro";
    }

    @PostMapping("/registro")
    public String registrar(@Valid @ModelAttribute("form") RegistroForm form, BindingResult errores) {
        if (form.getPassword() != null && form.getConfirmar() != null
                && !form.getPassword().equals(form.getConfirmar())) {
            errores.rejectValue("confirmar", "no.coincide", "Las contraseñas no coinciden.");
        }
        if (form.getPassword() != null
                && form.getPassword().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES_PASSWORD) {
            errores.rejectValue("password", "demasiado.larga", "La contraseña es demasiado larga.");
        }
        if (errores.hasErrors()) {
            return "registro";
        }
        usuarios.registrar(form);
        // Patrón Post/Redirect/Get: evita reenviar el formulario al recargar la página.
        return "redirect:/registro/ok";
    }

    @GetMapping("/registro/ok")
    public String registroOk() {
        return "registro-ok";
    }

    @GetMapping("/verificar")
    public String verificar(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("verificado", usuarios.verificar(token));
        return "verificacion";
    }
}
