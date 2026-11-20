package app.tonalli.nativo;

import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Escáner: lee el código de barras de un producto y muestra dónde está más barato.
 * También acepta escribir el código o el nombre del producto.
 */
public class EscanerActivity extends AppCompatActivity {

    private TonalliDb db;
    private EditText etBuscar;
    private TextView tvResumen, tvVacio;
    private final List<Oferta> resultados = new ArrayList<>();
    private OfertaAdapter adapter;
    private final ExecutorService hilo = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_escaner);
        Ui.ajustarBordes(findViewById(R.id.main));

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Escáner");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        db = new TonalliDb(this);
        etBuscar = findViewById(R.id.etBuscar);
        tvResumen = findViewById(R.id.tvResumen);
        tvVacio = findViewById(R.id.tvVacio);
        ListView lista = findViewById(R.id.lvResultados);
        adapter = new OfertaAdapter(this, resultados);
        lista.setAdapter(adapter);
        lista.setEmptyView(tvVacio);
        lista.setOnItemClickListener((p, v, pos, id) -> MainActivity.abrirDetalle(this, resultados.get(pos)));

        findViewById(R.id.btnEscanear).setOnClickListener(v -> Escaneo.leer(this, codigo -> {
            etBuscar.setText(codigo);
            buscar(codigo);
        }));
        findViewById(R.id.btnBuscar).setOnClickListener(v -> buscar(etBuscar.getText().toString()));
        etBuscar.setOnEditorActionListener((v, accion, e) -> {
            if (accion == EditorInfo.IME_ACTION_SEARCH) {
                buscar(etBuscar.getText().toString());
                return true;
            }
            return false;
        });
        tvVacio.setText("Escanea el código de barras de un producto\no escribe su nombre para buscarlo.");
    }

    @Override
    protected void onDestroy() {
        hilo.shutdown();
        super.onDestroy();
    }

    private void buscar(String texto) {
        final String t = texto == null ? "" : texto.trim();
        if (t.length() < 2) {
            tvResumen.setText("Escribe al menos 2 letras o un código.");
            return;
        }
        boolean esCodigo = t.matches("\\d{8,14}");
        if (esCodigo && !Oferta.eanValido(t)) {
            tvResumen.setText("El código " + t + " no es válido (el dígito verificador no coincide).");
            resultados.clear();
            adapter.notifyDataSetChanged();
            return;
        }
        tvResumen.setText("Buscando…");
        hilo.execute(() -> {
            // Si es un código y hay Supabase, primero se traen las ofertas de la comunidad con ese código.
            if (esCodigo && Supabase.configurado()) {
                try {
                    for (Oferta o : Supabase.porCodigo(t)) db.guardarRemota(o);
                } catch (Exception ignored) {
                    // sin internet se busca solo en el teléfono
                }
            }
            List<Oferta> encontradas = db.buscar(t);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                resultados.clear();
                resultados.addAll(encontradas);
                adapter.notifyDataSetChanged();
                if (encontradas.isEmpty()) {
                    tvResumen.setText(esCodigo ? "Código " + t + " · sin ofertas todavía" : "Sin resultados para \"" + t + "\"");
                    tvVacio.setText("Nadie ha publicado una oferta de esto.\nPuedes ser la primera persona en hacerlo.");
                } else {
                    Oferta mejor = encontradas.get(0);
                    tvResumen.setText(encontradas.size() + (encontradas.size() == 1 ? " oferta" : " ofertas")
                            + " · la más barata: " + Ui.dinero(mejor.ahora) + " en " + mejor.tienda);
                }
            });
        });
    }
}
