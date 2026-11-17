package app.tonalli.nativo;

import android.content.Context;
import android.graphics.Paint;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/** Convierte cada Oferta en una fila de la lista (item_oferta.xml). */
public class OfertaAdapter extends ArrayAdapter<Oferta> {

    public OfertaAdapter(@NonNull Context context, @NonNull List<Oferta> ofertas) {
        super(context, R.layout.item_oferta, ofertas);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View fila = convertView != null ? convertView
                : LayoutInflater.from(getContext()).inflate(R.layout.item_oferta, parent, false);
        Oferta o = getItem(position);
        if (o == null) return fila;

        TextView tvTemp = fila.findViewById(R.id.tvTemp);
        TextView tvCalor = fila.findViewById(R.id.tvCalor);
        TextView tvTitulo = fila.findViewById(R.id.tvTitulo);
        TextView tvTienda = fila.findViewById(R.id.tvTienda);
        TextView tvAhora = fila.findViewById(R.id.tvAhora);
        TextView tvAntes = fila.findViewById(R.id.tvAntes);
        TextView tvDescuento = fila.findViewById(R.id.tvDescuento);
        ImageView ivCalor = fila.findViewById(R.id.ivCalor);
        ImageView ivCategoria = fila.findViewById(R.id.ivCategoria);

        Marca.pintarCategoria(getContext(), fila.findViewById(R.id.miniatura), ivCategoria, o.categoria);
        tvTemp.setText(o.temperatura() + "°");
        tvCalor.setText(o.calor());
        Marca.pintarCalor(getContext(), o, ivCalor, tvTemp, tvCalor);

        tvTitulo.setText(o.titulo);
        // Con GPS se muestra la distancia; sin GPS, hace cuánto se publicó.
        String detalle = o.distanciaM >= 0 ? "a " + Ui.distancia(o.distanciaM)
                : DateUtils.getRelativeTimeSpanString(o.fecha, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString();
        tvTienda.setText(o.tienda + " · " + detalle);
        tvAhora.setText(Ui.dinero(o.ahora));
        tvAntes.setText(Ui.dinero(o.antes));
        tvAntes.setPaintFlags(tvAntes.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        tvDescuento.setText("−" + o.descuento() + "%");
        return fila;
    }
}
