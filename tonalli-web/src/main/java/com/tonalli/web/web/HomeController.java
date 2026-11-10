package com.tonalli.web.web;

import com.tonalli.web.dto.Formato;
import com.tonalli.web.dto.OfertaVista;
import com.tonalli.web.service.OfertaService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Catálogo público de ofertas. */
@Controller
public class HomeController {

    private final OfertaService ofertas;

    public HomeController(OfertaService ofertas) {
        this.ofertas = ofertas;
    }

    @GetMapping({"/", "/ofertas"})
    public String catalogo(@RequestParam(required = false) String q,
                           @RequestParam(required = false) Long categoria,
                           @RequestParam(defaultValue = "0") int pagina,
                           Model model) {
        Page<OfertaVista> resultado = ofertas.listar(q, categoria, pagina);
        model.addAttribute("pagina", resultado);
        model.addAttribute("categorias", ofertas.categorias());
        model.addAttribute("q", Formato.limpiarBusqueda(q));
        model.addAttribute("categoriaActual", categoria);
        return "catalogo";
    }
}
