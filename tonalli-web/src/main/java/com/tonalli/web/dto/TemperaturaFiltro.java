package com.tonalli.web.dto;

/**
 * Filtro por estado de temperatura. Los límites coinciden con las etiquetas del catálogo:
 * Caliente desde 100, Tibia de 0 a 99, Fría por debajo de 0.
 */
public enum TemperaturaFiltro {

    TODAS("Cualquier temperatura", "Todas", null, null),
    CALIENTE("Solo calientes", "Calientes", 100L, null),
    TIBIA("Solo tibias", "Tibias", 0L, 99L),
    FRIA("Solo frías", "Frías", null, -1L);

    private final String etiqueta;
    private final String nombre;
    private final Long minimo;
    private final Long maximo;

    TemperaturaFiltro(String etiqueta, String nombre, Long minimo, Long maximo) {
        this.etiqueta = etiqueta;
        this.nombre = nombre;
        this.minimo = minimo;
        this.maximo = maximo;
    }

    public String getEtiqueta() { return etiqueta; }

    /**
     * Nombre corto del tramo, para los gráficos. Va aparte de {@link #getEtiqueta()} porque en el
     * desplegable del catálogo el texto es una orden («Solo calientes») y en un gráfico es un rótulo
     * («Calientes»): el mismo enum sirve para los dos sin que los límites se escriban dos veces.
     */
    public String getNombre() { return nombre; }

    /** true si esa temperatura cae en este tramo. Los tramos del catálogo no se solapan. */
    public boolean contiene(long temperatura) {
        return (minimo == null || temperatura >= minimo) && (maximo == null || temperatura <= maximo);
    }
    public Long getMinimo() { return minimo; }
    public Long getMaximo() { return maximo; }

    public static TemperaturaFiltro desdeTexto(String texto) {
        if (texto != null) {
            for (TemperaturaFiltro t : values()) {
                if (t.name().equalsIgnoreCase(texto)) {
                    return t;
                }
            }
        }
        return TODAS;
    }
}
