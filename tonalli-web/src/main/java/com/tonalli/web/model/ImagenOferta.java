package com.tonalli.web.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * Foto de una oferta, ya re-codificada por el servidor como JPEG (ver ImagenService).
 *
 * Va en su propia tabla, y la oferta solo guarda el id (Oferta.imagenId). Así, listar el catálogo nunca
 * carga los bytes de las fotos: solo se leen cuando el navegador pide /imagenes/{id}.
 * Se guarda en la base y no en el disco del contenedor, porque el disco se pierde al reconstruir la imagen
 * de Docker; la base vive en el volumen de MySQL y entra en sus respaldos.
 */
@Entity
@Table(name = "imagenes_oferta")
public class ImagenOferta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "oferta_id", nullable = false, unique = true)
    private Long ofertaId;

    /** 1 MB alcanza de sobra para un JPEG de 1000 px; en MySQL Hibernate lo crea como MEDIUMBLOB. */
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, length = 1_048_576)
    private byte[] datos;

    @Column(nullable = false)
    private int ancho;

    @Column(nullable = false)
    private int alto;

    protected ImagenOferta() {
    }

    public ImagenOferta(Long ofertaId, byte[] datos, int ancho, int alto) {
        this.ofertaId = ofertaId;
        this.datos = datos;
        this.ancho = ancho;
        this.alto = alto;
    }

    public Long getId() { return id; }
    public Long getOfertaId() { return ofertaId; }
    public byte[] getDatos() { return datos; }
    public int getAncho() { return ancho; }
    public int getAlto() { return alto; }
}
