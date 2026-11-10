package com.tonalli.web.service;

import com.tonalli.web.dto.AlertaForm;
import com.tonalli.web.dto.AlertaVista;
import com.tonalli.web.dto.Formato;
import com.tonalli.web.dto.NombreCategoriaForm;
import com.tonalli.web.dto.PropuestaVista;
import com.tonalli.web.exception.OperacionInvalidaException;
import com.tonalli.web.exception.RecursoNoEncontradoException;
import com.tonalli.web.model.Alerta;
import com.tonalli.web.model.EstadoOferta;
import com.tonalli.web.model.EstadoPropuesta;
import com.tonalli.web.model.Propuesta;
import com.tonalli.web.repository.AlertaRepository;
import com.tonalli.web.repository.CategoriaRepository;
import com.tonalli.web.repository.OfertaRepository;
import com.tonalli.web.repository.PropuestaRepository;
import com.tonalli.web.repository.UsuarioRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cosas que hace un usuario sobre su propia cuenta: alertas y propuestas de categorías. */
@Service
public class CuentaService {

    static final int MAX_ALERTAS = 20;
    static final int MAX_PROPUESTAS_PENDIENTES = 3;

    private final AlertaRepository alertas;
    private final OfertaRepository ofertas;
    private final PropuestaRepository propuestas;
    private final CategoriaRepository categorias;
    private final UsuarioRepository usuarios;

    public CuentaService(AlertaRepository alertas, OfertaRepository ofertas, PropuestaRepository propuestas,
                         CategoriaRepository categorias, UsuarioRepository usuarios) {
        this.alertas = alertas;
        this.ofertas = ofertas;
        this.propuestas = propuestas;
        this.categorias = categorias;
        this.usuarios = usuarios;
    }

    @Transactional(readOnly = true)
    public List<AlertaVista> alertas(Long usuarioId) {
        return alertas.findByUsuarioIdOrderByIdDesc(usuarioId).stream().map(a -> {
            int maximo = a.getPrecioMaxCentavos() == null ? Integer.MAX_VALUE : a.getPrecioMaxCentavos();
            List<com.tonalli.web.dto.OfertaVista> coincidencias = ofertas
                    .coincidencias(EstadoOferta.ACTIVA, Formato.limpiarBusqueda(a.getPalabraClave()), maximo,
                            PageRequest.of(0, 5))
                    .stream().map(o -> OfertaService.vista(o, 0L)).toList();
            return new AlertaVista(a.getId(), a.getPalabraClave(),
                    a.getPrecioMaxCentavos() == null ? null : Formato.dolares(a.getPrecioMaxCentavos()),
                    coincidencias);
        }).toList();
    }

    @Transactional
    public void crearAlerta(Long usuarioId, AlertaForm form) {
        if (alertas.countByUsuarioId(usuarioId) >= MAX_ALERTAS) {
            throw new OperacionInvalidaException("Alcanzaste el límite de " + MAX_ALERTAS + " alertas.");
        }
        Alerta a = new Alerta();
        a.setUsuario(usuarios.getReferenceById(usuarioId));
        a.setPalabraClave(Formato.limpiarTexto(form.getPalabraClave()));
        a.setPrecioMaxCentavos(form.getPrecioMax() == null ? null : Formato.centavos(form.getPrecioMax()));
        alertas.save(a);
    }

    /** Solo el dueño puede borrar su alerta; para cualquier otro es como si no existiera. */
    @Transactional
    public void eliminarAlerta(Long usuarioId, Long alertaId) {
        Alerta a = alertas.findById(alertaId)
                .filter(x -> x.getUsuario().getId().equals(usuarioId))
                .orElseThrow(() -> new RecursoNoEncontradoException("Alerta no encontrada"));
        alertas.delete(a);
    }

    @Transactional(readOnly = true)
    public List<PropuestaVista> propuestas(Long usuarioId) {
        return propuestas.findByEstadoOrderByCreadaEnDesc(EstadoPropuesta.PENDIENTE).stream()
                .map(p -> new PropuestaVista(p.getId(), p.getNombre(), p.getAutor().getNombre(),
                        p.getApoyos().size(), p.getApoyos().contains(usuarioId)))
                .toList();
    }

    @Transactional
    public void proponer(Long usuarioId, NombreCategoriaForm form) {
        String nombre = AdminService.normalizarNombre(form.getNombre());
        if (categorias.existsByNombreIgnoreCase(nombre)) {
            throw new OperacionInvalidaException("Esa categoría ya existe.");
        }
        if (propuestas.countByAutorIdAndEstado(usuarioId, EstadoPropuesta.PENDIENTE) >= MAX_PROPUESTAS_PENDIENTES) {
            throw new OperacionInvalidaException("Ya tienes " + MAX_PROPUESTAS_PENDIENTES + " propuestas pendientes.");
        }
        Propuesta p = new Propuesta();
        p.setNombre(nombre);
        p.setAutor(usuarios.getReferenceById(usuarioId));
        p.getApoyos().add(usuarioId);
        propuestas.save(p);
    }

    @Transactional
    public void apoyar(Long usuarioId, Long propuestaId) {
        Propuesta p = propuestas.findById(propuestaId)
                .filter(x -> x.getEstado() == EstadoPropuesta.PENDIENTE)
                .orElseThrow(() -> new RecursoNoEncontradoException("Propuesta no encontrada"));
        p.getApoyos().add(usuarioId); // un Set: apoyar dos veces no suma dos veces
    }
}
