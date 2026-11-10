package com.tonalli.web.security;

import com.tonalli.web.model.Usuario;
import com.tonalli.web.repository.UsuarioRepository;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Puente entre la tabla usuarios y Spring Security. */
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarios;
    private final LoginAttemptService intentos;
    private final boolean verificacionObligatoria;

    public UsuarioDetailsService(UsuarioRepository usuarios,
                                 LoginAttemptService intentos,
                                 @Value("${tonalli.verificacion.obligatoria:true}") boolean verificacionObligatoria) {
        this.usuarios = usuarios;
        this.intentos = intentos;
        this.verificacionObligatoria = verificacionObligatoria;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String correo = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);

        // Un correo bloqueado se trata igual que uno inexistente: misma respuesta, mismo tiempo.
        if (intentos.bloqueado(correo)) {
            throw new UsernameNotFoundException("Credenciales inválidas");
        }

        Usuario u = usuarios.findByCorreo(correo)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas"));
        boolean correoVerificado = u.isVerificado() || !verificacionObligatoria;
        return new UsuarioActual(u.getId(), u.getCorreo(), u.getNombre(), u.getPasswordHash(), u.getRol(), correoVerificado);
    }
}
