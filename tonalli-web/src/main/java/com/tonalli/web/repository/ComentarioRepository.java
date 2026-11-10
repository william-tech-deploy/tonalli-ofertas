package com.tonalli.web.repository;

import com.tonalli.web.model.Comentario;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComentarioRepository extends JpaRepository<Comentario, Long> {

    @EntityGraph(attributePaths = "autor")
    List<Comentario> findTop50ByOfertaIdOrderByCreadoEnDesc(Long ofertaId);
}
