package app.tonalli.nativo;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Guarda la sesión (tokens de Supabase) CIFRADA en el teléfono.
 * La llave AES vive en el Android Keystore: no se puede leer ni copiar fuera del dispositivo.
 * Rúbrica: OWASP MASVS-STORAGE-1 (datos sensibles cifrados en reposo).
 */
public final class Sesion {
    private static final String ALIAS = "tonalli_sesion";
    private static final String PREFS = "sesion";

    public String accessToken;
    public String refreshToken;
    public long expiraMs;   // momento en que vence el token de acceso
    public String userId;
    public String email;

    private Sesion() {
    }

    // ------------------------------------------------------------ cifrado

    private static SecretKey llave() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return kg.generateKey();
    }

    private static String cifrar(String texto) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, llave());
        byte[] iv = c.getIV();
        byte[] datos = c.doFinal(texto.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(datos, Base64.NO_WRAP);
    }

    private static String descifrar(String guardado) throws Exception {
        String[] partes = guardado.split(":");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, llave(), new GCMParameterSpec(128, Base64.decode(partes[0], Base64.NO_WRAP)));
        return new String(c.doFinal(Base64.decode(partes[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------ leer y guardar

    /** Devuelve la sesión guardada, o null si no hay (o si no se pudo descifrar). */
    public static synchronized Sesion cargar(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String guardado = p.getString("datos", null);
        if (guardado == null) return null;
        try {
            JSONObject j = new JSONObject(descifrar(guardado));
            Sesion s = new Sesion();
            s.accessToken = j.getString("a");
            s.refreshToken = j.getString("r");
            s.expiraMs = j.getLong("e");
            s.userId = j.getString("u");
            s.email = j.optString("m", "");
            return s;
        } catch (Exception e) {
            borrar(c); // datos dañados o llave perdida: se pide iniciar sesión otra vez
            return null;
        }
    }

    /** Crea la sesión a partir de la respuesta de /auth/v1/token o /auth/v1/signup. */
    public static synchronized Sesion guardar(Context c, JSONObject r) throws Exception {
        Sesion s = new Sesion();
        s.accessToken = r.getString("access_token");
        s.refreshToken = r.getString("refresh_token");
        s.expiraMs = System.currentTimeMillis() + r.optLong("expires_in", 3600) * 1000L;
        JSONObject u = r.getJSONObject("user");
        s.userId = u.getString("id");
        s.email = u.optString("email", "");
        JSONObject j = new JSONObject();
        j.put("a", s.accessToken).put("r", s.refreshToken).put("e", s.expiraMs).put("u", s.userId).put("m", s.email);
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("datos", cifrar(j.toString())).apply();
        return s;
    }

    public static synchronized void borrar(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }
}
