package com.tonalli.web.exception;

/**
 * Regla de negocio incumplida (votar la propia oferta, reportar dos veces...).
 * El mensaje es siempre texto propio, escrito por nosotros, así que es seguro mostrarlo al usuario.
 */
public class OperacionInvalidaException extends RuntimeException {

    public OperacionInvalidaException(String mensaje) {
        super(mensaje);
    }
}
