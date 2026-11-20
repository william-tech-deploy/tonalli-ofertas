package app.tonalli.nativo;

import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Iniciar sesión o crear cuenta con Supabase Auth.
 * La contraseña nunca se guarda en el teléfono: solo los tokens, cifrados (ver Sesion.java).
 */
public class LoginActivity extends AppCompatActivity {

    public static final String EXTRA_REGISTRO = "registro";

    private MaterialButtonToggleGroup modo;
    private EditText etCorreo, etUsuario, etClave;
    private View grupoUsuario;
    private TextView tvMensaje, tvAyudaClave;
    private Button btnEnviar;
    private boolean registro = false;
    private final ExecutorService hilo = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        Ui.ajustarBordes(findViewById(R.id.main));

        modo = findViewById(R.id.grupoModo);
        etCorreo = findViewById(R.id.etCorreo);
        etUsuario = findViewById(R.id.etUsuario);
        etClave = findViewById(R.id.etClave);
        grupoUsuario = findViewById(R.id.grupoUsuario);
        tvMensaje = findViewById(R.id.tvMensaje);
        tvAyudaClave = findViewById(R.id.tvAyudaClave);
        btnEnviar = findViewById(R.id.btnEnviar);

        modo.addOnButtonCheckedListener((g, id, marcado) -> {
            if (marcado) cambiarModo(id == R.id.btnModoRegistro);
        });
        modo.check(getIntent().getBooleanExtra(EXTRA_REGISTRO, false) ? R.id.btnModoRegistro : R.id.btnModoLogin);
        findViewById(R.id.btnCerrar).setOnClickListener(v -> finish());
        btnEnviar.setOnClickListener(v -> enviar());

        if (!Supabase.configurado()) {
            mensaje("Falta configurar Supabase en local.properties (ver supabase/LEEME.md).", true);
            btnEnviar.setEnabled(false);
        }
    }

    @Override
    protected void onDestroy() {
        hilo.shutdown();
        super.onDestroy();
    }

    private void cambiarModo(boolean esRegistro) {
        registro = esRegistro;
        grupoUsuario.setVisibility(esRegistro ? View.VISIBLE : View.GONE);
        tvAyudaClave.setVisibility(esRegistro ? View.VISIBLE : View.GONE);
        btnEnviar.setText(esRegistro ? "Crear cuenta" : "Iniciar sesión");
        tvMensaje.setVisibility(View.GONE);
    }

    private void enviar() {
        String correo = etCorreo.getText().toString().trim();
        String usuario = etUsuario.getText().toString().trim();
        String clave = etClave.getText().toString();

        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            mensaje("Escribe un correo válido.", true);
            return;
        }
        if (registro && !usuario.matches("[A-Za-z0-9_.]{3,20}")) {
            mensaje("El usuario debe tener de 3 a 20 letras, números, punto o guion bajo.", true);
            return;
        }
        if (registro && clave.length() < 10) {
            mensaje("La contraseña debe tener al menos 10 caracteres.", true);
            return;
        }
        if (clave.isEmpty()) {
            mensaje("Escribe tu contraseña.", true);
            return;
        }

        btnEnviar.setEnabled(false);
        mensaje(registro ? "Creando tu cuenta…" : "Entrando…", false);
        final android.content.Context app = getApplicationContext();
        final boolean esRegistro = registro;
        hilo.execute(() -> {
            String error = null;
            boolean dentro = false;
            try {
                if (esRegistro) dentro = Supabase.registrar(app, correo, clave, usuario) != null;
                else dentro = Supabase.iniciar(app, correo, clave) != null;
            } catch (Exception e) {
                error = e instanceof Supabase.ErrorApi ? e.getMessage() : "No se pudo completar. Inténtalo de nuevo.";
            }
            final String err = error;
            final boolean ok = dentro;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                btnEnviar.setEnabled(true);
                if (err != null) {
                    mensaje(err, true);
                } else if (ok) {
                    setResult(RESULT_OK);
                    finish();
                } else {
                    // Supabase pide confirmar el correo antes de iniciar sesión.
                    etClave.setText("");
                    modo.check(R.id.btnModoLogin);
                    mensaje("Te enviamos un correo a " + correo + ". Ábrelo, confirma tu cuenta y luego inicia sesión.", false);
                }
            });
        });
    }

    private void mensaje(String texto, boolean esError) {
        tvMensaje.setText(texto);
        tvMensaje.setTextColor(getColor(esError ? R.color.tonalli_error : R.color.oferta_verde));
        tvMensaje.setVisibility(View.VISIBLE);
    }
}
