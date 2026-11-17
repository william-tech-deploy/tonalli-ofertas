package com.tonalli.web.config;

import com.tonalli.web.model.Categoria;
import com.tonalli.web.model.Comentario;
import com.tonalli.web.model.Oferta;
import com.tonalli.web.model.Usuario;
import com.tonalli.web.model.Voto;
import com.tonalli.web.repository.CategoriaRepository;
import com.tonalli.web.repository.ComentarioRepository;
import com.tonalli.web.repository.OfertaRepository;
import com.tonalli.web.repository.UsuarioRepository;
import com.tonalli.web.repository.VotoRepository;
import com.tonalli.web.service.OfertaService;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Datos de ejemplo: 5 usuarios, 15 ofertas, votos y comentarios.
 *
 * <p>Solo existe si tonalli.demo.activo=true (variable TONALLI_DEMO). Por defecto vale false, así que en
 * producción esta clase ni siquiera se crea. El perfil "dev" la activa para programar y para las pruebas.
 *
 * <p>Las ofertas son productos reales publicados por tiendas de El Salvador. Nombre, precio y enlace se
 * copiaron de la página pública de cada tienda el 10 de noviembre de 2026. Ninguna de esas páginas indicaba
 * fecha de fin de la oferta: los precios pueden haber cambiado y hay que revisarlos antes de una demostración.
 *
 * <p>Los usuarios son ficticios (dominio tonalli.local, que no existe en internet).
 */
@Component
@ConditionalOnProperty(name = "tonalli.demo.activo", havingValue = "true")
@Order(2)
public class DemoData implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoData.class);

    private static final String SELECTOS = "Súper Selectos (tienda en línea)";
    private static final String SELECTOS_URL = "https://www.superselectos.com/products-landing/BAJA1209";
    private static final String SAMSUNG = "Samsung El Salvador (tienda en línea)";
    private static final String SAMSUNG_URL = "https://shop.samsung.com/latin/cac/sv/";
    private static final String ADOC = "ADOC El Salvador (tienda en línea)";
    private static final String ADOC_URL = "https://sv.tiendasadoc.com/collections/hombre/products/";
    private static final String WALMART = "Walmart El Salvador (tienda en línea)";

    private static final String DEMO = "demo@tonalli.local";
    private static final String ANA = "ana.martinez@tonalli.local";
    private static final String CARLOS = "carlos.rivas@tonalli.local";
    private static final String SOFIA = "sofia.hernandez@tonalli.local";
    private static final String DIEGO = "diego.flores@tonalli.local";

    /** Una oferta de ejemplo. Los precios van en centavos, igual que en la entidad. */
    private record Ejemplo(String autor, String titulo, String tienda, int precio, Integer anterior,
                           String categoria, String enlace) {
    }

    /**
     * La PRIMERA oferta es del usuario demo y no recibe votos ni comentarios: las pruebas automáticas
     * parten de una oferta del usuario demo con temperatura 0.
     */
    private static final List<Ejemplo> EJEMPLOS = List.of(
            new Ejemplo(DEMO, "Aceite vegetal El Dorado 3,750 ml", SELECTOS, 1015, 1240, "Supermercado", SELECTOS_URL),
            new Ejemplo(ANA, "Café soluble Nescafé Reserva Xiapan 180 g", SELECTOS, 1200, 1400, "Supermercado", SELECTOS_URL),
            new Ejemplo(CARLOS, "Lavaplatos líquido Axion 1.5 L doypack", SELECTOS, 450, 550, "Supermercado", SELECTOS_URL),
            new Ejemplo(SOFIA, "Galleta Oreo de chocolate con crema 216 g", SELECTOS, 130, 150, "Supermercado", SELECTOS_URL),
            new Ejemplo(DEMO, "Huevo Selectos 30 unidades, grande rojo", SELECTOS, 605, null, "Supermercado", SELECTOS_URL),
            new Ejemplo(DIEGO, "Arroz blanco Selectos 1,816 g", SELECTOS, 210, null, "Supermercado", SELECTOS_URL),
            new Ejemplo(ANA, "Televisor Samsung 50\" Crystal UHD 4K U8000H (2026)", SAMSUNG, 32900, 54900, "Tecnología",
                    SAMSUNG_URL + "50-crystal-uhd-4k-u8000h-tizen-os-smart-tv-2026.html"),
            new Ejemplo(CARLOS, "Televisor Samsung 55\" Mini LED M70H 4K (2026)", SAMSUNG, 43900, 79900, "Tecnología",
                    SAMSUNG_URL + "55-mini-led-m70haf-4k-vision-ai-smart-tv.html"),
            new Ejemplo(DEMO, "Samsung Galaxy A37 5G 256 GB", SAMSUNG, 47900, 52900, "Tecnología",
                    SAMSUNG_URL + "galaxy-a37-5g-256gb.html"),
            new Ejemplo(SOFIA, "Bocina Samsung Music Studio 5 LS50H negra (2026)", SAMSUNG, 16900, 29900, "Tecnología",
                    SAMSUNG_URL + "wireless-speaker-ls50h-black-hw-ls50h-zp.html"),
            new Ejemplo(ANA, "Samsung Galaxy A57 5G 256 GB", SAMSUNG, 52900, 59900, "Tecnología",
                    SAMSUNG_URL + "galaxy-a57-5g-256gb.html"),
            new Ejemplo(DIEGO, "Sandalias Sponge color negro ADOC", ADOC, 1000, 2199, "Ropa",
                    ADOC_URL + "30070078-sandalias-sponge-color-negro"),
            new Ejemplo(CARLOS, "Botas Brock café para hombre CAT", ADOC, 6999, 9999, "Ropa",
                    ADOC_URL + "30032681-botas-brock-cafe-para-hombre"),
            new Ejemplo(SOFIA, "Sneakers Cody color café ADOC", ADOC, 4199, null, "Ropa",
                    ADOC_URL + "30072866-sneakers-cody-color-cafe"),
            new Ejemplo(ANA, "Camiseta Fruit of the Loom blanca, paquete de 3", WALMART, 975, null, "Ropa",
                    "https://www.walmart.com.sv/camiseta-fruit-of-the-loom-blanco-l-3p/p"));

    private final CategoriaRepository categorias;
    private final UsuarioRepository usuarios;
    private final OfertaRepository ofertas;
    private final VotoRepository votos;
    private final ComentarioRepository comentarios;
    private final PasswordEncoder codificador;
    private final String demoPassword;

    public DemoData(CategoriaRepository categorias, UsuarioRepository usuarios, OfertaRepository ofertas,
                    VotoRepository votos, ComentarioRepository comentarios, PasswordEncoder codificador,
                    @Value("${tonalli.demo.password:}") String demoPassword) {
        this.categorias = categorias;
        this.usuarios = usuarios;
        this.ofertas = ofertas;
        this.votos = votos;
        this.comentarios = comentarios;
        this.codificador = codificador;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Solo se cargan sobre una base sin ofertas: reiniciar el sitio no duplica nada.
        if (ofertas.count() > 0) {
            return;
        }

        // Un solo hash para todos: BCrypt con costo 12 tarda ~250 ms y aquí la contraseña es la misma.
        String hash = codificador.encode(claveDeEjemplo());
        Map<String, Usuario> porCorreo = new LinkedHashMap<>();
        porCorreo.put(DEMO, usuario(DEMO, "Usuario Demo", 12, hash));
        porCorreo.put(ANA, usuario(ANA, "Ana Martínez", 55, hash));
        porCorreo.put(CARLOS, usuario(CARLOS, "Carlos Rivas", 24, hash));
        porCorreo.put(SOFIA, usuario(SOFIA, "Sofía Hernández", 8, hash));
        porCorreo.put(DIEGO, usuario(DIEGO, "Diego Flores", 0, hash));

        List<Oferta> creadas = new ArrayList<>();
        for (Ejemplo e : EJEMPLOS) {
            creadas.add(oferta(e, porCorreo.get(e.autor())));
        }

        // Votos: +1 caliente, -1 frío. Nadie vota su propia oferta (misma regla que OfertaService.votar).
        votar(creadas.get(1), porCorreo, 1, DEMO, CARLOS, SOFIA);
        votar(creadas.get(2), porCorreo, 1, ANA, DIEGO);
        votar(creadas.get(3), porCorreo, -1, ANA, CARLOS);
        votar(creadas.get(4), porCorreo, 1, ANA, CARLOS);
        votar(creadas.get(5), porCorreo, 1, DEMO, ANA);
        votar(creadas.get(6), porCorreo, 1, DEMO, CARLOS, SOFIA, DIEGO);
        votar(creadas.get(7), porCorreo, 1, DEMO, ANA, SOFIA, DIEGO);
        votar(creadas.get(8), porCorreo, 1, ANA, CARLOS);
        votar(creadas.get(9), porCorreo, 1, DEMO, ANA, CARLOS);
        votar(creadas.get(10), porCorreo, 1, DEMO);
        votar(creadas.get(10), porCorreo, -1, CARLOS);
        votar(creadas.get(11), porCorreo, 1, DEMO, ANA, CARLOS, SOFIA);
        votar(creadas.get(12), porCorreo, 1, ANA);
        votar(creadas.get(12), porCorreo, -1, SOFIA);
        votar(creadas.get(14), porCorreo, 1, DEMO, DIEGO);

        comentar(creadas.get(3), porCorreo.get(ANA), "Son solo 20 centavos de rebaja; para mí no llega a oferta.");
        comentar(creadas.get(6), porCorreo.get(SOFIA), "¿Alguien sabe hasta cuándo dura el precio? La página no dice fecha.");
        comentar(creadas.get(11), porCorreo.get(CARLOS), "Menos de la mitad del precio anterior. Buen hallazgo.");

        log.info("Datos de ejemplo cargados: {} usuarios y {} ofertas.", porCorreo.size(), creadas.size());
    }

    /**
     * Contraseña de los usuarios de ejemplo. Si no se configuró una válida (12 a 64 caracteres) se usa una
     * al azar que no se guarda ni se escribe en el registro: los usuarios existen, pero nadie puede entrar.
     */
    private String claveDeEjemplo() {
        if (demoPassword != null && demoPassword.length() >= 12 && demoPassword.length() <= 64) {
            return demoPassword;
        }
        byte[] azar = new byte[32];
        new SecureRandom().nextBytes(azar);
        log.info("TONALLI_DEMO_PASSWORD no definida: los usuarios de ejemplo no podrán iniciar sesión.");
        return Base64.getUrlEncoder().withoutPadding().encodeToString(azar);
    }

    private Usuario usuario(String correo, String nombre, int karma, String hash) {
        return usuarios.findByCorreo(correo).orElseGet(() -> {
            Usuario u = new Usuario();
            u.setCorreo(correo);
            u.setNombre(nombre);
            u.setPasswordHash(hash);
            u.setVerificado(true);
            u.setKarma(karma);
            return usuarios.save(u);
        });
    }

    private Oferta oferta(Ejemplo e, Usuario autor) {
        Categoria c = categorias.findByNombreIgnoreCase(e.categoria()).orElseThrow();
        Oferta o = new Oferta();
        o.setTitulo(e.titulo());
        o.setTienda(e.tienda());
        o.setPrecioCentavos(e.precio());
        o.setPrecioAnteriorCentavos(e.anterior());
        o.setEnlace(e.enlace());
        o.setCategoria(c);
        o.setAutor(autor);
        return ofertas.save(o);
    }

    private void votar(Oferta oferta, Map<String, Usuario> porCorreo, int valor, String... correos) {
        for (String correo : correos) {
            Usuario votante = porCorreo.get(correo);
            Voto v = new Voto();
            v.setUsuario(votante);
            v.setOferta(oferta);
            v.setValor(valor);
            v.setPeso(OfertaService.pesoPorKarma(votante.getKarma()));
            votos.save(v);
        }
    }

    private void comentar(Oferta oferta, Usuario autor, String texto) {
        Comentario c = new Comentario();
        c.setOferta(oferta);
        c.setAutor(autor);
        c.setTexto(texto);
        comentarios.save(c);
    }
}
