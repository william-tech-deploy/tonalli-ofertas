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
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.tabs.TabLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pantalla principal: lista de ofertas.
 * Temas: T4 (Activity + Intents), T5 (controles), T7 (menú de opciones, menú contextual, ActionBar),
 * T10-A (lee SQLite), T10-B (sincroniza con Supabase o con el JSON), T11 (ordena por cercanía con GPS).
 */
public class MainActivity extends AppCompatActivity {

    private static final int ORDEN_CERCA = 2;

    private TonalliDb db;
    private final List<Oferta> ofertas = new ArrayList<>();
    private OfertaAdapter adapter;
    private TabLayout tabs;
    private BottomNavigationView nav;
    private MaterialButtonToggleGroup grupoOrden;
    private TextView tvVacio, tvNota;
    private String categoria = "Todas";
    private int orden = TonalliDb.ORDEN_CALIENTES;
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

        tvVacio = findViewById(R.id.tvVacio);
        tvNota = findViewById(R.id.tvNota);
        ListView lista = findViewById(R.id.lvOfertas);

        // Pestañas de categoría con su ícono, igual que la versión web.
        tabs = findViewById(R.id.tabs);
        for (String nombre : getResources().getStringArray(R.array.filtro_categorias)) {
            TabLayout.Tab t = tabs.newTab().setText(nombre);
            if (!nombre.equals("Todas")) t.setIcon(Marca.iconoCategoria(nombre));
            tabs.addTab(t);
        }
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                categoria = tab.getText() == null ? "Todas" : tab.getText().toString();
                cargar();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        // Selector Calientes / Nuevas / Cerca.
        grupoOrden = findViewById(R.id.grupoOrden);
        grupoOrden.addOnButtonCheckedListener((grupo, id, marcado) -> {
            if (!marcado) return;
            if (id == R.id.btnCalientes) {
                orden = TonalliDb.ORDEN_CALIENTES;
                cargar();
            } else if (id == R.id.btnNuevas) {
                orden = TonalliDb.ORDEN_NUEVAS;
                cargar();
            } else if (id == R.id.btnCerca) {
                pedirCercania();
            }
        });

        adapter = new OfertaAdapter(this, ofertas);
        lista.setAdapter(adapter);
        lista.setEmptyView(tvVacio);
        // Toque corto: abre el detalle pasando el id con un Intent explícito.
        lista.setOnItemClickListener((parent, view, position, id) -> abrirDetalle(this, ofertas.get(position)));
        // Toque largo: menú contextual.
        registerForContextMenu(lista);

        // Cuando PublicarActivity termina con RESULT_OK se recarga la lista.
        publicarLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                resultado -> {
                    if (resultado.getResultCode() == RESULT_OK) cargar();
                });
        permisoUbicacionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                permisos -> {
                    if (Ubicacion.tienePermiso(this)) ordenarPorCercania();
                    else {
                        Toast.makeText(this, "Sin permiso de ubicación no se puede ordenar por cercanía", Toast.LENGTH_LONG).show();
                        grupoOrden.check(R.id.btnCalientes);
                    }
                });
        permisoNotificacionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                concedido -> {
                });

        // Barra inferior: igual que la versión web.
        nav = findViewById(R.id.nav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_inicio) return true;
            if (id == R.id.nav_escaner) startActivity(new Intent(this, EscanerActivity.class));
            else if (id == R.id.nav_publicar) publicarLauncher.launch(new Intent(this, PublicarActivity.class));
            else if (id == R.id.nav_alertas) startActivity(new Intent(this, AlertasActivity.class));
            else if (id == R.id.nav_perfil) startActivity(new Intent(this, PerfilActivity.class));
            return false; // la pestaña marcada sigue siendo Inicio
        });

        // Android 13+: las notificaciones se piden en pantalla.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notificador.tienePermiso(this)) {
            permisoNotificacionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }

        // Con Supabase configurado, al abrir se traen las ofertas de la comunidad.
        if (savedInstanceState == null && Supabase.configurado()) sincronizar(false);
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

    /** Lee las ofertas de SQLite según la pestaña y el orden, y refresca la lista. */
    private void cargar() {
        ofertas.clear();
        ofertas.addAll(db.listar(categoria, orden == TonalliDb.ORDEN_NUEVAS ? TonalliDb.ORDEN_NUEVAS : TonalliDb.ORDEN_CALIENTES));
        if (orden == ORDEN_CERCA && miUbicacion != null) aplicarDistancias();
        adapter.notifyDataSetChanged();
        tvVacio.setText("No hay ofertas en esta categoría.\nPublica la primera con el botón Publicar.");
        tvNota.setText(Supabase.configurado()
                ? "Ofertas de la comunidad (Supabase) y de este teléfono."
                : "Datos de ejemplo guardados en este teléfono.");
    }

    public static void abrirDetalle(AppCompatActivity a, Oferta o) {
        Intent i = new Intent(a, DetalleActivity.class);
        i.putExtra(DetalleActivity.EXTRA_ID, o.id);
        a.startActivity(i);
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
        if (id == R.id.op_alertas) {
            startActivity(new Intent(this, AlertasActivity.class));
            return true;
        } else if (id == R.id.op_sincronizar) {
            sincronizar(true);
            return true;
        } else if (id == R.id.op_cercanas) {
            grupoOrden.check(R.id.btnCerca);
            return true;
        } else if (id == R.id.op_acerca) {
            new AlertDialog.Builder(this)
                    .setTitle("Tonalli")
                    .setMessage("Ofertas con temperatura real.\n\nVersión nativa (prototipo 2).\n"
                            + "Los datos se guardan en SQLite dentro del teléfono y se sincronizan con "
                            + (Supabase.configurado() ? "Supabase." : "un servicio web en JSON."))
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
            abrirDetalle(this, o);
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

    /** Pide el permiso de ubicación si falta y luego ordena por cercanía. */
    private void pedirCercania() {
        if (Ubicacion.tienePermiso(this)) ordenarPorCercania();
        else permisoUbicacionLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
    }

    private void ordenarPorCercania() {
        Toast.makeText(this, "Buscando tu ubicación…", Toast.LENGTH_SHORT).show();
        Ubicacion.obtener(this, ubicacion -> {
            if (isFinishing() || isDestroyed()) return;
            if (ubicacion == null) {
                Toast.makeText(this, "No se pudo obtener la ubicación. Activa el GPS e inténtalo de nuevo.", Toast.LENGTH_LONG).show();
                grupoOrden.check(R.id.btnCalientes);
                return;
            }
            miUbicacion = ubicacion;
            orden = ORDEN_CERCA;
            cargar();
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

    /**
     * Descarga ofertas en un hilo de fondo (Supabase si está configurado; si no, el JSON de ejemplo),
     * las guarda en SQLite y vuelve al hilo principal para refrescar la lista.
     */
    private void sincronizar(boolean avisar) {
        if (avisar) Toast.makeText(this, "Sincronizando…", Toast.LENGTH_SHORT).show();
        final android.content.Context app = getApplicationContext();
        hiloDeFondo.execute(() -> {
            String mensaje;
            try {
                List<Oferta> remotas;
                if (Supabase.configurado()) {
                    Sesion s = Supabase.sesionVigente(app);
                    remotas = Supabase.ofertas(s == null ? null : s.accessToken);
                    Set<String> vigentes = new HashSet<>();
                    for (Oferta o : remotas) vigentes.add(o.remota);
                    if (remotas.size() < 200) db.borrarRemotasQueNoEstan(vigentes);
                    guardarYAvisar(app, remotas);
                    Map<String, Integer> mios = new HashMap<>();
                    if (s != null) {
                        JSONArray v = Supabase.misVotos(s);
                        for (int i = 0; i < v.length(); i++) {
                            JSONObject j = v.getJSONObject(i);
                            mios.put("sb-" + j.getLong("oferta"), j.getInt("valor"));
                        }
                    }
                    db.aplicarMisVotos(mios);
                } else {
                    remotas = ServicioWeb.descargar();
                    guardarYAvisar(app, remotas);
                }
                mensaje = remotas.size() + " ofertas sincronizadas";
            } catch (Supabase.ErrorApi e) {
                mensaje = e.getMessage();
            } catch (Exception e) {
                mensaje = "No se pudo sincronizar: revisa tu conexión";
            }
            final String texto = mensaje;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                cargar();
                if (avisar) Toast.makeText(this, texto, Toast.LENGTH_LONG).show();
            });
        });
    }

    /** Guarda en SQLite y, si una oferta nueva cumple una alerta, avisa con una notificación. */
    private void guardarYAvisar(android.content.Context app, List<Oferta> remotas) {
        for (Oferta o : remotas) {
            long idNueva = db.guardarRemota(o);
            if (idNueva > 0) {
                o.id = idNueva;
                TonalliDb.Alerta alerta = db.alertaPara(o);
                if (alerta != null) Notificador.avisar(app, o, alerta);
            }
        }
    }
}
