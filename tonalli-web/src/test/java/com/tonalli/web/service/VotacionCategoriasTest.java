package com.tonalli.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonalli.web.dto.NombreCategoriaForm;
import com.tonalli.web.dto.PropuestaVista;
import com.tonalli.web.model.Usuario;
import com.tonalli.web.repository.CategoriaRepository;
import com.tonalli.web.repository.UsuarioRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Votación de categorías nuevas. En el perfil dev el umbral es 3, así que tres votos bastan
 * para que la categoría se cree sola.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class VotacionCategoriasTest {

    @Autowired private CuentaService cuenta;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private CategoriaRepository categorias;

    private Usuario u(String correo) { return usuarios.findByCorreo(correo).orElseThrow(); }

    private Long proponer(String nombre, Usuario autor) {
        NombreCategoriaForm form = new NombreCategoriaForm();
        form.setNombre(nombre);
        cuenta.proponer(autor.getId(), form);
        return cuenta.propuestas(autor.getId()).stream()
                .filter(p -> p.getNombre().equals(nombre)).findFirst().orElseThrow().getId();
    }

    @Test
    void quienProponeYaVotaPorSuPropuesta() {
        Long id = proponer("Hogar", u("demo@tonalli.local"));
        PropuestaVista p = cuenta.propuestas(u("demo@tonalli.local").getId()).stream()
                .filter(x -> x.getId().equals(id)).findFirst().orElseThrow();
        assertEquals(1, p.getApoyos());
        assertTrue(p.isYaApoyada());
        assertEquals(cuenta.getUmbral() - 1, p.getFaltan());
    }

    @Test
    void votarDosVecesRetiraElVoto() {
        Long id = proponer("Juguetes", u("demo@tonalli.local"));
        Long ana = u("ana.martinez@tonalli.local").getId();

        cuenta.apoyar(ana, id);
        assertEquals(2, apoyos(ana, id));
        assertTrue(buscar(ana, id).isYaApoyada());

        cuenta.apoyar(ana, id); // el mismo usuario otra vez: retira su voto
        assertEquals(1, apoyos(ana, id));
        assertFalse(buscar(ana, id).isYaApoyada());
    }

    @Test
    void alLlegarAlUmbralLaCategoriaSeCreaSola() {
        assertEquals(3, cuenta.getUmbral(), "El perfil dev usa un umbral bajo");
        Long id = proponer("Mascotas", u("demo@tonalli.local"));
        assertFalse(categorias.existsByNombreIgnoreCase("Mascotas"));

        assertFalse(cuenta.apoyar(u("ana.martinez@tonalli.local").getId(), id), "Con 2 votos todavía no");
        assertTrue(cuenta.apoyar(u("carlos.rivas@tonalli.local").getId(), id), "El tercer voto la aprueba");

        assertTrue(categorias.existsByNombreIgnoreCase("Mascotas"));
        Long demo = u("demo@tonalli.local").getId();
        assertTrue(cuenta.propuestas(demo).stream().noneMatch(p -> p.getNombre().equals("Mascotas")),
                "Ya no está en votación");
        assertTrue(cuenta.aprobadasRecientes().stream().anyMatch(p -> p.getNombre().equals("Mascotas")));
    }

    @Test
    void lasPropuestasSeMuestranDeLaMasApoyadaALaMenos() {
        Usuario demo = u("demo@tonalli.local");
        Long pocos = proponer("Libros", demo);
        Long varios = proponer("Ferretería", demo);
        cuenta.apoyar(u("ana.martinez@tonalli.local").getId(), varios);

        List<PropuestaVista> lista = cuenta.propuestas(demo.getId());
        int posVarios = indice(lista, varios);
        int posPocos = indice(lista, pocos);
        assertTrue(posVarios < posPocos, "La más apoyada va primero");
    }

    @Test
    void elAvanceSeExpresaEnPorcentaje() {
        PropuestaVista p = new PropuestaVista(1L, "Deportes", "Ana", 2, false, 4);
        assertEquals(50, p.getPorcentaje());
        assertEquals(2, p.getFaltan());
        // Nunca pasa de 100, aunque sobren votos: la barra no se desborda.
        assertEquals(100, new PropuestaVista(1L, "Deportes", "Ana", 9, false, 4).getPorcentaje());
    }

    private int apoyos(Long usuarioId, Long propuestaId) {
        return buscar(usuarioId, propuestaId).getApoyos();
    }

    private PropuestaVista buscar(Long usuarioId, Long propuestaId) {
        return cuenta.propuestas(usuarioId).stream()
                .filter(p -> p.getId().equals(propuestaId)).findFirst().orElseThrow();
    }

    private int indice(List<PropuestaVista> lista, Long id) {
        for (int i = 0; i < lista.size(); i++) {
            if (lista.get(i).getId().equals(id)) {
                return i;
            }
        }
        throw new IllegalStateException("No está en la lista");
    }
}
