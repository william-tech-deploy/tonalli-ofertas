package app.tonalli.nativo;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Base de datos local SQLite (tema T10-A).
 * Archivo: /data/data/app.tonalli.nativo/databases/tonalli.db
 * Todas las consultas usan parámetros (?) para evitar inyección SQL.
 */
public class TonalliDb extends SQLiteOpenHelper {

    private static final String NOMBRE = "tonalli.db";
    private static final int VERSION = 2; // 2: columnas ean y autor

    public TonalliDb(Context context) {
        super(context.getApplicationContext(), NOMBRE, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE ofertas (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "remota TEXT UNIQUE," +
                "titulo TEXT NOT NULL," +
                "tienda TEXT NOT NULL," +
                "categoria TEXT NOT NULL," +
                "precio_antes REAL NOT NULL," +
                "precio_ahora REAL NOT NULL," +
                "votos_up INTEGER NOT NULL DEFAULT 0," +
                "votos_down INTEGER NOT NULL DEFAULT 0," +
                "mi_voto INTEGER NOT NULL DEFAULT 0," +
                "lat REAL NOT NULL DEFAULT 0," +
                "lng REAL NOT NULL DEFAULT 0," +
                "foto BLOB," +
                "ean TEXT," +
                "autor TEXT," +
                "fecha INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE alertas (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "palabra TEXT NOT NULL," +
                "precio_max REAL NOT NULL)");
        sembrar(db);
    }

    /** Migración: agrega columnas sin borrar los datos que ya tiene el teléfono. */
    @Override
    public void onUpgrade(SQLiteDatabase db, int anterior, int nueva) {
        if (anterior < 2) {
            db.execSQL("ALTER TABLE ofertas ADD COLUMN ean TEXT");
            db.execSQL("ALTER TABLE ofertas ADD COLUMN autor TEXT");
            String[][] codigos = {{"Leche en polvo entera 800 g", "7412345000110"}, {"Arroz blanco 5 lb", "7412345000288"},
                    {"Audífonos Bluetooth", "7412345000356"}, {"Memoria USB 64 GB", "7412345000424"},
                    {"Camisa polo algodón", "7412345000592"}};
            for (String[] c : codigos) {
                db.execSQL("UPDATE ofertas SET ean = ? WHERE titulo = ? AND remota IS NULL", new Object[]{c[1], c[0]});
            }
        }
    }

    /** Ofertas de ejemplo (tiendas ficticias en Santa Ana) para que la app no abra vacía. */
    private void sembrar(SQLiteDatabase db) {
        long ahora = System.currentTimeMillis();
        semilla(db, "Leche en polvo entera 800 g", "Súper Familiar", "Supermercado", 7.20, 5.45, 238, 12, 13.9942, -89.5597, ahora - 3600000L, "7412345000110");
        semilla(db, "Arroz blanco 5 lb", "Autoservicio Don Pepe", "Supermercado", 3.10, 2.45, 64, 9, 13.9895, -89.5561, ahora - 7200000L, "7412345000288");
        semilla(db, "Audífonos Bluetooth", "TecnoPlaza", "Tecnología", 59.90, 39.90, 121, 18, 13.9978, -89.5632, ahora - 10800000L, "7412345000356");
        semilla(db, "Memoria USB 64 GB", "TecnoPlaza", "Tecnología", 12.00, 10.50, 14, 31, 13.9978, -89.5632, ahora - 86400000L, "7412345000424");
        semilla(db, "Camisa polo algodón", "Almacén La Central", "Ropa", 18.00, 11.99, 47, 5, 13.9921, -89.5548, ahora - 172800000L, "7412345000592");
    }

    private void semilla(SQLiteDatabase db, String titulo, String tienda, String categoria,
                         double antes, double ahora, int up, int down, double lat, double lng, long fecha, String ean) {
        ContentValues v = new ContentValues();
        v.put("titulo", titulo);
        v.put("tienda", tienda);
        v.put("categoria", categoria);
        v.put("precio_antes", antes);
        v.put("precio_ahora", ahora);
        v.put("votos_up", up);
        v.put("votos_down", down);
        v.put("lat", lat);
        v.put("lng", lng);
        v.put("fecha", fecha);
        v.put("ean", ean);
        db.insert("ofertas", null, v);
    }

    // ---------------------------------------------------------------- ofertas

    private static final String COLUMNAS_LISTA =
            "id, remota, titulo, tienda, categoria, precio_antes, precio_ahora, votos_up, votos_down, mi_voto, lat, lng, fecha, ean, autor";

    /** Orden de la lista: por temperatura (calientes) o por fecha (nuevas). */
    public static final int ORDEN_CALIENTES = 0;
    public static final int ORDEN_NUEVAS = 1;

    /** Lista las ofertas; categoria null o "Todas" = sin filtro. La foto no se carga aquí (pesa). */
    public List<Oferta> listar(String categoria) {
        return listar(categoria, ORDEN_CALIENTES);
    }

    public List<Oferta> listar(String categoria, int tipoOrden) {
        List<Oferta> lista = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c;
        String orden = tipoOrden == ORDEN_NUEVAS ? " ORDER BY fecha DESC"
                : " ORDER BY (votos_up - votos_down) DESC, fecha DESC";
        if (categoria == null || categoria.equals("Todas")) {
            c = db.rawQuery("SELECT " + COLUMNAS_LISTA + " FROM ofertas" + orden, null);
        } else {
            c = db.rawQuery("SELECT " + COLUMNAS_LISTA + " FROM ofertas WHERE categoria = ?" + orden,
                    new String[]{categoria});
        }
        try {
            while (c.moveToNext()) lista.add(leer(c, false));
        } finally {
            c.close();
        }
        return lista;
    }

    public Oferta obtener(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT " + COLUMNAS_LISTA + ", foto FROM ofertas WHERE id = ?",
                new String[]{String.valueOf(id)});
        try {
            return c.moveToFirst() ? leer(c, true) : null;
        } finally {
            c.close();
        }
    }

    private Oferta leer(Cursor c, boolean conFoto) {
        Oferta o = new Oferta();
        o.id = c.getLong(0);
        o.remota = c.isNull(1) ? null : c.getString(1);
        o.titulo = c.getString(2);
        o.tienda = c.getString(3);
        o.categoria = c.getString(4);
        o.antes = c.getDouble(5);
        o.ahora = c.getDouble(6);
        o.up = c.getInt(7);
        o.down = c.getInt(8);
        o.miVoto = c.getInt(9);
        o.lat = c.getDouble(10);
        o.lng = c.getDouble(11);
        o.fecha = c.getLong(12);
        o.ean = c.isNull(13) ? null : c.getString(13);
        o.autor = c.isNull(14) ? null : c.getString(14);
        if (conFoto && !c.isNull(15)) o.foto = c.getBlob(15);
        return o;
    }

    /** Inserta una oferta publicada en el teléfono. Devuelve el id nuevo (o -1 si falla). */
    public long insertar(Oferta o) {
        ContentValues v = valores(o);
        v.put("fecha", System.currentTimeMillis());
        if (o.foto != null) v.put("foto", o.foto);
        return getWritableDatabase().insert("ofertas", null, v);
    }

    /**
     * Guarda una oferta que vino del servicio web. Si ya existe (misma "remota") solo actualiza
     * precios y votos de la comunidad, sin tocar tu voto. Devuelve el id nuevo, o -1 si ya existía.
     */
    public long guardarRemota(Oferta o) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = valores(o);
        v.put("remota", o.remota);
        v.put("votos_up", o.up);
        v.put("votos_down", o.down);
        v.put("autor", o.autor);
        int cambiadas = db.update("ofertas", v, "remota = ?", new String[]{o.remota});
        if (cambiadas > 0) return -1;
        v.put("fecha", o.fecha > 0 ? o.fecha : System.currentTimeMillis());
        if (o.foto != null) v.put("foto", o.foto);
        return db.insert("ofertas", null, v);
    }

    private ContentValues valores(Oferta o) {
        ContentValues v = new ContentValues();
        v.put("titulo", o.titulo);
        v.put("tienda", o.tienda);
        v.put("categoria", o.categoria);
        v.put("precio_antes", o.antes);
        v.put("precio_ahora", o.ahora);
        v.put("lat", o.lat);
        v.put("lng", o.lng);
        v.put("ean", o.ean);
        return v;
    }

    /** Borra las ofertas de Supabase que ya no existen en el servidor. */
    public void borrarRemotasQueNoEstan(Set<String> vigentes) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT id, remota FROM ofertas WHERE remota LIKE 'sb-%'", null);
        try {
            while (c.moveToNext()) {
                if (!vigentes.contains(c.getString(1))) eliminar(c.getLong(0));
            }
        } finally {
            c.close();
        }
    }

    /** Pone "mi voto" en las ofertas de Supabase según lo que dice el servidor. */
    public void aplicarMisVotos(java.util.Map<String, Integer> votos) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("UPDATE ofertas SET mi_voto = 0 WHERE remota LIKE 'sb-%'");
            for (java.util.Map.Entry<String, Integer> e : votos.entrySet()) {
                db.execSQL("UPDATE ofertas SET mi_voto = ? WHERE remota = ?", new Object[]{e.getValue(), e.getKey()});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Guarda los votos que devolvió el servidor después de votar. */
    public void actualizarVotos(long id, int up, int down, int miVoto) {
        ContentValues v = new ContentValues();
        v.put("votos_up", up);
        v.put("votos_down", down);
        v.put("mi_voto", miVoto);
        getWritableDatabase().update("ofertas", v, "id = ?", new String[]{String.valueOf(id)});
    }

    /** Busca por código de barras exacto o por palabra en el título o la tienda. */
    public List<Oferta> buscar(String texto) {
        List<Oferta> lista = new ArrayList<>();
        String t = texto == null ? "" : texto.trim();
        if (t.isEmpty()) return lista;
        String patron = "%" + t.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        Cursor c = getReadableDatabase().rawQuery("SELECT " + COLUMNAS_LISTA + " FROM ofertas WHERE ean = ? "
                        + "OR titulo LIKE ? ESCAPE '\\' OR tienda LIKE ? ESCAPE '\\' ORDER BY precio_ahora ASC",
                new String[]{t, patron, patron});
        try {
            while (c.moveToNext()) lista.add(leer(c, false));
        } finally {
            c.close();
        }
        return lista;
    }

    /** Registra tu voto (1, -1 o 0 para quitarlo) y ajusta los contadores. */
    public void votar(long id, int nuevo) {
        Oferta o = obtener(id);
        if (o == null) return;
        int up = o.up, down = o.down;
        if (o.miVoto == 1) up--;
        if (o.miVoto == -1) down--;
        if (nuevo == 1) up++;
        if (nuevo == -1) down++;
        ContentValues v = new ContentValues();
        v.put("votos_up", Math.max(0, up));
        v.put("votos_down", Math.max(0, down));
        v.put("mi_voto", nuevo);
        getWritableDatabase().update("ofertas", v, "id = ?", new String[]{String.valueOf(id)});
    }

    public void eliminar(long id) {
        getWritableDatabase().delete("ofertas", "id = ?", new String[]{String.valueOf(id)});
    }

    // ---------------------------------------------------------------- alertas

    public static class Alerta {
        public long id;
        public String palabra;
        public double precioMax;

        @Override
        public String toString() {
            return String.format(Locale.US, "%s  ·  hasta $%.2f", palabra, precioMax);
        }
    }

    public List<Alerta> listarAlertas() {
        List<Alerta> lista = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id, palabra, precio_max FROM alertas ORDER BY id DESC", null);
        try {
            while (c.moveToNext()) {
                Alerta a = new Alerta();
                a.id = c.getLong(0);
                a.palabra = c.getString(1);
                a.precioMax = c.getDouble(2);
                lista.add(a);
            }
        } finally {
            c.close();
        }
        return lista;
    }

    public long insertarAlerta(String palabra, double precioMax) {
        ContentValues v = new ContentValues();
        v.put("palabra", palabra);
        v.put("precio_max", precioMax);
        return getWritableDatabase().insert("alertas", null, v);
    }

    public void eliminarAlerta(long id) {
        getWritableDatabase().delete("alertas", "id = ?", new String[]{String.valueOf(id)});
    }

    /** Devuelve la primera alerta que la oferta cumple (palabra en el título y precio dentro del límite), o null. */
    public Alerta alertaPara(Oferta o) {
        String titulo = o.titulo == null ? "" : o.titulo.toLowerCase(Locale.ROOT);
        for (Alerta a : listarAlertas()) {
            if (titulo.contains(a.palabra.toLowerCase(Locale.ROOT)) && o.ahora <= a.precioMax) return a;
        }
        return null;
    }
}
