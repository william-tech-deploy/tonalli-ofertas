package app.tonalli.nativo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio web (tema T10-B): descarga ofertas en JSON por HTTPS.
 * Debe llamarse desde un hilo de fondo: Android prohíbe usar la red en el hilo principal.
 */
public final class ServicioWeb {
    /** Dirección del JSON. Para cambiar de servidor, cambia solo esta línea. */
    public static final String URL_OFERTAS = "https://william-tech-deploy.github.io/tonalli-ofertas/ofertas.json";

    private static final int MAX_BYTES = 512 * 1024; // no aceptar respuestas enormes

    private ServicioWeb() {
    }

    public static List<Oferta> descargar() throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(URL_OFERTAS).openConnection();
        try {
            con.setRequestMethod("GET");
            con.setConnectTimeout(8000);
            con.setReadTimeout(8000);
            con.setRequestProperty("Accept", "application/json");
            int codigo = con.getResponseCode();
            if (codigo != 200) throw new Exception("El servidor respondió " + codigo);
            String texto = leer(con.getInputStream());
            return interpretar(texto);
        } finally {
            con.disconnect();
        }
    }

    private static String leer(InputStream in) throws Exception {
        try (InputStream entrada = in; ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            byte[] bloque = new byte[4096];
            int n;
            while ((n = entrada.read(bloque)) != -1) {
                salida.write(bloque, 0, n);
                if (salida.size() > MAX_BYTES) throw new Exception("Respuesta demasiado grande");
            }
            return new String(salida.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** Convierte el texto JSON en objetos Oferta, validando cada campo. */
    static List<Oferta> interpretar(String texto) throws Exception {
        List<Oferta> lista = new ArrayList<>();
        JSONArray arreglo = new JSONObject(texto).getJSONArray("ofertas");
        for (int i = 0; i < arreglo.length() && i < 200; i++) {
            JSONObject j = arreglo.getJSONObject(i);
            Oferta o = new Oferta();
            o.remota = j.getString("id");
            o.titulo = recortar(j.getString("titulo"), 80);
            o.tienda = recortar(j.getString("tienda"), 50);
            o.categoria = recortar(j.getString("categoria"), 30);
            o.antes = j.getDouble("precio_antes");
            o.ahora = j.getDouble("precio_ahora");
            o.up = Math.max(0, j.optInt("votos_up", 0));
            o.down = Math.max(0, j.optInt("votos_down", 0));
            o.lat = j.optDouble("lat", 0);
            o.lng = j.optDouble("lng", 0);
            if (o.remota.isEmpty() || o.titulo.isEmpty() || o.antes <= 0 || o.ahora <= 0 || o.ahora >= o.antes) continue;
            lista.add(o);
        }
        return lista;
    }

    private static String recortar(String s, int max) {
        s = s == null ? "" : s.trim();
        return s.length() > max ? s.substring(0, max) : s;
    }
}
