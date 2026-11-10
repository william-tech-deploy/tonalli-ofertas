package com.tonalli.web.config;

import com.tonalli.web.model.Categoria;
import com.tonalli.web.model.Rol;
import com.tonalli.web.model.Usuario;
import com.tonalli.web.repository.CategoriaRepository;
import com.tonalli.web.repository.UsuarioRepository;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Al arrancar: crea las categorías base y, si se configuró, el primer administrador.
 * El administrador sale de variables de entorno (TONALLI_ADMIN_CORREO y TONALLI_ADMIN_PASSWORD), nunca del código.
 */
@Component
@Order(1)
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final CategoriaRepository categorias;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificador;
    private final String adminCorreo;
    private final String adminPassword;

    public DataInitializer(CategoriaRepository categorias, UsuarioRepository usuarios, PasswordEncoder codificador,
                           @Value("${tonalli.admin.correo:}") String adminCorreo,
                           @Value("${tonalli.admin.password:}") String adminPassword) {
        this.categorias = categorias;
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.adminCorreo = adminCorreo;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String nombre : List.of("Supermercado", "Tecnología", "Ropa")) {
            if (!categorias.existsByNombreIgnoreCase(nombre)) {
                categorias.save(new Categoria(nombre, true));
            }
        }
        crearAdministrador();
    }

    private void crearAdministrador() {
        if (adminCorreo.isBlank() || adminPassword.isBlank()) {
            log.info("No se definió administrador inicial (TONALLI_ADMIN_CORREO / TONALLI_ADMIN_PASSWORD).");
            return;
        }
        String correo = adminCorreo.trim().toLowerCase(Locale.ROOT);
        if (!correo.contains("@") || adminPassword.length() < 12 || adminPassword.length() > 64) {
            log.error("Administrador inicial NO creado: el correo no es válido o la contraseña no tiene entre 12 y 64 caracteres.");
            return;
        }
        if (usuarios.existsByCorreo(correo)) {
            return;
        }
        Usuario admin = new Usuario();
        admin.setCorreo(correo);
        admin.setNombre("Administrador");
        admin.setPasswordHash(codificador.encode(adminPassword));
        admin.setRol(Rol.ADMIN);
        admin.setVerificado(true);
        usuarios.save(admin);
        log.info("Administrador inicial creado: {}", correo);
    }
}
