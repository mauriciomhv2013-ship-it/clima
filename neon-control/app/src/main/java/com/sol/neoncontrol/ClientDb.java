package com.sol.neoncontrol;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class ClientDb extends SQLiteOpenHelper {
    public static final String DB_NAME = "neon_control.db";
    public ClientDb(Context c) { super(c, DB_NAME, null, 1); }

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
                "notes TEXT," +
                "status TEXT NOT NULL DEFAULT 'ACTIVE'," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_clients_group ON clients(group_code)");
        db.execSQL("CREATE INDEX idx_clients_expiry ON clients(expiry_date)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    public long insert(String group, String name, String phone, String service, int days, double price, String notes) {
        LocalDate start = LocalDate.now();
        LocalDate expiry = start.plusDays(Math.max(1, days));
        ContentValues v = base(group, name, phone, service, days, price, notes, start, expiry, "ACTIVE");
        long now = System.currentTimeMillis();
        v.put("created_at", now); v.put("updated_at", now);
        return getWritableDatabase().insert("clients", null, v);
    }

    public void update(Client c) {
        ContentValues v = base(c.groupCode, c.name, c.phone, c.service, c.days, c.price, c.notes,
                LocalDate.parse(c.startDate), LocalDate.parse(c.expiryDate), c.status);
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("clients", v, "id=?", new String[]{Long.toString(c.id)});
    }

    public void renew(long id, int days) {
        LocalDate start = LocalDate.now();
        LocalDate expiry = start.plusDays(Math.max(1, days));
        ContentValues v = new ContentValues();
        v.put("start_date", start.toString()); v.put("expiry_date", expiry.toString());
        v.put("days", Math.max(1, days)); v.put("status", "ACTIVE");
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("clients", v, "id=?", new String[]{Long.toString(id)});
    }

    public void setStatus(long id, String status) {
        ContentValues v = new ContentValues(); v.put("status", status); v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("clients", v, "id=?", new String[]{Long.toString(id)});
    }

    public void delete(long id) { getWritableDatabase().delete("clients", "id=?", new String[]{Long.toString(id)}); }

    public Client get(long id) {
        try (Cursor c = getReadableDatabase().query("clients", null, "id=?", new String[]{Long.toString(id)}, null, null, null)) {
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
        if ("URGENT".equals(filter)) where.append(" AND status='ACTIVE' AND expiry_date<=?");
        if ("URGENT".equals(filter)) args.add(LocalDate.now().plusDays(3).toString());
        String order = "CASE WHEN status='ACTIVE' THEN 0 ELSE 1 END, expiry_date ASC, id DESC";
        try (Cursor c = getReadableDatabase().query("clients", null, where.toString(), args.toArray(new String[0]), null, null, order)) {
            while (c.moveToNext()) out.add(from(c));
        }
        return out;
    }

    public Stats stats(String group) {
        Stats s = new Stats(); LocalDate today = LocalDate.now();
        for (Client c : list(group, "", null)) {
            if (!"ACTIVE".equals(c.status)) { s.inactive++; continue; }
            s.active++;
            long d = c.daysLeft();
            if (d < 0) s.expired++;
            else if (d == 0) s.today++;
            else if (d <= 3) s.next3++;
            if (c.price > 0) s.expected += c.price;
        }
        return s;
    }

    public int urgentCount(String group) {
        int n=0; for (Client c:list(group,"",null)) if ("ACTIVE".equals(c.status) && c.daysLeft()<=3) n++; return n;
    }

    private ContentValues base(String group, String name, String phone, String service, int days, double price, String notes,
                               LocalDate start, LocalDate expiry, String status) {
        ContentValues v = new ContentValues();
        v.put("group_code", group); v.put("name", name==null?"":name.trim()); v.put("phone", phone.trim());
        v.put("service", service.trim()); v.put("start_date", start.toString()); v.put("expiry_date", expiry.toString());
        v.put("days", Math.max(1,days)); v.put("price", price); v.put("notes", notes==null?"":notes.trim()); v.put("status", status);
        return v;
    }

    private Client from(Cursor c) {
        Client x = new Client();
        x.id = c.getLong(c.getColumnIndexOrThrow("id")); x.groupCode = c.getString(c.getColumnIndexOrThrow("group_code"));
        x.name = c.getString(c.getColumnIndexOrThrow("name")); x.phone = c.getString(c.getColumnIndexOrThrow("phone"));
        x.service = c.getString(c.getColumnIndexOrThrow("service")); x.startDate = c.getString(c.getColumnIndexOrThrow("start_date"));
        x.expiryDate = c.getString(c.getColumnIndexOrThrow("expiry_date")); x.days = c.getInt(c.getColumnIndexOrThrow("days"));
        x.price = c.getDouble(c.getColumnIndexOrThrow("price")); x.notes = c.getString(c.getColumnIndexOrThrow("notes"));
        x.status = c.getString(c.getColumnIndexOrThrow("status")); x.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"));
        return x;
    }

    public static class Stats { public int active, today, next3, expired, inactive; public double expected; }

    public static class Client {
        public long id, updatedAt; public String groupCode, name, phone, service, startDate, expiryDate, notes, status; public int days; public double price;
        public long daysLeft() { return ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(expiryDate)); }
        public String displayName() { return name==null || name.trim().isEmpty() ? "Cliente" : name.trim(); }
    }
}
