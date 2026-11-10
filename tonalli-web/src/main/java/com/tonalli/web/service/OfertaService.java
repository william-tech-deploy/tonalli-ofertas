package com.tonalli.web.service;

import com.tonalli.web.dto.CategoriaVista;
import com.tonalli.web.dto.ComentarioVista;
import com.tonalli.web.dto.Formato;
import com.tonalli.web.dto.OfertaForm;
import com.tonalli.web.dto.OfertaVista;
import com.tonalli.web.exception.OperacionInvalidaException;
import com.tonalli.web.exception.RecursoNoEncontradoException;
import com.tonalli.web.model.Categoria;
import com.tonalli.web.model.Comentario;
import com.tonalli.web.model.EstadoOferta;
import com.tonalli.web.model.MotivoReporte;
import com.tonalli.web.model.Oferta;
import com.tonalli.web.model.Reporte;
import com.tonalli.web.model.Usuario;
import com.tonalli.web.model.Voto;
import com.tonalli.web.repository.CategoriaRepository;
import com.tonalli.web.repository.ComentarioRepository;
import com.tonalli.web.repository.OfertaRepository;
import com.tonalli.web.repository.ReporteRepository;
import com.tonalli.web.repository.UsuarioRepository;
import com.tonalli.web.repository.VotoRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de negocio de las ofertas. Lo usan igual los controladores JSP y la API REST:
 * por eso la lógica vive aquí y no en los controladores.
 * Regla de oro: el usuario que actúa SIEMPRE llega como argumento desde la sesión, nunca desde un formulario.
 */
@Service
public class OfertaService {

    static final int TAMANO_PAGINA = 12;
    static final int MAX_OFERTAS_POR_DIA = 10;
    static final int MAX_PAGINA = 10_000;

    private final OfertaRepository ofertas;
    private final CategoriaRepository categorias;
    private final UsuarioRepository usuarios;
    private final VotoRepository votos;
    private final ComentarioRepository comentarios;
    private final ReporteRepository reportes;

    public OfertaService(OfertaRepository ofertas, CategoriaRepository categorias, UsuarioRepository usuarios,
                         VotoRepository votos, ComentarioRepository comentarios, ReporteRepository reportes) {
        this.ofertas = ofertas;
        this.categorias = categorias;
        this.usuarios = usuarios;
        this.votos = votos;
        this.comentarios = comentarios;
        this.reportes = reportes;
    }

    @Transactional(readOnly = true)
    public List<CategoriaVista> categorias() {
        return categorias.findAllByOrderByNombreAsc().stream()
                .map(c -> new CategoriaVista(c.getId(), c.getNombre(), c.isBase()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<OfertaVista> listar(String q, Long categoriaId, int pagina) {
        String busqueda = Formato.limpiarBusqueda(q);
        Pageable pageable = PageRequest.of(Math.min(Math.max(pagina, 0), MAX_PAGINA), TAMANO_PAGINA,
                Sort.by(Sort.Direction.DESC, "creadaEn"));
        Page<Oferta> resultado = categoriaId == null
                ? ofertas.buscar(EstadoOferta.ACTIVA, busqueda, pageable)
                : ofertas.buscarPorCategoria(EstadoOferta.ACTIVA, categoriaId, busqueda, pageable);

        List<Long> ids = resultado.getContent().stream().map(Oferta::getId).toList();
        Map<Long, Long> temperaturas = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] fila : votos.temperaturas(ids)) {
                temperaturas.put((Long) fila[0], ((Number) fila[1]).longValue());
            }
        }
        return resultado.map(o -> vista(o, temperaturas.getOrDefault(o.getId(), 0L)));
    }

    /**
     * Devuelve una oferta. Las ofertas ocultas solo las ve un moderador; las eliminadas, nadie.
     * Si no es visible se responde "no encontrada", igual que si no existiera.
     */
    @Transactional(readOnly = true)
    public OfertaVista obtener(Long id, boolean esModerador) {
        Oferta o = ofertas.cargarConDetalle(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Oferta no encontrada"));
        boolean visible = o.getEstado() == EstadoOferta.ACTIVA
                || (esModerador && o.getEstado() == EstadoOferta.OCULTA);
        if (!visible) {
            throw new RecursoNoEncontradoException("Oferta no encontrada");
        }
        Long temperatura = votos.temperatura(id);
        return vista(o, temperatura == null ? 0L : temperatura);
    }

    @Transactional(readOnly = true)
    public List<ComentarioVista> comentarios(Long ofertaId) {
        return comentarios.findTop50ByOfertaIdOrderByCreadoEnDesc(ofertaId).stream()
                .map(c -> new ComentarioVista(c.getAutor().getNombre(), c.getTexto(), Formato.fecha(c.getCreadoEn())))
                .toList();
    }

    @Transactional
    public Long crear(Long autorId, OfertaForm form) {
        Instant hace24Horas = Instant.now().minus(Duration.ofHours(24));
        if (ofertas.countByAutorIdAndCreadaEnAfter(autorId, hace24Horas) >= MAX_OFERTAS_POR_DIA) {
            throw new OperacionInvalidaException("Alcanzaste el límite de " + MAX_OFERTAS_POR_DIA + " ofertas por día.");
        }
        Categoria categoria = categorias.findById(form.getCategoriaId())
                .orElseThrow(() -> new OperacionInvalidaException("La categoría elegida no existe."));
        int precio = Formato.centavos(form.getPrecio());
        Integer anterior = form.getPrecioAnterior() == null ? null : Formato.centavos(form.getPrecioAnterior());
        if (anterior != null && anterior <= precio) {
            throw new OperacionInvalidaException("El precio anterior debe ser mayor que el precio actual.");
        }

        Oferta o = new Oferta();
        o.setTitulo(Formato.limpiarTexto(form.getTitulo()));
        o.setTienda(Formato.limpiarTexto(form.getTienda()));
        o.setPrecioCentavos(precio);
        o.setPrecioAnteriorCentavos(anterior);
        o.setEnlace(Formato.enlaceONulo(form.getEnlace()));
        o.setCategoria(categoria);
        o.setAutor(usuarios.getReferenceById(autorId));
        return ofertas.save(o).getId();
    }

    /**
     * Vota (+1 caliente / -1 frío). Votar igual otra vez quita el voto; votar distinto lo cambia.
     * El peso del voto depende de la karma del votante. Devuelve la temperatura actualizada.
     */
    @Transactional
    public long votar(Long usuarioId, Long ofertaId, int valor) {
        if (valor != 1 && valor != -1) {
            throw new OperacionInvalidaException("Voto no válido.");
        }
        Oferta oferta = ofertaActiva(ofertaId);
        if (oferta.getAutor().getId().equals(usuarioId)) {
            throw new OperacionInvalidaException("No puedes votar tu propia oferta.");
        }
        Usuario votante = usuarios.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        Optional<Voto> existente = votos.findByUsuarioIdAndOfertaId(usuarioId, ofertaId);
        try {
            if (existente.isPresent() && existente.get().getValor() == valor) {
                votos.delete(existente.get());
            } else if (existente.isPresent()) {
                existente.get().setValor(valor);
                existente.get().setPeso(pesoPorKarma(votante.getKarma()));
            } else {
                Voto nuevo = new Voto();
                nuevo.setUsuario(votante);
                nuevo.setOferta(oferta);
                nuevo.setValor(valor);
                nuevo.setPeso(pesoPorKarma(votante.getKarma()));
                votos.save(nuevo);
            }
            votos.flush();
        } catch (DataIntegrityViolationException e) {
            throw new OperacionInvalidaException("Tu voto ya estaba registrado.");
        }
        Long temperatura = votos.temperatura(ofertaId);
        return temperatura == null ? 0L : temperatura;
    }

    @Transactional
    public void comentar(Long usuarioId, Long ofertaId, String texto) {
        Oferta oferta = ofertaActiva(ofertaId);
        String limpio = Formato.limpiarTexto(texto);
        if (limpio.isEmpty()) {
            throw new OperacionInvalidaException("El comentario no puede estar vacío.");
        }
        Comentario c = new Comentario();
        c.setOferta(oferta);
        c.setAutor(usuarios.getReferenceById(usuarioId));
        c.setTexto(limpio);
        comentarios.save(c);
    }

    @Transactional
    public void reportar(Long usuarioId, Long ofertaId, MotivoReporte motivo) {
        Oferta oferta = ofertaActiva(ofertaId);
        if (reportes.existsByReportanteIdAndOfertaId(usuarioId, ofertaId)) {
            throw new OperacionInvalidaException("Ya reportaste esta oferta.");
        }
        Reporte r = new Reporte();
        r.setOferta(oferta);
        r.setReportante(usuarios.getReferenceById(usuarioId));
        r.setMotivo(motivo);
        try {
            reportes.saveAndFlush(r);
        } catch (DataIntegrityViolationException e) {
            throw new OperacionInvalidaException("Ya reportaste esta oferta.");
        }
    }

    /** Peso del voto según la karma del votante: 1 (nuevo), 2 (desde 10) o 3 (desde 50). */
    public static int pesoPorKarma(int karma) {
        if (karma >= 50) {
            return 3;
        }
        return karma >= 10 ? 2 : 1;
    }

    static OfertaVista vista(Oferta o, long temperatura) {
        return new OfertaVista(o.getId(), o.getTitulo(), o.getTienda(), o.getCategoria().getNombre(),
                o.getAutor().getNombre(), o.getAutor().getId(), o.getPrecioCentavos(), o.getPrecioAnteriorCentavos(),
                temperatura, Formato.fecha(o.getCreadaEn()), o.getEstado(), o.getEnlace());
    }

    private Oferta ofertaActiva(Long id) {
        return ofertas.findById(id)
                .filter(o -> o.getEstado() == EstadoOferta.ACTIVA)
                .orElseThrow(() -> new RecursoNoEncontradoException("Oferta no encontrada"));
    }
}
