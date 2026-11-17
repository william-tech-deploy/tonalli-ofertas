package app.tonalli.nativo;

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
    private View insignia;

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
        insignia = findViewById(R.id.insignia);

        ((TextView) findViewById(R.id.tvTitulo)).setText(oferta.titulo);
        ((TextView) findViewById(R.id.tvTienda)).setText(oferta.tienda + " · " + oferta.categoria);
        ((TextView) findViewById(R.id.tvAhora)).setText(Ui.dinero(oferta.ahora));
        TextView tvAntes = findViewById(R.id.tvAntes);
        tvAntes.setText(Ui.dinero(oferta.antes));
        tvAntes.setPaintFlags(tvAntes.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        ((TextView) findViewById(R.id.tvDescuento)).setText("-" + oferta.descuento() + "%  ·  ahorras "
                + Ui.dinero(oferta.antes - oferta.ahora));
        ((TextView) findViewById(R.id.tvFecha)).setText("Publicada "
                + DateUtils.getRelativeTimeSpanString(oferta.fecha, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                + (oferta.remota != null ? "  ·  vino del servicio web" : "  ·  guardada en este teléfono"));

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

    /** Votar dos veces lo mismo quita el voto. El cambio se guarda en SQLite de inmediato. */
    private void votar(int valor) {
        int nuevo = oferta.miVoto == valor ? 0 : valor;
        db.votar(oferta.id, nuevo);
        Oferta fresca = db.obtener(oferta.id);
        if (fresca != null) oferta = fresca;
        pintar();
    }

    private void pintar() {
        int t = oferta.temperatura();
        tvTemp.setText(t + "°");
        tvCalor.setText(oferta.calor());
        OfertaAdapter.pintarCalor(this, oferta, insignia, tvTemp, tvCalor);
        // Termómetro: de -50 a 150 grados se dibuja en una barra de 0 a 200.
        pbTemp.setMax(200);
        pbTemp.setProgress(Math.max(0, Math.min(200, t + 50)));
        tvVotos.setText(oferta.up + " a favor  ·  " + oferta.down + " en contra");
        btnSubir.setText(oferta.miVoto == 1 ? "✓ Es buena" : "Es buena");
        btnBajar.setText(oferta.miVoto == -1 ? "✓ No es real" : "No es real");
    }
}
