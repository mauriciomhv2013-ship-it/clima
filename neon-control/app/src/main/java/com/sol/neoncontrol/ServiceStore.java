package com.sol.neoncontrol;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catálogo editable de servicios. Vive en la misma base de datos de NEON Control,
 * pero se crea de forma compatible con las instalaciones 1.x existentes.
 */
public class ServiceStore {
    private final ClientDb db;

    public static class ServiceItem {
        public long id;
        public String name;
        public double price;
        public double profit;
        public boolean active;
    }

    public ServiceStore(ClientDb db) {
        this.db = db;
        ensureSchema();
    }

    private void ensureSchema() {
        SQLiteDatabase sql = db.getWritableDatabase();
        sql.execSQL("CREATE TABLE IF NOT EXISTS services (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL UNIQUE COLLATE NOCASE," +
                "price REAL NOT NULL DEFAULT 0," +
                "profit REAL NOT NULL DEFAULT 0," +
                "active INTEGER NOT NULL DEFAULT 1," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL)");
        seedDefaults(sql);
    }

    private void seedDefaults(SQLiteDatabase sql) {
        String[] defaults = {
                "Netflix",
                "HBO Max",
                "Prime Video",
                "Paramount+",
                "Crunchyroll",
                "Partido de fútbol"
        };
        long now = System.currentTimeMillis();
        for (String name : defaults) {
            ContentValues v = new ContentValues();
            v.put("name", name);
            v.put("price", 0);
            v.put("profit", 0);
            v.put("active", 1);
            v.put("created_at", now);
            v.put("updated_at", now);
            sql.insertWithOnConflict("services", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    public List<ServiceItem> list(boolean includeInactive) {
        ArrayList<ServiceItem> out = new ArrayList<>();
        String where = includeInactive ? null : "active=1";
        try (Cursor c = db.getReadableDatabase().query(
                "services", null, where, null, null, null,
                "active DESC, name COLLATE NOCASE ASC")) {
            while (c.moveToNext()) out.add(from(c));
        }
        return out;
    }

    public ServiceItem findByName(String name) {
        if (name == null) return null;
        try (Cursor c = db.getReadableDatabase().query(
                "services", null, "name=? COLLATE NOCASE",
                new String[]{name.trim()}, null, null, null, "1")) {
            return c.moveToFirst() ? from(c) : null;
        }
    }

    private ServiceItem from(Cursor c) {
        ServiceItem s = new ServiceItem();
        s.id = c.getLong(c.getColumnIndexOrThrow("id"));
        s.name = c.getString(c.getColumnIndexOrThrow("name"));
        s.price = c.getDouble(c.getColumnIndexOrThrow("price"));
        s.profit = c.getDouble(c.getColumnIndexOrThrow("profit"));
        s.active = c.getInt(c.getColumnIndexOrThrow("active")) != 0;
        return s;
    }

    public long save(ServiceItem s) {
        if (s == null || s.name == null || s.name.trim().isEmpty()) {
            throw new IllegalArgumentException("El servicio necesita un nombre");
        }
        ContentValues v = new ContentValues();
        v.put("name", s.name.trim());
        v.put("price", Math.max(0, s.price));
        v.put("profit", Math.max(0, s.profit));
        v.put("active", s.active ? 1 : 0);
        v.put("updated_at", System.currentTimeMillis());
        SQLiteDatabase sql = db.getWritableDatabase();
        if (s.id > 0) {
            int changed = sql.update("services", v, "id=?", new String[]{Long.toString(s.id)});
            if (changed == 0) throw new IllegalStateException("Servicio no encontrado");
            return s.id;
        }
        v.put("created_at", System.currentTimeMillis());
        return sql.insertOrThrow("services", null, v);
    }

    public void delete(long id) {
        db.getWritableDatabase().delete("services", "id=?", new String[]{Long.toString(id)});
    }

    public JSONArray exportJson() throws Exception {
        JSONArray arr = new JSONArray();
        for (ServiceItem s : list(true)) {
            JSONObject o = new JSONObject();
            o.put("name", s.name);
            o.put("price", s.price);
            o.put("profit", s.profit);
            o.put("active", s.active);
            arr.put(o);
        }
        return arr;
    }

    public void importJson(JSONObject root) throws Exception {
        JSONArray arr = root.optJSONArray("services");
        if (arr == null) return; // copias de seguridad de versiones anteriores
        SQLiteDatabase sql = db.getWritableDatabase();
        sql.beginTransaction();
        try {
            sql.delete("services", null, null);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String name = o.optString("name", "").trim();
                if (name.isEmpty()) continue;
                ContentValues v = new ContentValues();
                v.put("name", name);
                v.put("price", Math.max(0, o.optDouble("price", 0)));
                v.put("profit", Math.max(0, o.optDouble("profit", 0)));
                v.put("active", o.optBoolean("active", true) ? 1 : 0);
                long now = System.currentTimeMillis();
                v.put("created_at", now);
                v.put("updated_at", now);
                sql.insertWithOnConflict("services", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            }
            seedDefaults(sql);
            sql.setTransactionSuccessful();
        } finally {
            sql.endTransaction();
        }
    }

    public Map<String, Double> monthlyProfit(String group) {
        return aggregateProfit(group, "substr(sale_date,1,7)");
    }

    public Map<String, Double> yearlyProfit(String group) {
        return aggregateProfit(group, "substr(sale_date,1,4)");
    }

    private Map<String, Double> aggregateProfit(String group, String expression) {
        LinkedHashMap<String, Double> out = new LinkedHashMap<>();
        String sql = "SELECT " + expression + " AS period, COALESCE(SUM(profit),0) " +
                "FROM sales WHERE group_code=? GROUP BY period ORDER BY period DESC";
        try (Cursor c = db.getReadableDatabase().rawQuery(sql, new String[]{group})) {
            while (c.moveToNext()) out.put(c.getString(0), c.getDouble(1));
        }
        return out;
    }
}
