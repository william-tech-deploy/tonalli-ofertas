package app.tonalli.nativo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;

/**
 * Formulario para publicar una oferta.
 * Temas: T5 (EditText, Spinner, Switch, Button), T9 (cámara y sonido), T10-A (INSERT en SQLite),
 * T8 (notificación si cumple una alerta), T11 (guarda la ubicación de la tienda).
 */
public class PublicarActivity extends AppCompatActivity {

    private EditText etTitulo, etTienda, etAntes, etAhora;
    private Spinner spCategoria;
    private SwitchCompat swUbicacion;
    private ImageView ivFoto;
    private TextView tvError;
    private byte[] fotoJpeg = null;
    private TonalliDb db;

    private ActivityResultLauncher<Void> camaraLauncher;
    private ActivityResultLauncher<String> permisoCamaraLauncher;
    private ActivityResultLauncher<String[]> permisoUbicacionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_publicar);
        Ui.ajustarBordes(findViewById(R.id.main));

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        db = new TonalliDb(this);
        etTitulo = findViewById(R.id.etTitulo);
        etTienda = findViewById(R.id.etTienda);
        etAntes = findViewById(R.id.etAntes);
        etAhora = findViewById(R.id.etAhora);
        spCategoria = findViewById(R.id.spCategoria);
        swUbicacion = findViewById(R.id.swUbicacion);
        ivFoto = findViewById(R.id.ivFoto);
        tvError = findViewById(R.id.tvError);

        ArrayAdapter<CharSequence> categorias = ArrayAdapter.createFromResource(this,
                R.array.categorias, android.R.layout.simple_spinner_item);
        categorias.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategoria.setAdapter(categorias);

        // La cámara devuelve una miniatura (Bitmap); se guarda como JPEG dentro de SQLite.
        camaraLauncher = registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
            if (bitmap == null) return;
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, salida);
            fotoJpeg = salida.toByteArray();
            ivFoto.setImageBitmap(bitmap);
            ivFoto.setVisibility(View.VISIBLE);
        });
        permisoCamaraLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), concedido -> {
            if (concedido) abrirCamara();
            else Toast.makeText(this, "Sin permiso de cámara no se puede tomar la foto", Toast.LENGTH_LONG).show();
        });
        permisoUbicacionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permisos -> {
            if (!Ubicacion.tienePermiso(this)) {
                swUbicacion.setChecked(false);
                Toast.makeText(this, "Sin permiso, la oferta se guardará sin ubicación", Toast.LENGTH_LONG).show();
            }
        });

        swUbicacion.setOnCheckedChangeListener((boton, activo) -> {
            if (activo && !Ubicacion.tienePermiso(this)) {
                permisoUbicacionLauncher.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            }
        });

        findViewById(R.id.btnFoto).setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) abrirCamara();
            else permisoCamaraLauncher.launch(Manifest.permission.CAMERA);
        });
        findViewById(R.id.btnGuardar).setOnClickListener(v -> validarYGuardar());
    }

    private void abrirCamara() {
        try {
            camaraLauncher.launch(null);
        } catch (RuntimeException e) {
            Toast.makeText(this, "No se encontró una app de cámara", Toast.LENGTH_SHORT).show();
        }
    }

    private double numero(EditText campo) {
        try {
            return Double.parseDouble(campo.getText().toString().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Valida los datos antes de guardar: nunca se confía en lo que escribe la persona. */
    private void validarYGuardar() {
        String titulo = etTitulo.getText().toString().trim();
        String tienda = etTienda.getText().toString().trim();
        double antes = numero(etAntes);
        double ahora = numero(etAhora);

        if (titulo.length() < 3) {
            error("Escribe el nombre del producto (mínimo 3 letras).");
            return;
        }
        if (tienda.length() < 2) {
            error("Escribe el nombre de la tienda.");
            return;
        }
        if (antes <= 0 || ahora <= 0 || antes > 100000) {
            error("Escribe los dos precios con números mayores que cero.");
            return;
        }
        if (ahora >= antes) {
            error("El precio de oferta debe ser menor que el precio anterior.");
            return;
        }
        tvError.setVisibility(View.GONE);

        final Oferta o = new Oferta();
        o.titulo = titulo;
        o.tienda = tienda;
        o.categoria = spCategoria.getSelectedItem().toString();
        o.antes = antes;
        o.ahora = ahora;
        o.foto = fotoJpeg;

        findViewById(R.id.btnGuardar).setEnabled(false); // evita guardar dos veces
        if (swUbicacion.isChecked() && Ubicacion.tienePermiso(this)) {
            Ubicacion.obtener(this, ubicacion -> {
                if (ubicacion != null) {
                    o.lat = ubicacion.getLatitude();
                    o.lng = ubicacion.getLongitude();
                }
                guardar(o);
            });
        } else {
            guardar(o);
        }
    }

    private void guardar(Oferta o) {
        if (isFinishing() || isDestroyed()) return;
        long id = db.insertar(o);
        if (id == -1) {
            findViewById(R.id.btnGuardar).setEnabled(true);
            error("No se pudo guardar. Inténtalo de nuevo.");
            return;
        }
        o.id = id;
        sonar();

        // ¿Cumple alguna alerta? Entonces se muestra una notificación del sistema.
        TonalliDb.Alerta alerta = db.alertaPara(o);
        if (alerta != null && !Notificador.avisar(this, o, alerta)) {
            Toast.makeText(this, "Cumple tu alerta \"" + alerta.palabra + "\" (activa las notificaciones para recibir el aviso)", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, o.tieneUbicacion() ? "Oferta publicada con ubicación" : "Oferta publicada", Toast.LENGTH_SHORT).show();
        }
        setResult(RESULT_OK); // MainActivity recarga la lista
        finish();
    }

    /** Audio (T9): reproduce res/raw/exito.wav con MediaPlayer y libera el reproductor al terminar. */
    private void sonar() {
        try {
            MediaPlayer mp = MediaPlayer.create(getApplicationContext(), R.raw.exito);
            if (mp != null) {
                mp.setOnCompletionListener(MediaPlayer::release);
                mp.start();
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void error(String mensaje) {
        tvError.setText(mensaje);
        tvError.setVisibility(View.VISIBLE);
    }
}
