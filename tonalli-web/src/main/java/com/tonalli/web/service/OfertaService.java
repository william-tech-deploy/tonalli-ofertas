package com.tonalli.web.service;

import com.tonalli.web.dto.CategoriaVista;
import com.tonalli.web.dto.ComentarioVista;
import com.tonalli.web.dto.FiltroOfertas;
import com.tonalli.web.dto.Formato;
import com.tonalli.web.dto.ImagenProcesada;
import com.tonalli.web.dto.OfertaForm;
import com.tonalli.web.dto.OfertaVista;
import com.tonalli.web.dto.OrdenOferta;
import com.tonalli.web.dto.TemperaturaFiltro;
import com.tonalli.web.exception.OperacionInvalidaException;
import com.tonalli.web.exception.RecursoNoEncontradoException;
import com.tonalli.web.model.Categoria;
import com.tonalli.web.model.Comentario;
import com.tonalli.web.model.EstadoOferta;
import com.tonalli.web.model.ImagenOferta;
import com.tonalli.web.model.MotivoReporte;
import com.tonalli.web.model.Oferta;
import com.tonalli.web.model.Reporte;
import com.tonalli.web.model.Usuario;
import com.tonalli.web.model.Voto;
import com.tonalli.web.repository.CategoriaRepository;
import com.tonalli.web.repository.ComentarioRepository;
import com.tonalli.web.repository.ImagenOfertaRepository;
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
    private final ImagenOfertaRepository imagenes;

    public OfertaService(OfertaRepository ofertas, CategoriaRepository categorias, UsuarioRepository usuarios,
                         VotoRepository votos, ComentarioRepository comentarios, ReporteRepository reportes,
                         ImagenOfertaRepository imagenes) {
        this.ofertas = ofertas;
        this.categorias = categorias;
        this.usuarios = usuarios;
        this.votos = votos;
        this.comentarios = comentarios;
        this.reportes = reportes;
        this.imagenes = imagenes;
    }

    /**
     * Bytes de una foto para servirla. Solo si su oferta está activa, o si quien la pide modera (puede ver
     * ofertas ocultas). Si no, "no encontrada": no se revela que la foto existe.
     */
    @Transactional(readOnly = true)
    public byte[] foto(Long imagenId, boolean esModerador) {
        ImagenOferta img = imagenes.findById(imagenId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Imagen no encontrada"));
        EstadoOferta estado = ofertas.findById(img.getOfertaId()).map(Oferta::getEstado).orElse(EstadoOferta.ELIMINADA);
        if (!(estado == EstadoOferta.ACTIVA || (esModerador && estado == EstadoOferta.OCULTA))) {
            throw new RecursoNoEncontradoException("Imagen no encontrada");
        }
        return img.getDatos();
    }

    @Transactional(readOnly = true)
    public List<CategoriaVista> categorias() {
        return categorias.findAllByOrderByNombreAsc().stream()
                .map(c -> new CategoriaVista(c.getId(), c.getNombre(), c.isBase()))
                .toList();
    }

    /** Búsqueda simple, sin filtros: la usan la API y las alertas. */
    @Transactional(readOnly = true)
    public Page<OfertaVista> listar(String q, Long categoriaId, int pagina) {
        FiltroOfertas filtro = new FiltroOfertas();
        filtro.setQ(q);
        filtro.setCategoriaId(categoriaId);
        filtro.setPagina(pagina);
        return listar(filtro);
    }

    /**
     * Catálogo con los filtros del buscador. Aquí se normaliza todo lo que escribió la persona:
     * la búsqueda pierde los comodines de LIKE, los precios pasan a centavos y se ordenan si vienen al revés,
     * y la página se recorta a un rango sano. El repositorio solo recibe valores ya limpios.
     */
    @Transactional(readOnly = true)
    public Page<OfertaVista> listar(FiltroOfertas filtro) {
        String busqueda = Formato.limpiarBusqueda(filtro.getQ());
        int precioMin = filtro.getPrecioMin() == null ? 0 : Math.max(0, Formato.centavos(filtro.getPrecioMin()));
        int precioMax = filtro.getPrecioMax() == null ? Integer.MAX_VALUE
                : Math.max(0, Formato.centavos(filtro.getPrecioMax()));
        if (precioMin > precioMax) { // "de 50 a 10": se entiende como "de 10 a 50" en vez de no devolver nada
            int intercambio = precioMin;
            precioMin = precioMax;
            precioMax = intercambio;
        }
        Boolean conDescuento = filtro.isSoloConDescuento() ? Boolean.TRUE : null;
        TemperaturaFiltro temp = filtro.getTemperatura();
        int pagina = Math.min(Math.max(filtro.getPagina(), 0), MAX_PAGINA);

        Page<Oferta> resultado = switch (filtro.getOrden()) {
            case TEMPERATURA -> ofertas.buscarPorTemperatura(EstadoOferta.ACTIVA, busqueda, filtro.getCategoriaId(),
                    precioMin, precioMax, conDescuento, temp.getMinimo(), temp.getMaximo(),
                    PageRequest.of(pagina, TAMANO_PAGINA));
            case DESCUENTO -> ofertas.buscarPorDescuento(EstadoOferta.ACTIVA, busqueda, filtro.getCategoriaId(),
                    precioMin, precioMax, conDescuento, temp.getMinimo(), temp.getMaximo(),
                    PageRequest.of(pagina, TAMANO_PAGINA));
            default -> ofertas.buscarConFiltros(EstadoOferta.ACTIVA, busqueda, filtro.getCategoriaId(),
                    precioMin, precioMax, conDescuento, temp.getMinimo(), temp.getMaximo(),
                    PageRequest.of(pagina, TAMANO_PAGINA, ordenSimple(filtro.getOrden())));
        };

        List<Long> ids = resultado.getContent().stream().map(Oferta::getId).toList();
        Map<Long, Long> temperaturas = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] fila : votos.temperaturas(ids)) {
                temperaturas.put((Long) fila[0], ((Number) fila[1]).longValue());
            }
        }
        return resultado.map(o -> vista(o, temperaturas.getOrDefault(o.getId(), 0L)));
    }

    /** Orden por una columna de la tabla. La temperatura y el descuento no lo son: tienen su propia consulta. */
    private static Sort ordenSimple(OrdenOferta orden) {
        return switch (orden) {
            case PRECIO_ASC -> Sort.by(Sort.Direction.ASC, "precioCentavos");
            case PRECIO_DESC -> Sort.by(Sort.Direction.DESC, "precioCentavos");
            default -> Sort.by(Sort.Direction.DESC, "creadaEn");
        };
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
        return crear(autorId, form, null);
    }

    /** Publica la oferta y, si viene, su foto ya limpiada por ImagenService. Todo en una sola transacción. */
    @Transactional
    public Long crear(Long autorId, OfertaForm form, ImagenProcesada foto) {
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
        Oferta guardada = ofertas.save(o);
        if (foto != null) {
            ImagenOferta img = imagenes.save(new ImagenOferta(guardada.getId(), foto.getJpeg(), foto.getAncho(), foto.getAlto()));
            guardada.setImagenId(img.getId());
        }
        return guardada.getId();
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
                temperatura, Formato.fecha(o.getCreadaEn()), o.getEstado(), o.getEnlace(), o.getImagenId());
    }

    private Oferta ofertaActiva(Long id) {
        return ofertas.findById(id)
                .filter(o -> o.getEstado() == EstadoOferta.ACTIVA)
                .orElseThrow(() -> new RecursoNoEncontradoException("Oferta no encontrada"));
    }
}
