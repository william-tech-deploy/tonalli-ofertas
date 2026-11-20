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
    public String ean;         // código de barras (EAN-8, UPC-A o EAN-13), puede ser null
    public String autor;       // id de la cuenta en Supabase que la publicó (null si es local)
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

    /** Nivel de la marca según el karma: Chispa, Brasa, Fogata, Hoguera. */
    public static final int[] NIVELES = {0, 50, 150, 400};
    public static final String[] NOMBRES_NIVEL = {"Chispa", "Brasa", "Fogata", "Hoguera"};

    public static int nivel(int karma) {
        int n = 0;
        for (int i = 0; i < NIVELES.length; i++) if (karma >= NIVELES[i]) n = i;
        return n;
    }

    /** Valida el dígito verificador de un código EAN-8, UPC-A (12) o EAN-13. */
    public static boolean eanValido(String codigo) {
        if (codigo == null || !codigo.matches("\\d{8}|\\d{12,13}")) return false;
        int suma = 0;
        int n = codigo.length();
        for (int i = 0; i < n - 1; i++) {
            int d = codigo.charAt(n - 2 - i) - '0';
            suma += (i % 2 == 0) ? d * 3 : d;
        }
        return (10 - suma % 10) % 10 == codigo.charAt(n - 1) - '0';
    }

    public boolean tieneUbicacion() {
        return lat != 0 || lng != 0;
    }
}
