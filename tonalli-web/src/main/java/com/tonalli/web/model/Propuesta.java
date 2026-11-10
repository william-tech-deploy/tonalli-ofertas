package com.tonalli.web.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Propuesta de nueva categoría hecha por un usuario. Otros usuarios la apoyan (un apoyo por usuario,
 * garantizado por el Set) y el administrador la aprueba o la rechaza.
 */
@Entity
@Table(name = "propuestas")
public class Propuesta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String nombre;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPropuesta estado = EstadoPropuesta.PENDIENTE;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "propuesta_apoyos", joinColumns = @JoinColumn(name = "propuesta_id"))
    @Column(name = "usuario_id", nullable = false)
    private Set<Long> apoyos = new HashSet<>();

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn = Instant.now();

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public Usuario getAutor() { return autor; }
    public void setAutor(Usuario autor) { this.autor = autor; }
    public EstadoPropuesta getEstado() { return estado; }
    public void setEstado(EstadoPropuesta estado) { this.estado = estado; }
    public Set<Long> getApoyos() { return apoyos; }
    public Instant getCreadaEn() { return creadaEn; }
}
