package com.tonalli.web.api;

import com.tonalli.web.exception.OperacionInvalidaException;
import com.tonalli.web.exception.RecursoNoEncontradoException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Respuestas de error en JSON, con la misma forma en toda la API y sin detalles internos. */
@RestControllerAdvice(basePackages = "com.tonalli.web.api")
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException e) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError f : e.getBindingResult().getFieldErrors()) {
            campos.putIfAbsent(f.getField(), f.getDefaultMessage());
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("error", "Datos no válidos");
        cuerpo.put("campos", campos);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cuerpo);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> malFormada() {
        return error(HttpStatus.BAD_REQUEST, "Petición mal formada");
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> noEncontrado() {
        return error(HttpStatus.NOT_FOUND, "No encontrado");
    }

    @ExceptionHandler(OperacionInvalidaException.class)
    public ResponseEntity<Map<String, Object>> operacionInvalida(OperacionInvalidaException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus estado, String mensaje) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("error", mensaje);
        return ResponseEntity.status(estado).body(cuerpo);
    }
}
