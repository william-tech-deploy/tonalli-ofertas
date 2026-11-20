package app.tonalli.nativo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Formulario para publicar una oferta.
 * Temas: T5 (EditText, Chips, Switch, Button), T9 (cámara y sonido), T10-A (INSERT en SQLite),
 * T10-B (envío a Supabase), T8 (notificación si cumple una alerta), T11 (ubicación de la tienda).
 */
public class PublicarActivity extends AppCompatActivity {

    private EditText etTitulo, etTienda, etAntes, etAhora, etCodigo;
    private ChipGroup grupoCategoria;
    private SwitchCompat swUbicacion;
    private ImageView ivFoto;
    private TextView tvError;
    private View btnGuardar;
    private byte[] fotoJpeg = null;
    private TonalliDb db;
    private final ExecutorService hilo = Executors.newSingleThreadExecutor();

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
        etCodigo = findViewById(R.id.etCodigo);
        grupoCategoria = findViewById(R.id.grupoCategoria);
        swUbicacion = findViewById(R.id.swUbicacion);
        ivFoto = findViewById(R.id.ivFoto);
        tvError = findViewById(R.id.tvError);
        btnGuardar = findViewById(R.id.btnGuardar);

        // Un chip por categoría, con su ícono (como en la versión web).
        boolean primero = true;
        for (String nombre : getResources().getStringArray(R.array.categorias)) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.chip_categoria, grupoCategoria, false);
            chip.setId(View.generateViewId());
            chip.setText(nombre);
            chip.setChipIconResource(Marca.iconoCategoria(nombre));
            grupoCategoria.addView(chip);
            if (primero) chip.setChecked(true);
            primero = false;
        }

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
        findViewById(R.id.btnEscanear).setOnClickListener(v -> Escaneo.leer(this, codigo -> etCodigo.setText(codigo)));
        btnGuardar.setOnClickListener(v -> validarYGuardar());
    }

    @Override
    protected void onDestroy() {
        hilo.shutdown();
        super.onDestroy();
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

    private String categoriaElegida() {
        Chip chip = findViewById(grupoCategoria.getCheckedChipId());
        return chip == null ? null : chip.getText().toString();
    }

    /** Valida los datos antes de guardar: nunca se confía en lo que escribe la persona. */
    private void validarYGuardar() {
        String titulo = etTitulo.getText().toString().trim();
        String tienda = etTienda.getText().toString().trim();
        String codigo = etCodigo.getText().toString().replaceAll("\\D", "");
        String categoria = categoriaElegida();
        double antes = numero(etAntes);
        double ahora = numero(etAhora);

        if (categoria == null) {
            error("Elige una categoría.");
            return;
        }
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
            error("El precio actual debe ser menor que el precio anterior.");
            return;
        }
        if (!codigo.isEmpty() && !Oferta.eanValido(codigo)) {
            error("El código de barras no es válido. Escanéalo o revisa los números.");
            return;
        }
        tvError.setVisibility(View.GONE);

        final Oferta o = new Oferta();
        o.titulo = titulo;
        o.tienda = tienda;
        o.categoria = categoria;
        o.antes = Math.round(antes * 100) / 100.0;
        o.ahora = Math.round(ahora * 100) / 100.0;
        o.ean = codigo.isEmpty() ? null : codigo;
        o.foto = fotoJpeg;

        btnGuardar.setEnabled(false); // evita guardar dos veces
        if (swUbicacion.isChecked() && Ubicacion.tienePermiso(this)) {
            Ubicacion.obtener(this, ubicacion -> {
                if (ubicacion != null) {
                    o.lat = ubicacion.getLatitude();
                    o.lng = ubicacion.getLongitude();
                }
                enviar(o);
            });
        } else {
            enviar(o);
        }
    }

    /** Con Supabase y sesión: se publica para toda la comunidad. Si no, se guarda solo en este teléfono. */
    private void enviar(Oferta o) {
        if (isFinishing() || isDestroyed()) return;
        if (!Supabase.configurado()) {
            guardarLocal(o);
            return;
        }
        final android.content.Context app = getApplicationContext();
        hilo.execute(() -> {
            Oferta publicada = null;
            String error = null;
            boolean sinSesion = false;
            try {
                Sesion s = Supabase.sesionVigente(app);
                if (s == null) sinSesion = true;
                else publicada = Supabase.publicar(s, o);
            } catch (Exception e) {
                error = e instanceof Supabase.ErrorApi ? e.getMessage() : "No se pudo publicar. Inténtalo de nuevo.";
            }
            final Oferta p = publicada;
            final String err = error;
            final boolean login = sinSesion;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (p != null) {
                    p.foto = o.foto; // la foto se queda en el teléfono
                    long id = db.guardarRemota(p);
                    if (id > 0) p.id = id;
                    terminar(p, "Oferta publicada para la comunidad");
                } else if (login) {
                    btnGuardar.setEnabled(true);
                    new AlertDialog.Builder(this)
                            .setTitle("Inicia sesión para publicar")
                            .setMessage("Con una cuenta, tu oferta la verá toda la comunidad y sumarás karma.")
                            .setPositiveButton("Iniciar sesión", (d, w) -> startActivity(new Intent(this, LoginActivity.class)))
                            .setNegativeButton("Guardar solo aquí", (d, w) -> guardarLocal(o))
                            .show();
                } else {
                    btnGuardar.setEnabled(true);
                    error(err);
                }
            });
        });
    }

    private void guardarLocal(Oferta o) {
        long id = db.insertar(o);
        if (id == -1) {
            btnGuardar.setEnabled(true);
            error("No se pudo guardar. Inténtalo de nuevo.");
            return;
        }
        o.id = id;
        terminar(o, o.tieneUbicacion() ? "Oferta guardada con ubicación" : "Oferta guardada en este teléfono");
    }

    private void terminar(Oferta o, String mensaje) {
        sonar();
        // ¿Cumple alguna alerta? Entonces se muestra una notificación del sistema.
        TonalliDb.Alerta alerta = db.alertaPara(o);
        if (alerta != null && !Notificador.avisar(this, o, alerta)) {
            Toast.makeText(this, "Cumple tu alerta \"" + alerta.palabra + "\" (activa las notificaciones para recibir el aviso)", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show();
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
