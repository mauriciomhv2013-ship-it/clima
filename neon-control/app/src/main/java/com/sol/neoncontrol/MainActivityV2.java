package com.sol.neoncontrol;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
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
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** NEON Control 2.0 */
public class MainActivityV2 extends Activity {
    private static final int REQ_IMPORT = 9210;
    private static final int REQ_NOTIFICATIONS = 9211;

    private static final int BG = Color.rgb(7, 11, 20);
    private static final int CARD = Color.rgb(16, 24, 40);
    private static final int CARD_2 = Color.rgb(24, 34, 55);
    private static final int WHITE = Color.rgb(245, 248, 255);
    private static final int MUTED = Color.rgb(151, 165, 190);
    private static final int CYAN = Color.rgb(0, 229, 255);
    private static final int PINK = Color.rgb(245, 59, 255);
    private static final int GREEN = Color.rgb(48, 220, 150);
    private static final int YELLOW = Color.rgb(255, 196, 75);
    private static final int RED = Color.rgb(255, 91, 113);

    private ClientDb db;
    private ServiceStore serviceStore;
    private SharedPreferences prefs;
    private FrameLayout root;
    private FrameLayout content;
    private LinearLayout nav;
    private String currentPage = "home";
    private String groupCode;
    private String groupName;

    private final Locale locale = new Locale("es", "AR");
    private final DateTimeFormatter displayDate = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final NumberFormat money = NumberFormat.getCurrencyInstance(locale);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        db = new ClientDb(this);
        serviceStore = new ServiceStore(db);
        prefs = getSharedPreferences("neon_prefs", MODE_PRIVATE);
        groupCode = prefs.getString("group_code", "LOCAL");
        groupName = prefs.getString("group_name", "Mi negocio");
        buildMain();
        requestNotifications();
        ReminderReceiver.schedule(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content != null) showPage(currentPage);
    }

    private void buildMain() {
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        content = new FrameLayout(this);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-1, -1);
        cp.bottomMargin = dp(96);
        root.addView(content, cp);

        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(7), dp(8), dp(7));
        nav.setBackground(round(CARD, dp(22), CYAN, 1));
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(-1, dp(70), Gravity.BOTTOM);
        np.setMargins(dp(12), 0, dp(12), dp(22));
        root.addView(nav, np);

        // Respeta la barra de navegación del teléfono: triángulo, círculo y cuadrado.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottom = insets.getSystemWindowInsetBottom();
            FrameLayout.LayoutParams navParams = (FrameLayout.LayoutParams) nav.getLayoutParams();
            navParams.bottomMargin = bottom + dp(16);
            nav.setLayoutParams(navParams);
            FrameLayout.LayoutParams contentParams = (FrameLayout.LayoutParams) content.getLayoutParams();
            contentParams.bottomMargin = bottom + dp(94);
            content.setLayoutParams(contentParams);
            return insets;
        });

        addNav("⌂\nInicio", "home");
        addNav("☷\nClientes", "clients");
        addNav("$\nGanancias", "profits");
        addNav("⚙\nAjustes", "settings");

        setContentView(root);
        root.requestApplyInsets();
        showPage("home");
    }

    private void addNav(String label, String page) {
        TextView b = text(label, 12, WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(5), dp(3), dp(5), dp(3));
        b.setOnClickListener(v -> showPage(page));
        nav.addView(b, new LinearLayout.LayoutParams(0, -1, 1));
    }

    private void showPage(String page) {
        currentPage = page;
        content.removeAllViews();
        if ("clients".equals(page)) clientsPage();
        else if ("profits".equals(page)) profitsPage();
        else if ("settings".equals(page)) settingsPage();
        else homePage();
    }

    private ScrollView pageShell(String title, String subtitle) {
        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(18), dp(18), dp(30));
        sc.addView(col, new ScrollView.LayoutParams(-1, -2));
        col.addView(text(title, 26, WHITE, true));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = text(subtitle, 14, MUTED, false);
            s.setPadding(0, dp(4), 0, dp(16));
            col.addView(s);
        }
        sc.setTag(col);
        content.addView(sc, new FrameLayout.LayoutParams(-1, -1));
        return sc;
    }

    private LinearLayout column(ScrollView sc) {
        return (LinearLayout) sc.getTag();
    }

    private void homePage() {
        ClientDb.Stats s = db.stats(groupCode);
        ScrollView sc = pageShell("NEON CONTROL", groupName + " · Resumen de hoy");
        LinearLayout col = column(sc);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(statCard("ACTIVOS", Integer.toString(s.active), CYAN), new LinearLayout.LayoutParams(0, dp(92), 1));
        space(row1, 8);
        row1.addView(statCard("VENCEN HOY", Integer.toString(s.today), YELLOW), new LinearLayout.LayoutParams(0, dp(92), 1));
        col.addView(row1);
        gap(col, 8);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(statCard("VENCIDOS", Integer.toString(s.expired), RED), new LinearLayout.LayoutParams(0, dp(92), 1));
        space(row2, 8);
        row2.addView(statCard("PRÓX. 3 DÍAS", Integer.toString(s.next3), GREEN), new LinearLayout.LayoutParams(0, dp(92), 1));
        col.addView(row2);

        gap(col, 16);
        Button add = bigButton("＋ AGREGAR CLIENTE");
        add.setOnClickListener(v -> clientDialog(null));
        col.addView(add);

        LocalDate today = LocalDate.now();
        ClientDb.ProfitReport pr = db.profitReport(groupCode, today, today);
        gap(col, 14);
        col.addView(highlightCard("GANANCIA DE HOY", money(pr.profit), "Ventas registradas: " + pr.sales.size(), PINK));

        gap(col, 18);
        col.addView(text("VENCIMIENTOS Y COBROS", 18, WHITE, true));
        gap(col, 8);
        List<ClientDb.Client> urgent = db.list(groupCode, "", "URGENT");
        if (urgent.isEmpty()) {
            col.addView(infoCard("No hay vencimientos en los próximos 3 días."));
        } else {
            for (ClientDb.Client c : urgent) col.addView(clientCard(c, true));
        }
    }

    private void clientsPage() {
        ScrollView sc = pageShell("Clientes", "Servicio, precio y ganancia quedan asociados a cada cliente");
        LinearLayout col = column(sc);
        EditText search = input("Buscar teléfono, nombre o servicio", InputType.TYPE_CLASS_TEXT);
        col.addView(search);
        gap(col, 10);
        Button add = bigButton("＋ NUEVO CLIENTE");
        add.setOnClickListener(v -> clientDialog(null));
        col.addView(add);
        gap(col, 12);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list);

        Runnable render = () -> renderClients(list, search.getText().toString());
        render.run();
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { render.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void renderClients(LinearLayout list, String search) {
        list.removeAllViews();
        List<ClientDb.Client> clients = db.list(groupCode, search, null);
        if (clients.isEmpty()) list.addView(infoCard("No encontré clientes."));
        else for (ClientDb.Client c : clients) list.addView(clientCard(c, false));
    }

    private View clientCard(ClientDb.Client c, boolean compact) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(round(CARD, dp(16), Color.rgb(42, 58, 86), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);

        String display = c.displayName();
        card.addView(text(display + " · " + c.service, 16, WHITE, true));
        long left = c.daysLeft();
        int color = left < 0 ? RED : left == 0 ? YELLOW : GREEN;
        String when = left < 0 ? "Vencido hace " + (-left) + " d" : left == 0 ? "Vence hoy" : "Vence en " + left + " d";
        TextView sub = text("📱 " + c.phone + "   •   " + when + " (" + fmt(parseDate(c.expiryDate)) + ")", 13, color, false);
        sub.setPadding(0, dp(5), 0, dp(7));
        card.addView(sub);
        if (!compact) card.addView(text("Venta: " + money(c.price) + "   ·   Ganancia: " + money(c.profit), 13, MUTED, false));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button wa = smallButton("WHATSAPP");
        wa.setOnClickListener(v -> openWhatsApp(c));
        actions.addView(wa, new LinearLayout.LayoutParams(0, dp(42), 1));
        space(actions, 6);
        Button edit = smallButton(compact ? "COBRAR / VER" : "EDITAR");
        edit.setOnClickListener(v -> { if (compact) renewDialog(c); else clientMore(c); });
        actions.addView(edit, new LinearLayout.LayoutParams(0, dp(42), 1));
        card.addView(actions);
        return card;
    }

    private void clientMore(ClientDb.Client c) {
        String[] items = {"Editar cliente", "Renovar / cobrar hoy", "Marcar no renovado", "Reactivar", "Eliminar definitivamente"};
        new AlertDialog.Builder(this)
                .setTitle(c.displayName())
                .setItems(items, (d, which) -> {
                    if (which == 0) clientDialog(c);
                    else if (which == 1) renewDialog(c);
                    else if (which == 2) { db.setStatus(c.id, "INACTIVE"); showPage(currentPage); }
                    else if (which == 3) { db.setStatus(c.id, "ACTIVE"); showPage(currentPage); }
                    else confirmDelete(c);
                }).show();
    }

    private void confirmDelete(ClientDb.Client c) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar cliente")
                .setMessage("Se eliminará el cliente. El historial de ventas se conserva para no alterar las ganancias históricas.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", (d, w) -> { db.delete(c.id); showPage(currentPage); })
                .show();
    }

    private void clientDialog(ClientDb.Client existing) {
        boolean edit = existing != null;
        LinearLayout col = dialogColumn();
        EditText name = input("Nombre (opcional)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText phone = input("Teléfono", InputType.TYPE_CLASS_PHONE);
        Spinner service = new Spinner(this);
        EditText days = input("Días", InputType.TYPE_CLASS_NUMBER);
        EditText price = input("Precio de venta", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText profit = input("Ganancia", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText notes = input("Notas (opcional)", InputType.TYPE_CLASS_TEXT);

        List<ServiceStore.ServiceItem> services = new ArrayList<>(serviceStore.list(false));
        if (edit && serviceStore.findByName(existing.service) == null) {
            ServiceStore.ServiceItem legacy = new ServiceStore.ServiceItem();
            legacy.name = existing.service;
            legacy.price = existing.price;
            legacy.profit = existing.profit;
            legacy.active = true;
            services.add(legacy);
        }
        ArrayList<String> serviceNames = new ArrayList<>();
        for (ServiceStore.ServiceItem s : services) serviceNames.add(s.name);
        if (serviceNames.isEmpty()) serviceNames.add("Sin servicio configurado");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, serviceNames);
        service.setAdapter(adapter);

        col.addView(label("NOMBRE")); col.addView(name);
        col.addView(label("TELÉFONO")); col.addView(phone);
        col.addView(label("SERVICIO / PLATAFORMA")); col.addView(service, new LinearLayout.LayoutParams(-1, dp(52)));
        col.addView(label("DURACIÓN")); col.addView(days);
        col.addView(label("PRECIO DE VENTA")); col.addView(price);
        col.addView(label("GANANCIA")); col.addView(profit);
        col.addView(label("NOTAS")); col.addView(notes);

        final boolean[] firstSelection = {true};
        service.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= services.size()) return;
                ServiceStore.ServiceItem selected = services.get(position);
                if (!edit || !firstSelection[0] || !selected.name.equalsIgnoreCase(existing.service)) {
                    price.setText(number(selected.price));
                    profit.setText(number(selected.profit));
                }
                firstSelection[0] = false;
            }
        });

        if (edit) {
            name.setText(existing.name);
            phone.setText(existing.phone);
            days.setText(Integer.toString(existing.days));
            price.setText(number(existing.price));
            profit.setText(number(existing.profit));
            notes.setText(existing.notes);
            int index = serviceNames.indexOf(existing.service);
            if (index >= 0) service.setSelection(index);
        } else {
            days.setText("30");
        }

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(edit ? "Editar cliente" : "Nuevo cliente")
                .setView(wrapDialog(col))
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("GUARDAR", null)
                .create();
        dlg.setOnShowListener(x -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String ph = phone.getText().toString().trim();
            if (ph.isEmpty()) { phone.setError("Ingresá un teléfono"); return; }
            String srv = service.getSelectedItem() == null ? "" : service.getSelectedItem().toString().trim();
            if (srv.isEmpty() || "Sin servicio configurado".equals(srv)) { toast("Primero agregá un servicio en Ajustes"); return; }
            int duration = Math.max(1, intValue(days, 30));
            double salePrice = Math.max(0, doubleValue(price));
            double gain = Math.max(0, doubleValue(profit));
            try {
                if (edit) {
                    existing.name = name.getText().toString().trim();
                    existing.phone = ph;
                    existing.service = srv;
                    existing.days = duration;
                    existing.price = salePrice;
                    existing.profit = gain;
                    existing.notes = notes.getText().toString().trim();
                    db.update(existing);
                } else {
                    db.insert(groupCode, name.getText().toString().trim(), ph, srv, duration, salePrice, gain, notes.getText().toString().trim());
                }
                dlg.dismiss();
                showPage("clients");
            } catch (Exception e) {
                toast("No se pudo guardar: " + safeMessage(e));
            }
        }));
        dlg.show();
    }

    private void renewDialog(ClientDb.Client c) {
        LinearLayout col = dialogColumn();
        EditText days = input("Días", InputType.TYPE_CLASS_NUMBER);
        EditText price = input("Precio de venta", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText profit = input("Ganancia", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        days.setText(Integer.toString(c.days > 0 ? c.days : 30));
        ServiceStore.ServiceItem preset = serviceStore.findByName(c.service);
        price.setText(number(preset == null ? c.price : preset.price));
        profit.setText(number(preset == null ? c.profit : preset.profit));
        col.addView(label("SERVICIO")); col.addView(infoCard(c.service));
        col.addView(label("DÍAS")); col.addView(days);
        col.addView(label("PRECIO DE VENTA")); col.addView(price);
        col.addView(label("GANANCIA")); col.addView(profit);
        new AlertDialog.Builder(this)
                .setTitle("Renovar / cobrar · " + c.displayName())
                .setView(wrapDialog(col))
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("REGISTRAR", (d, w) -> {
                    try {
                        db.renew(c.id, Math.max(1, intValue(days, 30)), Math.max(0, doubleValue(price)), Math.max(0, doubleValue(profit)));
                        showPage(currentPage);
                    } catch (Exception e) {
                        toast("No se pudo renovar: " + safeMessage(e));
                    }
                }).show();
    }

    private void profitsPage() {
        ScrollView sc = pageShell("Ganancias", "Día, semana, mes, año e historial completo");
        LinearLayout col = column(sc);
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate yearStart = today.withDayOfYear(1);

        ClientDb.ProfitReport day = db.profitReport(groupCode, today, today);
        ClientDb.ProfitReport week = db.profitReport(groupCode, weekStart, today);
        ClientDb.ProfitReport month = db.profitReport(groupCode, monthStart, today);
        ClientDb.ProfitReport year = db.profitReport(groupCode, yearStart, today);

        col.addView(highlightCard("HOY", money(day.profit), "Vendido: " + money(day.revenue) + " · " + day.sales.size() + " ventas", PINK));
        gap(col, 8);
        col.addView(highlightCard("ESTA SEMANA", money(week.profit), fmt(weekStart) + " al " + fmt(today), CYAN));
        gap(col, 8);
        col.addView(highlightCard("ESTE MES", money(month.profit), monthName(today) + " · Vendido: " + money(month.revenue), GREEN));
        gap(col, 8);
        col.addView(highlightCard("ESTE AÑO", money(year.profit), Integer.toString(today.getYear()) + " · Vendido: " + money(year.revenue), YELLOW));

        gap(col, 14);
        Button custom = outlineButton("ELEGIR PERÍODO");
        custom.setOnClickListener(v -> customPeriodDialog());
        col.addView(custom);

        gap(col, 20);
        col.addView(text("GANANCIA POR SERVICIO · ESTE MES", 17, WHITE, true));
        gap(col, 8);
        if (month.byService.isEmpty()) col.addView(infoCard("Todavía no hay ventas registradas este mes."));
        else for (Map.Entry<String, Double> e : month.byService.entrySet()) col.addView(historyRow(e.getKey(), e.getValue()));

        gap(col, 20);
        col.addView(text("HISTORIAL POR MES", 17, WHITE, true));
        gap(col, 8);
        Map<String, Double> monthly = serviceStore.monthlyProfit(groupCode);
        if (monthly.isEmpty()) col.addView(infoCard("El historial mensual aparecerá cuando registres ventas."));
        else for (Map.Entry<String, Double> e : monthly.entrySet()) col.addView(historyRow(monthLabel(e.getKey()), e.getValue()));

        gap(col, 20);
        col.addView(text("HISTORIAL POR AÑO", 17, WHITE, true));
        gap(col, 8);
        Map<String, Double> yearly = serviceStore.yearlyProfit(groupCode);
        if (yearly.isEmpty()) col.addView(infoCard("Todavía no hay años con ventas registradas."));
        else for (Map.Entry<String, Double> e : yearly.entrySet()) col.addView(historyRow(e.getKey(), e.getValue()));
    }

    private void customPeriodDialog() {
        final LocalDate[] from = {LocalDate.now().withDayOfMonth(1)};
        final LocalDate[] to = {LocalDate.now()};
        LinearLayout col = dialogColumn();
        TextView fromText = infoCard("Desde: " + fmt(from[0]));
        TextView toText = infoCard("Hasta: " + fmt(to[0]));
        fromText.setOnClickListener(v -> pickDate(from[0], d -> { from[0] = d; fromText.setText("Desde: " + fmt(d)); }));
        toText.setOnClickListener(v -> pickDate(to[0], d -> { to[0] = d; toText.setText("Hasta: " + fmt(d)); }));
        col.addView(fromText); gap(col, 8); col.addView(toText);
        new AlertDialog.Builder(this)
                .setTitle("Elegir período")
                .setView(col)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("VER", (d, w) -> {
                    if (to[0].isBefore(from[0])) { toast("La fecha final no puede ser anterior a la inicial"); return; }
                    showPeriodReport(from[0], to[0]);
                }).show();
    }

    private void showPeriodReport(LocalDate from, LocalDate to) {
        ClientDb.ProfitReport r = db.profitReport(groupCode, from, to);
        LinearLayout col = dialogColumn();
        col.addView(highlightCard("GANANCIA", money(r.profit), "Vendido: " + money(r.revenue) + " · " + r.sales.size() + " ventas", GREEN));
        gap(col, 12);
        if (r.byService.isEmpty()) col.addView(infoCard("No hay ventas en este período."));
        else {
            col.addView(text("Por servicio", 16, WHITE, true));
            gap(col, 6);
            for (Map.Entry<String, Double> e : r.byService.entrySet()) col.addView(historyRow(e.getKey(), e.getValue()));
        }
        new AlertDialog.Builder(this)
                .setTitle(fmt(from) + " al " + fmt(to))
                .setView(wrapDialog(col))
                .setPositiveButton("CERRAR", null)
                .show();
    }

    private void settingsPage() {
        ScrollView sc = pageShell("Ajustes", "Servicios, copias de seguridad y recordatorios");
        LinearLayout col = column(sc);

        col.addView(sectionCard("Servicios y precios", "Administrá Netflix, Max, Prime Video, Paramount+, Crunchyroll, fútbol o cualquier servicio nuevo. Cada uno tiene precio de venta y ganancia.", "ADMINISTRAR SERVICIOS", v -> servicesDialog()));
        gap(col, 10);
        col.addView(sectionCard("Copia de seguridad", "Guarda clientes, ventas, historial y servicios. Podés elegir Gmail, Google Drive u otra aplicación y luego restaurar el archivo.", "GUARDAR / RESTAURAR", v -> backupDialog()));
        gap(col, 10);
        col.addView(sectionCard("Grupo de trabajo", groupName + " · " + groupCode, "CAMBIAR GRUPO", v -> groupDialog()));
        gap(col, 10);
        int hour = prefs.getInt("reminder_hour", 10);
        int minute = prefs.getInt("reminder_minute", 0);
        col.addView(sectionCard("Recordatorio diario", String.format(locale, "Avisos de vencimientos a las %02d:%02d", hour, minute), "CAMBIAR HORARIO", v -> pickReminderTime()));
        gap(col, 10);
        col.addView(sectionCard("Mensajes de WhatsApp", "Personalizá los mensajes para próximos vencimientos y vencidos.", "EDITAR MENSAJES", v -> messagesDialog()));
        gap(col, 18);
        col.addView(text("NEON Control 2.0", 13, MUTED, false));
    }

    private void servicesDialog() {
        LinearLayout col = dialogColumn();
        Button add = bigButton("＋ AGREGAR SERVICIO");
        col.addView(add);
        gap(col, 10);
        List<ServiceStore.ServiceItem> items = serviceStore.list(true);
        for (ServiceStore.ServiceItem s : items) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackground(round(CARD_2, dp(12), Color.rgb(58, 73, 102), 1));
            row.addView(text(s.name + (s.active ? "" : " · INACTIVO"), 16, WHITE, true));
            row.addView(text("Venta: " + money(s.price) + " · Ganancia: " + money(s.profit), 13, MUTED, false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(7);
            col.addView(row, lp);
            row.setOnClickListener(v -> { activeServicesDialog = null; serviceEditDialog(s); });
        }
        ScrollView sc = wrapDialog(col);
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Servicios y precios")
                .setView(sc)
                .setPositiveButton("CERRAR", null)
                .create();
        activeServicesDialog = dlg;
        add.setOnClickListener(v -> { if (activeServicesDialog != null) activeServicesDialog.dismiss(); serviceEditDialog(null); });
        dlg.show();
    }

    private AlertDialog activeServicesDialog;

    private void serviceEditDialog(ServiceStore.ServiceItem existing) {
        if (activeServicesDialog != null) activeServicesDialog.dismiss();
        boolean edit = existing != null;
        LinearLayout col = dialogColumn();
        EditText name = input("Nombre del servicio", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText price = input("Precio de venta", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText profit = input("Ganancia", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        CheckBox active = new CheckBox(this);
        active.setText("Servicio activo");
        active.setTextColor(WHITE);
        active.setChecked(existing == null || existing.active);
        col.addView(name); gap(col, 8); col.addView(price); gap(col, 8); col.addView(profit); col.addView(active);
        if (edit) {
            name.setText(existing.name);
            price.setText(number(existing.price));
            profit.setText(number(existing.profit));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(edit ? "Editar servicio" : "Agregar servicio")
                .setView(col)
                .setNegativeButton("Cancelar", (d, w) -> servicesDialog())
                .setPositiveButton("GUARDAR", null);
        if (edit) builder.setNeutralButton("ELIMINAR", null);
        AlertDialog dlg = builder.create();
        dlg.setOnShowListener(x -> {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String n = name.getText().toString().trim();
                if (n.isEmpty()) { name.setError("Ingresá un nombre"); return; }
                ServiceStore.ServiceItem s = edit ? existing : new ServiceStore.ServiceItem();
                s.name = n;
                s.price = Math.max(0, doubleValue(price));
                s.profit = Math.max(0, doubleValue(profit));
                s.active = active.isChecked();
                try {
                    serviceStore.save(s);
                    dlg.dismiss();
                    servicesDialog();
                } catch (Exception e) {
                    name.setError("Ya existe un servicio con ese nombre");
                }
            });
            if (edit) {
                Button remove = dlg.getButton(AlertDialog.BUTTON_NEUTRAL);
                remove.setOnClickListener(v -> new AlertDialog.Builder(this)
                        .setTitle("Eliminar servicio")
                        .setMessage("Los clientes y ventas anteriores conservarán el nombre del servicio. Solo se quita de la lista para futuras ventas.")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Eliminar", (d, w) -> {
                            serviceStore.delete(existing.id);
                            dlg.dismiss();
                            servicesDialog();
                        }).show());
            }
        });
        dlg.show();
    }

    private void backupDialog() {
        String[] options = {"Guardar copia · Gmail / Drive", "Restaurar una copia"};
        new AlertDialog.Builder(this)
                .setTitle("Copia de seguridad")
                .setItems(options, (d, which) -> {
                    if (which == 0) shareBackup();
                    else pickBackup();
                }).show();
    }

    private void shareBackup() {
        try {
            JSONObject root = new JSONObject(db.exportGroup(groupCode, groupName));
            root.put("version", 3);
            root.put("services", serviceStore.exportJson());
            JSONObject settings = new JSONObject();
            settings.put("group_name", groupName);
            settings.put("reminder_hour", prefs.getInt("reminder_hour", 10));
            settings.put("reminder_minute", prefs.getInt("reminder_minute", 0));
            settings.put("msg_soon", prefs.getString("msg_soon", defaultSoonMessage()));
            settings.put("msg_expired", prefs.getString("msg_expired", defaultExpiredMessage()));
            root.put("settings", settings);

            File dir = new File(getCacheDir(), "backups");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("No se pudo crear la carpeta de copia");
            String filename = "NEON_Control_Backup_" + LocalDate.now() + ".json";
            File file = new File(dir, filename);
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = new Uri.Builder().scheme("content").authority("com.sol.neoncontrol.backup").appendPath(filename).build();
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("application/json");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_SUBJECT, "Copia de seguridad NEON Control");
            send.putExtra(Intent.EXTRA_TEXT, "Guardá este archivo para poder recuperar NEON Control.");
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "Guardar con Gmail, Drive u otra app"));
        } catch (Exception e) {
            toast("No se pudo crear la copia: " + safeMessage(e));
        }
    }

    private void pickBackup() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        startActivityForResult(i, REQ_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_IMPORT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            restoreBackup(data.getData());
        }
    }

    private void restoreBackup(Uri uri) {
        new AlertDialog.Builder(this)
                .setTitle("Restaurar copia")
                .setMessage("Se reemplazarán los clientes y ventas del grupo actual por los datos de la copia. También se restaurarán los servicios guardados.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("RESTAURAR", (d, w) -> {
                    try (InputStream in = getContentResolver().openInputStream(uri)) {
                        if (in == null) throw new IllegalStateException("No se pudo abrir el archivo");
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        byte[] buffer = new byte[8192];
                        int n;
                        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                        String json = out.toString("UTF-8");
                        JSONObject root = new JSONObject(json);
                        if (!"NEON_CONTROL_BACKUP".equals(root.optString("format"))) throw new IllegalArgumentException("La copia no es de NEON Control");
                        db.importGroup(groupCode, json, true);
                        serviceStore.importJson(root);
                        JSONObject settings = root.optJSONObject("settings");
                        if (settings != null) {
                            groupName = settings.optString("group_name", groupName);
                            prefs.edit()
                                    .putString("group_name", groupName)
                                    .putInt("reminder_hour", settings.optInt("reminder_hour", 10))
                                    .putInt("reminder_minute", settings.optInt("reminder_minute", 0))
                                    .putString("msg_soon", settings.optString("msg_soon", defaultSoonMessage()))
                                    .putString("msg_expired", settings.optString("msg_expired", defaultExpiredMessage()))
                                    .apply();
                            ReminderReceiver.schedule(this);
                        }
                        toast("Copia restaurada correctamente");
                        showPage("home");
                    } catch (Exception e) {
                        toast("No se pudo restaurar: " + safeMessage(e));
                    }
                }).show();
    }

    private void groupDialog() {
        LinearLayout col = dialogColumn();
        EditText name = input("Nombre para mostrar", InputType.TYPE_CLASS_TEXT);
        EditText code = input("Código del grupo", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        name.setText(groupName);
        code.setText(groupCode);
        col.addView(name); gap(col, 8); col.addView(code);
        new AlertDialog.Builder(this)
                .setTitle("Grupo de trabajo")
                .setView(col)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("GUARDAR", (d, w) -> {
                    String n = name.getText().toString().trim();
                    String c = code.getText().toString().trim().toUpperCase(Locale.ROOT);
                    if (n.isEmpty()) n = "Mi negocio";
                    if (c.isEmpty()) c = "NEON-" + (1000 + new Random().nextInt(9000));
                    groupName = n;
                    groupCode = c;
                    prefs.edit().putString("group_name", n).putString("group_code", c).apply();
                    showPage("settings");
                }).show();
    }

    private void pickReminderTime() {
        int h = prefs.getInt("reminder_hour", 10);
        int m = prefs.getInt("reminder_minute", 0);
        new TimePickerDialog(this, (TimePicker view, int hour, int minute) -> {
            prefs.edit().putBoolean("reminders", true).putInt("reminder_hour", hour).putInt("reminder_minute", minute).apply();
            ReminderReceiver.schedule(this);
            showPage("settings");
        }, h, m, true).show();
    }

    private void messagesDialog() {
        LinearLayout col = dialogColumn();
        EditText soon = input("Mensaje de próximo vencimiento", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        EditText expired = input("Mensaje de vencido", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        soon.setMinLines(3);
        expired.setMinLines(3);
        soon.setText(prefs.getString("msg_soon", defaultSoonMessage()));
        expired.setText(prefs.getString("msg_expired", defaultExpiredMessage()));
        col.addView(label("PRÓXIMO VENCIMIENTO")); col.addView(soon, new LinearLayout.LayoutParams(-1, dp(100)));
        col.addView(label("VENCIDO")); col.addView(expired, new LinearLayout.LayoutParams(-1, dp(100)));
        new AlertDialog.Builder(this)
                .setTitle("Mensajes de WhatsApp")
                .setView(wrapDialog(col))
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("GUARDAR", (d, w) -> prefs.edit().putString("msg_soon", soon.getText().toString()).putString("msg_expired", expired.getText().toString()).apply())
                .show();
    }

    private String defaultSoonMessage() {
        return "Hola {nombre} 👋 Tu servicio de {servicio} vence en {dias} días ({fecha}). Si querés renovarlo, avisame.";
    }

    private String defaultExpiredMessage() {
        return "Hola {nombre} 👋 Tu servicio de {servicio} está vencido. Si querés renovarlo, avisame y lo activamos.";
    }

    private void openWhatsApp(ClientDb.Client c) {
        long days = c.daysLeft();
        String template = days < 0 ? prefs.getString("msg_expired", defaultExpiredMessage()) : prefs.getString("msg_soon", defaultSoonMessage());
        String name = c.name == null ? "" : c.name;
        String msg = template
                .replace("{nombre}", name)
                .replace("{servicio}", c.service == null ? "" : c.service)
                .replace("{dias}", Long.toString(Math.abs(days)))
                .replace("{fecha}", fmt(parseDate(c.expiryDate)));
        String phone = c.phone == null ? "" : c.phone.replaceAll("[^0-9]", "");
        try {
            String url = "https://wa.me/" + phone + "?text=" + URLEncoder.encode(msg, "UTF-8");
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast("No se pudo abrir WhatsApp");
        }
    }

    private LinearLayout dialogColumn() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(4), dp(16), dp(12));
        return col;
    }

    private ScrollView wrapDialog(LinearLayout col) {
        ScrollView sc = new ScrollView(this);
        sc.addView(col);
        return sc;
    }

    private View sectionCard(String title, String body, String button, View.OnClickListener listener) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        c.setBackground(round(CARD, dp(16), Color.rgb(42, 58, 86), 1));
        c.addView(text(title, 17, WHITE, true));
        TextView b = text(body, 13, MUTED, false);
        b.setPadding(0, dp(6), 0, dp(10));
        c.addView(b);
        Button x = outlineButton(button);
        x.setOnClickListener(listener);
        c.addView(x);
        return c;
    }

    private View highlightCard(String title, String value, String sub, int color) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(round(CARD, dp(18), color, 2));
        c.addView(text(title, 13, MUTED, true));
        TextView v = text(value, 28, color, true);
        v.setPadding(0, dp(3), 0, dp(3));
        c.addView(v);
        c.addView(text(sub, 13, MUTED, false));
        return c;
    }

    private View statCard(String title, String value, int color) {
        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(CARD, dp(16), color, 1));
        c.addView(text(value, 26, color, true));
        c.addView(text(title, 11, MUTED, true));
        return c;
    }

    private View historyRow(String label, double value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(round(CARD_2, dp(12), Color.rgb(48, 63, 91), 1));
        row.addView(text(label, 14, WHITE, true), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(text(money(value), 16, GREEN, true));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(6);
        row.setLayoutParams(lp);
        return row;
    }

    private TextView infoCard(String value) {
        TextView v = text(value, 14, MUTED, false);
        v.setPadding(dp(14), dp(13), dp(14), dp(13));
        v.setBackground(round(CARD, dp(14), Color.rgb(42, 58, 86), 1));
        return v;
    }

    private TextView label(String value) {
        TextView v = text(value, 12, MUTED, true);
        v.setPadding(0, dp(8), 0, dp(4));
        return v;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private EditText input(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(115, 129, 153));
        e.setTextColor(Color.rgb(20, 26, 38));
        e.setTextSize(16);
        e.setInputType(type);
        e.setSingleLine((type & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0);
        e.setPadding(dp(12), 0, dp(12), 0);
        e.setBackground(round(Color.rgb(239, 243, 250), dp(10), Color.rgb(182, 194, 214), 1));
        e.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(52)));
        return e;
    }

    private Button bigButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(BG);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setBackground(round(CYAN, dp(14), CYAN, 0));
        b.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(54)));
        return b;
    }

    private Button smallButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(WHITE);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setPadding(dp(6), 0, dp(6), 0);
        b.setBackground(round(CARD_2, dp(11), CYAN, 1));
        return b;
    }

    private Button outlineButton(String label) {
        Button b = smallButton(label);
        b.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(48)));
        return b;
    }

    private GradientDrawable round(int fill, float radius, int stroke, int strokeWidth) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius);
        if (strokeWidth > 0) g.setStroke(dp(strokeWidth), stroke);
        return g;
    }

    private void gap(LinearLayout parent, int height) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private void space(LinearLayout parent, int width) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(dp(width), 1));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String money(double value) {
        money.setMaximumFractionDigits(0);
        return money.format(value);
    }

    private String number(double value) {
        if (Math.rint(value) == value) return Long.toString((long) value);
        return Double.toString(value);
    }

    private int intValue(EditText e, int fallback) {
        try { return Integer.parseInt(e.getText().toString().trim()); }
        catch (Exception ignored) { return fallback; }
    }

    private double doubleValue(EditText e) {
        try { return Double.parseDouble(e.getText().toString().trim().replace(",", ".")); }
        catch (Exception ignored) { return 0; }
    }

    private LocalDate parseDate(String value) {
        try { return LocalDate.parse(value); }
        catch (Exception ignored) { return LocalDate.now(); }
    }

    private String fmt(LocalDate d) {
        return d.format(displayDate);
    }

    private String monthName(LocalDate d) {
        String s = d.getMonth().getDisplayName(TextStyle.FULL, locale);
        return s.substring(0, 1).toUpperCase(locale) + s.substring(1) + " " + d.getYear();
    }

    private String monthLabel(String yyyyMm) {
        try { return monthName(LocalDate.parse(yyyyMm + "-01")); }
        catch (Exception ignored) { return yyyyMm; }
    }

    private interface DateListener { void selected(LocalDate date); }

    private void pickDate(LocalDate initial, DateListener listener) {
        new DatePickerDialog(this, (view, y, m, d) -> listener.selected(LocalDate.of(y, m + 1, d)),
                initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth()).show();
    }

    private String safeMessage(Exception e) {
        String m = e.getMessage();
        return m == null || m.trim().isEmpty() ? e.getClass().getSimpleName() : m;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }
}
