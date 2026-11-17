package com.tonalli.web.dto;

/**
 * Un archivo ya generado en memoria, listo para enviarlo al navegador.
 *
 * <p>Por qué el contenido viaja como {@code byte[]} y no como un {@code File} en disco: el informe se
 * arma, se envía y se olvida. Un archivo temporal habría que crearlo con permisos correctos, borrarlo
 * siempre (también si la petición falla) y vigilar que dos descargas a la vez no se pisen el nombre; y
 * mientras existiera, un informe con datos de usuarios estaría escrito en el disco del servidor. El
 * tamaño está acotado por el tope de filas del servicio, así que no hay riesgo de llenar la memoria.
 */
public class ArchivoDescarga {

    private final String nombre;
    private final byte[] contenido;

    public ArchivoDescarga(String nombre, byte[] contenido) {
        this.nombre = nombre;
        this.contenido = contenido;
    }

    /** Nombre con el que el navegador guarda el archivo. Lo arma el servicio a partir de {@link TipoInforme}. */
    public String getNombre() {
        return nombre;
    }

    public byte[] getContenido() {
        return contenido;
    }
}
