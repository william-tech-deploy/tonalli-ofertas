package app.tonalli.nativo;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cliente de Supabase por HTTP (tema T10-B: servicios web REST con JSON).
 *  - Auth (registro, inicio de sesión, renovar, cerrar): /auth/v1/...
 *  - Datos (tablas perfiles, ofertas y votos):           /rest/v1/...
 * Todas las llamadas son de red: deben hacerse en un hilo de fondo.
 * La seguridad la impone el servidor (Row Level Security, ver supabase/esquema.sql).
 */
public final class Supabase {

    private static final String URL_BASE = BuildConfig.SUPABASE_URL.replaceAll("/+$", "");
    private static final String CLAVE = BuildConfig.SUPABASE_KEY; // clave pública (anon / publishable)
    private static final int MAX_BYTES = 1024 * 1024;

    private Supabase() {
    }

    /** true si local.properties tiene la dirección (HTTPS) y la clave pública. */
    public static boolean configurado() {
        boolean https = URL_BASE.startsWith("https://");
        boolean pruebaLocal = BuildConfig.DEBUG && URL_BASE.startsWith("http://10.0.2.2:");
        return (https || pruebaLocal) && !CLAVE.isEmpty();
    }

    /** Error devuelto por Supabase, con un mensaje listo para mostrar en español. */
    public static class ErrorApi extends Exception {
        public final int codigo;
        public final String clave;

        ErrorApi(int codigo, String clave, String mensaje) {
            super(mensaje);
            this.codigo = codigo;
            this.clave = clave;
        }
    }

    // ------------------------------------------------------------ HTTP

    private static String llamar(String metodo, String ruta, String json, String token, String prefer) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(URL_BASE + ruta).openConnection();
        try {
            con.setRequestMethod(metodo);
            con.setConnectTimeout(10000);
            con.setReadTimeout(15000);
            con.setUseCaches(false);
            con.setRequestProperty("apikey", CLAVE);
            con.setRequestProperty("Accept", "application/json");
            if (token != null) con.setRequestProperty("Authorization", "Bearer " + token);
            if (prefer != null) con.setRequestProperty("Prefer", prefer);
            if (json != null) {
                con.setDoOutput(true);
                con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream out = con.getOutputStream()) {
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                }
            }
            int codigo = con.getResponseCode();
            InputStream in = codigo >= 400 ? con.getErrorStream() : con.getInputStream();
            String cuerpo = in == null ? "" : leer(in);
            if (codigo >= 400) throw error(codigo, cuerpo);
            return cuerpo;
        } catch (java.io.IOException e) {
            throw new ErrorApi(0, "red", "No se pudo conectar. Revisa tu internet.");
        } finally {
            con.disconnect();
        }
    }

    private static String leer(InputStream in) throws java.io.IOException {
        try (InputStream entrada = in; ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            byte[] b = new byte[4096];
            int n;
            while ((n = entrada.read(b)) != -1) {
                salida.write(b, 0, n);
                if (salida.size() > MAX_BYTES) throw new java.io.IOException("respuesta demasiado grande");
            }
            return new String(salida.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** Traduce los errores de Supabase a mensajes claros, sin mostrar detalles internos. */
    private static ErrorApi error(int codigo, String cuerpo) {
        String clave = "";
        String texto = "";
        try {
            JSONObject j = new JSONObject(cuerpo);
            clave = j.optString("error_code", j.optString("code", j.optString("error", "")));
            texto = j.optString("msg", j.optString("message", j.optString("error_description", "")));
        } catch (Exception ignored) {
        }
        String t = texto.toLowerCase(Locale.ROOT);
        String m;
        if (clave.equals("invalid_credentials") || t.contains("invalid login")) m = "Correo o contraseña incorrectos.";
        else if (clave.equals("email_not_confirmed")) m = "Primero confirma tu correo con el enlace que te enviamos.";
        else if (clave.equals("user_already_exists") || t.contains("already registered")) m = "Ya existe una cuenta con ese correo.";
        else if (clave.equals("weak_password")) m = "La contraseña es muy débil. Usa al menos 10 caracteres.";
        else if (clave.equals("email_address_invalid") || t.contains("invalid format")) m = "Ese correo no es válido.";
        else if (clave.startsWith("over_") || codigo == 429) m = "Demasiados intentos. Espera un momento.";
        else if (clave.equals("signup_disabled")) m = "El registro está desactivado.";
        else if (t.contains("limite_diario")) m = "Llegaste al máximo de 20 ofertas por día.";
        else if (clave.equals("23505")) m = "Eso ya existe.";
        else if (clave.equals("23514") || clave.equals("22P02")) m = "Algún dato no es válido.";
        else if (clave.equals("42501") || codigo == 401 || codigo == 403) m = "No tienes permiso para hacer eso. Inicia sesión otra vez.";
        else if (clave.equals("refresh_token_not_found") || clave.equals("session_not_found")) m = "Tu sesión terminó. Inicia sesión otra vez.";
        else m = "Algo salió mal (" + codigo + "). Inténtalo de nuevo.";
        return new ErrorApi(codigo, clave, m);
    }

    private static String q(String v) {
        try {
            return URLEncoder.encode(v, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    // ------------------------------------------------------------ cuentas (Auth)

    /** Crea la cuenta. Devuelve la sesión si quedó iniciada, o null si Supabase pide confirmar el correo. */
    public static Sesion registrar(Context c, String email, String clave, String usuario) throws Exception {
        JSONObject cuerpo = new JSONObject()
                .put("email", email)
                .put("password", clave)
                .put("data", new JSONObject().put("usuario", usuario));
        JSONObject r = new JSONObject(llamar("POST", "/auth/v1/signup", cuerpo.toString(), null, null));
        if (r.has("access_token")) return Sesion.guardar(c, r);
        return null;
    }

    public static Sesion iniciar(Context c, String email, String clave) throws Exception {
        JSONObject cuerpo = new JSONObject().put("email", email).put("password", clave);
        return Sesion.guardar(c, new JSONObject(llamar("POST", "/auth/v1/token?grant_type=password", cuerpo.toString(), null, null)));
    }

    /**
     * Devuelve la sesión con un token de acceso vigente, renovándolo si vence en menos de 1 minuto.
     * Devuelve null si no hay sesión o si ya no se puede renovar.
     */
    public static synchronized Sesion sesionVigente(Context c) throws Exception {
        Sesion s = Sesion.cargar(c);
        if (s == null) return null;
        if (System.currentTimeMillis() < s.expiraMs - 60_000) return s;
        try {
            JSONObject cuerpo = new JSONObject().put("refresh_token", s.refreshToken);
            return Sesion.guardar(c, new JSONObject(llamar("POST", "/auth/v1/token?grant_type=refresh_token", cuerpo.toString(), null, null)));
        } catch (ErrorApi e) {
            if (e.codigo == 0) throw e;   // sin internet: no se borra la sesión
            Sesion.borrar(c);             // token revocado o vencido: hay que iniciar sesión otra vez
            return null;
        }
    }

    public static void cerrar(Context c) {
        try {
            Sesion s = Sesion.cargar(c);
            if (s != null) llamar("POST", "/auth/v1/logout", "{}", s.accessToken, null);
        } catch (Exception ignored) {
            // aunque falle la red, la sesión se borra del teléfono
        } finally {
            Sesion.borrar(c);
        }
    }

    // ------------------------------------------------------------ perfil

    public static class Perfil {
        public String usuario;
        public int karma;
        public int ofertas;
        public int votos;
    }

    public static Perfil perfil(Sesion s) throws Exception {
        JSONArray a = new JSONArray(llamar("GET", "/rest/v1/perfiles?select=usuario,karma&id=eq." + q(s.userId), null, s.accessToken, null));
        if (a.length() == 0) throw new ErrorApi(404, "", "No se encontró tu perfil.");
        Perfil p = new Perfil();
        p.usuario = a.getJSONObject(0).getString("usuario");
        p.karma = a.getJSONObject(0).getInt("karma");
        p.ofertas = new JSONArray(llamar("GET", "/rest/v1/ofertas?select=id&autor=eq." + q(s.userId), null, s.accessToken, null)).length();
        p.votos = new JSONArray(llamar("GET", "/rest/v1/votos?select=oferta", null, s.accessToken, null)).length();
        return p;
    }

    // ------------------------------------------------------------ ofertas y votos

    private static final String COLUMNAS = "id,autor,titulo,tienda,categoria,precio_antes,precio_ahora,ean,lat,lng,votos_up,votos_down,creada";

    /** Las 200 ofertas más recientes. El token es opcional (las ofertas son públicas). */
    public static List<Oferta> ofertas(String token) throws Exception {
        return convertir(new JSONArray(llamar("GET", "/rest/v1/ofertas?select=" + COLUMNAS + "&order=creada.desc&limit=200", null, token, null)));
    }

    public static List<Oferta> porCodigo(String ean) throws Exception {
        return convertir(new JSONArray(llamar("GET", "/rest/v1/ofertas?select=" + COLUMNAS + "&ean=eq." + q(ean), null, null, null)));
    }

    /** Mis votos: lista de {oferta, valor}. La regla RLS devuelve solo los de esta cuenta. */
    public static JSONArray misVotos(Sesion s) throws Exception {
        return new JSONArray(llamar("GET", "/rest/v1/votos?select=oferta,valor", null, s.accessToken, null));
    }

    /** Publica. El servidor pone autor, votos y fecha; aquí solo van los campos permitidos. */
    public static Oferta publicar(Sesion s, Oferta o) throws Exception {
        JSONObject j = new JSONObject()
                .put("titulo", o.titulo).put("tienda", o.tienda).put("categoria", o.categoria)
                .put("precio_antes", o.antes).put("precio_ahora", o.ahora)
                .put("lat", o.lat).put("lng", o.lng);
        if (o.ean != null && !o.ean.isEmpty()) j.put("ean", o.ean);
        JSONArray r = new JSONArray(llamar("POST", "/rest/v1/ofertas", j.toString(), s.accessToken, "return=representation"));
        return convertir(r).get(0);
    }

    /**
     * Vota (1 o -1) o quita el voto (0). Primero borra el voto anterior y luego crea el nuevo;
     * el servidor recuenta. Devuelve {votos_up, votos_down} actualizados.
     */
    public static int[] votar(Sesion s, long idRemoto, int valor) throws Exception {
        llamar("DELETE", "/rest/v1/votos?oferta=eq." + idRemoto + "&usuario=eq." + q(s.userId), null, s.accessToken, null);
        if (valor != 0) {
            String j = new JSONObject().put("oferta", idRemoto).put("valor", valor).toString();
            llamar("POST", "/rest/v1/votos", j, s.accessToken, null);
        }
        JSONArray a = new JSONArray(llamar("GET", "/rest/v1/ofertas?select=votos_up,votos_down&id=eq." + idRemoto, null, s.accessToken, null));
        if (a.length() == 0) throw new ErrorApi(404, "", "La oferta ya no existe.");
        return new int[]{a.getJSONObject(0).getInt("votos_up"), a.getJSONObject(0).getInt("votos_down")};
    }

    private static List<Oferta> convertir(JSONArray a) throws Exception {
        List<Oferta> lista = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject j = a.getJSONObject(i);
            Oferta o = new Oferta();
            o.remota = "sb-" + j.getLong("id");
            o.autor = j.optString("autor", null);
            o.titulo = j.getString("titulo");
            o.tienda = j.getString("tienda");
            o.categoria = j.getString("categoria");
            o.antes = j.getDouble("precio_antes");
            o.ahora = j.getDouble("precio_ahora");
            o.ean = j.isNull("ean") ? null : j.getString("ean");
            o.lat = j.optDouble("lat", 0);
            o.lng = j.optDouble("lng", 0);
            o.up = j.optInt("votos_up", 0);
            o.down = j.optInt("votos_down", 0);
            try {
                o.fecha = OffsetDateTime.parse(j.getString("creada")).toInstant().toEpochMilli();
            } catch (Exception e) {
                o.fecha = System.currentTimeMillis();
            }
            lista.add(o);
        }
        return lista;
    }

    /** Id numérico de Supabase a partir de "sb-123", o -1 si la oferta no es de Supabase. */
    public static long idRemoto(Oferta o) {
        if (o.remota == null || !o.remota.startsWith("sb-")) return -1;
        try {
            return Long.parseLong(o.remota.substring(3));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
