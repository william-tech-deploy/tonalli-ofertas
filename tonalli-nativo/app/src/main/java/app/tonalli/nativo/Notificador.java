package app.tonalli.nativo;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/** Notificaciones del sistema (tema T8): avisa cuando una oferta cumple una alerta. */
public final class Notificador {
    private static final String CANAL = "alertas_precio";

    private Notificador() {
    }

    /** Desde Android 8 toda notificación necesita un canal. Crear uno que ya existe no hace nada. */
    public static void crearCanal(Context c) {
        NotificationChannel canal = new NotificationChannel(CANAL, "Alertas de precio", NotificationManager.IMPORTANCE_DEFAULT);
        canal.setDescription("Avisa cuando una oferta cumple una de tus alertas");
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(canal);
    }

    public static boolean tienePermiso(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    /** Muestra el aviso. Al tocarlo abre el detalle de la oferta. Devuelve false si no hay permiso. */
    public static boolean avisar(Context c, Oferta o, TonalliDb.Alerta alerta) {
        if (!tienePermiso(c)) return false;
        crearCanal(c);
        Intent abrir = new Intent(c, DetalleActivity.class);
        abrir.putExtra(DetalleActivity.EXTRA_ID, o.id);
        abrir.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent alTocar = PendingIntent.getActivity(c, (int) o.id, abrir,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String texto = o.titulo + " a " + Ui.dinero(o.ahora) + " en " + o.tienda
                + " (tu límite: " + Ui.dinero(alerta.precioMax) + ")";
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CANAL)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setColor(ContextCompat.getColor(c, R.color.brand_brasa))
                .setContentTitle("Alerta: " + alerta.palabra)
                .setContentText(texto)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(texto))
                .setContentIntent(alTocar)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        try {
            NotificationManagerCompat.from(c).notify((int) o.id, b.build());
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }
}
