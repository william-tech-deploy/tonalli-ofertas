package app.tonalli.nativo;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pantalla principal: lista de ofertas.
 * Temas: T4 (Activity + Intents), T5 (controles), T7 (menú de opciones, menú contextual, ActionBar),
 * T10-A (lee SQLite), T10-B (sincroniza con el servicio web), T11 (ordena por cercanía con GPS).
 */
public class MainActivity extends AppCompatActivity {

    private TonalliDb db;
    private final List<Oferta> ofertas = new ArrayList<>();
    private OfertaAdapter adapter;
    private Spinner spFiltro;
    private TextView tvVacio;
    private boolean porCercania = false;
    private Location miUbicacion = null;
    private final ExecutorService hiloDeFondo = Executors.newSingleThreadExecutor();

    private ActivityResultLauncher<Intent> publicarLauncher;
    private ActivityResultLauncher<String[]> permisoUbicacionLauncher;
    private ActivityResultLauncher<String> permisoNotificacionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        Ui.ajustarBordes(findViewById(R.id.main));

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        db = new TonalliDb(this);
        Notificador.crearCanal(this);

        spFiltro = findViewById(R.id.spFiltro);
        tvVacio = findViewById(R.id.tvVacio);
        ListView lista = findViewById(R.id.lvOfertas);

        ArrayAdapter<CharSequence> filtro = ArrayAdapter.createFromResource(this,
                R.array.filtro_categorias, android.R.layout.simple_spinner_item);
        filtro.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spFiltro.setAdapter(filtro);
        spFiltro.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                cargar();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        adapter = new OfertaAdapter(this, ofertas);
        lista.setAdapter(adapter);
        lista.setEmptyView(tvVacio);
        // Toque corto: abre el detalle pasando el id con un Intent explícito.
        lista.setOnItemClickListener((parent, view, position, id) -> abrirDetalle(ofertas.get(position)));
        // Toque largo: menú contextual.
        registerForContextMenu(lista);

        findViewById(R.id.fabPublicar).setOnClickListener(v ->
                publicarLauncher.launch(new Intent(this, PublicarActivity.class)));

        // Cuando PublicarActivity termina con RESULT_OK se recarga la lista.
        publicarLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                resultado -> {
                    if (resultado.getResultCode() == RESULT_OK) cargar();
                });
        permisoUbicacionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                permisos -> {
                    if (Ubicacion.tienePermiso(this)) ordenarPorCercania();
                    else Toast.makeText(this, "Sin permiso de ubicación no se puede ordenar por cercanía", Toast.LENGTH_LONG).show();
                });
        permisoNotificacionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                concedido -> {
                });

        // Android 13+: las notificaciones se piden en pantalla.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notificador.tienePermiso(this)) {
            permisoNotificacionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargar(); // al volver del detalle, los votos pueden haber cambiado
    }

    @Override
    protected void onDestroy() {
        hiloDeFondo.shutdown();
        super.onDestroy();
    }

    /** Lee las ofertas de SQLite según el filtro y refresca la lista. */
    private void cargar() {
        String categoria = spFiltro.getSelectedItem() == null ? "Todas" : spFiltro.getSelectedItem().toString();
        ofertas.clear();
        ofertas.addAll(db.listar(categoria));
        if (porCercania && miUbicacion != null) aplicarDistancias();
        adapter.notifyDataSetChanged();
        tvVacio.setText("No hay ofertas en esta categoría.\nPublica la primera con el botón de abajo.");
    }

    private void abrirDetalle(Oferta o) {
        Intent i = new Intent(this, DetalleActivity.class);
        i.putExtra(DetalleActivity.EXTRA_ID, o.id);
        startActivity(i);
    }

    // ------------------------------------------------------------ menú de opciones (ActionBar)

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_principal, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.op_sincronizar) {
            sincronizar();
            return true;
        } else if (id == R.id.op_cercanas) {
            if (Ubicacion.tienePermiso(this)) ordenarPorCercania();
            else permisoUbicacionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            return true;
        } else if (id == R.id.op_temperatura) {
            porCercania = false;
            cargar();
            return true;
        } else if (id == R.id.op_alertas) {
            startActivity(new Intent(this, AlertasActivity.class));
            return true;
        } else if (id == R.id.op_acerca) {
            new AlertDialog.Builder(this)
                    .setTitle("Tonalli")
                    .setMessage("Ofertas con temperatura real.\n\nVersión nativa (prototipo 2).\n"
                            + "Los datos se guardan en SQLite dentro del teléfono y se sincronizan con un servicio web en JSON.")
                    .setPositiveButton("Cerrar", null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ------------------------------------------------------------ menú contextual (toque largo)

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        getMenuInflater().inflate(R.menu.menu_oferta, menu);
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        if (info != null && info.position < ofertas.size()) menu.setHeaderTitle(ofertas.get(info.position).titulo);
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        if (info == null || info.position >= ofertas.size()) return super.onContextItemSelected(item);
        Oferta o = ofertas.get(info.position);
        int id = item.getItemId();
        if (id == R.id.ctx_ver) {
            abrirDetalle(o);
            return true;
        } else if (id == R.id.ctx_compartir) {
            compartir(this, o);
            return true;
        } else if (id == R.id.ctx_mapa) {
            abrirMapa(this, o);
            return true;
        } else if (id == R.id.ctx_eliminar) {
            new AlertDialog.Builder(this)
                    .setTitle("Eliminar oferta")
                    .setMessage("¿Eliminar \"" + o.titulo + "\" de este teléfono?")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Eliminar", (d, w) -> {
                        db.eliminar(o.id);
                        cargar();
                    })
                    .show();
            return true;
        }
        return super.onContextItemSelected(item);
    }

    // ------------------------------------------------------------ Intents implícitos

    /** Intent implícito ACTION_SEND: Android muestra las apps que pueden compartir texto. */
    public static void compartir(AppCompatActivity a, Oferta o) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, o.titulo + " a " + Ui.dinero(o.ahora) + " (antes " + Ui.dinero(o.antes)
                + ") en " + o.tienda + ". Visto en Tonalli.");
        a.startActivity(Intent.createChooser(i, "Compartir oferta"));
    }

    /** Intent implícito ACTION_VIEW con una dirección geo: abre Google Maps en la tienda. */
    public static void abrirMapa(AppCompatActivity a, Oferta o) {
        if (!o.tieneUbicacion()) {
            Toast.makeText(a, "Esta oferta no tiene ubicación guardada", Toast.LENGTH_SHORT).show();
            return;
        }
        String geo = String.format(Locale.US, "geo:%f,%f?q=%f,%f(%s)", o.lat, o.lng, o.lat, o.lng, Uri.encode(o.tienda));
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(geo)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(a, "No hay una app de mapas instalada", Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------ GPS

    private void ordenarPorCercania() {
        Toast.makeText(this, "Buscando tu ubicación…", Toast.LENGTH_SHORT).show();
        Ubicacion.obtener(this, ubicacion -> {
            if (isFinishing() || isDestroyed()) return;
            if (ubicacion == null) {
                Toast.makeText(this, "No se pudo obtener la ubicación. Activa el GPS e inténtalo de nuevo.", Toast.LENGTH_LONG).show();
                return;
            }
            miUbicacion = ubicacion;
            porCercania = true;
            cargar();
            Toast.makeText(this, "Ordenadas de la más cercana a la más lejana", Toast.LENGTH_SHORT).show();
        });
    }

    private void aplicarDistancias() {
        float[] r = new float[1];
        for (Oferta o : ofertas) {
            if (o.tieneUbicacion()) {
                Location.distanceBetween(miUbicacion.getLatitude(), miUbicacion.getLongitude(), o.lat, o.lng, r);
                o.distanciaM = r[0];
            } else {
                o.distanciaM = -1f;
            }
        }
        Collections.sort(ofertas, (a, b) -> {
            float da = a.distanciaM < 0 ? Float.MAX_VALUE : a.distanciaM;
            float dbb = b.distanciaM < 0 ? Float.MAX_VALUE : b.distanciaM;
            return Float.compare(da, dbb);
        });
    }

    // ------------------------------------------------------------ servicio web

    /** Descarga el JSON en un hilo de fondo, lo guarda en SQLite y vuelve al hilo principal para refrescar. */
    private void sincronizar() {
        Toast.makeText(this, "Sincronizando…", Toast.LENGTH_SHORT).show();
        hiloDeFondo.execute(() -> {
            String mensaje;
            try {
                List<Oferta> remotas = ServicioWeb.descargar();
                int nuevas = 0;
                for (Oferta o : remotas) {
                    long idNueva = db.guardarRemota(o);
                    if (idNueva > 0) {
                        nuevas++;
                        o.id = idNueva;
                        // Si la oferta nueva cumple una alerta, se avisa con una notificación.
                        TonalliDb.Alerta alerta = db.alertaPara(o);
                        if (alerta != null) Notificador.avisar(getApplicationContext(), o, alerta);
                    }
                }
                mensaje = remotas.size() + " ofertas recibidas, " + nuevas + " nuevas";
            } catch (Exception e) {
                mensaje = "No se pudo sincronizar: revisa tu conexión";
            }
            final String texto = mensaje;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                cargar();
                Toast.makeText(this, texto, Toast.LENGTH_LONG).show();
            });
        });
    }
}
