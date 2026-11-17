package com.tonalli.web.api;

import com.tonalli.web.service.EstadisticasService;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Datos para los gráficos. Solo ADMIN (lo exige SecurityConfig para /api/estadisticas/**). */
@RestController
@RequestMapping("/api/estadisticas")
public class EstadisticasApiController {

    private final EstadisticasService estadisticas;

    public EstadisticasApiController(EstadisticasService estadisticas) {
        this.estadisticas = estadisticas;
    }

    @GetMapping("/ofertas-por-categoria")
    public Map<String, Long> ofertasPorCategoria() {
        return estadisticas.ofertasPorCategoria();
    }

    @GetMapping("/reportes-por-motivo")
    public Map<String, Long> reportesPorMotivo() {
        return estadisticas.reportesPorMotivo();
    }

    @GetMapping("/reportes-por-estado")
    public Map<String, Long> reportesPorEstado() {
        return estadisticas.reportesPorEstado();
    }

    @GetMapping("/usuarios-por-rol")
    public Map<String, Long> usuariosPorRol() {
        return estadisticas.usuariosPorRol();
    }

    @GetMapping("/ofertas-por-mes")
    public Map<String, Long> ofertasPorMes() {
        return estadisticas.ofertasPorMes();
    }

    @GetMapping("/temperatura")
    public Map<String, Long> distribucionTemperatura() {
        return estadisticas.distribucionTemperatura();
    }

    @GetMapping("/descuento-por-categoria")
    public Map<String, BigDecimal> descuentoPorCategoria() {
        return estadisticas.descuentoPorCategoria();
    }
}
