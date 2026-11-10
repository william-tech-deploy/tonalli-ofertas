package com.tonalli.web.exception;

/** El recurso no existe, o existe pero el usuario no debe verlo (se responde igual: 404). */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
