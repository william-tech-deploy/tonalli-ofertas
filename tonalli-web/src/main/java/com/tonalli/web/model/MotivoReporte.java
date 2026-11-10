package com.tonalli.web.model;

public enum MotivoReporte {
    SPAM("Spam o publicidad"),
    PRECIO_FALSO("Precio falso"),
    EXPIRADA("Oferta expirada"),
    OTRO("Otro motivo");

    private final String etiqueta;

    MotivoReporte(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
