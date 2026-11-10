package com.tonalli.web.security;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/** Alimenta el contador de intentos con los eventos que publica Spring Security. */
@Component
public class LoginEventsListener {

    private final LoginAttemptService intentos;

    public LoginEventsListener(LoginAttemptService intentos) {
        this.intentos = intentos;
    }

    @EventListener
    public void alFallar(AuthenticationFailureBadCredentialsEvent evento) {
        intentos.registrarFallo(evento.getAuthentication().getName());
    }

    @EventListener
    public void alAcertar(AuthenticationSuccessEvent evento) {
        intentos.registrarExito(evento.getAuthentication().getName());
    }
}
