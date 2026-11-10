package com.tonalli.web.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Oferta publicada por un usuario. Los precios se guardan en centavos enteros
 * para evitar errores de redondeo con decimales.
 */
@Entity
@Table(name = "ofertas", indexes = {
        @Index(name = "ix_ofertas_estado_creada", columnList = "estado, creada_en"),
        @Index(name = "ix_ofertas_categoria", columnList = "categoria_id")
})
public class Oferta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(nullable = false, length = 80)
    private String tienda;

    @Column(name = "precio_centavos", nullable = false)
    private int precioCentavos;

    @Column(name = "precio_anterior_centavos")
    private Integer precioAnteriorCentavos;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoOferta estado = EstadoOferta.ACTIVA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn = Instant.now();

    public Long getId() { return id; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getTienda() { return tienda; }
    public void setTienda(String tienda) { this.tienda = tienda; }
    public int getPrecioCentavos() { return precioCentavos; }
    public void setPrecioCentavos(int precioCentavos) { this.precioCentavos = precioCentavos; }
    public Integer getPrecioAnteriorCentavos() { return precioAnteriorCentavos; }
    public void setPrecioAnteriorCentavos(Integer precioAnteriorCentavos) { this.precioAnteriorCentavos = precioAnteriorCentavos; }
    public EstadoOferta getEstado() { return estado; }
    public void setEstado(EstadoOferta estado) { this.estado = estado; }
    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }
    public Usuario getAutor() { return autor; }
    public void setAutor(Usuario autor) { this.autor = autor; }
    public Instant getCreadaEn() { return creadaEn; }
}
