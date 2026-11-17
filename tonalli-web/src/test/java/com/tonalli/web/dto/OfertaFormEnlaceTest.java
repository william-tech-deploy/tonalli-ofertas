package com.tonalli.web.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** El enlace de una oferta lo escribe un usuario y termina en un href: solo se admite https bien formado. */
class OfertaFormEnlaceTest {

    private static ValidatorFactory fabrica;
    private static Validator validador;

    @BeforeAll
    static void preparar() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void cerrar() {
        fabrica.close();
    }

    private boolean esValido(String enlace) {
        OfertaForm form = new OfertaForm();
        form.setEnlace(enlace);
        return validador.validateProperty(form, "enlace").isEmpty();
    }

    @Test
    void elEnlaceEsOpcional() {
        assertTrue(esValido(null));
        assertTrue(esValido(""));
    }

    @Test
    void aceptaDireccionesHttpsNormales() {
        assertTrue(esValido("https://www.superselectos.com/products-landing/BAJA1209"));
        assertTrue(esValido("https://shop.samsung.com/latin/cac/sv/galaxy-a37-5g-256gb.html?color=negro&x=1"));
    }

    @Test
    void rechazaEsquemasPeligrososYHttpSinCifrar() {
        assertFalse(esValido("javascript:alert(1)"));
        assertFalse(esValido("data:text/html,<script>alert(1)</script>"));
        assertFalse(esValido("http://tienda.com/oferta"));
        assertFalse(esValido("//tienda.com/oferta"));
    }

    @Test
    void rechazaCaracteresQueRompenElAtributoHref() {
        assertFalse(esValido("https://tienda.com/a\" onclick=\"x"));
        assertFalse(esValido("https://tienda.com/a b"));
        assertFalse(esValido("https://tienda.com/<script>"));
    }

    @Test
    void rechazaEnlacesDemasiadoLargos() {
        assertFalse(esValido("https://tienda.com/" + "a".repeat(300)));
    }
}
