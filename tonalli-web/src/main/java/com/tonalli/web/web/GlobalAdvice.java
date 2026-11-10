package com.tonalli.web.web;

import com.tonalli.web.exception.OperacionInvalidaException;
import com.tonalli.web.exception.RecursoNoEncontradoException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Convierte las excepciones propias de las páginas en una vista de error con texto propio.
 * Lo inesperado (500, 405...) no se captura aquí: lo atiende la página error/5xx.jsp de Spring Boot,
 * que nunca muestra trazas ni mensajes internos (ver application.properties).
 */
@ControllerAdvice(basePackages = "com.tonalli.web.web")
public class GlobalAdvice {

    @ExceptionHandler(RecursoNoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String noEncontrado(Model model) {
        model.addAttribute("titulo", "No encontrado");
        model.addAttribute("mensaje", "La página que buscas no existe o ya no está disponible.");
        return "error-pagina";
    }

    @ExceptionHandler(OperacionInvalidaException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String operacionInvalida(OperacionInvalidaException e, Model model) {
        model.addAttribute("titulo", "No se pudo completar la acción");
        model.addAttribute("mensaje", e.getMessage());
        return "error-pagina";
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String peticionMalFormada(Model model) {
        model.addAttribute("titulo", "Petición no válida");
        model.addAttribute("mensaje", "Los datos enviados no tienen el formato esperado.");
        return "error-pagina";
    }
}
