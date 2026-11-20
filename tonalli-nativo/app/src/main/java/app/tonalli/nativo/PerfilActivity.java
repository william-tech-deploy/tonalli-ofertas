package app.tonalli.nativo;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Perfil: nombre, nivel y karma (los calcula Supabase), estadísticas y cerrar sesión. */
public class PerfilActivity extends AppCompatActivity {

    private View sinSesion, conSesion;
    private TextView tvInicial, tvUsuario, tvCorreo, tvNivel, tvKarma, tvSiguiente, tvOfertas, tvVotos, tvAlertas, tvAviso;
    private ProgressBar pbNivel;
    private final ExecutorService hilo = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_perfil);
        Ui.ajustarBordes(findViewById(R.id.main));

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Perfil");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        sinSesion = findViewById(R.id.sinSesion);
        conSesion = findViewById(R.id.conSesion);
        tvInicial = findViewById(R.id.tvInicial);
        tvUsuario = findViewById(R.id.tvUsuario);
        tvCorreo = findViewById(R.id.tvCorreo);
        tvNivel = findViewById(R.id.tvNivel);
        tvKarma = findViewById(R.id.tvKarma);
        tvSiguiente = findViewById(R.id.tvSiguiente);
        tvOfertas = findViewById(R.id.tvOfertas);
        tvVotos = findViewById(R.id.tvVotos);
        tvAlertas = findViewById(R.id.tvAlertas);
        tvAviso = findViewById(R.id.tvAviso);
        pbNivel = findViewById(R.id.pbNivel);

        findViewById(R.id.btnEntrar).setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));
        findViewById(R.id.btnCrear).setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class).putExtra(LoginActivity.EXTRA_REGISTRO, true)));
        findViewById(R.id.btnSalir).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Cerrar sesión")
                .setMessage("¿Seguro que quieres salir de tu cuenta en este teléfono?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Cerrar sesión", (d, w) -> salir())
                .show());
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargar(); // al volver del inicio de sesión se actualiza
    }

    @Override
    protected void onDestroy() {
        hilo.shutdown();
        super.onDestroy();
    }

    private void cargar() {
        tvAlertas.setText(String.valueOf(new TonalliDb(this).listarAlertas().size()));
        if (!Supabase.configurado()) {
            mostrarSinSesion("Para tener cuenta, configura Supabase en local.properties (ver supabase/LEEME.md).");
            return;
        }
        final android.content.Context app = getApplicationContext();
        hilo.execute(() -> {
            Sesion s = null;
            Supabase.Perfil p = null;
            String error = null;
            try {
                s = Supabase.sesionVigente(app);
                if (s != null) p = Supabase.perfil(s);
            } catch (Exception e) {
                error = e instanceof Supabase.ErrorApi ? e.getMessage() : "No se pudo cargar el perfil.";
            }
            final Sesion ses = s;
            final Supabase.Perfil per = p;
            final String err = error;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (ses == null && err == null) mostrarSinSesion(null);
                else if (per == null) {
                    mostrarSinSesion(null);
                    tvAviso.setText(err);
                    tvAviso.setVisibility(View.VISIBLE);
                } else mostrar(ses, per);
            });
        });
    }

    private void mostrarSinSesion(String aviso) {
        conSesion.setVisibility(View.GONE);
        sinSesion.setVisibility(View.VISIBLE);
        tvAviso.setVisibility(aviso == null ? View.GONE : View.VISIBLE);
        if (aviso != null) {
            tvAviso.setText(aviso);
            findViewById(R.id.btnEntrar).setEnabled(false);
            findViewById(R.id.btnCrear).setEnabled(false);
        }
    }

    private void mostrar(Sesion s, Supabase.Perfil p) {
        sinSesion.setVisibility(View.GONE);
        conSesion.setVisibility(View.VISIBLE);
        tvInicial.setText(p.usuario.substring(0, 1).toUpperCase(java.util.Locale.ROOT));
        tvUsuario.setText(p.usuario);
        tvCorreo.setText(s.email);
        int n = Oferta.nivel(p.karma);
        tvNivel.setText("Nivel " + (n + 1) + " · " + Oferta.NOMBRES_NIVEL[n]);
        tvKarma.setText(p.karma + " de karma");
        if (n + 1 < Oferta.NIVELES.length) {
            int desde = Oferta.NIVELES[n], hasta = Oferta.NIVELES[n + 1];
            pbNivel.setMax(hasta - desde);
            pbNivel.setProgress(p.karma - desde);
            tvSiguiente.setText("Faltan " + (hasta - p.karma) + " para " + Oferta.NOMBRES_NIVEL[n + 1]);
        } else {
            pbNivel.setMax(1);
            pbNivel.setProgress(1);
            tvSiguiente.setText("Llegaste al nivel más alto");
        }
        tvOfertas.setText(String.valueOf(p.ofertas));
        tvVotos.setText(String.valueOf(p.votos));
    }

    private void salir() {
        final android.content.Context app = getApplicationContext();
        hilo.execute(() -> {
            Supabase.cerrar(app);
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) cargar();
            });
        });
    }
}
