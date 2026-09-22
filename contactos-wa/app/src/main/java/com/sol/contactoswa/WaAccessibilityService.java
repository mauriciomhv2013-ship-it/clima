package com.sol.contactoswa;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WaAccessibilityService extends AccessibilityService {
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(\\+?\\d[\\d\\s().-]{6,}\\d)(?!\\d)");

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        SharedPreferences prefs = getSharedPreferences("wa_contacts", MODE_PRIVATE);
        boolean active = prefs.getBoolean("capture_active", false);
        long until = prefs.getLong("capture_until", 0L);
        if (!active) return;
        if (System.currentTimeMillis() >= until) {
            prefs.edit().putBoolean("capture_active", false).apply();
            return;
        }

        CharSequence pkgCs = event.getPackageName();
        if (pkgCs == null) return;
        String pkg = pkgCs.toString();
        if (!pkg.equals("com.whatsapp") && !pkg.equals("com.whatsapp.w4b")) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        Set<String> found = new HashSet<>();
        scanNode(root, found);
        root.recycle();

        if (found.isEmpty()) return;
        Set<String> saved = new HashSet<>(prefs.getStringSet("numbers", new HashSet<>()));
        boolean changed = false;
        for (String phone : found) {
            if (!isAlreadyContact(phone) && saved.add(phone)) changed = true;
        }
        if (changed) prefs.edit().putStringSet("numbers", saved).apply();
    }

    private void scanNode(AccessibilityNodeInfo node, Set<String> out) {
        if (node == null) return;
        readText(node.getText(), out);
        readText(node.getContentDescription(), out);
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                scanNode(child, out);
                child.recycle();
            }
        }
    }

    private void readText(CharSequence text, Set<String> out) {
        if (text == null) return;
        Matcher matcher = PHONE_PATTERN.matcher(text.toString());
        while (matcher.find()) {
            String raw = matcher.group(1).trim();
            String normalized = normalize(raw);
            if (normalized != null) out.add(normalized);
        }
    }

    private String normalize(String raw) {
        boolean plus = raw.startsWith("+");
        String digits = raw.replaceAll("\\D", "");
        int len = digits.length();
        if (plus) {
            if (len < 8 || len > 15) return null;
            return "+" + digits;
        }
        if (len < 10 || len > 15) return null;
        return digits;
    }

    private boolean isAlreadyContact(String phone) {
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phone));
        try (Cursor c = getContentResolver().query(uri,
                new String[]{ContactsContract.PhoneLookup._ID}, null, null, null)) {
            return c != null && c.moveToFirst();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onInterrupt() {
        // No se requiere acción.
    }
}
