package com.tonalli.web.dto;

/**
 * Los cuatro informes que el administrador puede descargar.
 *
 * <p>Por qué un enum y no un texto libre: el nombre del archivo que se descarga sale de aquí, nunca de
 * lo que escriba alguien en la URL. Spring convierte {@code ?tipo=OFERTAS} a esta constante y responde
 * 400 si no coincide con ninguna, así que la lista blanca la hace el propio tipo. Con un parámetro de
 * texto habría que validarlo a mano y un descuido dejaría pasar un nombre con {@code ../} (travesía de
 * rutas) o con un salto de línea, que en el encabezado {@code Content-Disposition} permite inyectar
 * encabezados HTTP.
 */
public enum TipoInforme {

    /** Catálogo publicado en el periodo, con precios, estado y temperatura. */
    OFERTAS("Ofertas publicadas", "tonalli-ofertas"),

    /** Altas de usuarios del periodo, con rol y verificación de correo. */
    USUARIOS("Usuarios registrados", "tonalli-usuarios"),

    /** Reportes recibidos en el periodo y en qué quedaron. */
    MODERACION("Reportes de moderación", "tonalli-moderacion"),

    /** Una sola hoja con todos los indicadores y los datos de cada gráfico. */
    RESUMEN("Resumen estadístico", "tonalli-resumen");

    private final String etiqueta;
    private final String archivo;

    TipoInforme(String etiqueta, String archivo) {
        this.etiqueta = etiqueta;
        this.archivo = archivo;
    }

    /** Nombre que se muestra en la página. */
    public String getEtiqueta() {
        return etiqueta;
    }

    /** Raíz del nombre del archivo. Solo letras minúsculas y guiones: no necesita saneamiento. */
    public String getArchivo() {
        return archivo;
    }
}
