package app.tonalli.nativo;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Ayudas pequeñas de interfaz compartidas por todas las pantallas. */
public final class Ui {
    private Ui() {
    }

    /** Evita que el contenido quede debajo de la barra de estado, la de navegación o el teclado. */
    public static void ajustarBordes(View raiz) {
        if (raiz == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(raiz, (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            // Con el teclado abierto, se desplaza el campo activo para que quede encima del teclado.
            if (insets.isVisible(WindowInsetsCompat.Type.ime())) {
                v.post(() -> {
                    View foco = v.findFocus();
                    if (foco != null) {
                        android.graphics.Rect r = new android.graphics.Rect(0, 0, foco.getWidth(), foco.getHeight() + 24);
                        foco.requestRectangleOnScreen(r, false);
                    }
                });
            }
            return insets;
        });
    }

    public static String dinero(double valor) {
        return String.format(java.util.Locale.US, "$%.2f", valor);
    }

    public static String distancia(float metros) {
        if (metros < 0) return "";
        if (metros < 1000) return Math.round(metros) + " m";
        return String.format(java.util.Locale.US, "%.1f km", metros / 1000f);
    }
}
