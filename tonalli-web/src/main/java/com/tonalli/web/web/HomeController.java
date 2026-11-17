package com.tonalli.web.web;

import com.tonalli.web.dto.FiltroOfertas;
import com.tonalli.web.dto.Formato;
import com.tonalli.web.dto.OfertaVista;
import com.tonalli.web.dto.OrdenOferta;
import com.tonalli.web.dto.TemperaturaFiltro;
import com.tonalli.web.service.OfertaService;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Catálogo público de ofertas, con el buscador y sus filtros. */
@Controller
public class HomeController {

    private final OfertaService ofertas;

    public HomeController(OfertaService ofertas) {
        this.ofertas = ofertas;
    }

    @GetMapping({"/", "/ofertas"})
    public String catalogo(@RequestParam(required = false) String q,
                           @RequestParam(required = false) Long categoria,
                           @RequestParam(required = false) String precioMin,
                           @RequestParam(required = false) String precioMax,
                           @RequestParam(required = false) String conDescuento,
                           @RequestParam(required = false) String temperatura,
                           @RequestParam(required = false) String orden,
                           @RequestParam(defaultValue = "0") int pagina,
                           Model model) {
        FiltroOfertas filtro = new FiltroOfertas();
        filtro.setQ(q);
        filtro.setCategoriaId(categoria);
        // Los precios llegan como texto: si alguien escribe "diez" o pega algo raro, ese filtro se ignora
        // en vez de romper la página con un error.
        filtro.setPrecioMin(aPrecio(precioMin));
        filtro.setPrecioMax(aPrecio(precioMax));
        filtro.setSoloConDescuento(conDescuento != null);
        filtro.setTemperatura(TemperaturaFiltro.desdeTexto(temperatura));
        filtro.setOrden(OrdenOferta.desdeTexto(orden));
        filtro.setPagina(pagina);

        Page<OfertaVista> resultado = ofertas.listar(filtro);
        model.addAttribute("pagina", resultado);
        model.addAttribute("categorias", ofertas.categorias());
        model.addAttribute("filtro", filtro);
        model.addAttribute("q", Formato.limpiarBusqueda(q));
        model.addAttribute("ordenes", OrdenOferta.values());
        model.addAttribute("temperaturas", TemperaturaFiltro.values());
        return "catalogo";
    }

    private static BigDecimal aPrecio(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            BigDecimal valor = new BigDecimal(texto.trim().replace(',', '.'));
            return valor.signum() < 0 ? null : valor;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
