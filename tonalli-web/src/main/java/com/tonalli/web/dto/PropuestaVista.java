package com.tonalli.web.dto;

/** Una propuesta de categoría tal como se muestra, con su avance hacia el umbral de aprobación. */
public class PropuestaVista {

    private final Long id;
    private final String nombre;
    private final String autor;
    private final int apoyos;
    private final boolean yaApoyada;
    private final int umbral;

    /** Para el panel del administrador, donde el umbral no se muestra. */
    public PropuestaVista(Long id, String nombre, String autor, int apoyos, boolean yaApoyada) {
        this(id, nombre, autor, apoyos, yaApoyada, 0);
    }

    public PropuestaVista(Long id, String nombre, String autor, int apoyos, boolean yaApoyada, int umbral) {
        this.id = id;
        this.nombre = nombre;
        this.autor = autor;
        this.apoyos = apoyos;
        this.yaApoyada = yaApoyada;
        this.umbral = umbral;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getAutor() { return autor; }
    public int getApoyos() { return apoyos; }
    public boolean isYaApoyada() { return yaApoyada; }
    public int getUmbral() { return umbral; }

    /** Votos que faltan para que la categoría se cree sola. */
    public int getFaltan() {
        return Math.max(0, umbral - apoyos);
    }

    /** Avance en porcentaje, recortado a 100 para la barra de progreso. */
    public int getPorcentaje() {
        if (umbral <= 0) {
            return 0;
        }
        return Math.min(100, (int) Math.round(apoyos * 100.0 / umbral));
    }
}
