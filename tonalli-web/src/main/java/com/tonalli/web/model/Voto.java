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
import jakarta.persistence.UniqueConstraint;

/**
 * Voto de un usuario sobre una oferta: +1 (caliente) o -1 (frío), multiplicado por el peso del votante.
 * La restricción única (usuario, oferta) hace que la propia base impida votar dos veces.
 */
@Entity
@Table(name = "votos",
        uniqueConstraints = @UniqueConstraint(name = "uk_votos_usuario_oferta", columnNames = {"usuario_id", "oferta_id"}))
public class Voto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "oferta_id", nullable = false)
    private Oferta oferta;

    @Column(nullable = false)
    private int valor;

    @Column(nullable = false)
    private int peso = 1;

    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public Oferta getOferta() { return oferta; }
    public void setOferta(Oferta oferta) { this.oferta = oferta; }
    public int getValor() { return valor; }
    public void setValor(int valor) { this.valor = valor; }
    public int getPeso() { return peso; }
    public void setPeso(int peso) { this.peso = peso; }
}
