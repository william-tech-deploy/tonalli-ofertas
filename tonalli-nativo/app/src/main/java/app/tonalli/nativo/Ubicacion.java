package app.tonalli.nativo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import java.util.function.Consumer;

/** Ubicación del teléfono con el GPS o la red (tema T11). */
public final class Ubicacion {
    private Ubicacion() {
    }

    public static boolean tienePermiso(Context c) {
        return ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Entrega la ubicación actual (o la última conocida). Entrega null si no hay permiso o el GPS está apagado. */
    @SuppressLint("MissingPermission")
    public static void obtener(Context c, Consumer<Location> respuesta) {
        if (!tienePermiso(c)) {
            respuesta.accept(null);
            return;
        }
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            respuesta.accept(null);
            return;
        }
        String proveedor = null;
        boolean fino = ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (fino && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) proveedor = LocationManager.GPS_PROVIDER;
        else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) proveedor = LocationManager.NETWORK_PROVIDER;
        if (proveedor == null) {
            respuesta.accept(null);
            return;
        }
        final Location ultima = ultimaConocida(lm, fino);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lm.getCurrentLocation(proveedor, null, ContextCompat.getMainExecutor(c),
                        actual -> respuesta.accept(actual != null ? actual : ultima));
            } else {
                respuesta.accept(ultima);
            }
        } catch (RuntimeException e) {
            respuesta.accept(ultima);
        }
    }

    @SuppressLint("MissingPermission")
    private static Location ultimaConocida(LocationManager lm, boolean fino) {
        Location mejor = null;
        try {
            if (fino) mejor = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location red = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (mejor == null || (red != null && red.getTime() > mejor.getTime())) mejor = red != null ? red : mejor;
        } catch (RuntimeException ignored) {
        }
        return mejor;
    }
}
