package com.tonalli.web.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Alerta de un usuario: palabra clave y precio máximo opcional. */
@Entity
@Table(name = "alertas")
public class Alerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "palabra_clave", nullable = false, length = 60)
    private String palabraClave;

    @Column(name = "precio_max_centavos")
    private Integer precioMaxCentavos;

    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public String getPalabraClave() { return palabraClave; }
    public void setPalabraClave(String palabraClave) { this.palabraClave = palabraClave; }
    public Integer getPrecioMaxCentavos() { return precioMaxCentavos; }
    public void setPrecioMaxCentavos(Integer precioMaxCentavos) { this.precioMaxCentavos = precioMaxCentavos; }
}
