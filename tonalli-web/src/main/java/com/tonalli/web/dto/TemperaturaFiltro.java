package com.tonalli.web.dto;

/**
 * Filtro por estado de temperatura. Los límites coinciden con las etiquetas del catálogo:
 * Caliente desde 100, Tibia de 0 a 99, Fría por debajo de 0.
 */
public enum TemperaturaFiltro {

    TODAS("Cualquier temperatura", null, null),
    CALIENTE("Solo calientes", 100L, null),
    TIBIA("Solo tibias", 0L, 99L),
    FRIA("Solo frías", null, -1L);

    private final String etiqueta;
    private final Long minimo;
    private final Long maximo;

    TemperaturaFiltro(String etiqueta, Long minimo, Long maximo) {
        this.etiqueta = etiqueta;
        this.minimo = minimo;
        this.maximo = maximo;
    }

    public String getEtiqueta() { return etiqueta; }
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
