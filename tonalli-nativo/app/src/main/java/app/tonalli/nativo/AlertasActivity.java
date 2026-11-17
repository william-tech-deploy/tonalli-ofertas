package app.tonalli.nativo;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.ArrayList;
import java.util.List;

/** Alertas de precio guardadas en SQLite. Al publicar o sincronizar una oferta que las cumpla, llega una notificación. */
public class AlertasActivity extends AppCompatActivity {

    private TonalliDb db;
    private final List<TonalliDb.Alerta> alertas = new ArrayList<>();
    private ArrayAdapter<TonalliDb.Alerta> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_alertas);
        Ui.ajustarBordes(findViewById(R.id.main));

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        db = new TonalliDb(this);
        EditText etPalabra = findViewById(R.id.etPalabra);
        EditText etMax = findViewById(R.id.etMax);
        ListView lista = findViewById(R.id.lvAlertas);
        TextView tvVacio = findViewById(R.id.tvVacio);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, alertas);
        lista.setAdapter(adapter);
        lista.setEmptyView(tvVacio);

        findViewById(R.id.btnAgregar).setOnClickListener(v -> {
            String palabra = etPalabra.getText().toString().trim();
            double max;
            try {
                max = Double.parseDouble(etMax.getText().toString().trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                max = -1;
            }
            if (palabra.length() < 2 || max <= 0) {
                Toast.makeText(this, "Escribe una palabra (mínimo 2 letras) y un precio mayor que cero", Toast.LENGTH_LONG).show();
                return;
            }
            db.insertarAlerta(palabra, max);
            etPalabra.setText("");
            etMax.setText("");
            cargar();
            Toast.makeText(this, "Alerta guardada", Toast.LENGTH_SHORT).show();
        });

        // Toque largo: confirmar y borrar.
        lista.setOnItemLongClickListener((parent, view, position, id) -> {
            TonalliDb.Alerta a = alertas.get(position);
            new AlertDialog.Builder(this)
                    .setTitle("Eliminar alerta")
                    .setMessage("¿Eliminar la alerta \"" + a.palabra + "\"?")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Eliminar", (d, w) -> {
                        db.eliminarAlerta(a.id);
                        cargar();
                    })
                    .show();
            return true;
        });

        cargar();
    }

    private void cargar() {
        alertas.clear();
        alertas.addAll(db.listarAlertas());
        adapter.notifyDataSetChanged();
    }
}
