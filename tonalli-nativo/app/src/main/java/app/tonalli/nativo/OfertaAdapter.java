package app.tonalli.nativo;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

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
        View insignia = fila.findViewById(R.id.insignia);

        tvTemp.setText(o.temperatura() + "°");
        tvCalor.setText(o.calor());
        pintarCalor(getContext(), o, insignia, tvTemp, tvCalor);

        tvTitulo.setText(o.titulo);
        String lugar = o.tienda + " · " + o.categoria;
        if (o.distanciaM >= 0) lugar += " · " + Ui.distancia(o.distanciaM);
        tvTienda.setText(lugar);
        tvAhora.setText(Ui.dinero(o.ahora));
        tvAntes.setText(Ui.dinero(o.antes));
        tvAntes.setPaintFlags(tvAntes.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        tvDescuento.setText("-" + o.descuento() + "%");
        return fila;
    }

    /** Colores de la marca según la temperatura: caliente, tibia o fría. */
    public static void pintarCalor(Context c, Oferta o, View fondo, TextView... textos) {
        int t = o.temperatura();
        int bg = t >= 100 ? R.color.tonalli_hot_bg : t >= 0 ? R.color.tonalli_warm_bg : R.color.tonalli_cold_bg;
        int fg = t >= 100 ? R.color.tonalli_hot_fg : t >= 0 ? R.color.tonalli_warm_fg : R.color.tonalli_cold_fg;
        ViewCompat.setBackgroundTintList(fondo, ColorStateList.valueOf(ContextCompat.getColor(c, bg)));
        for (TextView tv : textos) tv.setTextColor(ContextCompat.getColor(c, fg));
    }
}
