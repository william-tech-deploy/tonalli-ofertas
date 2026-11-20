package app.tonalli.nativo;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

/**
 * Detalle de una oferta. Recibe el id por Intent (EXTRA_ID) y lee el resto de SQLite.
 * Temas: T4 (paso de datos entre Activities), T5/T6 (controles y elementos gráficos).
 */
public class DetalleActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "oferta_id";

    private TonalliDb db;
    private Oferta oferta;
    private TextView tvTemp, tvCalor, tvVotos;
    private ProgressBar pbTemp;
    private Button btnSubir, btnBajar;
    private ImageView ivCalor;
    private boolean enviando = false;
    private final java.util.concurrent.ExecutorService hilo = java.util.concurrent.Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_detalle);
        Ui.ajustarBordes(findViewById(R.id.main));

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        db = new TonalliDb(this);
        long id = getIntent().getLongExtra(EXTRA_ID, -1);
        oferta = db.obtener(id);
        if (oferta == null) {
            Toast.makeText(this, "La oferta ya no existe", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvTemp = findViewById(R.id.tvTemp);
        tvCalor = findViewById(R.id.tvCalor);
        tvVotos = findViewById(R.id.tvVotos);
        pbTemp = findViewById(R.id.pbTemp);
        btnSubir = findViewById(R.id.btnSubir);
        btnBajar = findViewById(R.id.btnBajar);
        ivCalor = findViewById(R.id.ivCalor);
        pbTemp.setOnTouchListener((v, e) -> true); // el termómetro solo se mira, no se arrastra

        ((TextView) findViewById(R.id.tvTitulo)).setText(oferta.titulo);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(oferta.categoria);
        ((TextView) findViewById(R.id.tvTienda)).setText(oferta.tienda + " · " + oferta.categoria);
        Marca.pintarCategoria(this, findViewById(R.id.hero), findViewById(R.id.ivCategoria), oferta.categoria);
        ((TextView) findViewById(R.id.tvAhora)).setText(Ui.dinero(oferta.ahora));
        TextView tvAntes = findViewById(R.id.tvAntes);
        tvAntes.setText(Ui.dinero(oferta.antes));
        tvAntes.setPaintFlags(tvAntes.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        ((TextView) findViewById(R.id.tvDescuento)).setText("−" + oferta.descuento() + "%");
        ((TextView) findViewById(R.id.tvAhorro)).setText("Ahorras " + Ui.dinero(oferta.antes - oferta.ahora));
        ((TextView) findViewById(R.id.tvFecha)).setText("Publicada "
                + DateUtils.getRelativeTimeSpanString(oferta.fecha, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                + (oferta.remota == null ? "  ·  guardada en este teléfono"
                : oferta.remota.startsWith("sb-") ? "  ·  de la comunidad (Supabase)" : "  ·  vino del servicio web")
                + (oferta.ean != null ? "\nCódigo de barras: " + oferta.ean : ""));

        ImageView ivFoto = findViewById(R.id.ivFoto);
        if (oferta.foto != null) {
            Bitmap bm = BitmapFactory.decodeByteArray(oferta.foto, 0, oferta.foto.length);
            if (bm != null) {
                ivFoto.setImageBitmap(bm);
                ivFoto.setVisibility(View.VISIBLE);
            }
        }

        btnSubir.setOnClickListener(v -> votar(1));
        btnBajar.setOnClickListener(v -> votar(-1));
        findViewById(R.id.btnMapa).setOnClickListener(v -> MainActivity.abrirMapa(this, oferta));
        findViewById(R.id.btnCompartir).setOnClickListener(v -> MainActivity.compartir(this, oferta));

        pintar();
    }

    /**
     * Votar dos veces lo mismo quita el voto.
     * Ofertas de la comunidad (Supabase): el voto va al servidor, que recuenta; se exige sesión.
     * Ofertas solo de este teléfono: el voto se guarda en SQLite.
     */
    private void votar(int valor) {
        if (enviando) return;
        int nuevo = oferta.miVoto == valor ? 0 : valor;
        long idRemoto = Supabase.idRemoto(oferta);
        if (idRemoto < 0 || !Supabase.configurado()) {
            db.votar(oferta.id, nuevo);
            refrescar();
            return;
        }
        enviando = true;
        final android.content.Context app = getApplicationContext();
        hilo.execute(() -> {
            String error = null;
            boolean pedirLogin = false;
            try {
                Sesion s = Supabase.sesionVigente(app);
                if (s == null) pedirLogin = true;
                else if (s.userId.equals(oferta.autor)) error = "No puedes votar tu propia oferta.";
                else {
                    int[] votos = Supabase.votar(s, idRemoto, nuevo);
                    db.actualizarVotos(oferta.id, votos[0], votos[1], nuevo);
                }
            } catch (Exception e) {
                error = e instanceof Supabase.ErrorApi ? e.getMessage() : "No se pudo votar. Inténtalo de nuevo.";
            }
            final String err = error;
            final boolean login = pedirLogin;
            runOnUiThread(() -> {
                enviando = false;
                if (isFinishing() || isDestroyed()) return;
                if (login) {
                    Toast.makeText(this, "Inicia sesión para votar", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, LoginActivity.class));
                } else if (err != null) {
                    Toast.makeText(this, err, Toast.LENGTH_LONG).show();
                } else {
                    refrescar();
                }
            });
        });
    }

    private void refrescar() {
        Oferta fresca = db.obtener(oferta.id);
        if (fresca != null) oferta = fresca;
        pintar();
    }

    @Override
    protected void onDestroy() {
        hilo.shutdown();
        super.onDestroy();
    }

    private void pintar() {
        int t = oferta.temperatura();
        tvTemp.setText(t + "°");
        tvCalor.setText(oferta.calor());
        Marca.pintarCalor(this, oferta, ivCalor, tvTemp, tvCalor);
        // Termómetro: de -50 a 150 grados se dibuja en una barra de 0 a 200.
        pbTemp.setMax(200);
        pbTemp.setProgress(Math.max(0, Math.min(200, t + 50)));
        tvVotos.setText(oferta.up + " a favor  ·  " + oferta.down + " en contra");
        btnSubir.setText(oferta.miVoto == 1 ? "✓ Es real +1" : "Es real +1");
        btnBajar.setText(oferta.miVoto == -1 ? "✓ Es falsa −1" : "Es falsa −1");
    }
}
