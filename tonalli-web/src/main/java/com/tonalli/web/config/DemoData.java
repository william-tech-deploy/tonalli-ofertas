package com.tonalli.web.config;

import com.tonalli.web.model.Categoria;
import com.tonalli.web.model.Oferta;
import com.tonalli.web.model.Usuario;
import com.tonalli.web.repository.CategoriaRepository;
import com.tonalli.web.repository.OfertaRepository;
import com.tonalli.web.repository.UsuarioRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Datos de ejemplo SOLO para el perfil "dev". En producción esta clase ni siquiera se crea. */
@Component
@Profile("dev")
@Order(2)
public class DemoData implements ApplicationRunner {

    private final CategoriaRepository categorias;
    private final UsuarioRepository usuarios;
    private final OfertaRepository ofertas;
    private final PasswordEncoder codificador;

    public DemoData(CategoriaRepository categorias, UsuarioRepository usuarios, OfertaRepository ofertas,
                    PasswordEncoder codificador) {
        this.categorias = categorias;
        this.usuarios = usuarios;
        this.ofertas = ofertas;
        this.codificador = codificador;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (ofertas.count() > 0) {
            return;
        }
        Usuario demo = new Usuario();
        demo.setCorreo("demo@tonalli.local");
        demo.setNombre("Usuario Demo");
        demo.setPasswordHash(codificador.encode("DemoClave#2026!"));
        demo.setVerificado(true);
        demo.setKarma(12);
        demo = usuarios.save(demo);

        crear(demo, "Arroz 5 lb marca blanca", "Supermercado A", 349, 429, "Supermercado");
        crear(demo, "Audífonos inalámbricos con cancelación de ruido", "Tienda Tech", 4999, 7999, "Tecnología");
        crear(demo, "Camisa de algodón manga larga", "Moda Express", 1250, 1990, "Ropa");
        crear(demo, "Memoria USB 128 GB", "Tienda Tech", 899, null, "Tecnología");
    }

    private void crear(Usuario autor, String titulo, String tienda, int precio, Integer anterior, String categoria) {
        Categoria c = categorias.findByNombreIgnoreCase(categoria).orElseThrow();
        Oferta o = new Oferta();
        o.setTitulo(titulo);
        o.setTienda(tienda);
        o.setPrecioCentavos(precio);
        o.setPrecioAnteriorCentavos(anterior);
        o.setCategoria(c);
        o.setAutor(autor);
        ofertas.save(o);
    }
}
