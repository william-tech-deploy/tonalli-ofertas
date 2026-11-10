package com.tonalli.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UsuarioServiceTest {

    @Test
    void elTokenTiene43CaracteresUrlSeguros() {
        String token = UsuarioService.nuevoToken();
        assertEquals(43, token.length());
        assertTrue(token.matches("[A-Za-z0-9_-]+"));
    }

    @Test
    void dosTokensNuncaSeRepiten() {
        assertNotEquals(UsuarioService.nuevoToken(), UsuarioService.nuevoToken());
    }

    @Test
    void elHashEsSha256EnHexadecimal() {
        // SHA-256 de "abc", valor de referencia publicado en el estándar FIPS 180
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", UsuarioService.hash("abc"));
    }

    @Test
    void elPesoDelVotoDependeDeLaKarma() {
        assertEquals(1, OfertaService.pesoPorKarma(0));
        assertEquals(1, OfertaService.pesoPorKarma(9));
        assertEquals(2, OfertaService.pesoPorKarma(10));
        assertEquals(2, OfertaService.pesoPorKarma(49));
        assertEquals(3, OfertaService.pesoPorKarma(50));
    }
}
