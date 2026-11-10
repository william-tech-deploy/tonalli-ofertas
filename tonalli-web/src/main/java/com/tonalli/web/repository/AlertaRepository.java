package com.tonalli.web.repository;

import com.tonalli.web.model.Alerta;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertaRepository extends JpaRepository<Alerta, Long> {

    List<Alerta> findByUsuarioIdOrderByIdDesc(Long usuarioId);

    long countByUsuarioId(Long usuarioId);
}
