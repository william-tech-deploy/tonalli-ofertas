package com.tonalli.web.service;

import com.tonalli.web.dto.Formato;
import com.tonalli.web.dto.OfertaVista;
import com.tonalli.web.dto.PerfilVista;
import com.tonalli.web.exception.RecursoNoEncontradoException;
import com.tonalli.web.model.EstadoOferta;
import com.tonalli.web.model.Oferta;
import com.tonalli.web.model.Usuario;
import com.tonalli.web.repository.OfertaRepository;
import com.tonalli.web.repository.UsuarioRepository;
import com.tonalli.web.repository.VotoRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Panel "Mi cuenta": lo que cada usuario ve y puede hacer sobre lo suyo.
 *
 * Regla de seguridad de toda la clase: el usuario SIEMPRE llega desde la sesión (el controlador pasa el id
 * de UsuarioActual), nunca desde un parámetro de la petición. Así nadie puede ver ni retirar ofertas ajenas
 * cambiando un número en la URL (OWASP A01: control de acceso roto).
 */
@Service
public class PanelService {

    private static final Logger AUDITORIA = LoggerFactory.getLogger("AUDITORIA");
    /** Tope de filas en "Mis ofertas": con el límite de 10 publicaciones al día alcanza para varias semanas. */
    private static final int MAX_MIS_OFERTAS = 50;

    private final UsuarioRepository usuarios;
    private final OfertaRepository ofertas;
    private final VotoRepository votos;

    public PanelService(UsuarioRepository usuarios, OfertaRepository ofertas, VotoRepository votos) {
        this.usuarios = usuarios;
        this.ofertas = ofertas;
        this.votos = votos;
    }

    @Transactional(readOnly = true)
    public PerfilVista perfil(Long usuarioId) {
        Usuario u = usuarios.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        return new PerfilVista(u.getNombre(), u.getCorreo(), u.getRol(), u.getKarma(),
                OfertaService.pesoPorKarma(u.getKarma()), Formato.fecha(u.getCreadoEn()));
    }

    /** Ofertas activas y ocultas del usuario, con su temperatura. Las eliminadas no se muestran. */
    @Transactional(readOnly = true)
    public List<OfertaVista> misOfertas(Long usuarioId) {
        List<Oferta> lista = ofertas.findByAutorIdAndEstadoNotOrderByCreadaEnDesc(
                usuarioId, EstadoOferta.ELIMINADA, PageRequest.of(0, MAX_MIS_OFERTAS));
        Map<Long, Long> temperaturas = new HashMap<>();
        if (!lista.isEmpty()) {
            // Una sola consulta para todas las temperaturas (evita una consulta por oferta).
            for (Object[] fila : votos.temperaturas(lista.stream().map(Oferta::getId).toList())) {
                temperaturas.put((Long) fila[0], ((Number) fila[1]).longValue());
            }
        }
        return lista.stream().map(o -> OfertaService.vista(o, temperaturas.getOrDefault(o.getId(), 0L))).toList();
    }

    /**
     * El autor retira su propia oferta (por ejemplo, porque la tienda terminó la promoción).
     * Solo funciona sobre una oferta ACTIVA del mismo usuario. Si la oferta no existe, es de otra persona
     * o ya no está activa, la respuesta es la misma "no encontrada": no se revela qué ofertas existen.
     */
    @Transactional
    public void retirar(Long usuarioId, Long ofertaId) {
        Oferta o = ofertas.findById(ofertaId)
                .filter(x -> x.getAutor().getId().equals(usuarioId))
                .filter(x -> x.getEstado() == EstadoOferta.ACTIVA)
                .orElseThrow(() -> new RecursoNoEncontradoException("Oferta no encontrada"));
        o.setEstado(EstadoOferta.ELIMINADA);
        AUDITORIA.info("usuario={} retiro su oferta={}", usuarioId, ofertaId);
    }
}
