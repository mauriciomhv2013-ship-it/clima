package com.smsflow.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import org.json.JSONArray;

import java.time.*;
import java.util.*;

public class MainActivity extends Activity {
    private static final int REQ_PERMS = 10;
    private final ArrayList<Contact> contacts = new ArrayList<>();
    private final LinkedHashSet<String> selectedNumbers = new LinkedHashSet<>();
    private TextView status, selectedCount;
    private EditText message, dailyLimit, interval, startTime, endTime;
    private CheckBox consent;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(10, 10, 20));
        getWindow().setNavigationBarColor(Color.rgb(10, 10, 20));
        buildUi();
        loadSavedIntoUi();
        requestCorePermissions();
        uiHandler.post(statusLoop);
    }

    private final Runnable statusLoop = new Runnable() {
        @Override public void run() {
            updateStatus();
            uiHandler.postDelayed(this, 1500);
        }
    };

    @Override protected void onDestroy() {
        uiHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(gradient(new int[]{Color.rgb(8,8,18), Color.rgb(18,15,45), Color.rgb(7,9,18)}, GradientDrawable.Orientation.TOP_BOTTOM, 0));
        LinearLayout root = column();
        root.setPadding(dp(18), dp(24), dp(18), dp(36));
        scroll.addView(root, new ViewGroup.LayoutParams(-1, -2));

        TextView title = text("SMS FLOW", 30, Color.WHITE, true);
        root.addView(title);
        TextView subtitle = text("Campañas SMS automáticas", 15, Color.rgb(177,181,210), false);
        subtitle.setPadding(0, dp(2), 0, dp(18));
        root.addView(subtitle);

        LinearLayout hero = card();
        hero.setBackground(gradient(new int[]{Color.rgb(111,43,255), Color.rgb(0,190,255), Color.rgb(255,52,174)}, GradientDrawable.Orientation.LEFT_RIGHT, dp(24)));
        hero.addView(text("Campaña automática", 20, Color.WHITE, true));
        status = text("Preparando…", 15, Color.WHITE, false);
        status.setPadding(0, dp(8), 0, 0);
        hero.addView(status);
        root.addView(hero, lpMatchWrap(dp(14)));

        LinearLayout contactsCard = card();
        contactsCard.addView(section("CONTACTOS"));
        selectedCount = text("0 seleccionados", 17, Color.WHITE, true);
        selectedCount.setPadding(0, dp(8), 0, dp(10));
        contactsCard.addView(selectedCount);
        LinearLayout contactButtons = row();
        Button choose = vividButton("Elegir contactos", Color.rgb(99,75,255));
        choose.setOnClickListener(v -> openContactPicker());
        Button all = vividButton("Seleccionar todos", Color.rgb(0,170,220));
        all.setOnClickListener(v -> ensureContactsLoaded(() -> {
            selectedNumbers.clear();
            for (Contact c: contacts) selectedNumbers.add(c.phone);
            updateSelectedCount();
            toast("Todos seleccionados");
        }));
        contactButtons.addView(choose, weightLp());
        contactButtons.addView(all, weightLpLeft());
        contactsCard.addView(contactButtons);
        root.addView(contactsCard, lpMatchWrap(dp(14)));

        LinearLayout composeCard = card();
        composeCard.addView(section("MENSAJE"));
        message = edit("Escribí el SMS que querés enviar", false);
        message.setMinLines(3);
        message.setGravity(Gravity.TOP | Gravity.START);
        composeCard.addView(message, lpMatchWrap(dp(12)));
        root.addView(composeCard, lpMatchWrap(dp(14)));

        LinearLayout rulesCard = card();
        rulesCard.addView(section("PROGRAMACIÓN"));
        dailyLimit = edit("Cantidad por día", true);
        interval = edit("Intervalo en minutos", true);
        startTime = edit("Inicio (HH:mm)", false);
        endTime = edit("Fin (HH:mm)", false);
        rulesCard.addView(labelled("SMS por día", dailyLimit));
        rulesCard.addView(labelled("Minutos entre SMS", interval));
        rulesCard.addView(labelled("Hora de inicio", startTime));
        rulesCard.addView(labelled("Hora de fin", endTime));
        TextView hint = text("Ejemplo: 100 por día, cada 1 minuto, de 09:00 a 11:00. Al día siguiente continúa con los contactos pendientes.", 13, Color.rgb(160,166,198), false);
        hint.setPadding(0, dp(8), 0, dp(8));
        rulesCard.addView(hint);
        root.addView(rulesCard, lpMatchWrap(dp(14)));

        LinearLayout safetyCard = card();
        consent = new CheckBox(this);
        consent.setText("Confirmo que estos contactos aceptaron recibir mis SMS");
        consent.setTextColor(Color.WHITE);
        consent.setTextSize(14);
        safetyCard.addView(consent);
        root.addView(safetyCard, lpMatchWrap(dp(14)));

        Button start = vividButton("INICIAR / GUARDAR CAMPAÑA", Color.rgb(112,58,255));
        start.setTextSize(16);
        start.setOnClickListener(v -> saveAndStart());
        root.addView(start, lpMatchWrap(dp(10)));
        Button stop = vividButton("DETENER CAMPAÑA", Color.rgb(210,48,95));
        stop.setOnClickListener(v -> stopCampaign());
        root.addView(stop, lpMatchWrap(dp(8)));

        TextView footer = text("La app usa tu línea móvil para enviar SMS. El costo y los límites dependen de tu operador.", 12, Color.rgb(130,135,160), false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(8), dp(14), dp(8), 0);
        root.addView(footer);

        setContentView(scroll);
    }

    private void loadSavedIntoUi() {
        Store s = new Store(this);
        message.setText(s.p.getString("message", ""));
        dailyLimit.setText(String.valueOf(s.p.getInt("dailyLimit", 100)));
        interval.setText(String.valueOf(s.p.getInt("interval", 1)));
        startTime.setText(s.p.getString("start", "09:00"));
        endTime.setText(s.p.getString("end", "11:00"));
        consent.setChecked(s.p.getBoolean("consent", false));
        selectedNumbers.addAll(s.getSelectedNumbers());
        updateSelectedCount();
    }

    private void requestCorePermissions() {
        ArrayList<String> need = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.READ_CONTACTS);
        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.SEND_SMS);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_PERMS);
        else loadContactsAsync(null);
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        if (req == REQ_PERMS && checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) loadContactsAsync(null);
    }

    private void ensureContactsLoaded(Runnable after) {
        if (!contacts.isEmpty()) { if (after != null) after.run(); return; }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestCorePermissions();
            toast("Permití acceso a Contactos");
            return;
        }
        loadContactsAsync(after);
    }

    private void loadContactsAsync(Runnable after) {
        new Thread(() -> {
            ArrayList<Contact> loaded = loadContacts();
            runOnUiThread(() -> {
                contacts.clear();
                contacts.addAll(loaded);
                updateSelectedCount();
                if (after != null) after.run();
            });
        }).start();
    }

    private ArrayList<Contact> loadContacts() {
        LinkedHashMap<String, Contact> map = new LinkedHashMap<>();
        String[] projection = {ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER};
        try (Cursor c = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC")) {
            if (c != null) {
                int ni = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int pi = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER);
                while (c.moveToNext()) {
                    String name = c.getString(ni);
                    String raw = c.getString(pi);
                    if (raw == null) continue;
                    String phone = raw.replaceAll("[^+0-9]", "");
                    if (phone.length() < 6) continue;
                    if (name == null || name.trim().isEmpty()) name = phone;
                    if (!map.containsKey(phone)) map.put(phone, new Contact(name, phone));
                }
            }
        } catch (Exception ignored) {}
        return new ArrayList<>(map.values());
    }

    private void openContactPicker() {
        ensureContactsLoaded(() -> {
            if (contacts.isEmpty()) { toast("No encontré contactos con teléfono"); return; }
            String[] labels = new String[contacts.size()];
            boolean[] checked = new boolean[contacts.size()];
            for (int i=0;i<contacts.size();i++) {
                Contact c = contacts.get(i);
                labels[i] = c.name + "\n" + c.phone;
                checked[i] = selectedNumbers.contains(c.phone);
            }
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("Elegí los contactos")
                    .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> {
                        String p = contacts.get(which).phone;
                        if (isChecked) selectedNumbers.add(p); else selectedNumbers.remove(p);
                    })
                    .setPositiveButton("Listo", (d,w) -> updateSelectedCount())
                    .setNegativeButton("Cancelar", null)
                    .create();
            dialog.setOnDismissListener(d -> updateSelectedCount());
            dialog.show();
        });
    }

    private void saveAndStart() {
        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestCorePermissions();
            toast("Falta permiso para enviar SMS");
            return;
        }
        if (selectedNumbers.isEmpty()) { toast("Seleccioná al menos un contacto"); return; }
        String msg = message.getText().toString().trim();
        if (msg.isEmpty()) { toast("Escribí el mensaje"); return; }
        int limit, gap;
        try {
            limit = Integer.parseInt(dailyLimit.getText().toString());
            gap = Integer.parseInt(interval.getText().toString());
        } catch (Exception e) {
            toast("Revisá cantidad e intervalo");
            return;
        }
        if (limit < 1 || gap < 1) { toast("Cantidad e intervalo deben ser mayores a 0"); return; }
        LocalTime st = parseTime(startTime.getText().toString());
        LocalTime en = parseTime(endTime.getText().toString());
        if (st == null || en == null || !en.isAfter(st)) { toast("Revisá los horarios HH:mm"); return; }
        long window = Duration.between(st, en).toMinutes();
        long capacity = 1 + Math.max(0, window - 1) / gap;
        if (limit > capacity) { toast("Con ese horario entran como máximo " + capacity + " SMS por día"); return; }
        if (!consent.isChecked()) { toast("Confirmá el consentimiento de los contactos"); return; }
        if (!Scheduler.canExact(this)) {
            Scheduler.openExactAlarmSettings(this);
            toast("Activá Alarmas y recordatorios y volvé a tocar Iniciar");
            return;
        }

        Store s = new Store(this);
        ArrayList<String> old = s.getSelectedNumbers();
        boolean same = old.equals(new ArrayList<>(selectedNumbers));
        SharedPreferences.Editor e = s.p.edit();
        e.putString("message", msg)
                .putInt("dailyLimit", limit)
                .putInt("interval", gap)
                .putString("start", fmt(st))
                .putString("end", fmt(en))
                .putBoolean("consent", true)
                .putBoolean("active", true)
                .putString("selected", toJson(selectedNumbers));
        if (!same) e.putInt("nextIndex", 0).putInt("sentToday", 0).putString("dayKey", LocalDate.now().toString());
        e.apply();

        Scheduler.cancel(this);
        LocalTime now = LocalTime.now();
        if (!now.isBefore(st) && now.isBefore(en) && s.sentToday() < limit) {
            Intent service = new Intent(this, CampaignService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
            toast("Campaña iniciada");
        } else {
            Scheduler.scheduleNext(this, false);
            toast("Campaña programada");
        }
        updateStatus();
    }

    private void stopCampaign() {
        new Store(this).p.edit().putBoolean("active", false).apply();
        Scheduler.cancel(this);
        stopService(new Intent(this, CampaignService.class));
        toast("Campaña detenida");
        updateStatus();
    }

    private void updateStatus() {
        Store s = new Store(this);
        s.resetDayIfNeeded();
        boolean active = s.p.getBoolean("active", false);
        int sent = s.p.getInt("sentToday", 0);
        int limit = s.p.getInt("dailyLimit", 100);
        int idx = s.p.getInt("nextIndex", 0);
        int total = s.getSelectedNumbers().size();
        int pending = Math.max(0, total - idx);
        status.setText((active ? "ACTIVA" : "DETENIDA") + "  •  " + sent + "/" + limit + " enviados hoy  •  " + pending + " pendientes");
        updateSelectedCount();
    }

    private void updateSelectedCount() {
        if (selectedCount != null) selectedCount.setText(selectedNumbers.size() + " contactos seleccionados");
    }

    private LocalTime parseTime(String s) {
        try {
            String[] p = s.trim().split(":");
            if (p.length != 2) return null;
            return LocalTime.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
        } catch (Exception e) {
            return null;
        }
    }

    private String fmt(LocalTime t) { return String.format(Locale.US, "%02d:%02d", t.getHour(), t.getMinute()); }
    private String toJson(Collection<String> vals) { JSONArray a = new JSONArray(); for (String v: vals) a.put(v); return a.toString(); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private LinearLayout card() { LinearLayout l=column(); l.setPadding(dp(16),dp(16),dp(16),dp(16)); l.setBackground(gradient(new int[]{Color.rgb(23,24,43),Color.rgb(15,17,31)}, GradientDrawable.Orientation.TOP_BOTTOM, dp(22))); return l; }
    private TextView section(String s) { TextView t=text(s,12,Color.rgb(106,213,255),true); t.setLetterSpacing(.12f); return t; }
    private TextView text(String s,int sp,int color,boolean bold) { TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return t; }
    private EditText edit(String hint, boolean number) { EditText e=new EditText(this); e.setHint(hint); e.setHintTextColor(Color.rgb(120,126,155)); e.setTextColor(Color.WHITE); e.setTextSize(16); e.setPadding(dp(14),dp(12),dp(14),dp(12)); e.setBackground(gradient(new int[]{Color.rgb(34,36,58),Color.rgb(27,29,48)}, GradientDrawable.Orientation.LEFT_RIGHT, dp(14))); if(number)e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); return e; }
    private LinearLayout labelled(String label, View field) { LinearLayout b=column(); TextView t=text(label,13,Color.rgb(185,190,216),false); t.setPadding(0,dp(10),0,dp(5)); b.addView(t); b.addView(field,lpMatchWrap(0)); return b; }
    private Button vividButton(String s,int color) { Button b=new Button(this); b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(14); b.setAllCaps(false); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(15)); b.setBackground(g); b.setPadding(dp(12),dp(12),dp(12),dp(12)); return b; }
    private GradientDrawable gradient(int[] colors, GradientDrawable.Orientation o, int radius) { GradientDrawable g=new GradientDrawable(o,colors); g.setCornerRadius(radius); return g; }
    private LinearLayout.LayoutParams lpMatchWrap(int bottom) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,0,0,bottom); return p; }
    private LinearLayout.LayoutParams weightLp() { return new LinearLayout.LayoutParams(0,-2,1f); }
    private LinearLayout.LayoutParams weightLpLeft() { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1f); p.setMargins(dp(8),0,0,0); return p; }
    private int dp(int v) { return Math.round(v*getResources().getDisplayMetrics().density); }

    static class Contact {
        final String name, phone;
        Contact(String n,String p){name=n;phone=p;}
    }
}

class Store {
    final SharedPreferences p;
    Store(Context c) { p=c.getSharedPreferences("sms_flow",Context.MODE_PRIVATE); }
    ArrayList<String> getSelectedNumbers() {
        ArrayList<String> out=new ArrayList<>();
        try {
            JSONArray a=new JSONArray(p.getString("selected","[]"));
            for(int i=0;i<a.length();i++)out.add(a.getString(i));
        } catch(Exception ignored) {}
        return out;
    }
    void resetDayIfNeeded() {
        String today=LocalDate.now().toString();
        if(!today.equals(p.getString("dayKey",""))) p.edit().putString("dayKey",today).putInt("sentToday",0).apply();
    }
    int sentToday() { resetDayIfNeeded(); return p.getInt("sentToday",0); }
}

class Scheduler {
    static boolean canExact(Context c) {
        if(Build.VERSION.SDK_INT<31)return true;
        AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        return a.canScheduleExactAlarms();
    }
    static void openExactAlarmSettings(Context c) {
        if(Build.VERSION.SDK_INT<31)return;
        try {
            Intent i=new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:"+c.getPackageName()));
            c.startActivity(i);
        } catch(Exception ignored) {}
    }
    static void scheduleNext(Context c, boolean tomorrow) {
        Store s=new Store(c);
        s.resetDayIfNeeded();
        if(!s.p.getBoolean("active",false))return;
        LocalTime st=parse(s.p.getString("start","09:00"));
        if(st==null)st=LocalTime.of(9,0);
        LocalDateTime next=LocalDateTime.of(LocalDate.now(),st);
        if(tomorrow||!next.isAfter(LocalDateTime.now()))next=next.plusDays(1);
        Intent in=new Intent(c,CampaignStartReceiver.class);
        PendingIntent pi=PendingIntent.getBroadcast(c,2201,in,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        long when=next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        if(Build.VERSION.SDK_INT<31||am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
    }
    static void cancel(Context c) {
        Intent in=new Intent(c,CampaignStartReceiver.class);
        PendingIntent pi=PendingIntent.getBroadcast(c,2201,in,PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
        if(pi!=null) {
            ((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(pi);
            pi.cancel();
        }
    }
    static LocalTime parse(String v) {
        try {
            String[] p=v.split(":");
            return LocalTime.of(Integer.parseInt(p[0]),Integer.parseInt(p[1]));
        } catch(Exception e) { return null; }
    }
}

class CampaignStartReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        Store s=new Store(c);
        s.resetDayIfNeeded();
        if(!s.p.getBoolean("active",false))return;
        Intent svc=new Intent(c,CampaignService.class);
        try {
            if(Build.VERSION.SDK_INT>=26)c.startForegroundService(svc); else c.startService(svc);
        } catch(Exception ignored) {}
    }
}

class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if(Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) Scheduler.scheduleNext(c,false);
    }
}

class CampaignService extends Service {
    private final Handler h=new Handler(Looper.getMainLooper());
    private boolean stopped=false;
    private Store store;

    @Override public void onCreate() {
        super.onCreate();
        store=new Store(this);
        createChannel();
        Notification n=notification("Preparando campaña…");
        if(Build.VERSION.SDK_INT>=34) startForeground(4401,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(4401,n);
    }

    @Override public int onStartCommand(Intent i,int flags,int id) {
        stopped=false;
        h.removeCallbacksAndMessages(null);
        h.post(this::sendNext);
        return START_NOT_STICKY;
    }

    private void sendNext() {
        if(stopped)return;
        store.resetDayIfNeeded();
        SharedPreferences p=store.p;
        if(!p.getBoolean("active",false)||!p.getBoolean("consent",false)) { finishService(); return; }
        String msg=p.getString("message","");
        ArrayList<String> nums=store.getSelectedNumbers();
        if(msg==null||msg.trim().isEmpty()||nums.isEmpty()) { finishService(); return; }
        LocalTime st=Scheduler.parse(p.getString("start","09:00"));
        LocalTime en=Scheduler.parse(p.getString("end","11:00"));
        LocalTime now=LocalTime.now();
        if(st==null||en==null) { finishService(); return; }
        if(now.isBefore(st)) { Scheduler.scheduleNext(this,false); finishService(); return; }
        int sent=p.getInt("sentToday",0);
        int limit=p.getInt("dailyLimit",100);
        int idx=p.getInt("nextIndex",0);
        if(!now.isBefore(en)||sent>=limit) { Scheduler.scheduleNext(this,true); finishService(); return; }
        if(idx>=nums.size()) {
            p.edit().putBoolean("active",false).apply();
            Scheduler.cancel(this);
            updateNotification("Campaña terminada");
            finishService();
            return;
        }
        if(checkSelfPermission(Manifest.permission.SEND_SMS)!=PackageManager.PERMISSION_GRANTED) {
            updateNotification("Falta permiso SEND_SMS");
            finishService();
            return;
        }
        String phone=nums.get(idx);
        try {
            @SuppressWarnings("deprecation") SmsManager sm=SmsManager.getDefault();
            ArrayList<String> parts=sm.divideMessage(msg);
            if(parts.size()>1)sm.sendMultipartTextMessage(phone,null,parts,null,null);
            else sm.sendTextMessage(phone,null,msg,null,null);
            int next=idx+1;
            int newSent=sent+1;
            p.edit().putInt("nextIndex",next).putInt("sentToday",newSent).putString("dayKey",LocalDate.now().toString()).apply();
            updateNotification(newSent+"/"+limit+" enviados hoy");
            if(next>=nums.size()) {
                p.edit().putBoolean("active",false).apply();
                Scheduler.cancel(this);
                updateNotification("Campaña terminada");
                finishService();
                return;
            }
            if(newSent>=limit) {
                Scheduler.scheduleNext(this,true);
                finishService();
                return;
            }
            long delay=Math.max(1,p.getInt("interval",1))*60000L;
            h.postDelayed(this::sendNext,delay);
        } catch(Exception e) {
            updateNotification("Envío pausado por un error");
            finishService();
        }
    }

    private void createChannel() {
        if(Build.VERSION.SDK_INT>=26) {
            NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel("campaigns","Campañas SMS",NotificationManager.IMPORTANCE_LOW));
        }
    }

    private Notification notification(String text) {
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"campaigns"):new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_dialog_email).setContentTitle("SMS Flow").setContentText(text).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(pi).build();
    }

    private void updateNotification(String t) {
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(4401,notification(t));
    }

    private void finishService() {
        stopped=true;
        h.removeCallbacksAndMessages(null);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onDestroy() {
        stopped=true;
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
