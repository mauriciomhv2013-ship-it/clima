package com.sol.neoncontrol;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(6, 7, 18);
    private static final int CARD = Color.rgb(16, 20, 42);
    private static final int WHITE = Color.rgb(247, 248, 255);
    private static final int MUTED = Color.rgb(167, 178, 205);
    private static final int CYAN = Color.rgb(71, 244, 255);
    private static final int PINK = Color.rgb(255, 59, 245);
    private static final int PURPLE = Color.rgb(138, 92, 255);
    private static final int GREEN = Color.rgb(51, 255, 155);
    private static final int YELLOW = Color.rgb(255, 211, 78);
    private static final int RED = Color.rgb(255, 90, 117);
    private static final int REQ_IMPORT = 7101;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String[] SERVICES = {
            "Netflix", "HBO Max", "Disney+", "Prime Video",
            "Paramount+", "Crunchyroll", "TV en vivo"
    };

    private SharedPreferences prefs;
    private ClientDb db;
    private FrameLayout content;
    private String page = "home";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences("neon_prefs", MODE_PRIVATE);
        db = new ClientDb(this);
        setDefaults();
        requestNotifications();
        if (group().isEmpty()) showOnboarding(); else buildMain();
    }

    @Override protected void onResume() {
        super.onResume();
        ReminderReceiver.schedule(this);
        if (content != null && !group().isEmpty()) showPage(page);
    }

    private void setDefaults() {
        if (!prefs.contains("defaults_set")) {
            prefs.edit()
                    .putBoolean("defaults_set", true)
                    .putBoolean("reminders", true)
                    .putInt("reminder_hour", 10)
                    .putInt("reminder_minute", 0)
                    .putInt("font_mode", 1)
                    .putString("msg_3", "Hola {nombre} 👋 Tu servicio de {servicio} vence en {dias} días ({fecha}). ¿Querés renovarlo por otros 30 días?")
                    .putString("msg_1", "Hola {nombre} 👋 Tu servicio de {servicio} vence mañana ({fecha}). ¿Querés renovarlo por otros 30 días?")
                    .putString("msg_0", "Hola {nombre} 👋 Tu servicio de {servicio} vence hoy. ¿Querés renovarlo por otros 30 días?")
                    .putString("msg_exp", "Hola {nombre} 👋 Tu servicio de {servicio} está vencido. Si querés renovarlo, avisame y lo activamos por otros 30 días.")
                    .apply();
        }
    }

    private void showOnboarding() {
        LinearLayout root = vertical();
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(24), 0, dp(24), dp(30));
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(root);
        setContentView(sv);

        space(root, 45);
        TextView logo = text("NC", 34, BG, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(neon(CYAN, 22, 0));
        root.addView(logo, lp(94, 94));
        TextView title = text("NEON CONTROL", 30, WHITE, true);
        title.setGravity(Gravity.CENTER);
        margin(root, title, 0, 24, 0, 4, -1, -2);
        TextView sub = text("Clientes · Vencimientos · Ganancias · WhatsApp", 17, MUTED, false);
        sub.setGravity(Gravity.CENTER);
        margin(root, sub, 0, 0, 0, 26, -1, -2);

        Button create = bigButton("✨ CREAR MI GRUPO", CYAN);
        create.setOnClickListener(v -> createGroupDialog());
        margin(root, create, 0, 0, 0, 12, -1, 62);
        Button join = outlineButton("ENTRAR CON UN CÓDIGO");
        join.setOnClickListener(v -> joinGroupDialog());
        margin(root, join, 0, 0, 0, 12, -1, 58);

        TextView lost = text("¿Cambiaste o perdiste el teléfono?", 17, WHITE, true);
        lost.setGravity(Gravity.CENTER);
        margin(root, lost, 0, 18, 0, 8, -1, -2);
        Button restore = outlineButton("RESTAURAR COPIA GUARDADA");
        restore.setOnClickListener(v -> pickBackup());
        margin(root, restore, 0, 0, 0, 10, -1, 58);
        Button paste = outlineButton("PEGAR RESPALDO COMO TEXTO");
        paste.setOnClickListener(v -> pasteBackupDialog());
        root.addView(paste, new LinearLayout.LayoutParams(-1, dp(58)));
    }

    private void createGroupDialog() {
        EditText name = field("Nombre del grupo", false);
        name.setText("Mi negocio");
        new AlertDialog.Builder(this)
                .setTitle("Crear grupo")
                .setView(wrap(name))
                .setPositiveButton("CREAR", (d, w) -> {
                    String code = "NEON-" + (100000 + new Random().nextInt(900000));
                    prefs.edit().putString("group_code", code)
                            .putString("group_name", clean(name.getText().toString(), "Mi negocio"))
                            .apply();
                    buildMain();
                    toast("Grupo creado: " + code);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void joinGroupDialog() {
        LinearLayout box = vertical();
        EditText code = field("Código del grupo", false);
        EditText name = field("Nombre para mostrar", false);
        box.addView(code);
        margin(box, name, 0, 8, 0, 0, -1, -2);
        new AlertDialog.Builder(this)
                .setTitle("Entrar con código")
                .setView(wrap(box))
                .setPositiveButton("ENTRAR", (d, w) -> {
                    String c = code.getText().toString().trim().toUpperCase(Locale.ROOT);
                    if (c.length() < 5) { toast("Código inválido"); return; }
                    prefs.edit().putString("group_code", c)
                            .putString("group_name", clean(name.getText().toString(), "Grupo " + c))
                            .apply();
                    buildMain();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void buildMain() {
        LinearLayout root = vertical();
        root.setBackgroundColor(BG);

        LinearLayout top = row();
        top.setPadding(dp(16), dp(10), dp(10), dp(8));
        LinearLayout titles = vertical();
        titles.addView(text("NEON CONTROL", 21, WHITE, true));
        titles.addView(text(prefs.getString("group_name", "Mi grupo"), 14, CYAN, true));
        top.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        Button zoom = smallButton(fontLabel(), PINK);
        zoom.setOnClickListener(v -> cycleFont());
        top.addView(zoom, lp(62, 48));
        root.addView(top);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout nav = row();
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(2), dp(4), dp(2), dp(6));
        nav.setBackgroundColor(Color.rgb(9, 11, 25));
        addNav(nav, "⌂\nInicio", "home");
        addNav(nav, "☷\nClientes", "clients");
        addNav(nav, "$\nCobrar", "collect");
        addNav(nav, "✦\nGanancias", "profits");
        addNav(nav, "⚙\nAjustes", "settings");
        root.addView(nav, new LinearLayout.LayoutParams(-1, dp(74)));
        setContentView(root);

        if (getIntent().getBooleanExtra("open_collect", false)) {
            page = "collect";
            getIntent().removeExtra("open_collect");
        }
        showPage(page);
        ReminderReceiver.schedule(this);
    }

    private void addNav(LinearLayout nav, String label, String target) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextColor(target.equals(page) ? CYAN : MUTED);
        b.setTextSize(ts(11.5f));
        b.setGravity(Gravity.CENTER);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setOnClickListener(v -> { page = target; buildMain(); });
        nav.addView(b, new LinearLayout.LayoutParams(0, -1, 1f));
    }

    private void showPage(String p) {
        if (content == null) return;
        content.removeAllViews();
        View v;
        if ("clients".equals(p)) v = clientsPage();
        else if ("collect".equals(p)) v = collectPage();
        else if ("profits".equals(p)) v = profitsPage();
        else if ("settings".equals(p)) v = settingsPage();
        else v = homePage();
        content.addView(v, new FrameLayout.LayoutParams(-1, -1));
    }

    private View homePage() {
        ScrollView sv = new ScrollView(this);
        LinearLayout box = vertical();
        box.setPadding(dp(14), dp(8), dp(14), dp(28));
        sv.addView(box);
        ClientDb.Stats s = db.stats(group());

        box.addView(text("Tu trabajo, de un vistazo", 24, WHITE, true));
        margin(box, text("Hoy · " + LocalDate.now().format(DATE), 16, MUTED, false), 0, 1, 0, 12, -1, -2);
        LinearLayout r1 = row();
        r1.addView(statCard("ACTIVOS", Integer.toString(s.active), CYAN), weight());
        r1.addView(statCard("VENCEN HOY", Integer.toString(s.today), YELLOW), weight());
        box.addView(r1);
        LinearLayout r2 = row();
        r2.addView(statCard("PRÓX. 3 DÍAS", Integer.toString(s.next3), PURPLE), weight());
        r2.addView(statCard("VENCIDOS", Integer.toString(s.expired), RED), weight());
        box.addView(r2);

        LinearLayout gain = card();
        gain.addView(text("GANANCIA DE HOY", 14, GREEN, true));
        gain.addView(text(money(s.profitToday), 31, GREEN, true));
        gain.setOnClickListener(v -> { page = "profits"; buildMain(); });
        margin(box, gain, 4, 10, 4, 10, -1, -2);

        Button collect = bigButton("⚡ COBRAR HOY", PINK);
        collect.setOnClickListener(v -> { page = "collect"; buildMain(); });
        margin(box, collect, 0, 0, 0, 10, -1, 64);
        Button add = bigButton("＋ AGREGAR CLIENTE", CYAN);
        add.setOnClickListener(v -> clientDialog(null));
        margin(box, add, 0, 0, 0, 18, -1, 64);

        box.addView(text("PRÓXIMOS VENCIMIENTOS", 16, CYAN, true));
        List<ClientDb.Client> urgent = db.list(group(), "", "URGENT");
        if (urgent.isEmpty()) {
            margin(box, empty("Todo tranquilo. No hay vencimientos en los próximos 3 días."), 0, 10, 0, 0, -1, -2);
        } else {
            for (int i = 0; i < Math.min(5, urgent.size()); i++)
                margin(box, clientCard(urgent.get(i), false), 0, 10, 0, 0, -1, -2);
        }
        return sv;
    }

    private View clientsPage() {
        LinearLayout outer = vertical();
        outer.setPadding(dp(12), dp(6), dp(12), dp(10));
        LinearLayout head = row();
        head.addView(text("Clientes", 25, WHITE, true), new LinearLayout.LayoutParams(0, -2, 1f));
        Button add = smallButton("＋ NUEVO", CYAN);
        add.setOnClickListener(v -> clientDialog(null));
        head.addView(add, lp(112, 52));
        outer.addView(head);

        EditText search = field("Buscar teléfono, nombre o servicio…", false);
        search.setTextSize(ts(18));
        margin(outer, search, 0, 10, 0, 8, -1, 58);

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        LinearLayout filters = row();
        String[] fs = {"TODOS", "ACTIVOS", "VENCIDOS/PRÓX.", "BAJAS"};
        String[] fv = {"ALL", "ACTIVE", "URGENT", "INACTIVE"};
        FrameLayout host = new FrameLayout(this);
        for (int i = 0; i < fs.length; i++) {
            Button f = smallButton(fs[i], i == 0 ? PURPLE : Color.rgb(45, 50, 80));
            final String val = fv[i];
            f.setOnClickListener(v -> renderClientList(host, search.getText().toString(), val));
            filters.addView(f, new LinearLayout.LayoutParams(-2, dp(46)));
        }
        hsv.addView(filters);
        outer.addView(hsv, new LinearLayout.LayoutParams(-1, dp(54)));
        outer.addView(host, new LinearLayout.LayoutParams(-1, 0, 1f));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) { renderClientList(host, s.toString(), "ALL"); }
            public void afterTextChanged(Editable e) {}
        });
        renderClientList(host, "", "ALL");
        return outer;
    }

    private void renderClientList(FrameLayout host, String search, String filter) {
        host.removeAllViews();
        ScrollView sv = new ScrollView(this);
        LinearLayout list = vertical();
        list.setPadding(0, dp(4), 0, dp(30));
        sv.addView(list);
        List<ClientDb.Client> data = db.list(group(), search, "ALL".equals(filter) ? null : filter);
        if (data.isEmpty()) list.addView(empty("No encontré clientes."));
        else {
            for (ClientDb.Client c : data) {
                View v = clientCard(c, true);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
                p.setMargins(0, 0, 0, dp(10));
                list.addView(v, p);
            }
        }
        host.addView(sv, new FrameLayout.LayoutParams(-1, -1));
    }

    private View collectPage() {
        ScrollView sv = new ScrollView(this);
        LinearLayout box = vertical();
        box.setPadding(dp(14), dp(8), dp(14), dp(30));
        sv.addView(box);
        box.addView(text("Cobrar hoy", 27, WHITE, true));
        margin(box, text("Mandá el mensaje por WhatsApp y, cuando paguen, tocá RENOVÓ.", 17, MUTED, false), 0, 2, 0, 12, -1, -2);
        List<ClientDb.Client> data = db.list(group(), "", "URGENT");
        margin(box, text(data.size() + " PARA REVISAR", 16, PINK, true), 0, 0, 0, 8, -1, -2);
        if (data.isEmpty()) box.addView(empty("✓ No tenés cobros pendientes."));
        else for (ClientDb.Client c : data) margin(box, clientCard(c, true), 0, 0, 0, 10, -1, -2);
        return sv;
    }

    private View profitsPage() {
        LinearLayout outer = vertical();
        outer.setPadding(dp(14), dp(8), dp(14), dp(18));
        outer.addView(text("Ganancias", 27, WHITE, true));
        outer.addView(text("Cuánto ganaste y en qué plataforma.", 16, MUTED, false));
        LinearLayout tabs = row();
        FrameLayout host = new FrameLayout(this);
        String[] names = {"HOY", "7 DÍAS", "MES"};
        for (int i = 0; i < 3; i++) {
            final int mode = i;
            Button b = smallButton(names[i], i == 0 ? GREEN : Color.rgb(45, 50, 80));
            b.setOnClickListener(v -> renderProfit(host, mode));
            tabs.addView(b, weight());
        }
        margin(outer, tabs, 0, 12, 0, 8, -1, 54);
        outer.addView(host, new LinearLayout.LayoutParams(-1, 0, 1f));
        renderProfit(host, 0);
        return outer;
    }

    private void renderProfit(FrameLayout host, int mode) {
        host.removeAllViews();
        LocalDate today = LocalDate.now();
        LocalDate from = mode == 0 ? today : mode == 1 ? today.minusDays(6) : today.withDayOfMonth(1);
        ClientDb.ProfitReport r = db.profitReport(group(), from, today);
        ScrollView sv = new ScrollView(this);
        LinearLayout box = vertical();
        box.setPadding(0, dp(4), 0, dp(28));
        sv.addView(box);

        LinearLayout total = card();
        total.addView(text(mode == 0 ? "GANANCIA DE HOY" : mode == 1 ? "GANANCIA ÚLTIMOS 7 DÍAS" : "GANANCIA DEL MES", 14, GREEN, true));
        total.addView(text(money(r.profit), 35, GREEN, true));
        total.addView(text("Ventas cobradas: " + money(r.revenue), 16, MUTED, false));
        margin(box, total, 0, 0, 0, 12, -1, -2);

        box.addView(text("POR PLATAFORMA", 15, CYAN, true));
        if (r.byService.isEmpty()) {
            margin(box, empty("Todavía no hay ventas registradas en este período."), 0, 8, 0, 12, -1, -2);
        } else {
            for (Map.Entry<String, Double> e : r.byService.entrySet()) {
                LinearLayout line = card();
                LinearLayout rr = row();
                rr.addView(text(e.getKey(), 18, WHITE, true), new LinearLayout.LayoutParams(0, -2, 1f));
                rr.addView(text(money(e.getValue()), 20, GREEN, true));
                line.addView(rr);
                margin(box, line, 0, 7, 0, 0, -1, -2);
            }
        }
        if (mode != 0 && !r.byDay.isEmpty()) {
            margin(box, text("DÍA POR DÍA", 15, PINK, true), 0, 18, 0, 2, -1, -2);
            for (Map.Entry<String, Double> e : r.byDay.entrySet()) {
                LocalDate d = LocalDate.parse(e.getKey());
                LinearLayout rr = row();
                rr.setPadding(dp(10), dp(10), dp(10), dp(10));
                rr.addView(text(d.format(DATE), 17, WHITE, true), new LinearLayout.LayoutParams(0, -2, 1f));
                rr.addView(text(money(e.getValue()), 18, GREEN, true));
                box.addView(rr);
            }
        }
        host.addView(sv, new FrameLayout.LayoutParams(-1, -1));
    }

    private View settingsPage() {
        ScrollView sv = new ScrollView(this);
        LinearLayout box = vertical();
        box.setPadding(dp(14), dp(8), dp(14), dp(30));
        sv.addView(box);
        box.addView(text("Ajustes", 27, WHITE, true));

        LinearLayout g = card();
        g.addView(text("GRUPO DE TRABAJO", 14, CYAN, true));
        g.addView(text(prefs.getString("group_name", "Mi grupo"), 22, WHITE, true));
        g.addView(text(group(), 20, PINK, true));
        Button ch = outlineButton("CAMBIAR NOMBRE / CÓDIGO");
        ch.setOnClickListener(v -> changeGroupDialog());
        margin(g, ch, 0, 10, 0, 0, -1, 54);
        box.addView(g);

        LinearLayout presets = card();
        presets.addView(text("⚡ Precios automáticos", 19, WHITE, true));
        presets.addView(text("Configurá una sola vez el precio y la ganancia de cada plataforma. Después se completan solos.", 15, MUTED, false));
        Button editPresets = bigButton("EDITAR PRECIOS Y GANANCIAS", GREEN);
        editPresets.setOnClickListener(v -> servicePresetsDialog());
        margin(presets, editPresets, 0, 12, 0, 0, -1, 58);
        margin(box, presets, 0, 12, 0, 0, -1, -2);

        LinearLayout rem = card();
        LinearLayout rr = row();
        LinearLayout rt = vertical();
        rt.addView(text("Aviso diario", 19, WHITE, true));
        rt.addView(text("Te avisa si hay clientes por vencer", 15, MUTED, false));
        rr.addView(rt, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(prefs.getBoolean("reminders", true));
        rr.addView(sw);
        rem.addView(rr);
        Button time = outlineButton("HORARIO · " + String.format(Locale.getDefault(), "%02d:%02d", prefs.getInt("reminder_hour", 10), prefs.getInt("reminder_minute", 0)));
        time.setOnClickListener(v -> pickTime());
        margin(rem, time, 0, 12, 0, 0, -1, 54);
        sw.setOnCheckedChangeListener((b, c) -> { prefs.edit().putBoolean("reminders", c).apply(); ReminderReceiver.schedule(this); });
        margin(box, rem, 0, 12, 0, 0, -1, -2);

        LinearLayout font = card();
        font.addView(text("Tamaño de letra", 19, WHITE, true));
        font.addView(text("Normal, grande o extra grande.", 15, MUTED, false));
        LinearLayout fr = row();
        String[] n = {"Normal", "Grande", "Extra"};
        for (int i = 0; i < 3; i++) {
            final int x = i;
            Button b = smallButton(n[i], prefs.getInt("font_mode", 1) == i ? CYAN : Color.rgb(45, 50, 80));
            b.setOnClickListener(v -> { prefs.edit().putInt("font_mode", x).apply(); buildMain(); });
            fr.addView(b, weight());
        }
        margin(font, fr, 0, 12, 0, 0, -1, 54);
        margin(box, font, 0, 12, 0, 0, -1, -2);

        LinearLayout backup = card();
        backup.addView(text("☁ Respaldo de datos", 19, WHITE, true));
        backup.addView(text("Guarda clientes, fechas, precios, ganancias, historial y tus precios automáticos.", 15, MUTED, false));
        Button save = bigButton("GUARDAR COPIA / GMAIL", CYAN);
        save.setOnClickListener(v -> shareBackup());
        margin(backup, save, 0, 12, 0, 8, -1, 58);
        Button copy = outlineButton("COPIAR RESPALDO COMO TEXTO");
        copy.setOnClickListener(v -> copyBackupText());
        backup.addView(copy, new LinearLayout.LayoutParams(-1, dp(54)));
        Button restore = outlineButton("RESTAURAR DESDE ARCHIVO");
        restore.setOnClickListener(v -> pickBackup());
        margin(backup, restore, 0, 8, 0, 0, -1, 54);
        Button paste = outlineButton("PEGAR RESPALDO COMO TEXTO");
        paste.setOnClickListener(v -> pasteBackupDialog());
        margin(backup, paste, 0, 8, 0, 0, -1, 54);
        margin(box, backup, 0, 12, 0, 0, -1, -2);

        LinearLayout msg = card();
        msg.addView(text("Mensajes de WhatsApp", 19, WHITE, true));
        msg.addView(text("Podés cambiar los textos automáticos.", 15, MUTED, false));
        Button edit = outlineButton("EDITAR MENSAJES");
        edit.setOnClickListener(v -> messagesDialog());
        margin(msg, edit, 0, 12, 0, 0, -1, 54);
        margin(box, msg, 0, 12, 0, 0, -1, -2);

        LinearLayout cloud = card();
        cloud.addView(text("Sincronización entre teléfonos", 19, WHITE, true));
        cloud.addView(text("La separación por código ya está lista. La sincronización en tiempo real se activará al conectar la base en la nube.", 15, MUTED, false));
        cloud.addView(text("● PENDIENTE DE CONECTAR NUBE", 14, YELLOW, true));
        box.addView(cloud);
        return sv;
    }

    private View clientCard(ClientDb.Client c, boolean full) {
        LinearLayout card = card();
        LinearLayout top = row();
        top.addView(text(c.service, 16, CYAN, true), new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(text(dayText(c), 15, dayColor(c), true));
        card.addView(top);
        if (c.name != null && !c.name.trim().isEmpty()) card.addView(text(c.name, 18, WHITE, true));
        TextView ph = text(c.phone, 21, WHITE, true);
        ph.setLetterSpacing(.03f);
        card.addView(ph);
        card.addView(text("Vence: " + LocalDate.parse(c.expiryDate).format(DATE) + " · " + c.days + " días", 16, MUTED, false));
        if (c.price > 0 || c.profit > 0) card.addView(text("Precio " + money(c.price) + "   ·   Ganancia " + money(c.profit), 16, GREEN, true));
        if ("INACTIVE".equals(c.status)) card.addView(text("BAJA / NO RENOVÓ", 14, MUTED, true));
        if (full) {
            LinearLayout a = row();
            Button wa = smallButton("WHATSAPP", GREEN);
            wa.setOnClickListener(v -> openWhatsApp(c));
            a.addView(wa, weight());
            Button re = smallButton("RENOVÓ", CYAN);
            re.setOnClickListener(v -> renewDialog(c));
            a.addView(re, weight());
            Button ed = smallButton("EDITAR", PURPLE);
            ed.setOnClickListener(v -> clientDialog(c));
            a.addView(ed, weight());
            margin(card, a, 0, 12, 0, 0, -1, 54);
        } else card.setOnClickListener(v -> clientDialog(c));
        return card;
    }

    private void clientDialog(ClientDb.Client existing) {
        boolean edit = existing != null;
        LinearLayout box = vertical();
        EditText phone = field("Teléfono", false);
        EditText name = field("Nombre (opcional)", false);
        EditText service = field("Plataforma / servicio", false);
        EditText days = field("Días", true);
        EditText price = field("Precio cobrado", true);
        EditText profit = field("Ganancia", true);
        EditText notes = field("Notas (opcional)", false);
        phone.setTextSize(ts(20));
        days.setText("30");

        if (edit) {
            phone.setText(existing.phone);
            name.setText(existing.name);
            service.setText(existing.service);
            days.setText(Integer.toString(existing.days));
            if (existing.price > 0) price.setText(num(existing.price));
            if (existing.profit > 0) profit.setText(num(existing.profit));
            notes.setText(existing.notes);
        }

        box.addView(phone);
        margin(box, name, 0, 8, 0, 0, -1, -2);
        margin(box, service, 0, 8, 0, 0, -1, -2);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout chips = row();
        for (String s : SERVICES) {
            Button b = smallButton(s, Color.rgb(42, 46, 75));
            b.setOnClickListener(v -> applyPresetToFields(s, service, price, profit));
            chips.addView(b, new LinearLayout.LayoutParams(-2, dp(45)));
        }
        hs.addView(chips);
        margin(box, hs, 0, 6, 0, 0, -1, 52);
        margin(box, days, 0, 8, 0, 0, -1, -2);
        margin(box, price, 0, 8, 0, 0, -1, -2);
        margin(box, profit, 0, 8, 0, 0, -1, -2);
        margin(box, notes, 0, 8, 0, 0, -1, -2);
        if (!edit) margin(box, text("Elegí una plataforma y el precio + ganancia se completan solos si ya los configuraste. Por defecto son 30 días.", 14, CYAN, false), 0, 8, 0, 0, -1, -2);

        ScrollView sc = new ScrollView(this);
        sc.addView(box);
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(edit ? "Editar cliente" : "Nuevo cliente")
                .setView(wrap(sc))
                .setPositiveButton(edit ? "GUARDAR" : "AGREGAR", null)
                .setNegativeButton("Cancelar", null)
                .create();
        if (edit) d.setButton(AlertDialog.BUTTON_NEUTRAL, "MÁS", (x, w) -> {});
        d.setOnShowListener(x -> {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String ph = phone.getText().toString().trim();
                String sv = service.getText().toString().trim();
                if (ph.isEmpty() || sv.isEmpty()) { toast("Completá teléfono y plataforma"); return; }
                int dd = parseInt(days.getText().toString(), 30);
                double pp = parseDouble(price.getText().toString(), 0);
                double gg = parseDouble(profit.getText().toString(), 0);
                learnPresetIfEmpty(sv, pp, gg);
                if (edit) {
                    existing.phone = ph;
                    existing.name = name.getText().toString();
                    existing.service = sv;
                    existing.days = dd;
                    existing.price = pp;
                    existing.profit = gg;
                    existing.notes = notes.getText().toString();
                    existing.expiryDate = LocalDate.parse(existing.startDate).plusDays(dd).toString();
                    db.update(existing);
                } else {
                    db.insert(group(), name.getText().toString(), ph, sv, dd, pp, gg, notes.getText().toString());
                }
                d.dismiss();
                showPage(page);
            });
            if (edit) {
                d.getButton(AlertDialog.BUTTON_NEUTRAL).setText("BAJA / BORRAR");
                d.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(RED);
                d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> clientMore(existing, d));
            }
        });
        d.show();
    }

    private void renewDialog(ClientDb.Client c) {
        LinearLayout box = vertical();
        EditText days = field("Días de renovación", true);
        EditText price = field("Precio cobrado", true);
        EditText profit = field("Ganancia", true);
        days.setText("30");
        double presetPrice = presetValue(c.service, "price");
        double presetProfit = presetValue(c.service, "profit");
        double p0 = presetPrice > 0 ? presetPrice : c.price;
        double g0 = presetProfit > 0 ? presetProfit : c.profit;
        if (p0 > 0) price.setText(num(p0));
        if (g0 > 0) profit.setText(num(g0));
        box.addView(text("Precio y ganancia vienen automáticamente de la plataforma. Podés cambiarlos sólo para esta renovación.", 16, MUTED, false));
        margin(box, days, 0, 10, 0, 0, -1, -2);
        margin(box, price, 0, 8, 0, 0, -1, -2);
        margin(box, profit, 0, 8, 0, 0, -1, -2);
        new AlertDialog.Builder(this)
                .setTitle("Renovar · " + c.displayName())
                .setView(wrap(box))
                .setPositiveButton("RENOVAR", (d, w) -> {
                    int n = parseInt(days.getText().toString(), 30);
                    double p = parseDouble(price.getText().toString(), 0);
                    double g = parseDouble(profit.getText().toString(), 0);
                    db.renew(c.id, n, p, g);
                    toast("Renovado por " + n + " días");
                    showPage(page);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void servicePresetsDialog() {
        LinearLayout box = vertical();
        box.addView(text("Poné estos valores una sola vez. Después, al tocar la plataforma, se completan automáticamente.", 15, MUTED, false));
        final EditText[] priceFields = new EditText[SERVICES.length];
        final EditText[] profitFields = new EditText[SERVICES.length];
        for (int i = 0; i < SERVICES.length; i++) {
            String s = SERVICES[i];
            TextView title = text(s, 18, CYAN, true);
            margin(box, title, 0, i == 0 ? 14 : 18, 0, 5, -1, -2);
            LinearLayout row = row();
            EditText p = field("Precio", true);
            EditText g = field("Ganancia", true);
            double pv = presetValue(s, "price");
            double gv = presetValue(s, "profit");
            if (pv > 0) p.setText(num(pv));
            if (gv > 0) g.setText(num(gv));
            priceFields[i] = p;
            profitFields[i] = g;
            row.addView(p, weight());
            row.addView(g, weight());
            box.addView(row);
        }
        ScrollView sc = new ScrollView(this);
        sc.addView(box);
        new AlertDialog.Builder(this)
                .setTitle("Precios y ganancias automáticos")
                .setView(wrap(sc))
                .setPositiveButton("GUARDAR", (d, w) -> {
                    SharedPreferences.Editor e = prefs.edit();
                    for (int i = 0; i < SERVICES.length; i++) {
                        e.putString(presetKey(SERVICES[i], "price"), priceFields[i].getText().toString().trim());
                        e.putString(presetKey(SERVICES[i], "profit"), profitFields[i].getText().toString().trim());
                    }
                    e.apply();
                    toast("Precios automáticos guardados");
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void applyPresetToFields(String serviceName, EditText service, EditText price, EditText profit) {
        service.setText(serviceName);
        double p = presetValue(serviceName, "price");
        double g = presetValue(serviceName, "profit");
        price.setText(p > 0 ? num(p) : "");
        profit.setText(g > 0 ? num(g) : "");
    }

    private void learnPresetIfEmpty(String service, double price, double profit) {
        String canonical = canonicalService(service);
        if (canonical == null) return;
        SharedPreferences.Editor e = prefs.edit();
        boolean changed = false;
        String kp = presetKey(canonical, "price");
        String kg = presetKey(canonical, "profit");
        if (!prefs.contains(kp) && price > 0) { e.putString(kp, num(price)); changed = true; }
        if (!prefs.contains(kg) && profit > 0) { e.putString(kg, num(profit)); changed = true; }
        if (changed) e.apply();
    }

    private String canonicalService(String s) {
        if (s == null) return null;
        for (String known : SERVICES) if (known.equalsIgnoreCase(s.trim())) return known;
        return null;
    }

    private String presetKey(String service, String field) {
        String k = service.toLowerCase(Locale.ROOT)
                .replace("+", "plus")
                .replace(" ", "_")
                .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u");
        return "preset_" + k + "_" + field;
    }

    private double presetValue(String service, String field) {
        String canonical = canonicalService(service);
        if (canonical == null) return 0;
        return parseDouble(prefs.getString(presetKey(canonical, field), ""), 0);
    }

    private void clientMore(ClientDb.Client c, AlertDialog parent) {
        String st = "ACTIVE".equals(c.status) ? "MARCAR NO RENOVÓ" : "REACTIVAR";
        new AlertDialog.Builder(this).setTitle(c.displayName())
                .setItems(new String[]{st, "Eliminar definitivamente"}, (d, w) -> {
                    if (w == 0) {
                        db.setStatus(c.id, "ACTIVE".equals(c.status) ? "INACTIVE" : "ACTIVE");
                        parent.dismiss();
                        showPage(page);
                    } else {
                        new AlertDialog.Builder(this).setTitle("Eliminar cliente").setMessage("¿Seguro?")
                                .setPositiveButton("ELIMINAR", (x, y) -> { db.delete(c.id); parent.dismiss(); showPage(page); })
                                .setNegativeButton("Cancelar", null).show();
                    }
                }).show();
    }

    private void openWhatsApp(ClientDb.Client c) {
        long left = c.daysLeft();
        String key = left < 0 ? "msg_exp" : left == 0 ? "msg_0" : left == 1 ? "msg_1" : "msg_3";
        String msg = prefs.getString(key, "")
                .replace("{nombre}", c.displayName())
                .replace("{servicio}", c.service)
                .replace("{dias}", Long.toString(Math.max(0, left)))
                .replace("{fecha}", LocalDate.parse(c.expiryDate).format(DATE));
        String phone = c.phone.replaceAll("[^0-9]", "");
        if (phone.length() < 8) { toast("Revisá el número"); return; }
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + phone + "?text=" + Uri.encode(msg)));
        i.setPackage("com.whatsapp.w4b");
        try { startActivity(i); }
        catch (Exception e) {
            try { i.setPackage("com.whatsapp"); startActivity(i); }
            catch (Exception x) { i.setPackage(null); startActivity(i); }
        }
    }

    private String backupJson() throws Exception {
        JSONObject root = new JSONObject(db.exportGroup(group(), prefs.getString("group_name", "Mi grupo")));
        JSONObject settings = new JSONObject();
        settings.put("font_mode", prefs.getInt("font_mode", 1));
        settings.put("reminders", prefs.getBoolean("reminders", true));
        settings.put("reminder_hour", prefs.getInt("reminder_hour", 10));
        settings.put("reminder_minute", prefs.getInt("reminder_minute", 0));
        settings.put("msg_3", prefs.getString("msg_3", ""));
        settings.put("msg_1", prefs.getString("msg_1", ""));
        settings.put("msg_0", prefs.getString("msg_0", ""));
        settings.put("msg_exp", prefs.getString("msg_exp", ""));
        JSONObject presets = new JSONObject();
        for (String s : SERVICES) {
            JSONObject p = new JSONObject();
            p.put("price", prefs.getString(presetKey(s, "price"), ""));
            p.put("profit", prefs.getString(presetKey(s, "profit"), ""));
            presets.put(s, p);
        }
        settings.put("service_presets", presets);
        root.put("app_settings", settings);
        return root.toString(2);
    }

    private void shareBackup() {
        try {
            String json = backupJson();
            File dir = new File(getCacheDir(), "backups");
            dir.mkdirs();
            File f = new File(dir, "NEON_Control_" + LocalDate.now() + ".json");
            try (FileOutputStream out = new FileOutputStream(f)) { out.write(json.getBytes(StandardCharsets.UTF_8)); }
            Uri u = BackupProvider.uriForFile(this, f);
            Intent s = new Intent(Intent.ACTION_SEND);
            s.setType("application/json");
            s.putExtra(Intent.EXTRA_STREAM, u);
            s.putExtra(Intent.EXTRA_SUBJECT, "Copia de seguridad NEON Control");
            s.putExtra(Intent.EXTRA_TEXT, "Copia de seguridad de " + prefs.getString("group_name", "Mi grupo") + " · " + LocalDate.now().format(DATE));
            s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            s.setClipData(ClipData.newRawUri("NEON Control backup", u));
            startActivity(Intent.createChooser(s, "Guardar copia con Gmail, Drive u otra app"));
        } catch (Exception e) { toast("No pude crear la copia: " + e.getMessage()); }
    }

    private void copyBackupText() {
        try {
            ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("NEON Control respaldo", backupJson()));
            toast("Respaldo copiado. Guardalo en un lugar seguro.");
        } catch (Exception e) { toast("No pude copiar el respaldo"); }
    }

    private void pasteBackupDialog() {
        EditText data = multi("Pegá acá el texto completo del respaldo", "");
        data.setMinLines(8);
        try {
            ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cb.hasPrimaryClip() && cb.getPrimaryClip() != null && cb.getPrimaryClip().getItemCount() > 0) {
                CharSequence t = cb.getPrimaryClip().getItemAt(0).coerceToText(this);
                if (t != null && t.toString().trim().startsWith("{")) data.setText(t);
            }
        } catch (Exception ignored) {}
        new AlertDialog.Builder(this)
                .setTitle("Pegar respaldo")
                .setMessage("Pegá el texto de la copia y tocá RESTAURAR.")
                .setView(wrap(data))
                .setPositiveButton("RESTAURAR", (d, w) -> confirmRestoreJson(data.getText().toString()))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void pickBackup() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        startActivityForResult(i, REQ_IMPORT);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_IMPORT && res == RESULT_OK && data != null && data.getData() != null) {
            try (InputStream in = getContentResolver().openInputStream(data.getData()); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) != -1) out.write(b, 0, n);
                confirmRestoreJson(out.toString("UTF-8"));
            } catch (Exception e) { toast("No pude leer la copia: " + e.getMessage()); }
        }
    }

    private void confirmRestoreJson(String json) {
        try {
            JSONObject root = new JSONObject(json);
            if (!"NEON_CONTROL_BACKUP".equals(root.optString("format"))) { toast("Ese texto no es una copia de NEON Control"); return; }
            String oldCode = root.optString("group_code", "").trim();
            String oldName = root.optString("group_name", "Mi negocio").trim();
            if (group().isEmpty()) {
                new AlertDialog.Builder(this).setTitle("Restaurar todo")
                        .setMessage("Se recuperará el grupo “" + oldName + "”, sus clientes, vencimientos, ganancias y configuración.")
                        .setPositiveButton("RESTAURAR", (d, w) -> restoreJson(json, oldCode, oldName, true))
                        .setNegativeButton("Cancelar", null).show();
                return;
            }
            new AlertDialog.Builder(this).setTitle("¿Dónde restaurar?")
                    .setMessage("Podés recuperar también el código y nombre original del grupo, o cargar la copia dentro del grupo que tenés abierto.")
                    .setPositiveButton("GRUPO ORIGINAL", (d, w) -> restoreJson(json, oldCode, oldName, true))
                    .setNeutralButton("GRUPO ACTUAL", (d, w) -> restoreJson(json, group(), prefs.getString("group_name", "Mi grupo"), false))
                    .setNegativeButton("Cancelar", null).show();
        } catch (Exception e) { toast("La copia no se pudo leer. Revisá que esté completa."); }
    }

    private void restoreJson(String json, String targetCode, String targetName, boolean restoreIdentity) {
        try {
            if (targetCode == null || targetCode.trim().isEmpty()) targetCode = "NEON-" + (100000 + new Random().nextInt(900000));
            db.importGroup(targetCode, json, true);
            restoreAppSettings(json);
            if (restoreIdentity || group().isEmpty()) {
                prefs.edit().putString("group_code", targetCode).putString("group_name", clean(targetName, "Mi negocio")).apply();
            }
            toast("Copia restaurada correctamente");
            page = "home";
            buildMain();
        } catch (Exception e) { toast("No pude restaurar: " + e.getMessage()); }
    }

    private void restoreAppSettings(String json) {
        try {
            JSONObject settings = new JSONObject(json).optJSONObject("app_settings");
            if (settings == null) return;
            SharedPreferences.Editor e = prefs.edit();
            e.putInt("font_mode", settings.optInt("font_mode", prefs.getInt("font_mode", 1)));
            e.putBoolean("reminders", settings.optBoolean("reminders", prefs.getBoolean("reminders", true)));
            e.putInt("reminder_hour", settings.optInt("reminder_hour", prefs.getInt("reminder_hour", 10)));
            e.putInt("reminder_minute", settings.optInt("reminder_minute", prefs.getInt("reminder_minute", 0)));
            for (String k : new String[]{"msg_3", "msg_1", "msg_0", "msg_exp"}) if (settings.has(k)) e.putString(k, settings.optString(k, prefs.getString(k, "")));
            JSONObject presets = settings.optJSONObject("service_presets");
            if (presets != null) {
                for (String s : SERVICES) {
                    JSONObject p = presets.optJSONObject(s);
                    if (p != null) {
                        e.putString(presetKey(s, "price"), p.optString("price", ""));
                        e.putString(presetKey(s, "profit"), p.optString("profit", ""));
                    }
                }
            }
            e.apply();
            ReminderReceiver.schedule(this);
        } catch (Exception ignored) {}
    }

    private void messagesDialog() {
        LinearLayout box = vertical();
        EditText m3 = multi("3 días o menos", prefs.getString("msg_3", ""));
        EditText m1 = multi("1 día antes", prefs.getString("msg_1", ""));
        EditText m0 = multi("Vence hoy", prefs.getString("msg_0", ""));
        EditText me = multi("Vencido", prefs.getString("msg_exp", ""));
        box.addView(text("Podés usar: {nombre} {servicio} {dias} {fecha}", 14, CYAN, false));
        margin(box, m3, 0, 8, 0, 0, -1, -2);
        margin(box, m1, 0, 8, 0, 0, -1, -2);
        margin(box, m0, 0, 8, 0, 0, -1, -2);
        margin(box, me, 0, 8, 0, 0, -1, -2);
        ScrollView s = new ScrollView(this);
        s.addView(box);
        new AlertDialog.Builder(this).setTitle("Mensajes automáticos").setView(wrap(s))
                .setPositiveButton("GUARDAR", (d, w) -> prefs.edit()
                        .putString("msg_3", m3.getText().toString())
                        .putString("msg_1", m1.getText().toString())
                        .putString("msg_0", m0.getText().toString())
                        .putString("msg_exp", me.getText().toString()).apply())
                .setNegativeButton("Cancelar", null).show();
    }

    private void changeGroupDialog() {
        LinearLayout box = vertical();
        EditText name = field("Nombre del grupo", false);
        EditText code = field("Código", false);
        name.setText(prefs.getString("group_name", ""));
        code.setText(group());
        box.addView(name);
        margin(box, code, 0, 8, 0, 0, -1, -2);
        new AlertDialog.Builder(this).setTitle("Grupo de trabajo").setView(wrap(box))
                .setPositiveButton("GUARDAR", (d, w) -> {
                    String c = code.getText().toString().trim().toUpperCase(Locale.ROOT);
                    if (c.length() < 5) { toast("Código inválido"); return; }
                    prefs.edit().putString("group_name", clean(name.getText().toString(), "Mi grupo"))
                            .putString("group_code", c).apply();
                    buildMain();
                }).setNegativeButton("Cancelar", null).show();
    }

    private void pickTime() {
        int h = prefs.getInt("reminder_hour", 10), m = prefs.getInt("reminder_minute", 0);
        new TimePickerDialog(this, (v, hh, mm) -> {
            prefs.edit().putInt("reminder_hour", hh).putInt("reminder_minute", mm).apply();
            ReminderReceiver.schedule(this);
            showPage("settings");
        }, h, m, true).show();
    }

    private String money(double v) {
        NumberFormat f = NumberFormat.getCurrencyInstance(new Locale("es", "AR"));
        f.setMaximumFractionDigits(0);
        return f.format(v);
    }
    private String num(double v) { return String.format(Locale.US, "%.0f", v); }
    private String dayText(ClientDb.Client c) {
        if ("INACTIVE".equals(c.status)) return "BAJA";
        long d = c.daysLeft();
        if (d < 0) return "VENCIDO " + Math.abs(d) + "d";
        if (d == 0) return "VENCE HOY";
        if (d == 1) return "1 DÍA";
        return d + " DÍAS";
    }
    private int dayColor(ClientDb.Client c) {
        if ("INACTIVE".equals(c.status)) return MUTED;
        long d = c.daysLeft();
        return d < 0 ? RED : d <= 1 ? YELLOW : d <= 3 ? PINK : GREEN;
    }
    private View statCard(String label, String value, int color) {
        LinearLayout c = card();
        c.addView(text(value, 31, color, true));
        c.addView(text(label, 13, MUTED, true));
        return c;
    }
    private LinearLayout card() {
        LinearLayout l = vertical();
        l.setPadding(dp(15), dp(15), dp(15), dp(15));
        l.setBackground(neon(CARD, 20, 1));
        return l;
    }
    private TextView empty(String s) {
        TextView t = text(s, 17, MUTED, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(18), dp(28), dp(18), dp(28));
        t.setBackground(neon(Color.rgb(30, 35, 66), 18, 1));
        return t;
    }
    private LinearLayout vertical() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(ts(size)); t.setTextColor(color); t.setLineSpacing(dp(2), 1f);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }
    private Button bigButton(String s, int color) {
        Button b = new Button(this); b.setAllCaps(false); b.setText(s); b.setTextSize(ts(17)); b.setTextColor(BG); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setBackground(neon(color, 18, 0)); return b;
    }
    private Button outlineButton(String s) {
        Button b = new Button(this); b.setAllCaps(false); b.setText(s); b.setTextSize(ts(15)); b.setTextColor(WHITE); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setBackground(neon(Color.rgb(34, 39, 69), 16, 1)); return b;
    }
    private Button smallButton(String s, int color) {
        Button b = new Button(this); b.setAllCaps(false); b.setText(s); b.setTextSize(ts(13)); b.setTextColor(color == CYAN || color == GREEN || color == YELLOW ? BG : WHITE); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setBackground(neon(color, 14, 0)); b.setPadding(dp(8), 0, dp(8), 0); return b;
    }
    private EditText field(String hint, boolean number) {
        EditText e = new EditText(this); e.setHint(hint); e.setHintTextColor(Color.rgb(120, 130, 158)); e.setTextColor(WHITE); e.setTextSize(ts(17)); e.setSingleLine(!hint.toLowerCase(Locale.ROOT).contains("nota")); e.setPadding(dp(13), dp(10), dp(13), dp(10)); e.setBackground(neon(Color.rgb(20, 24, 48), 14, 1));
        if (number) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL); return e;
    }
    private EditText multi(String hint, String value) { EditText e = field(hint, false); e.setSingleLine(false); e.setMinLines(3); e.setText(value); e.setGravity(Gravity.TOP); return e; }
    private GradientDrawable neon(int color, int radius, int stroke) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); if (stroke > 0) g.setStroke(dp(stroke), Color.argb(110, 112, 126, 190)); return g; }
    private View wrap(View v) { LinearLayout w = vertical(); w.setPadding(dp(22), dp(8), dp(22), 0); w.addView(v, new LinearLayout.LayoutParams(-1, -2)); return w; }
    private void margin(ViewGroup p, View c, int l, int t, int r, int b, int w, int h) { LinearLayout.LayoutParams x = new LinearLayout.LayoutParams(w == -1 ? -1 : dp(w), h == -2 ? -2 : dp(h)); x.setMargins(dp(l), dp(t), dp(r), dp(b)); p.addView(c, x); }
    private LinearLayout.LayoutParams weight() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f); p.setMargins(dp(4), 0, dp(4), 0); return p; }
    private LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(dp(w), dp(h)); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void space(LinearLayout l, int h) { l.addView(new View(this), new LinearLayout.LayoutParams(1, dp(h))); }
    private String clean(String s, String d) { return s == null || s.trim().isEmpty() ? d : s.trim(); }
    private int parseInt(String s, int d) { try { return Math.max(1, Integer.parseInt(s.trim())); } catch (Exception e) { return d; } }
    private double parseDouble(String s, double d) { try { return Double.parseDouble(s.trim().replace(',', '.')); } catch (Exception e) { return d; } }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    private String group() { return prefs.getString("group_code", ""); }
    private void cycleFont() { int n = (prefs.getInt("font_mode", 1) + 1) % 3; prefs.edit().putInt("font_mode", n).apply(); buildMain(); }
    private String fontLabel() { int x = prefs.getInt("font_mode", 1); return x == 0 ? "A" : x == 1 ? "A+" : "A++"; }
    private float ts(float base) { int x = prefs == null ? 1 : prefs.getInt("font_mode", 1); return base * (x == 0 ? 1f : x == 1 ? 1.10f : 1.22f); }
    private void requestNotifications() { if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 5001); }
}
