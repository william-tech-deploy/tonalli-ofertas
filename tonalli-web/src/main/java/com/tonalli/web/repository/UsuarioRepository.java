package com.tonalli.web.repository;

import com.tonalli.web.model.Rol;
import com.tonalli.web.model.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data genera la implementación a partir del nombre del método.
 * Todas las consultas son parametrizadas: no hay SQL concatenado en ningún punto del proyecto.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByCorreo(String correo);

    boolean existsByCorreo(String correo);

    Optional<Usuario> findByTokenVerificacionHash(String tokenVerificacionHash);

    long countByRol(Rol rol);
}
