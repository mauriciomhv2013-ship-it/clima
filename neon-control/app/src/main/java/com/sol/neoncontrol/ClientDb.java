package com.sol.neoncontrol;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ClientDb extends SQLiteOpenHelper {
    public static final String DB_NAME = "neon_control.db";
    private static final int DB_VERSION = 2;

    public ClientDb(Context c) { super(c, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE clients (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "group_code TEXT NOT NULL," +
                "name TEXT," +
                "phone TEXT NOT NULL," +
                "service TEXT NOT NULL," +
                "start_date TEXT NOT NULL," +
                "expiry_date TEXT NOT NULL," +
                "days INTEGER NOT NULL DEFAULT 30," +
                "price REAL NOT NULL DEFAULT 0," +
                "profit REAL NOT NULL DEFAULT 0," +
                "notes TEXT," +
                "status TEXT NOT NULL DEFAULT 'ACTIVE'," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_clients_group ON clients(group_code)");
        db.execSQL("CREATE INDEX idx_clients_expiry ON clients(expiry_date)");
        createSales(db);
    }

    private void createSales(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sales (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "group_code TEXT NOT NULL," +
                "client_id INTEGER NOT NULL DEFAULT 0," +
                "service TEXT NOT NULL," +
                "price REAL NOT NULL DEFAULT 0," +
                "profit REAL NOT NULL DEFAULT 0," +
                "sale_date TEXT NOT NULL," +
                "kind TEXT NOT NULL DEFAULT 'SALE'," +
                "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sales_group_date ON sales(group_code,sale_date)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try { db.execSQL("ALTER TABLE clients ADD COLUMN profit REAL NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
            createSales(db);
        }
    }

    public long insert(String group, String name, String phone, String service, int days, double price, double profit, String notes) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            LocalDate start = LocalDate.now();
            LocalDate expiry = start.plusDays(Math.max(1, days));
            ContentValues v = base(group, name, phone, service, days, price, profit, notes, start, expiry, "ACTIVE");
            long now = System.currentTimeMillis();
            v.put("created_at", now); v.put("updated_at", now);
            long id = db.insertOrThrow("clients", null, v);
            addSale(db, group, id, service, price, profit, start, "NUEVO");
            db.setTransactionSuccessful();
            return id;
        } finally { db.endTransaction(); }
    }

    public void update(Client c) {
        ContentValues v = base(c.groupCode, c.name, c.phone, c.service, c.days, c.price, c.profit, c.notes,
                LocalDate.parse(c.startDate), LocalDate.parse(c.expiryDate), c.status);
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("clients", v, "id=?", new String[]{Long.toString(c.id)});
    }

    public void renew(long id, int days, double price, double profit) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Client c = getFrom(db, id);
            if (c == null) { db.setTransactionSuccessful(); return; }
            LocalDate start = LocalDate.now();
            LocalDate expiry = start.plusDays(Math.max(1, days));
            ContentValues v = new ContentValues();
            v.put("start_date", start.toString()); v.put("expiry_date", expiry.toString());
            v.put("days", Math.max(1, days)); v.put("price", price); v.put("profit", profit);
            v.put("status", "ACTIVE"); v.put("updated_at", System.currentTimeMillis());
            db.update("clients", v, "id=?", new String[]{Long.toString(id)});
            addSale(db, c.groupCode, id, c.service, price, profit, start, "RENOVACION");
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private void addSale(SQLiteDatabase db, String group, long clientId, String service, double price, double profit, LocalDate date, String kind) {
        ContentValues s = new ContentValues();
        s.put("group_code", group); s.put("client_id", clientId); s.put("service", service == null ? "" : service.trim());
        s.put("price", price); s.put("profit", profit); s.put("sale_date", date.toString()); s.put("kind", kind);
        s.put("created_at", System.currentTimeMillis());
        db.insert("sales", null, s);
    }

    public void setStatus(long id, String status) {
        ContentValues v = new ContentValues(); v.put("status", status); v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("clients", v, "id=?", new String[]{Long.toString(id)});
    }

    public void delete(long id) { getWritableDatabase().delete("clients", "id=?", new String[]{Long.toString(id)}); }

    public Client get(long id) { return getFrom(getReadableDatabase(), id); }

    private Client getFrom(SQLiteDatabase db, long id) {
        try (Cursor c = db.query("clients", null, "id=?", new String[]{Long.toString(id)}, null, null, null)) {
            return c.moveToFirst() ? from(c) : null;
        }
    }

    public List<Client> list(String group, String search, String filter) {
        ArrayList<Client> out = new ArrayList<>();
        StringBuilder where = new StringBuilder("group_code=?");
        ArrayList<String> args = new ArrayList<>(); args.add(group);
        if (search != null && !search.trim().isEmpty()) {
            where.append(" AND (phone LIKE ? OR name LIKE ? OR service LIKE ?)");
            String q = "%" + search.trim() + "%"; args.add(q); args.add(q); args.add(q);
        }
        if ("ACTIVE".equals(filter)) where.append(" AND status='ACTIVE'");
        if ("INACTIVE".equals(filter)) where.append(" AND status='INACTIVE'");
        if ("URGENT".equals(filter)) {
            where.append(" AND status='ACTIVE' AND expiry_date<=?");
            args.add(LocalDate.now().plusDays(3).toString());
        }
        String order = "CASE WHEN status='ACTIVE' THEN 0 ELSE 1 END, expiry_date ASC, id DESC";
        try (Cursor c = getReadableDatabase().query("clients", null, where.toString(), args.toArray(new String[0]), null, null, order)) {
            while (c.moveToNext()) out.add(from(c));
        }
        return out;
    }

    public Stats stats(String group) {
        Stats s = new Stats();
        for (Client c : list(group, "", null)) {
            if (!"ACTIVE".equals(c.status)) { s.inactive++; continue; }
            s.active++;
            long d = c.daysLeft();
            if (d < 0) s.expired++;
            else if (d == 0) s.today++;
            else if (d <= 3) s.next3++;
            if (c.price > 0) s.expected += c.price;
        }
        s.profitToday = profitReport(group, LocalDate.now(), LocalDate.now()).profit;
        return s;
    }

    public int urgentCount(String group) {
        int n=0; for (Client c:list(group,"",null)) if ("ACTIVE".equals(c.status) && c.daysLeft()<=3) n++; return n;
    }

    public ProfitReport profitReport(String group, LocalDate from, LocalDate to) {
        ProfitReport r = new ProfitReport();
        String where = "group_code=? AND sale_date>=? AND sale_date<=?";
        String[] args = {group, from.toString(), to.toString()};
        try (Cursor c = getReadableDatabase().query("sales", null, where, args, null, null, "sale_date DESC, id DESC")) {
            while (c.moveToNext()) {
                Sale s = saleFrom(c); r.sales.add(s); r.revenue += s.price; r.profit += s.profit;
                Double old = r.byService.get(s.service); r.byService.put(s.service, (old==null?0:old)+s.profit);
                Double day = r.byDay.get(s.saleDate); r.byDay.put(s.saleDate, (day==null?0:day)+s.profit);
            }
        }
        return r;
    }

    public String exportGroup(String group, String groupName) throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", "NEON_CONTROL_BACKUP"); root.put("version", 2); root.put("group_code", group);
        root.put("group_name", groupName == null ? "" : groupName); root.put("exported_at", System.currentTimeMillis());
        JSONArray clients = new JSONArray();
        for (Client c : list(group, "", null)) {
            JSONObject o = new JSONObject();
            o.put("name", c.name); o.put("phone", c.phone); o.put("service", c.service); o.put("start_date", c.startDate);
            o.put("expiry_date", c.expiryDate); o.put("days", c.days); o.put("price", c.price); o.put("profit", c.profit);
            o.put("notes", c.notes); o.put("status", c.status); o.put("updated_at", c.updatedAt); clients.put(o);
        }
        root.put("clients", clients);
        JSONArray sales = new JSONArray();
        try (Cursor c = getReadableDatabase().query("sales", null, "group_code=?", new String[]{group}, null, null, "sale_date ASC,id ASC")) {
            while (c.moveToNext()) {
                Sale s = saleFrom(c); JSONObject o = new JSONObject();
                o.put("service", s.service); o.put("price", s.price); o.put("profit", s.profit); o.put("sale_date", s.saleDate);
                o.put("kind", s.kind); o.put("created_at", s.createdAt); sales.put(o);
            }
        }
        root.put("sales", sales); return root.toString(2);
    }

    public void importGroup(String targetGroup, String json, boolean replace) throws Exception {
        JSONObject root = new JSONObject(json);
        if (!"NEON_CONTROL_BACKUP".equals(root.optString("format"))) throw new IllegalArgumentException("Formato de copia no reconocido");
        JSONArray clients = root.optJSONArray("clients"), sales = root.optJSONArray("sales");
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            if (replace) { db.delete("clients", "group_code=?", new String[]{targetGroup}); db.delete("sales", "group_code=?", new String[]{targetGroup}); }
            if (clients != null) for (int i=0;i<clients.length();i++) {
                JSONObject o = clients.getJSONObject(i); ContentValues v = new ContentValues(); long now=System.currentTimeMillis();
                v.put("group_code", targetGroup); v.put("name", o.optString("name","")); v.put("phone", o.optString("phone",""));
                v.put("service", o.optString("service","")); v.put("start_date", o.optString("start_date",LocalDate.now().toString()));
                v.put("expiry_date", o.optString("expiry_date",LocalDate.now().plusDays(30).toString())); v.put("days", Math.max(1,o.optInt("days",30)));
                v.put("price", o.optDouble("price",0)); v.put("profit", o.optDouble("profit",0)); v.put("notes", o.optString("notes",""));
                v.put("status", o.optString("status","ACTIVE")); v.put("created_at", now); v.put("updated_at", o.optLong("updated_at",now));
                db.insert("clients", null, v);
            }
            if (sales != null) for (int i=0;i<sales.length();i++) {
                JSONObject o = sales.getJSONObject(i); ContentValues v = new ContentValues();
                v.put("group_code", targetGroup); v.put("client_id",0); v.put("service",o.optString("service",""));
                v.put("price",o.optDouble("price",0)); v.put("profit",o.optDouble("profit",0));
                v.put("sale_date",o.optString("sale_date",LocalDate.now().toString())); v.put("kind",o.optString("kind","SALE"));
                v.put("created_at",o.optLong("created_at",System.currentTimeMillis())); db.insert("sales",null,v);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private ContentValues base(String group, String name, String phone, String service, int days, double price, double profit, String notes,
                               LocalDate start, LocalDate expiry, String status) {
        ContentValues v = new ContentValues();
        v.put("group_code", group); v.put("name", name==null?"":name.trim()); v.put("phone", phone.trim());
        v.put("service", service.trim()); v.put("start_date", start.toString()); v.put("expiry_date", expiry.toString());
        v.put("days", Math.max(1,days)); v.put("price", price); v.put("profit", profit);
        v.put("notes", notes==null?"":notes.trim()); v.put("status", status); return v;
    }

    private Client from(Cursor c) {
        Client x = new Client();
        x.id = c.getLong(c.getColumnIndexOrThrow("id")); x.groupCode = c.getString(c.getColumnIndexOrThrow("group_code"));
        x.name = c.getString(c.getColumnIndexOrThrow("name")); x.phone = c.getString(c.getColumnIndexOrThrow("phone"));
        x.service = c.getString(c.getColumnIndexOrThrow("service")); x.startDate = c.getString(c.getColumnIndexOrThrow("start_date"));
        x.expiryDate = c.getString(c.getColumnIndexOrThrow("expiry_date")); x.days = c.getInt(c.getColumnIndexOrThrow("days"));
        x.price = c.getDouble(c.getColumnIndexOrThrow("price")); x.profit = c.getDouble(c.getColumnIndexOrThrow("profit"));
        x.notes = c.getString(c.getColumnIndexOrThrow("notes")); x.status = c.getString(c.getColumnIndexOrThrow("status"));
        x.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")); return x;
    }

    private Sale saleFrom(Cursor c) {
        Sale s = new Sale(); s.id = c.getLong(c.getColumnIndexOrThrow("id")); s.service = c.getString(c.getColumnIndexOrThrow("service"));
        s.price = c.getDouble(c.getColumnIndexOrThrow("price")); s.profit = c.getDouble(c.getColumnIndexOrThrow("profit"));
        s.saleDate = c.getString(c.getColumnIndexOrThrow("sale_date")); s.kind = c.getString(c.getColumnIndexOrThrow("kind"));
        s.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")); return s;
    }

    public static class Stats { public int active, today, next3, expired, inactive; public double expected, profitToday; }
    public static class Client {
        public long id, updatedAt; public String groupCode, name, phone, service, startDate, expiryDate, notes, status;
        public int days; public double price, profit;
        public long daysLeft() { return ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(expiryDate)); }
        public String displayName() { return name==null || name.trim().isEmpty() ? "Cliente" : name.trim(); }
    }
    public static class Sale { public long id, createdAt; public String service, saleDate, kind; public double price, profit; }
    public static class ProfitReport {
        public double revenue, profit; public final List<Sale> sales = new ArrayList<>();
        public final Map<String,Double> byService = new LinkedHashMap<>(); public final Map<String,Double> byDay = new LinkedHashMap<>();
    }
}
