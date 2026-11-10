package com.tonalli.web.service;

import com.tonalli.web.dto.CategoriaVista;
import com.tonalli.web.dto.Formato;
import com.tonalli.web.dto.PropuestaVista;
import com.tonalli.web.dto.UsuarioVista;
import com.tonalli.web.exception.OperacionInvalidaException;
import com.tonalli.web.exception.RecursoNoEncontradoException;
import com.tonalli.web.model.Categoria;
import com.tonalli.web.model.EstadoPropuesta;
import com.tonalli.web.model.Propuesta;
import com.tonalli.web.model.Rol;
import com.tonalli.web.model.Usuario;
import com.tonalli.web.repository.CategoriaRepository;
import com.tonalli.web.repository.OfertaRepository;
import com.tonalli.web.repository.PropuestaRepository;
import com.tonalli.web.repository.UsuarioRepository;
import com.tonalli.web.security.UsuarioActual;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operaciones del administrador: usuarios y roles, categorías y propuestas. */
@Service
public class AdminService {

    private static final Logger AUDITORIA = LoggerFactory.getLogger("AUDITORIA");
    private static final int TAMANO_PAGINA = 20;

    private final UsuarioRepository usuarios;
    private final CategoriaRepository categorias;
    private final OfertaRepository ofertas;
    private final PropuestaRepository propuestas;
    private final SessionRegistry sesiones;

    public AdminService(UsuarioRepository usuarios, CategoriaRepository categorias, OfertaRepository ofertas,
                        PropuestaRepository propuestas, SessionRegistry sesiones) {
        this.usuarios = usuarios;
        this.categorias = categorias;
        this.ofertas = ofertas;
        this.propuestas = propuestas;
        this.sesiones = sesiones;
    }

    @Transactional(readOnly = true)
    public Page<UsuarioVista> usuarios(int pagina) {
        PageRequest pageable = PageRequest.of(Math.min(Math.max(pagina, 0), 10_000), TAMANO_PAGINA, Sort.by("id"));
        return usuarios.findAll(pageable)
                .map(u -> new UsuarioVista(u.getId(), u.getNombre(), u.getCorreo(), u.getRol(), u.isVerificado()));
    }

    /**
     * Cambia el rol de un usuario. Reglas: nadie cambia su propio rol y nunca puede quedar el sistema sin
     * administradores. Al cambiar el rol se cierran las sesiones abiertas de ese usuario, porque Spring
     * guarda los permisos en la sesión y, si no, un administrador degradado conservaría su acceso.
     */
    @Transactional
    public void cambiarRol(Long actorId, Long objetivoId, Rol nuevoRol) {
        if (actorId.equals(objetivoId)) {
            throw new OperacionInvalidaException("No puedes cambiar tu propio rol.");
        }
        Usuario objetivo = usuarios.findById(objetivoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        if (objetivo.getRol() == nuevoRol) {
            return;
        }
        if (objetivo.getRol() == Rol.ADMIN && usuarios.countByRol(Rol.ADMIN) <= 1) {
            throw new OperacionInvalidaException("Debe quedar al menos un administrador.");
        }
        Rol anterior = objetivo.getRol();
        objetivo.setRol(nuevoRol);
        cerrarSesiones(objetivoId);
        AUDITORIA.info("admin={} usuario={} rol {} -> {}", actorId, objetivoId, anterior, nuevoRol);
    }

    @Transactional(readOnly = true)
    public List<CategoriaVista> categorias() {
        return categorias.findAllByOrderByNombreAsc().stream()
                .map(c -> new CategoriaVista(c.getId(), c.getNombre(), c.isBase()))
                .toList();
    }

    @Transactional
    public void crearCategoria(Long adminId, String nombre) {
        String limpio = normalizarNombre(nombre);
        if (categorias.existsByNombreIgnoreCase(limpio)) {
            throw new OperacionInvalidaException("Ya existe una categoría con ese nombre.");
        }
        categorias.save(new Categoria(limpio, false));
        AUDITORIA.info("admin={} categoria creada={}", adminId, limpio);
    }

    @Transactional
    public void eliminarCategoria(Long adminId, Long categoriaId) {
        Categoria c = categorias.findById(categoriaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Categoría no encontrada"));
        if (c.isBase()) {
            throw new OperacionInvalidaException("Las categorías base no se pueden eliminar.");
        }
        if (ofertas.existsByCategoriaId(categoriaId)) {
            throw new OperacionInvalidaException("La categoría tiene ofertas y no se puede eliminar.");
        }
        categorias.delete(c);
        AUDITORIA.info("admin={} categoria eliminada={}", adminId, categoriaId);
    }

    @Transactional(readOnly = true)
    public List<PropuestaVista> propuestasPendientes() {
        return propuestas.findByEstadoOrderByCreadaEnDesc(EstadoPropuesta.PENDIENTE).stream()
                .map(p -> new PropuestaVista(p.getId(), p.getNombre(), p.getAutor().getNombre(), p.getApoyos().size(), false))
                .toList();
    }

    @Transactional
    public void aprobarPropuesta(Long adminId, Long propuestaId) {
        Propuesta p = propuestaPendiente(propuestaId);
        if (!categorias.existsByNombreIgnoreCase(p.getNombre())) {
            categorias.save(new Categoria(p.getNombre(), false));
        }
        p.setEstado(EstadoPropuesta.APROBADA);
        AUDITORIA.info("admin={} propuesta aprobada={}", adminId, propuestaId);
    }

    @Transactional
    public void rechazarPropuesta(Long adminId, Long propuestaId) {
        Propuesta p = propuestaPendiente(propuestaId);
        p.setEstado(EstadoPropuesta.RECHAZADA);
        AUDITORIA.info("admin={} propuesta rechazada={}", adminId, propuestaId);
    }

    static String normalizarNombre(String nombre) {
        return Formato.limpiarTexto(nombre).replaceAll("\\s+", " ");
    }

    private Propuesta propuestaPendiente(Long id) {
        Propuesta p = propuestas.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Propuesta no encontrada"));
        if (p.getEstado() != EstadoPropuesta.PENDIENTE) {
            throw new OperacionInvalidaException("Esa propuesta ya fue resuelta.");
        }
        return p;
    }

    private void cerrarSesiones(Long usuarioId) {
        for (Object principal : sesiones.getAllPrincipals()) {
            if (principal instanceof UsuarioActual actual && usuarioId.equals(actual.getId())) {
                for (SessionInformation sesion : sesiones.getAllSessions(principal, false)) {
                    sesion.expireNow();
                }
            }
        }
    }
}
