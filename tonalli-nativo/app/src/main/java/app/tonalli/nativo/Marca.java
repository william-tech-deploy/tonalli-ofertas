package app.tonalli.nativo;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.widget.ImageViewCompat;

/** Reglas visuales de la marca Tonalli: color e ícono de cada categoría y de cada temperatura. */
public final class Marca {
    private Marca() {
    }

    public static int iconoCategoria(String categoria) {
        if ("Supermercado".equals(categoria)) return R.drawable.ic_cart;
        if ("Tecnología".equals(categoria)) return R.drawable.ic_phone;
        if ("Ropa".equals(categoria)) return R.drawable.ic_shirt;
        return R.drawable.ic_tag;
    }

    public static int colorCategoria(Context c, String categoria) {
        int id = R.color.tonalli_on_surface_muted;
        if ("Supermercado".equals(categoria)) id = R.color.cat_super;
        else if ("Tecnología".equals(categoria)) id = R.color.cat_tech;
        else if ("Ropa".equals(categoria)) id = R.color.cat_ropa;
        return ContextCompat.getColor(c, id);
    }

    /** Cuadro de la categoría: fondo suave del color de la categoría y su ícono encima. */
    public static void pintarCategoria(Context c, View fondo, ImageView icono, String categoria) {
        int color = colorCategoria(c, categoria);
        ViewCompat.setBackgroundTintList(fondo, ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 40)));
        icono.setImageResource(iconoCategoria(categoria));
        ImageViewCompat.setImageTintList(icono, ColorStateList.valueOf(color));
    }

    /** Temperatura: llama si es caliente, termómetro si es tibia, copo si es fría; con su color. */
    public static void pintarCalor(Context c, Oferta o, ImageView icono, TextView... textos) {
        int t = o.temperatura();
        int color = ContextCompat.getColor(c, t >= 100 ? R.color.tonalli_hot_fg : t >= 0 ? R.color.tonalli_warm_fg : R.color.tonalli_cold_fg);
        icono.setImageResource(t >= 100 ? R.drawable.ic_flame : t >= 0 ? R.drawable.ic_therm : R.drawable.ic_snow);
        ImageViewCompat.setImageTintList(icono, ColorStateList.valueOf(color));
        for (TextView tv : textos) tv.setTextColor(color);
    }
}
