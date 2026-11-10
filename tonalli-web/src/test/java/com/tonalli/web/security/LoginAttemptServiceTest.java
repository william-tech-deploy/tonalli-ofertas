package com.tonalli.web.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    /** Reloj que se puede adelantar a mano. */
    private static final class RelojMutable extends Clock {
        private Instant ahora = Instant.parse("2026-11-05T12:00:00Z");

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
        void avanzar(Duration d) { ahora = ahora.plus(d); }
    }

    @Test
    void sebloqueaConElQuintoFallo() {
        LoginAttemptService servicio = new LoginAttemptService(new RelojMutable());
        for (int i = 0; i < 4; i++) {
            servicio.registrarFallo("a@b.com");
        }
        assertFalse(servicio.bloqueado("a@b.com"));
        servicio.registrarFallo("a@b.com");
        assertTrue(servicio.bloqueado("a@b.com"));
    }

    @Test
    void ignoraMayusculasYEspacios() {
        LoginAttemptService servicio = new LoginAttemptService(new RelojMutable());
        for (int i = 0; i < 5; i++) {
            servicio.registrarFallo("  A@B.com ");
        }
        assertTrue(servicio.bloqueado("a@b.com"));
    }

    @Test
    void elBloqueoVenceALos15Minutos() {
        RelojMutable reloj = new RelojMutable();
        LoginAttemptService servicio = new LoginAttemptService(reloj);
        for (int i = 0; i < 5; i++) {
            servicio.registrarFallo("a@b.com");
        }
        reloj.avanzar(Duration.ofMinutes(16));
        assertFalse(servicio.bloqueado("a@b.com"));
    }

    @Test
    void unExitoLimpiaElContador() {
        LoginAttemptService servicio = new LoginAttemptService(new RelojMutable());
        for (int i = 0; i < 4; i++) {
            servicio.registrarFallo("a@b.com");
        }
        servicio.registrarExito("a@b.com");
        servicio.registrarFallo("a@b.com");
        assertFalse(servicio.bloqueado("a@b.com"));
    }

    @Test
    void cuentaTambienLosCorreosInexistentes() {
        LoginAttemptService servicio = new LoginAttemptService(new RelojMutable());
        for (int i = 0; i < 5; i++) {
            servicio.registrarFallo("noexiste@b.com");
        }
        assertTrue(servicio.bloqueado("noexiste@b.com"));
    }
}
