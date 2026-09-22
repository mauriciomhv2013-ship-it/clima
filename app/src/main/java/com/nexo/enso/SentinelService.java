package com.nexo.enso;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SentinelService extends Service {
    public static final String ACTION_START="com.nexo.enso.START_SENTINEL";
    public static final String ACTION_STOP="com.nexo.enso.STOP_SENTINEL";
    private static final String CH_STATUS="nexo_status", CH_ALERTS="nexo_alerts";
    private static final int STATUS_ID=1101;
    private static final long INTERVAL_MS=5*60*1000L;
    private final AtomicBoolean running=new AtomicBoolean(false);
    private Thread worker;
    private boolean explicitStop=false;

    @Override public void onCreate(){ super.onCreate(); createChannels(); }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null && ACTION_STOP.equals(intent.getAction())){
            explicitStop=true; stopLoop();
            getSharedPreferences("nexo_native",MODE_PRIVATE).edit().putBoolean("sentinel_enabled",false).apply();
            stopForeground(true); stopSelf(); return START_NOT_STICKY;
        }
        explicitStop=false;
        getSharedPreferences("nexo_native",MODE_PRIVATE).edit().putBoolean("sentinel_enabled",true).apply();
        startForegroundCompat(statusNotification("Centinela activo • revisando clima y alertas"));
        startLoop();
        return START_STICKY;
    }

    private void startForegroundCompat(Notification n){
        if(Build.VERSION.SDK_INT>=34) startForeground(STATUS_ID,n,0x40000000);
        else startForeground(STATUS_ID,n);
    }

    private void startLoop(){
        if(running.getAndSet(true)) return;
        worker=new Thread(() -> {
            while(running.get()){
                PowerManager.WakeLock wl=null;
                try{
                    PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
                    wl=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"NEXO:SentinelCheck"); wl.acquire(60000);
                    checkWeather(); checkSmn();
                }catch(Exception ignored){}finally{ if(wl!=null && wl.isHeld()) wl.release(); }
                try{Thread.sleep(INTERVAL_MS);}catch(InterruptedException e){break;}
            }
        },"NEXO-Sentinel");
        worker.start();
    }

    private void stopLoop(){ running.set(false); if(worker!=null) worker.interrupt(); }

    private void checkWeather() throws Exception{
        SharedPreferences sp=getSharedPreferences("nexo_native",MODE_PRIVATE);
        double lat=parse(sp.getString("lat","-34.7609"),-34.7609), lon=parse(sp.getString("lon","-58.4060"),-58.4060);
        String name=sp.getString("name","tu zona"); if(name==null||name.trim().isEmpty()) name="tu zona";
        String url="https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon+
            "&hourly=precipitation_probability,precipitation,weather_code,wind_gusts_10m,cape&timezone=auto&forecast_days=2";
        JSONObject j=new JSONObject(get(url)); JSONObject h=j.getJSONObject("hourly");
        JSONArray times=h.getJSONArray("time"), pop=h.getJSONArray("precipitation_probability"), rain=h.getJSONArray("precipitation"),
            codes=h.getJSONArray("weather_code"), gust=h.getJSONArray("wind_gusts_10m"), cape=h.getJSONArray("cape");
        int idx=0; long now=System.currentTimeMillis();
        for(int i=0;i<times.length();i++){
            try{ long t=java.time.LocalDateTime.parse(times.getString(i)).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(); if(t>=now){idx=i;break;} }
            catch(Throwable e){ idx=Math.max(0,i); }
        }
        double maxPop=0,sumRain=0,maxRain=0,maxGust=0,maxCape=0,maxCode=0;
        for(int i=idx;i<Math.min(idx+12,times.length());i++){
            double p=num(pop,i),r=num(rain,i),g=num(gust,i),c=num(cape,i),w=num(codes,i);
            maxPop=Math.max(maxPop,p);sumRain+=r;maxRain=Math.max(maxRain,r);maxGust=Math.max(maxGust,g);maxCape=Math.max(maxCape,c);maxCode=Math.max(maxCode,w);
        }
        double storm=clamp(maxPop*.35+Math.min(maxCape,2500)/2500*45+(maxCode>=95?35:0));
        double rainR=clamp(maxPop*.45+Math.min(sumRain,50)/50*55), wind=clamp((maxGust-25)/75*100),
            hail=clamp((maxCape-700)/1800*60+(maxCode>=96?45:0)+(maxRain>12?10:0)), flood=clamp(sumRain/45*65+maxRain/18*35),
            lightning=clamp(maxCape/2200*60+(maxCode>=95?50:0));
        int overall=(int)Math.round(clamp(Math.max(Math.max(Math.max(storm,rainR),Math.max(wind,hail)),Math.max(flood,lightning))*.72+(storm+rainR+wind)/3*.28));
        String key="weather:"+(overall/10)+":"+(int)(maxGust/10)+":"+(int)(sumRain/5)+":"+(int)maxCode;
        if(overall>=60 && shouldNotify(key,90*60*1000L)){
            String body="Riesgo NEXO "+overall+"/100 • lluvia "+Math.round(maxPop)+"% • "+String.format(Locale.US,"%.1f",sumRain)+" mm/12 h • ráfagas "+Math.round(maxGust)+" km/h";
            sendAlert("⚠️ NEXO • Atención en "+name,body,key);
        }
        updateStatus("Centinela activo • último control "+new java.text.SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()));
    }

    private void checkSmn(){
        try{
            SharedPreferences sp=getSharedPreferences("nexo_native",MODE_PRIVATE); String name=sp.getString("name","");
            String raw=get("https://ws.smn.gob.ar/alerts/type/AL")+"\n"+get("https://ws.smn.gob.ar/alerts/type/AC");
            String low=raw.toLowerCase(Locale.ROOT); String needle=name==null?"":name.toLowerCase(Locale.ROOT).trim();
            boolean specific=!needle.isEmpty() && needle.length()>3 && !needle.equals("mi ubicación") && !needle.equals("mi ubicacion") && low.contains(needle);
            boolean defaultArea=(needle.contains("lomas")||needle.contains("banfield")||needle.contains("amba")||needle.contains("buenos aires")) && (low.contains("buenos aires")||low.contains("amba"));
            boolean likely=specific||defaultArea;
            if(likely){
                String hash=sha256(raw.substring(0,Math.min(raw.length(),12000))); String key="smn:"+hash;
                if(shouldNotify(key,3*60*60*1000L)) sendAlert("🚨 Información oficial SMN para revisar","NEXO detectó una actualización del feed oficial que puede corresponder a tu zona. Abrí NEXO y verificá Alertas SMN.",key);
            }
        }catch(Exception ignored){}
    }

    private boolean shouldNotify(String key,long cooldown){
        SharedPreferences sp=getSharedPreferences("nexo_native",MODE_PRIVATE); long now=System.currentTimeMillis();
        String prev=sp.getString("last_alert_key",""); long t=sp.getLong("last_alert_time",0);
        if(key.equals(prev)&&now-t<cooldown)return false;
        sp.edit().putString("last_alert_key",key).putLong("last_alert_time",now).apply(); return true;
    }

    private void sendAlert(String title,String body,String key){
        Intent open=new Intent(this,MainActivity.class); open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(this,2,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,CH_ALERTS).setSmallIcon(com.nexo.enso.R.drawable.ic_stat_nexo)
            .setContentTitle(title).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true).setContentIntent(pi).setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC).build();
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(2000+Math.abs(key.hashCode()%7000),n);
    }

    private Notification statusNotification(String text){
        Intent open=new Intent(this,MainActivity.class); PendingIntent pi=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent stop=new Intent(this,SentinelService.class).setAction(ACTION_STOP); PendingIntent ps=PendingIntent.getService(this,3,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CH_STATUS).setSmallIcon(com.nexo.enso.R.drawable.ic_stat_nexo).setContentTitle("NEXO ENSO")
            .setContentText(text).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(pi).addAction(0,"Detener",ps).setVisibility(Notification.VISIBILITY_PUBLIC).build();
    }
    private void updateStatus(String s){ ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(STATUS_ID,statusNotification(s)); }

    private void createChannels(){ if(Build.VERSION.SDK_INT>=26){
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel status=new NotificationChannel(CH_STATUS,"Centinela NEXO",NotificationManager.IMPORTANCE_LOW); status.setDescription("Mantiene activo el monitoreo solicitado por el usuario"); status.setShowBadge(false); nm.createNotificationChannel(status);
        NotificationChannel alerts=new NotificationChannel(CH_ALERTS,"Alertas meteorológicas NEXO",NotificationManager.IMPORTANCE_HIGH); alerts.setDescription("Alertas meteorológicas y avisos para revisar información oficial"); alerts.enableVibration(true); alerts.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC); nm.createNotificationChannel(alerts);
    }}

    private static String get(String u)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection(); c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestProperty("User-Agent","NEXO-ENSO-Android/2.1");
        try(InputStream in=c.getInputStream()){ ByteArrayOutputStream b=new ByteArrayOutputStream(); byte[] x=new byte[8192]; for(int n;(n=in.read(x))>0;)b.write(x,0,n); return new String(b.toByteArray(),StandardCharsets.UTF_8); }
        finally{c.disconnect();}
    }
    private static double num(JSONArray a,int i){try{return a.isNull(i)?0:a.getDouble(i);}catch(Exception e){return 0;}}
    private static double clamp(double x){return Math.max(0,Math.min(100,x));}
    private static double parse(String s,double d){try{return Double.parseDouble(s);}catch(Exception e){return d;}}
    private static String sha256(String s)throws Exception{byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));StringBuilder x=new StringBuilder();for(byte v:b)x.append(String.format("%02x",v));return x.toString().substring(0,16);}

    @Override public void onDestroy(){ stopLoop(); if(!explicitStop && getSharedPreferences("nexo_native",MODE_PRIVATE).getBoolean("sentinel_enabled",true)){ try{ Intent i=new Intent(this,SentinelService.class).setAction(ACTION_START); if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i); }catch(Exception ignored){} } super.onDestroy(); }
    @Override public android.os.IBinder onBind(Intent i){return null;}
}
