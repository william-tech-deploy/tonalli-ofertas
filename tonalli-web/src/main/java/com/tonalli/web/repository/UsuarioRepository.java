package com.tonalli.web.repository;

import com.tonalli.web.model.Rol;
import com.tonalli.web.model.Usuario;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data genera la implementación a partir del nombre del método.
 * Todas las consultas son parametrizadas: no hay SQL concatenado en ningún punto del proyecto.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByCorreo(String correo);

    boolean existsByCorreo(String correo);

    Optional<Usuario> findByTokenVerificacionHash(String tokenVerificacionHash);

    // ----- Informes y estadísticas -----

    long countByVerificado(boolean verificado);

    @Query("select u.rol, count(u) from Usuario u group by u.rol")
    List<Object[]> contarPorRol();

    /**
     * Filas del informe de usuarios. Incluye el correo porque el informe es para el administrador, que
     * ya lo ve en /admin/usuarios; a cambio, la descarga queda registrada en el log de AUDITORIA y la
     * respuesta se marca no-store para que no quede en cache del navegador ni de un proxy.
     */
    @Query("select u.id, u.nombre, u.correo, u.rol, u.verificado, u.karma, u.creadoEn from Usuario u "
            + "where u.creadoEn >= :desde and u.creadoEn < :hasta order by u.id")
    List<Object[]> filasInformeUsuarios(@Param("desde") Instant desde,
                                        @Param("hasta") Instant hasta,
                                        Pageable pageable);

    long countByRol(Rol rol);
}
