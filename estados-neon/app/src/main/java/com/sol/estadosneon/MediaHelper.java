package com.sol.estadosneon;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class MediaHelper {
    public static final String PREFS = "estados_neon_prefs";
    public static final String[] DEFAULT_ENTRIES = {
            "default:estado_hbo.jpg", "default:estado_tv.jpg", "default:estado_prime.jpg",
            "default:estado_disney.jpg", "default:estado_paramount.jpg",
            "default:estado_netflix.jpg", "default:estado_video.mp4"
    };

    private MediaHelper() {}

    public static void ensureDefaults(Activity a) {
        File dir = new File(a.getFilesDir(), "status_media");
        if (!dir.exists()) dir.mkdirs();
        for (String entry : DEFAULT_ENTRIES) {
            String name = entry.substring("default:".length());
            File target = new File(dir, name);
            if (target.exists() && target.length() > 0) continue;
            try (InputStream in = a.getAssets().open(name); OutputStream out = new FileOutputStream(target)) {
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) != -1) out.write(b, 0, n);
            } catch (Exception ignored) {}
        }
    }

    public static void initialize(SharedPreferences p) {
        if (!p.contains("media_initialized")) {
            save(p, new ArrayList<>(Arrays.asList(DEFAULT_ENTRIES)));
            p.edit().putBoolean("media_initialized", true)
                    .putInt("schedule_hour", 10).putInt("schedule_minute", 0)
                    .putBoolean("schedule_enabled", false).apply();
        }
    }

    public static List<String> load(SharedPreferences p) {
        ArrayList<String> list = new ArrayList<>();
        String raw = p.getString("media_list", "");
        if (raw == null || raw.trim().isEmpty()) return list;
        for (String s : raw.split("\\n")) if (!s.trim().isEmpty()) list.add(s.trim());
        return list;
    }

    public static void save(SharedPreferences p, List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            if (s == null || s.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(s.trim());
        }
        p.edit().putString("media_list", sb.toString()).apply();
    }

    public static Uri resolve(Activity a, String entry) {
        if (entry.startsWith("default:")) {
            String name = entry.substring("default:".length());
            return MediaProvider.uriForFile(a, new File(new File(a.getFilesDir(), "status_media"), name));
        }
        return Uri.parse(entry);
    }

    public static boolean isVideo(Activity a, String entry) {
        if (entry.toLowerCase(Locale.ROOT).endsWith(".mp4")) return true;
        if (!entry.startsWith("default:")) {
            try {
                String type = a.getContentResolver().getType(Uri.parse(entry));
                return type != null && type.startsWith("video/");
            } catch (Exception ignored) {}
        }
        return false;
    }

    public static void shareAll(Activity a, SharedPreferences p) {
        List<String> entries = load(p);
        if (entries.isEmpty()) {
            Toast.makeText(a, "Primero agregá al menos una foto o video", Toast.LENGTH_LONG).show();
            return;
        }
        ArrayList<Uri> uris = new ArrayList<>();
        for (String entry : entries) try { uris.add(resolve(a, entry)); } catch (Exception ignored) {}
        if (uris.isEmpty()) {
            Toast.makeText(a, "No pude abrir los archivos. Probá restaurar los originales.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent share = new Intent(Intent.ACTION_SEND_MULTIPLE);
        share.setType("*/*");
        share.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ClipData clip = new ClipData("Estados Neon", new String[]{"image/*", "video/*"}, new ClipData.Item(uris.get(0)));
        for (int i = 1; i < uris.size(); i++) clip.addItem(new ClipData.Item(uris.get(i)));
        share.setClipData(clip);

        if (installed(a, "com.whatsapp.w4b")) {
            share.setPackage("com.whatsapp.w4b");
            try { a.startActivity(share); Toast.makeText(a, "Elegí “Mi estado” y confirmá publicar", Toast.LENGTH_LONG).show(); return; }
            catch (Exception ignored) {}
        }
        if (installed(a, "com.whatsapp")) {
            share.setPackage("com.whatsapp");
            try { a.startActivity(share); Toast.makeText(a, "Abrí “Mi estado” y confirmá publicar", Toast.LENGTH_LONG).show(); return; }
            catch (Exception ignored) {}
        }
        share.setPackage(null);
        try { a.startActivity(Intent.createChooser(share, "Publicar estados")); }
        catch (Exception e) { Toast.makeText(a, "No encontré una app compatible", Toast.LENGTH_LONG).show(); }
    }

    private static boolean installed(Activity a, String pkg) {
        try { a.getPackageManager().getPackageInfo(pkg, 0); return true; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }
}
