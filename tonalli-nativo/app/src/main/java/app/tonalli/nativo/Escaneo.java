package app.tonalli.nativo;

import android.content.Context;
import android.widget.Toast;

import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import java.util.function.Consumer;

/**
 * Lector de códigos de barras (Google Code Scanner, ML Kit).
 * Google abre su propia pantalla de cámara, así que la app NO necesita el permiso CAMERA para esto.
 * Requiere Google Play Services en el teléfono.
 */
public final class Escaneo {
    private Escaneo() {
    }

    /** Abre el escáner y entrega el número leído. Solo acepta códigos de productos (EAN y UPC). */
    public static void leer(Context c, Consumer<String> alLeer) {
        GmsBarcodeScannerOptions opciones = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                .build();
        GmsBarcodeScanner escaner = GmsBarcodeScanning.getClient(c, opciones);
        escaner.startScan()
                .addOnSuccessListener(codigo -> {
                    String valor = codigo.getRawValue();
                    if (valor != null) alLeer.accept(valor.replaceAll("\\D", ""));
                })
                .addOnFailureListener(e -> Toast.makeText(c,
                        "No se pudo abrir el escáner. Revisa que el teléfono tenga Google Play Services actualizado.",
                        Toast.LENGTH_LONG).show());
        // Si la persona cancela, no se hace nada.
    }
}
