package app.tonalli.nativo;

/** Una oferta tal como se guarda en SQLite. Es el "modelo" de datos de la app. */
public class Oferta {
    public long id;
    public String remota;      // id que viene del servicio web (null si se publicó en el teléfono)
    public String titulo;
    public String tienda;
    public String categoria;
    public double antes;       // precio anterior, en dólares
    public double ahora;       // precio de oferta, en dólares
    public int up;             // votos a favor
    public int down;           // votos en contra
    public int miVoto;         // 1, -1 o 0
    public double lat;
    public double lng;
    public byte[] foto;        // JPEG; solo se carga en el detalle
    public long fecha;
    public float distanciaM = -1f; // se calcula con el GPS; -1 = desconocida

    /** Temperatura = votos a favor menos votos en contra. */
    public int temperatura() {
        return up - down;
    }

    public int descuento() {
        if (antes <= 0) return 0;
        return (int) Math.round((antes - ahora) / antes * 100.0);
    }

    /** Regla de la marca: fría bajo 0, tibia de 0 a 99, caliente desde 100. */
    public String calor() {
        int t = temperatura();
        if (t >= 100) return "Caliente";
        if (t >= 0) return "Tibia";
        return "Fría";
    }

    public boolean tieneUbicacion() {
        return lat != 0 || lng != 0;
    }
}
