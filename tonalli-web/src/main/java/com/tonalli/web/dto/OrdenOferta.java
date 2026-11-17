package com.tonalli.web.dto;

/** Formas de ordenar el catálogo. El valor que viaja en la URL es el nombre en minúsculas. */
public enum OrdenOferta {

    RECIENTES("Más recientes"),
    TEMPERATURA("Más calientes"),
    PRECIO_ASC("Precio: de menor a mayor"),
    PRECIO_DESC("Precio: de mayor a menor"),
    DESCUENTO("Mayor descuento");

    private final String etiqueta;

    OrdenOferta(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() { return etiqueta; }

    /** Nunca falla: un valor desconocido en la URL cae en el orden por omisión. */
    public static OrdenOferta desdeTexto(String texto) {
        if (texto != null) {
            for (OrdenOferta o : values()) {
                if (o.name().equalsIgnoreCase(texto)) {
                    return o;
                }
            }
        }
        return RECIENTES;
    }
}
