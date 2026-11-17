package com.tonalli.web.dto;

/** Resultado de ImagenService: un JPEG limpio, ya reducido, con sus medidas. */
public final class ImagenProcesada {

    private final byte[] jpeg;
    private final int ancho;
    private final int alto;

    public ImagenProcesada(byte[] jpeg, int ancho, int alto) {
        this.jpeg = jpeg;
        this.ancho = ancho;
        this.alto = alto;
    }

    public byte[] getJpeg() { return jpeg; }
    public int getAncho() { return ancho; }
    public int getAlto() { return alto; }
}
