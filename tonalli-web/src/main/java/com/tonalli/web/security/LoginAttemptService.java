package com.tonalli.web.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Límite de intentos de inicio de sesión: 5 fallos en 15 minutos bloquean ese correo hasta que
 * pasen los 15 minutos. Cuenta también los correos que no existen, así un atacante no puede saber
 * qué cuentas son reales mirando quién se bloquea.
 *
 * Limitación conocida: vive en la memoria de este proceso. Con varias instancias del servidor
 * habría que moverlo a Redis o a la base de datos.
 */
@Service
public class LoginAttemptService {

    static final int MAX_FALLOS = 5;
    static final Duration VENTANA = Duration.ofMinutes(15);
    static final int CAPACIDAD = 10_000;

    private static final class Estado {
        private final int fallos;
        private final Instant desde;

        private Estado(int fallos, Instant desde) {
            this.fallos = fallos;
            this.desde = desde;
        }
    }

    private final ConcurrentHashMap<String, Estado> intentos = new ConcurrentHashMap<>();
    private final Clock reloj;

    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    LoginAttemptService(Clock reloj) {
        this.reloj = reloj;
    }

    public void registrarFallo(String correo) {
        String clave = clave(correo);
        Instant ahora = reloj.instant();
        if (intentos.size() >= CAPACIDAD) {
            intentos.entrySet().removeIf(e -> vencido(e.getValue(), ahora));
            if (intentos.size() >= CAPACIDAD && !intentos.containsKey(clave)) {
                return; // tabla llena: no se aceptan claves nuevas para no agotar la memoria
            }
        }
        intentos.compute(clave, (k, actual) -> {
            if (actual == null || vencido(actual, ahora)) {
                return new Estado(1, ahora);
            }
            return new Estado(actual.fallos + 1, actual.desde);
        });
    }

    public boolean bloqueado(String correo) {
        Estado e = intentos.get(clave(correo));
        return e != null && e.fallos >= MAX_FALLOS && !vencido(e, reloj.instant());
    }

    public void registrarExito(String correo) {
        intentos.remove(clave(correo));
    }

    private static boolean vencido(Estado e, Instant ahora) {
        return ahora.isAfter(e.desde.plus(VENTANA));
    }

    private static String clave(String correo) {
        return correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
    }
}
