package com.tonalli.web.api;

import com.tonalli.web.service.EstadisticasService;
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
}
