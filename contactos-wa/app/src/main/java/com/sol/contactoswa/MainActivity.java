package com.sol.contactoswa;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentProviderOperation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQ_CONTACTS = 10;
    private static final int REQ_ACCOUNT = 20;
    private static final long SESSION_MS = 15L * 60L * 1000L;

    private SharedPreferences prefs;
    private TextView status;
    private TextView accountLabel;
    private TextView detectedLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("wa_contacts", MODE_PRIVATE);
        buildUi();
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs != null) refreshUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(28));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Contactos WA");
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Detecta números visibles en WhatsApp durante una sesión iniciada por vos. No usa Internet y no está afiliada a WhatsApp ni Meta.");
        subtitle.setTextSize(16);
        subtitle.setPadding(0, dp(8), 0, dp(18));
        root.addView(subtitle);

        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(0, dp(6), 0, dp(12));
        root.addView(status);

        Button permissions = button("1. Permitir acceso a Contactos");
        permissions.setOnClickListener(v -> requestContactsPermission());
        root.addView(permissions);

        Button accessibility = button("2. Activar detector en Accesibilidad");
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibility);

        accountLabel = new TextView(this);
        accountLabel.setTextSize(15);
        accountLabel.setPadding(0, dp(12), 0, dp(6));
        root.addView(accountLabel);

        Button chooseAccount = button("3. Elegir cuenta Gmail");
        chooseAccount.setOnClickListener(v -> chooseGoogleAccount());
        root.addView(chooseAccount);

        Button start = button("4. Iniciar captura y abrir WhatsApp");
        start.setOnClickListener(v -> startCapture());
        root.addView(start);

        Button stop = button("Detener captura");
        stop.setOnClickListener(v -> stopCapture());
        root.addView(stop);

        detectedLabel = new TextView(this);
        detectedLabel.setTextSize(16);
        detectedLabel.setPadding(0, dp(18), 0, dp(10));
        root.addView(detectedLabel);

        Button save = button("Guardar detectados en Gmail");
        save.setOnClickListener(v -> saveDetected());
        root.addView(save);

        Button clear = button("Borrar lista detectada");
        clear.setOnClickListener(v -> {
            prefs.edit().remove("numbers").apply();
            refreshUi();
        });
        root.addView(clear);

        TextView note = new TextView(this);
        note.setText("Uso: iniciá una sesión, recorré en WhatsApp los chats o grupos donde aparezcan números no agendados, volvé a esta app, revisá la lista y tocá Guardar. La sesión se apaga sola a los 15 minutos.");
        note.setTextSize(14);
        note.setPadding(0, dp(18), 0, 0);
        root.addView(note);

        setContentView(scroll);
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(5), 0, dp(5));
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void requestContactsPermission() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permisos de Contactos ya concedidos", Toast.LENGTH_SHORT).show();
            return;
        }
        requestPermissions(new String[]{Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS}, REQ_CONTACTS);
    }

    private void chooseGoogleAccount() {
        try {
            Intent intent = AccountManager.newChooseAccountIntent(
                    null,
                    null,
                    new String[]{"com.google"},
                    "Elegí la cuenta Gmail donde querés guardar los contactos",
                    null,
                    null,
                    null
            );
            startActivityForResult(intent, REQ_ACCOUNT);
        } catch (Exception e) {
            Toast.makeText(this, "No pude abrir el selector de cuentas: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ACCOUNT && resultCode == RESULT_OK && data != null) {
            String name = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
            String type = data.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE);
            if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(type)) {
                prefs.edit().putString("account_name", name).putString("account_type", type).apply();
                refreshUi();
            }
        }
    }

    private void startCapture() {
        if (!hasContactPermissions()) {
            requestContactsPermission();
            Toast.makeText(this, "Primero permití acceso a Contactos", Toast.LENGTH_LONG).show();
            return;
        }
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "Activá Contactos WA en Accesibilidad y volvé", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        prefs.edit()
                .putBoolean("capture_active", true)
                .putLong("capture_until", System.currentTimeMillis() + SESSION_MS)
                .apply();
        refreshUi();

        Intent wa = getPackageManager().getLaunchIntentForPackage("com.whatsapp");
        if (wa == null) wa = getPackageManager().getLaunchIntentForPackage("com.whatsapp.w4b");
        if (wa != null) {
            startActivity(wa);
        } else {
            Toast.makeText(this, "No encontré WhatsApp instalado", Toast.LENGTH_LONG).show();
        }
    }

    private void stopCapture() {
        prefs.edit().putBoolean("capture_active", false).remove("capture_until").apply();
        refreshUi();
        Toast.makeText(this, "Captura detenida", Toast.LENGTH_SHORT).show();
    }

    private boolean hasContactPermissions() {
        return checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isAccessibilityEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        String mine = new ComponentName(this, WaAccessibilityService.class).flattenToString();
        for (String s : enabled.split(":")) {
            if (s.equalsIgnoreCase(mine)) return true;
        }
        return false;
    }

    private void refreshUi() {
        boolean active = prefs.getBoolean("capture_active", false) &&
                System.currentTimeMillis() < prefs.getLong("capture_until", 0L);
        if (!active && prefs.getBoolean("capture_active", false)) {
            prefs.edit().putBoolean("capture_active", false).apply();
        }
        status.setText(active ? "Estado: CAPTURA ACTIVA" : "Estado: detenida");

        String account = prefs.getString("account_name", "");
        accountLabel.setText(TextUtils.isEmpty(account)
                ? "Cuenta Gmail: todavía no elegida"
                : "Cuenta Gmail: " + account);

        Set<String> set = new HashSet<>(prefs.getStringSet("numbers", new HashSet<>()));
        StringBuilder sb = new StringBuilder();
        sb.append("Números detectados: ").append(set.size());
        int shown = 0;
        for (String n : set) {
            if (shown >= 30) {
                sb.append("\n… y más");
                break;
            }
            sb.append("\n• ").append(n);
            shown++;
        }
        detectedLabel.setText(sb.toString());
    }

    private void saveDetected() {
        if (!hasContactPermissions()) {
            requestContactsPermission();
            return;
        }
        String accountName = prefs.getString("account_name", "");
        String accountType = prefs.getString("account_type", "");
        if (TextUtils.isEmpty(accountName) || TextUtils.isEmpty(accountType)) {
            Toast.makeText(this, "Elegí primero la cuenta Gmail", Toast.LENGTH_LONG).show();
            chooseGoogleAccount();
            return;
        }

        Set<String> numbers = new HashSet<>(prefs.getStringSet("numbers", new HashSet<>()));
        if (numbers.isEmpty()) {
            Toast.makeText(this, "No hay números detectados para guardar", Toast.LENGTH_SHORT).show();
            return;
        }

        int saved = 0;
        int skipped = 0;
        for (String phone : numbers) {
            if (isAlreadyContact(phone)) {
                skipped++;
                continue;
            }
            if (insertGoogleContact(accountName, accountType, phone)) saved++;
        }
        Toast.makeText(this, "Guardados: " + saved + " · Ya existentes/no guardados: " + skipped, Toast.LENGTH_LONG).show();
        refreshUi();
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

    private boolean insertGoogleContact(String accountName, String accountType, String phone) {
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        int rawIndex = ops.size();
        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, accountName)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, accountType)
                .build());

        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, "WA " + phone)
                .build());

        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .build());
        try {
            getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
