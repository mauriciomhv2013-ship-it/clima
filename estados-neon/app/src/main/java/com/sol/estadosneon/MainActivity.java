package com.sol.estadosneon;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PICK_MEDIA = 4101;
    private static final int REQ_NOTIFICATIONS = 4102;
    private SharedPreferences prefs;
    private LinearLayout previewRow;
    private TextView countView, timeView;
    private Switch scheduleSwitch;
    private boolean replaceWhenPicking;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(5,0,11));
        getWindow().setNavigationBarColor(Color.rgb(5,0,11));
        prefs = getSharedPreferences(MediaHelper.PREFS, MODE_PRIVATE);
        MediaHelper.ensureDefaults(this);
        MediaHelper.initialize(prefs);
        createChannel();
        setContentView(R.layout.activity_main);
        bindUi();
        requestNotifications();
        if (getIntent().getBooleanExtra("publish_now", false))
            getWindow().getDecorView().postDelayed(() -> MediaHelper.shareAll(this, prefs), 450);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        if (intent.getBooleanExtra("publish_now", false))
            getWindow().getDecorView().postDelayed(() -> MediaHelper.shareAll(this, prefs), 350);
    }

    private void bindUi() {
        countView = findViewById(R.id.countView);
        timeView = findViewById(R.id.timeView);
        previewRow = findViewById(R.id.previewRow);
        scheduleSwitch = findViewById(R.id.scheduleSwitch);
        scheduleSwitch.setChecked(prefs.getBoolean("schedule_enabled", false));

        findViewById(R.id.publishButton).setOnClickListener(v -> MediaHelper.shareAll(this, prefs));
        findViewById(R.id.changeTimeButton).setOnClickListener(v -> pickTime());
        findViewById(R.id.addButton).setOnClickListener(v -> pickMedia(false));
        findViewById(R.id.replaceButton).setOnClickListener(v -> pickMedia(true));
        findViewById(R.id.restoreButton).setOnClickListener(v -> {
            MediaHelper.ensureDefaults(this);
            MediaHelper.save(prefs, new ArrayList<>(Arrays.asList(MediaHelper.DEFAULT_ENTRIES)));
            refresh(); Toast.makeText(this, "Volvieron los 7 originales", Toast.LENGTH_SHORT).show();
        });
        scheduleSwitch.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean("schedule_enabled", checked).apply();
            if (checked) { requestNotifications(); AlarmReceiver.scheduleNext(this); }
            else AlarmReceiver.cancel(this);
            Toast.makeText(this, checked ? "Aviso diario activado" : "Aviso diario desactivado", Toast.LENGTH_SHORT).show();
        });
        refresh();
    }

    private void refresh() {
        List<String> items = MediaHelper.load(prefs);
        countView.setText(items.size() + (items.size() == 1 ? " ESTADO LISTO" : " ESTADOS LISTOS"));
        timeView.setText(String.format(Locale.getDefault(), "%02d:%02d", prefs.getInt("schedule_hour",10), prefs.getInt("schedule_minute",0)));
        previewRow.removeAllViews();
        if (items.isEmpty()) {
            TextView t = label("No hay contenido. Agregá fotos o videos.", 15, Color.WHITE, false);
            previewRow.addView(t); return;
        }
        for (int i=0;i<items.size();i++) addPreview(items, i);
    }

    private void addPreview(List<String> items, int index) {
        String entry = items.get(index);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL); card.setGravity(Gravity.CENTER);
        card.setPadding(dp(6),dp(6),dp(6),dp(6)); card.setBackgroundResource(R.drawable.bg_media);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(128),dp(215)); cp.rightMargin=dp(10);
        if (MediaHelper.isVideo(this, entry)) {
            TextView v = label("▶\nVIDEO",22,Color.rgb(255,0,212),true); v.setGravity(Gravity.CENTER);
            card.addView(v,new LinearLayout.LayoutParams(-1,0,1f));
        } else {
            ImageView image = new ImageView(this); image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            try { image.setImageURI(MediaHelper.resolve(this,entry)); } catch(Exception e) { image.setImageResource(R.drawable.ic_launcher); }
            card.addView(image,new LinearLayout.LayoutParams(-1,0,1f));
        }
        TextView remove = label("×  QUITAR",12,Color.WHITE,true); remove.setGravity(Gravity.CENTER);
        remove.setOnClickListener(v -> { List<String> cur=MediaHelper.load(prefs); if(index<cur.size()){cur.remove(index); MediaHelper.save(prefs,cur); refresh();}});
        card.addView(remove,new LinearLayout.LayoutParams(-1,dp(38))); previewRow.addView(card,cp);
    }

    private void pickTime() {
        new TimePickerDialog(this,(v,h,m)->{
            prefs.edit().putInt("schedule_hour",h).putInt("schedule_minute",m).apply();
            if(prefs.getBoolean("schedule_enabled",false)) AlarmReceiver.scheduleNext(this);
            refresh(); Toast.makeText(this,"Horario guardado",Toast.LENGTH_SHORT).show();
        },prefs.getInt("schedule_hour",10),prefs.getInt("schedule_minute",0),true).show();
    }

    private void pickMedia(boolean replace) {
        replaceWhenPicking=replace;
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*"}); i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        startActivityForResult(i,PICK_MEDIA);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=PICK_MEDIA||resultCode!=RESULT_OK||data==null)return;
        ArrayList<String> picked=new ArrayList<>();
        if(data.getClipData()!=null){
            ClipData c=data.getClipData(); for(int i=0;i<c.getItemCount();i++) addPicked(picked,c.getItemAt(i).getUri(),data.getFlags());
        } else if(data.getData()!=null) addPicked(picked,data.getData(),data.getFlags());
        if(picked.isEmpty())return;
        List<String> current=replaceWhenPicking?new ArrayList<>():MediaHelper.load(prefs); current.addAll(picked); MediaHelper.save(prefs,current);
        refresh(); Toast.makeText(this,picked.size()+" archivo(s) agregado(s)",Toast.LENGTH_SHORT).show();
    }

    private void addPicked(List<String> out, Uri uri, int flags){
        try{getContentResolver().takePersistableUriPermission(uri,flags & Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
        out.add(uri.toString());
    }

    private void requestNotifications(){
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFICATIONS);
    }

    private void createChannel(){
        if(Build.VERSION.SDK_INT>=26){ NotificationManager nm=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
            if(nm!=null){NotificationChannel c=new NotificationChannel(AlarmReceiver.CHANNEL_ID,"Estados diarios",NotificationManager.IMPORTANCE_HIGH); c.setDescription("Aviso para publicar tus estados diarios"); nm.createNotificationChannel(c);}}
    }

    private TextView label(String s,float size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
