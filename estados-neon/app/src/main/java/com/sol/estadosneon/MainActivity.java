package com.sol.estadosneon;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends Activity {
    public static final String PREFS = "estados_neon_prefs";
    public static final String KEY_ENABLED = "daily_enabled";
    private static final String KEY_HOUR = "hour";
    private static final String KEY_MINUTE = "minute";
    private static final int PICK_MEDIA_BASE = 3000;
    private static final int SLOT_COUNT = 7;

    private final String[] defaultAssets = {"default_1.jpg","default_2.jpg","default_3.jpg","default_4.jpg","default_5.jpg","default_6.jpg","default_7.mp4"};
    private final String[] defaultMimes = {"image/jpeg","image/jpeg","image/jpeg","image/jpeg","image/jpeg","image/jpeg","video/mp4"};

    private SharedPreferences prefs;
    private LinearLayout mediaList;
    private TextView timeText;
    private Switch dailySwitch;
    private int selectedHour;
    private int selectedMinute;
    private int pendingSlot = -1;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        selectedHour = prefs.getInt(KEY_HOUR, 10);
        selectedMinute = prefs.getInt(KEY_MINUTE, 0);
        ensureDefaultMedia();
        AlarmReceiver.createChannel(this);
        requestNotificationPermissionIfNeeded();
        buildUi();
        if (getIntent().getBooleanExtra("from_notification", false))
            Toast.makeText(this, "Tus 7 estados están listos para publicar ✨", Toast.LENGTH_LONG).show();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundResource(R.drawable.app_background);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(32));
        scroll.addView(root);

        root.addView(text("●  ESTADOS NEON", 13, Color.rgb(0,245,255), true));
        TextView title = text("Tu vidriera diaria,\nlista para brillar ✨", 30, Color.WHITE, true);
        title.setPadding(0,dp(8),0,0); root.addView(title);
        TextView subtitle = text("6 imágenes + 1 video · todos los días · horario a elección", 15, Color.rgb(205,197,220), false);
        subtitle.setPadding(0,dp(8),0,dp(18)); root.addView(subtitle);

        LinearLayout scheduleCard = card();
        root.addView(scheduleCard, new LinearLayout.LayoutParams(-1,-2));
        scheduleCard.addView(text("⏰ HORARIO DIARIO", 14, Color.rgb(255,43,214), true));
        timeText = text(formatTime(selectedHour,selectedMinute),34,Color.WHITE,true);
        timeText.setPadding(0,dp(4),0,dp(8)); scheduleCard.addView(timeText);
        Button chooseTime = neonButton("CAMBIAR HORARIO", new int[]{0xFF00F5FF,0xFF8A2BE2});
        chooseTime.setOnClickListener(v -> showTimePicker());
        scheduleCard.addView(chooseTime, new LinearLayout.LayoutParams(-1,dp(52)));
        dailySwitch = new Switch(this);
        dailySwitch.setText("  Avisarme todos los días"); dailySwitch.setTextColor(Color.WHITE); dailySwitch.setTextSize(16);
        dailySwitch.setChecked(prefs.getBoolean(KEY_ENABLED,false)); dailySwitch.setPadding(0,dp(12),0,0);
        dailySwitch.setOnCheckedChangeListener((buttonView,isChecked) -> {
            prefs.edit().putBoolean(KEY_ENABLED,isChecked).apply();
            if (isChecked) {
                scheduleNextAlarm(this); requestNotificationPermissionIfNeeded();
                Toast.makeText(this,"Aviso diario activado a las "+formatTime(selectedHour,selectedMinute),Toast.LENGTH_SHORT).show();
            } else { cancelAlarm(this); Toast.makeText(this,"Aviso diario desactivado",Toast.LENGTH_SHORT).show(); }
        });
        scheduleCard.addView(dailySwitch);

        Button publish = neonButton("⚡ PUBLICAR LOS 7 AHORA", new int[]{0xFFFF2BD6,0xFF7C3CFF,0xFF00E5FF});
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1,dp(64)); pp.setMargins(0,dp(18),0,dp(10));
        root.addView(publish,pp); publish.setOnClickListener(v -> shareAllToWhatsAppBusiness());
        TextView help = text("Se abre WhatsApp Business con todo preparado. Elegís “Mi estado” y confirmás publicar.",13,Color.rgb(205,197,220),false);
        help.setGravity(Gravity.CENTER); help.setPadding(dp(8),0,dp(8),dp(22)); root.addView(help);

        LinearLayout header = new LinearLayout(this); header.setOrientation(LinearLayout.HORIZONTAL); header.setGravity(Gravity.CENTER_VERTICAL); root.addView(header);
        header.addView(text("CONTENIDO CARGADO",16,Color.WHITE,true), new LinearLayout.LayoutParams(0,-2,1f));
        Button reset = compactButton("Restaurar"); reset.setOnClickListener(v -> resetAllDefaults()); header.addView(reset);
        mediaList = new LinearLayout(this); mediaList.setOrientation(LinearLayout.VERTICAL); mediaList.setPadding(0,dp(10),0,0); root.addView(mediaList);
        refreshMediaList();
        TextView footer = text("Todo queda guardado en tu teléfono. Podés reemplazar cualquier imagen o video sin reinstalar la APK.",12,Color.rgb(158,149,178),false);
        footer.setGravity(Gravity.CENTER); footer.setPadding(dp(10),dp(20),dp(10),0); root.addView(footer);
        setContentView(scroll);
    }

    private void refreshMediaList() {
        mediaList.removeAllViews();
        for (int i=0;i<SLOT_COUNT;i++) {
            final int slot=i; LinearLayout row=card();
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2); rp.setMargins(0,0,0,dp(10)); mediaList.addView(row,rp);
            LinearLayout inner=new LinearLayout(this); inner.setOrientation(LinearLayout.HORIZONTAL); inner.setGravity(Gravity.CENTER_VERTICAL); row.addView(inner);
            ImageView preview=new ImageView(this); preview.setScaleType(ImageView.ScaleType.CENTER_CROP); preview.setBackground(makeRounded(0xFF12071F,0xFF7C3CFF,14,1));
            File f=getSlotFile(i); String mime=getSlotMime(i); Bitmap bm=loadPreview(f,mime); if(bm!=null) preview.setImageBitmap(bm);
            LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(82),dp(118)); ip.setMargins(0,0,dp(14),0); inner.addView(preview,ip);
            LinearLayout info=new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL); inner.addView(info,new LinearLayout.LayoutParams(0,-2,1f));
            boolean video=mime!=null&&mime.startsWith("video/"); info.addView(text("Estado "+(i+1),18,Color.WHITE,true));
            TextView type=text(video?"VIDEO  ▶":"IMAGEN  ◇",12,video?Color.rgb(255,43,214):Color.rgb(0,245,255),true); type.setPadding(0,dp(3),0,dp(10)); info.addView(type);
            Button change=compactButton("Cambiar"); change.setOnClickListener(v -> pickMedia(slot)); info.addView(change);
        }
    }

    private void showTimePicker() {
        new TimePickerDialog(this,(view,hour,minute)->{
            selectedHour=hour; selectedMinute=minute;
            prefs.edit().putInt(KEY_HOUR,hour).putInt(KEY_MINUTE,minute).apply();
            timeText.setText(formatTime(hour,minute)); if(prefs.getBoolean(KEY_ENABLED,false)) scheduleNextAlarm(this);
        },selectedHour,selectedMinute,true).show();
    }

    private void pickMedia(int slot) {
        pendingSlot=slot; Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*"); intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*"});
        startActivityForResult(intent,PICK_MEDIA_BASE+slot);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        int slot=requestCode-PICK_MEDIA_BASE; if(slot<0||slot>=SLOT_COUNT)slot=pendingSlot; if(slot<0||slot>=SLOT_COUNT)return;
        Uri uri=data.getData(); String mime=getContentResolver().getType(uri);
        if(mime==null||(!mime.startsWith("image/")&&!mime.startsWith("video/"))){Toast.makeText(this,"Elegí una imagen o un video",Toast.LENGTH_SHORT).show();return;}
        try{replaceSlotFromUri(slot,uri,mime);refreshMediaList();Toast.makeText(this,"Estado "+(slot+1)+" actualizado ✨",Toast.LENGTH_SHORT).show();}
        catch(IOException e){Toast.makeText(this,"No pude copiar ese archivo",Toast.LENGTH_LONG).show();}
    }

    private void replaceSlotFromUri(int slot,Uri uri,String mime)throws IOException{
        deleteSlotFiles(slot); File out=new File(mediaDir(),"slot_"+(slot+1)+(mime.startsWith("video/")?".mp4":".jpg"));
        try(InputStream in=getContentResolver().openInputStream(uri);FileOutputStream fos=new FileOutputStream(out)){
            if(in==null)throw new IOException("No se pudo abrir"); byte[] buf=new byte[8192]; int n; while((n=in.read(buf))>0)fos.write(buf,0,n);
        }
        prefs.edit().putString("slot_"+slot+"_name",out.getName()).putString("slot_"+slot+"_mime",mime).apply();
    }

    private void resetAllDefaults(){
        for(int i=0;i<SLOT_COUNT;i++){deleteSlotFiles(i);prefs.edit().remove("slot_"+i+"_name").remove("slot_"+i+"_mime").apply();}
        ensureDefaultMedia();refreshMediaList();Toast.makeText(this,"Contenido original restaurado",Toast.LENGTH_SHORT).show();
    }

    private void ensureDefaultMedia(){
        File dir=mediaDir();
        for(int i=0;i<SLOT_COUNT;i++){
            String saved=prefs.getString("slot_"+i+"_name",null);if(saved!=null&&new File(dir,saved).exists())continue;
            File dest=new File(dir,"slot_"+(i+1)+(defaultAssets[i].endsWith(".mp4")?".mp4":".jpg"));
            try(InputStream in=getAssets().open(defaultAssets[i]);FileOutputStream out=new FileOutputStream(dest)){
                byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)out.write(buf,0,n);
                prefs.edit().putString("slot_"+i+"_name",dest.getName()).putString("slot_"+i+"_mime",defaultMimes[i]).apply();
            }catch(IOException ignored){}
        }
    }

    private File mediaDir(){File dir=new File(getFilesDir(),"media");if(!dir.exists())dir.mkdirs();return dir;}
    private File getSlotFile(int slot){String name=prefs.getString("slot_"+slot+"_name",null);return name==null?null:new File(mediaDir(),name);}
    private String getSlotMime(int slot){return prefs.getString("slot_"+slot+"_mime",defaultMimes[slot]);}
    private void deleteSlotFiles(int slot){File[] files=mediaDir().listFiles();if(files==null)return;String prefix="slot_"+(slot+1)+".";for(File f:files)if(f.getName().startsWith(prefix))f.delete();}

    private Bitmap loadPreview(File file,String mime){
        if(file==null||!file.exists())return null;
        try{
            if(mime!=null&&mime.startsWith("video/")){MediaMetadataRetriever r=new MediaMetadataRetriever();r.setDataSource(file.getAbsolutePath());Bitmap b=r.getFrameAtTime(1000000,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);r.release();return b;}
            BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=4;return BitmapFactory.decodeFile(file.getAbsolutePath(),o);
        }catch(Exception e){return null;}
    }

    private void shareAllToWhatsAppBusiness(){
        ArrayList<Uri> uris=new ArrayList<>();
        for(int i=0;i<SLOT_COUNT;i++){File f=getSlotFile(i);if(f!=null&&f.exists())uris.add(FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f));}
        if(uris.isEmpty()){Toast.makeText(this,"No hay contenido para publicar",Toast.LENGTH_SHORT).show();return;}
        Intent share=new Intent(Intent.ACTION_SEND_MULTIPLE);share.setType("*/*");share.putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris);share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ClipData clip=ClipData.newUri(getContentResolver(),"estado_1",uris.get(0));for(int i=1;i<uris.size();i++)clip.addItem(new ClipData.Item(uris.get(i)));share.setClipData(clip);
        if(isInstalled("com.whatsapp.w4b")){share.setPackage("com.whatsapp.w4b");try{startActivity(share);return;}catch(ActivityNotFoundException ignored){}}
        if(isInstalled("com.whatsapp")){share.setPackage("com.whatsapp");try{startActivity(share);Toast.makeText(this,"WhatsApp Business no está instalado; abrí WhatsApp normal.",Toast.LENGTH_LONG).show();return;}catch(ActivityNotFoundException ignored){}}
        share.setPackage(null);try{startActivity(Intent.createChooser(share,"Publicar estados"));}catch(ActivityNotFoundException e){Toast.makeText(this,"No encontré una app compatible para compartir",Toast.LENGTH_LONG).show();}
    }

    private boolean isInstalled(String packageName){try{getPackageManager().getPackageInfo(packageName,0);return true;}catch(PackageManager.NameNotFoundException e){return false;}}

    public static void scheduleNextAlarm(Context context){
        SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);if(!p.getBoolean(KEY_ENABLED,false))return;
        int hour=p.getInt(KEY_HOUR,10),minute=p.getInt(KEY_MINUTE,0);Calendar now=Calendar.getInstance(),next=Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY,hour);next.set(Calendar.MINUTE,minute);next.set(Calendar.SECOND,0);next.set(Calendar.MILLISECOND,0);if(!next.after(now))next.add(Calendar.DAY_OF_YEAR,1);
        AlarmManager am=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);if(am==null)return;PendingIntent pi=alarmPendingIntent(context);am.cancel(pi);am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.getTimeInMillis(),pi);
    }

    public static void cancelAlarm(Context context){AlarmManager am=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);if(am!=null)am.cancel(alarmPendingIntent(context));}
    private static PendingIntent alarmPendingIntent(Context context){return PendingIntent.getBroadcast(context,991,new Intent(context,AlarmReceiver.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private void requestNotificationPermissionIfNeeded(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},42);}
    private String formatTime(int hour,int minute){return String.format(Locale.getDefault(),"%02d:%02d",hour,minute);}

    private LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(16),dp(16),dp(16),dp(16));l.setBackground(makeRounded(0xDD10091B,0xFF7028E4,20,1));l.setElevation(dp(6));return l;}
    private Button neonButton(String label,int[] colors){Button b=new Button(this);b.setText(label);b.setTextColor(Color.WHITE);b.setTextSize(16);b.setAllCaps(false);b.setTypeface(Typeface.DEFAULT_BOLD);b.setGravity(Gravity.CENTER);GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,colors);g.setCornerRadius(dp(18));b.setBackground(g);b.setElevation(dp(8));return b;}
    private Button compactButton(String label){Button b=new Button(this);b.setText(label);b.setTextColor(Color.WHITE);b.setTextSize(13);b.setAllCaps(false);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(14),dp(8),dp(14),dp(8));b.setBackground(makeRounded(0xFF1C1230,0xFF00F5FF,14,1));return b;}
    private TextView text(String v,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(v);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private GradientDrawable makeRounded(int fill,int stroke,int radius,int sw){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));g.setStroke(dp(sw),stroke);return g;}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
}
