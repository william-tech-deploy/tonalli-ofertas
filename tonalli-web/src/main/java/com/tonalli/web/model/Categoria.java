package com.tonalli.web.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "categorias",
        uniqueConstraints = @UniqueConstraint(name = "uk_categorias_nombre", columnNames = "nombre"))
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String nombre;

    /** Las categorías base (Supermercado, Tecnología, Ropa) no se pueden eliminar. */
    @Column(nullable = false)
    private boolean base = false;

    protected Categoria() {
        // requerido por JPA
    }

    public Categoria(String nombre, boolean base) {
        this.nombre = nombre;
        this.base = base;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public boolean isBase() { return base; }
}
