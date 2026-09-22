package com.sol.neoncontrol;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(6,7,18), CARD = Color.rgb(16,20,42), CARD2 = Color.rgb(12,16,34);
    private static final int WHITE = Color.rgb(247,248,255), MUTED = Color.rgb(167,178,205);
    private static final int CYAN = Color.rgb(71,244,255), PINK = Color.rgb(255,59,245), PURPLE = Color.rgb(138,92,255);
    private static final int GREEN = Color.rgb(51,255,155), YELLOW = Color.rgb(255,211,78), RED = Color.rgb(255,90,117);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private SharedPreferences prefs;
    private ClientDb db;
    private FrameLayout content;
    private TextView groupTop;
    private String page = "home";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences("neon_prefs", MODE_PRIVATE);
        db = new ClientDb(this);
        setDefaults(); requestNotifications();
        if (prefs.getString("group_code", "").isEmpty()) showOnboarding(); else buildMain();
    }

    @Override protected void onResume() {
        super.onResume();
        ReminderReceiver.schedule(this);
        if (content != null && !prefs.getString("group_code", "").isEmpty()) showPage(page);
    }

    private void setDefaults() {
        if (!prefs.contains("defaults_set")) prefs.edit()
                .putBoolean("defaults_set", true).putBoolean("reminders", true)
                .putInt("reminder_hour", 10).putInt("reminder_minute", 0)
                .putInt("font_mode", 1)
                .putString("msg_3", "Hola {nombre} 👋 Tu servicio de {servicio} vence en {dias} días ({fecha}). ¿Querés renovarlo por otros 30 días?")
                .putString("msg_1", "Hola {nombre} 👋 Tu servicio de {servicio} vence mañana ({fecha}). ¿Querés renovarlo por otros 30 días?")
                .putString("msg_0", "Hola {nombre} 👋 Tu servicio de {servicio} vence hoy. ¿Querés renovarlo por otros 30 días?")
                .putString("msg_exp", "Hola {nombre} 👋 Tu servicio de {servicio} está vencido. Si querés renovarlo, avisame y lo activamos por otros 30 días.")
                .apply();
    }

    private void showOnboarding() {
        LinearLayout root = vertical(24); root.setGravity(Gravity.CENTER_HORIZONTAL); root.setBackgroundColor(BG);
        ScrollView sv = new ScrollView(this); sv.setFillViewport(true); sv.addView(root); setContentView(sv);
        space(root, 55);
        TextView logo = text("NC", 34, BG, true); logo.setGravity(Gravity.CENTER); logo.setBackground(neon(CYAN, 22, 0));
        root.addView(logo, lp(94,94));
        TextView title = text("NEON CONTROL", 30, WHITE, true); title.setGravity(Gravity.CENTER); margin(root,title,0,25,0,4,-1,-2);
        TextView sub = text("Clientes · Vencimientos · Cobros · WhatsApp", 17, MUTED, false); sub.setGravity(Gravity.CENTER); margin(root,sub,12,0,12,30,-1,-2);
        TextView help = text("Una agenda simple para no olvidarte de ningún cliente.\nCada grupo mantiene sus propios datos.", 18, WHITE, false); help.setGravity(Gravity.CENTER); help.setLineSpacing(dp(5),1f); margin(root,help,8,0,8,28,-1,-2);
        Button create = bigButton("✨ CREAR MI GRUPO", CYAN); create.setOnClickListener(v -> createGroupDialog()); margin(root,create,0,0,0,14,-1,62);
        Button join = outlineButton("ENTRAR CON UN CÓDIGO"); join.setOnClickListener(v -> joinGroupDialog()); margin(root,join,0,0,0,20,-1,60);
        TextView note = text("El código identifica qué teléfonos comparten la misma agenda. La conexión entre teléfonos quedará activa al conectar la base en la nube.", 15, MUTED, false);
        note.setGravity(Gravity.CENTER); margin(root,note,8,12,8,20,-1,-2);
    }

    private void createGroupDialog() {
        EditText name = field("Nombre del grupo (ej. Delidash Streaming)", false); name.setText("Mi negocio");
        new AlertDialog.Builder(this).setTitle("Crear grupo").setView(wrapDialog(name))
                .setPositiveButton("CREAR", (d,w) -> {
                    String code = "NEON-" + (100000 + new Random().nextInt(900000));
                    prefs.edit().putString("group_code",code).putString("group_name",clean(name.getText().toString(),"Mi negocio")).apply();
                    buildMain(); toast("Grupo creado: " + code);
                }).setNegativeButton("Cancelar",null).show();
    }

    private void joinGroupDialog() {
        LinearLayout box = vertical(14); EditText code = field("Código del grupo", false); EditText name = field("Nombre para mostrar (opcional)", false);
        box.addView(code); box.addView(name);
        new AlertDialog.Builder(this).setTitle("Entrar con código").setView(wrapDialog(box))
                .setPositiveButton("ENTRAR", (d,w) -> {
                    String c = code.getText().toString().trim().toUpperCase(Locale.ROOT);
                    if (c.length() < 5) { toast("El código es demasiado corto"); return; }
                    prefs.edit().putString("group_code",c).putString("group_name",clean(name.getText().toString(),"Grupo " + c)).apply();
                    buildMain();
                }).setNegativeButton("Cancelar",null).show();
    }

    private void buildMain() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(dp(16),dp(10),dp(10),dp(8));
        LinearLayout titles = vertical(0); TextView brand = text("NEON CONTROL", 21, WHITE, true); groupTop = text(prefs.getString("group_name","Mi grupo"), 14, CYAN, true);
        titles.addView(brand); titles.addView(groupTop); top.addView(titles,new LinearLayout.LayoutParams(0,-2,1f));
        Button zoom = smallButton(fontLabel(), PINK); zoom.setOnClickListener(v -> cycleFont()); top.addView(zoom, lp(62,48));
        root.addView(top, new LinearLayout.LayoutParams(-1,-2));
        content = new FrameLayout(this); root.addView(content,new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout nav = new LinearLayout(this); nav.setGravity(Gravity.CENTER); nav.setPadding(dp(6),dp(5),dp(6),dp(7)); nav.setBackgroundColor(Color.rgb(9,11,25));
        addNav(nav,"⌂\nInicio","home"); addNav(nav,"☷\nClientes","clients"); addNav(nav,"$\nCobrar","collect"); addNav(nav,"⚙\nAjustes","settings");
        root.addView(nav,new LinearLayout.LayoutParams(-1,dp(72))); setContentView(root);
        if (getIntent().getBooleanExtra("open_collect",false)) { page="collect"; getIntent().removeExtra("open_collect"); }
        showPage(page); ReminderReceiver.schedule(this);
    }

    private void addNav(LinearLayout nav, String label, String target) {
        Button b = new Button(this); b.setAllCaps(false); b.setText(label); b.setTextColor(target.equals(page)?CYAN:MUTED); b.setTextSize(ts(13)); b.setGravity(Gravity.CENTER); b.setBackgroundColor(Color.TRANSPARENT);
        b.setOnClickListener(v -> { page=target; showPage(target); buildMain(); }); nav.addView(b,new LinearLayout.LayoutParams(0,-1,1f));
    }

    private void showPage(String p) {
        if (content == null) return; content.removeAllViews();
        View v; if ("clients".equals(p)) v=clientsPage(); else if ("collect".equals(p)) v=collectPage(); else if ("settings".equals(p)) v=settingsPage(); else v=homePage();
        content.addView(v,new FrameLayout.LayoutParams(-1,-1));
    }

    private View homePage() {
        ScrollView sv = new ScrollView(this); LinearLayout box = vertical(14); box.setPadding(dp(14),dp(8),dp(14),dp(26)); sv.addView(box);
        ClientDb.Stats s=db.stats(group());
        TextView hello=text("Tu trabajo, de un vistazo",24,WHITE,true); box.addView(hello);
        TextView date=text("Hoy · " + LocalDate.now().format(DATE),16,MUTED,false); margin(box,date,0,1,0,12,-1,-2);
        LinearLayout r1=row(); r1.addView(statCard("ACTIVOS",Integer.toString(s.active),CYAN),weight()); r1.addView(statCard("VENCEN HOY",Integer.toString(s.today),YELLOW),weight()); box.addView(r1);
        LinearLayout r2=row(); r2.addView(statCard("PRÓX. 3 DÍAS",Integer.toString(s.next3),PURPLE),weight()); r2.addView(statCard("VENCIDOS",Integer.toString(s.expired),RED),weight()); margin(box,r2,0,10,0,8,-1,-2);
        Button collect=bigButton("⚡ COBRAR HOY", PINK); collect.setOnClickListener(v->{page="collect";buildMain();}); margin(box,collect,0,4,0,10,-1,64);
        Button add=bigButton("＋ AGREGAR CLIENTE", CYAN); add.setOnClickListener(v->clientDialog(null)); margin(box,add,0,0,0,18,-1,64);
        TextView t=text("PRÓXIMOS VENCIMIENTOS",16,CYAN,true); box.addView(t);
        List<ClientDb.Client> urgent=db.list(group(),"","URGENT");
        if(urgent.isEmpty()) margin(box,empty("Todo tranquilo. No hay vencimientos en los próximos 3 días."),0,10,0,0,-1,-2);
        else { int max=Math.min(5,urgent.size()); for(int i=0;i<max;i++) margin(box,clientCard(urgent.get(i),false),0,10,0,0,-1,-2); }
        return sv;
    }

    private View clientsPage() {
        LinearLayout outer=vertical(0); outer.setPadding(dp(12),dp(6),dp(12),dp(10));
        LinearLayout head=row(); TextView title=text("Clientes",25,WHITE,true); head.addView(title,new LinearLayout.LayoutParams(0,-2,1f)); Button add=smallButton("＋ NUEVO",CYAN); add.setOnClickListener(v->clientDialog(null)); head.addView(add,lp(112,52)); outer.addView(head);
        EditText search=field("Buscar por teléfono, nombre o servicio…",false); search.setTextSize(ts(18)); margin(outer,search,0,10,0,8,-1,58);
        HorizontalScrollView hsv=new HorizontalScrollView(this); LinearLayout filters=row(); String[] fs={"TODOS","ACTIVOS","VENCIDOS/PRÓX.","BAJAS"}; String[] fv={"ALL","ACTIVE","URGENT","INACTIVE"};
        FrameLayout listHost=new FrameLayout(this);
        for(int i=0;i<fs.length;i++){Button f=smallButton(fs[i],i==0?PURPLE:Color.rgb(45,50,80)); final String val=fv[i]; f.setOnClickListener(v->renderClientList(listHost,search.getText().toString(),val)); filters.addView(f,new LinearLayout.LayoutParams(-2,dp(46)));}
        hsv.addView(filters); outer.addView(hsv,new LinearLayout.LayoutParams(-1,dp(54))); outer.addView(listHost,new LinearLayout.LayoutParams(-1,0,1f));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){renderClientList(listHost,s.toString(),"ALL");} public void afterTextChanged(Editable e){}});
        renderClientList(listHost,"","ALL"); return outer;
    }

    private void renderClientList(FrameLayout host,String search,String filter){
        host.removeAllViews(); ScrollView sv=new ScrollView(this); LinearLayout list=vertical(10); list.setPadding(0,dp(4),0,dp(30)); sv.addView(list);
        List<ClientDb.Client> data=db.list(group(),search,"ALL".equals(filter)?null:filter);
        if(data.isEmpty()) list.addView(empty("No encontré clientes en esta sección.")); else for(ClientDb.Client c:data) list.addView(clientCard(c,true));
        host.addView(sv,new FrameLayout.LayoutParams(-1,-1));
    }

    private View collectPage(){
        ScrollView sv=new ScrollView(this); LinearLayout box=vertical(12); box.setPadding(dp(14),dp(8),dp(14),dp(30)); sv.addView(box);
        TextView title=text("Cobrar hoy",27,WHITE,true); box.addView(title); TextView sub=text("Clientes vencidos o que vencen dentro de 3 días. Mandá el mensaje, y cuando paguen tocá RENOVÓ.",17,MUTED,false); sub.setLineSpacing(dp(4),1f); margin(box,sub,0,2,0,12,-1,-2);
        List<ClientDb.Client> data=db.list(group(),"","URGENT");
        TextView count=text(data.size()+ (data.size()==1?" CLIENTE PARA REVISAR":" CLIENTES PARA REVISAR"),16,PINK,true); margin(box,count,0,2,0,6,-1,-2);
        if(data.isEmpty()) box.addView(empty("✓ No tenés cobros pendientes para los próximos 3 días.")); else for(ClientDb.Client c:data) box.addView(clientCard(c,true));
        return sv;
    }

    private View settingsPage(){
        ScrollView sv=new ScrollView(this); LinearLayout box=vertical(14); box.setPadding(dp(14),dp(8),dp(14),dp(30)); sv.addView(box);
        box.addView(text("Ajustes",27,WHITE,true));
        LinearLayout groupCard=card(); groupCard.addView(text("GRUPO DE TRABAJO",14,CYAN,true)); groupCard.addView(text(prefs.getString("group_name","Mi grupo"),22,WHITE,true)); groupCard.addView(text(group(),20,PINK,true));
        TextView gnote=text("Los teléfonos que usen este código pertenecerán a la misma agenda cuando activemos la sincronización en la nube.",15,MUTED,false); margin(groupCard,gnote,0,5,0,8,-1,-2);
        Button rename=outlineButton("CAMBIAR NOMBRE / CÓDIGO"); rename.setOnClickListener(v->changeGroupDialog()); groupCard.addView(rename,new LinearLayout.LayoutParams(-1,dp(54))); box.addView(groupCard);

        LinearLayout rem=card(); LinearLayout rr=row(); LinearLayout rt=vertical(0); rt.addView(text("Aviso diario",19,WHITE,true)); rt.addView(text("Te avisa si hay clientes por vencer",15,MUTED,false)); rr.addView(rt,new LinearLayout.LayoutParams(0,-2,1f)); Switch sw=new Switch(this); sw.setChecked(prefs.getBoolean("reminders",true)); rr.addView(sw); rem.addView(rr);
        Button time=outlineButton("HORARIO · "+String.format(Locale.getDefault(),"%02d:%02d",prefs.getInt("reminder_hour",10),prefs.getInt("reminder_minute",0))); time.setOnClickListener(v->pickTime()); margin(rem,time,0,12,0,0,-1,54);
        sw.setOnCheckedChangeListener((b,c)->{prefs.edit().putBoolean("reminders",c).apply(); ReminderReceiver.schedule(this);}); box.addView(rem);

        LinearLayout font=card(); font.addView(text("Tamaño de letra",19,WHITE,true)); font.addView(text("Pensado para leer cómodo durante muchas horas.",15,MUTED,false)); LinearLayout fr=row(); String[] names={"Normal","Grande","Extra"}; for(int i=0;i<3;i++){final int x=i; Button b=smallButton(names[i],prefs.getInt("font_mode",1)==i?CYAN:Color.rgb(45,50,80)); b.setOnClickListener(v->{prefs.edit().putInt("font_mode",x).apply(); buildMain();}); fr.addView(b,weight());} margin(font,fr,0,12,0,0,-1,54); box.addView(font);

        LinearLayout msg=card(); msg.addView(text("Mensajes de WhatsApp",19,WHITE,true)); msg.addView(text("Podés cambiar los textos que se preparan automáticamente.",15,MUTED,false)); Button edit=outlineButton("EDITAR MENSAJES"); edit.setOnClickListener(v->messagesDialog()); margin(msg,edit,0,12,0,0,-1,54); box.addView(msg);

        LinearLayout cloud=card(); cloud.addView(text("☁ Sincronización entre teléfonos",19,WHITE,true)); cloud.addView(text("La estructura por código ya está incluida. Para compartir cambios en tiempo real entre dos celulares falta conectar una base gratuita en la nube.",15,MUTED,false)); TextView status=text("● PENDIENTE DE CONECTAR NUBE",14,YELLOW,true); margin(cloud,status,0,10,0,0,-1,-2); box.addView(cloud);

        LinearLayout info=card(); info.addView(text("Regla rápida",19,WHITE,true)); info.addView(text("Cliente nuevo = 30 días por defecto. Al tocar RENOVÓ vuelve a empezar 30 días desde hoy, salvo que elijas otra cantidad.",16,MUTED,false)); box.addView(info);
        return sv;
    }

    private View clientCard(ClientDb.Client c, boolean full){
        LinearLayout card=card(); card.setPadding(dp(15),dp(14),dp(15),dp(14));
        LinearLayout top=row(); TextView service=text(c.service,16,CYAN,true); top.addView(service,new LinearLayout.LayoutParams(0,-2,1f)); TextView badge=text(dayText(c),15,dayColor(c),true); badge.setGravity(Gravity.RIGHT); top.addView(badge); card.addView(top);
        if(c.name!=null&&!c.name.trim().isEmpty()) margin(card,text(c.name,18,WHITE,true),0,6,0,0,-1,-2);
        TextView phone=text(c.phone,21,WHITE,true); phone.setLetterSpacing(.03f); margin(card,phone,0,3,0,5,-1,-2);
        TextView dates=text("Vence: "+LocalDate.parse(c.expiryDate).format(DATE)+"   ·   "+c.days+" días",16,MUTED,false); card.addView(dates);
        if("INACTIVE".equals(c.status)) margin(card,text("BAJA / NO RENOVÓ",14,MUTED,true),0,6,0,0,-1,-2);
        if(full){LinearLayout actions=row(); Button wa=smallButton("WHATSAPP",GREEN); wa.setOnClickListener(v->openWhatsApp(c)); actions.addView(wa,weight()); Button renew=smallButton("RENOVÓ",CYAN); renew.setOnClickListener(v->renewDialog(c)); actions.addView(renew,weight()); Button edit=smallButton("EDITAR",PURPLE); edit.setOnClickListener(v->clientDialog(c)); actions.addView(edit,weight()); margin(card,actions,0,13,0,0,-1,54);} else card.setOnClickListener(v->clientDialog(c));
        return card;
    }

    private void clientDialog(ClientDb.Client existing){
        boolean edit=existing!=null; LinearLayout box=vertical(10); box.setPadding(dp(4),0,dp(4),0);
        EditText phone=field("Teléfono",false), name=field("Nombre (opcional)",false), service=field("Servicio: Netflix, HBO, Disney…",false), days=field("Días",true), price=field("Precio (opcional)",true), notes=field("Notas (opcional)",false);
        phone.setTextSize(ts(20)); days.setText("30");
        if(edit){phone.setText(existing.phone);name.setText(existing.name);service.setText(existing.service);days.setText(Integer.toString(existing.days));if(existing.price>0)price.setText(String.format(Locale.US,"%.0f",existing.price));notes.setText(existing.notes);}
        box.addView(phone); box.addView(name); box.addView(service);
        LinearLayout chips=row(); String[] popular={"Netflix","HBO Max","Disney+","Prime Video","Paramount+"}; for(String s:popular){Button ch=smallButton(s,Color.rgb(42,46,75));ch.setOnClickListener(v->service.setText(s));chips.addView(ch,new LinearLayout.LayoutParams(-2,dp(45)));} HorizontalScrollView hs=new HorizontalScrollView(this);hs.addView(chips);box.addView(hs,new LinearLayout.LayoutParams(-1,dp(52)));
        box.addView(days); box.addView(price); box.addView(notes);
        if(!edit) box.addView(text("Se cargará automáticamente por 30 días desde hoy. Podés cambiarlo arriba.",14,CYAN,false));
        ScrollView sv=new ScrollView(this); sv.addView(box);
        AlertDialog d=new AlertDialog.Builder(this).setTitle(edit?"Editar cliente":"Nuevo cliente").setView(wrapDialog(sv)).setPositiveButton(edit?"GUARDAR":"AGREGAR",null).setNegativeButton("Cancelar",null).create();
        if(edit) d.setButton(AlertDialog.BUTTON_NEUTRAL,"MÁS",(x,w)->{});
        d.setOnShowListener(x->{d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String ph=phone.getText().toString().trim(),svv=service.getText().toString().trim();if(ph.isEmpty()||svv.isEmpty()){toast("Completá teléfono y servicio");return;}int dd=parseInt(days.getText().toString(),30);double pp=parseDouble(price.getText().toString(),0);if(edit){existing.phone=ph;existing.name=name.getText().toString();existing.service=svv;existing.days=dd;existing.price=pp;existing.notes=notes.getText().toString();existing.expiryDate=LocalDate.parse(existing.startDate).plusDays(dd).toString();db.update(existing);}else db.insert(group(),name.getText().toString(),ph,svv,dd,pp,notes.getText().toString());d.dismiss();showPage(page);}); if(edit){d.getButton(AlertDialog.BUTTON_NEUTRAL).setText("BAJA / BORRAR"); d.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(RED); d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->clientMore(existing,d));}}); d.show();
    }

    private void clientMore(ClientDb.Client c, AlertDialog parent){
        String status="ACTIVE".equals(c.status)?"MARCAR NO RENOVÓ":"REACTIVAR";
        new AlertDialog.Builder(this).setTitle(c.displayName()).setItems(new String[]{status,"Eliminar definitivamente"},(d,which)->{
            if(which==0){db.setStatus(c.id,"ACTIVE".equals(c.status)?"INACTIVE":"ACTIVE");parent.dismiss();showPage(page);}else new AlertDialog.Builder(this).setTitle("Eliminar cliente").setMessage("¿Seguro? Esta acción no se puede deshacer.").setPositiveButton("ELIMINAR",(x,w)->{db.delete(c.id);parent.dismiss();showPage(page);}).setNegativeButton("Cancelar",null).show();
        }).show();
    }

    private void renewDialog(ClientDb.Client c){
        LinearLayout box=vertical(10); EditText days=field("Días de renovación",true);days.setText("30");box.addView(text("El contador volverá a empezar desde hoy.",16,MUTED,false));box.addView(days);
        new AlertDialog.Builder(this).setTitle("Renovar · "+c.displayName()).setView(wrapDialog(box)).setPositiveButton("RENOVAR",(d,w)->{int n=parseInt(days.getText().toString(),30);db.renew(c.id,n);toast("Renovado por "+n+" días");showPage(page);}).setNegativeButton("Cancelar",null).show();
    }

    private void openWhatsApp(ClientDb.Client c){
        long left=c.daysLeft(); String key=left<0?"msg_exp":left==0?"msg_0":left==1?"msg_1":"msg_3"; String template=prefs.getString(key,"");
        String msg=template.replace("{nombre}",c.displayName()).replace("{servicio}",c.service).replace("{dias}",Long.toString(Math.max(0,left))).replace("{fecha}",LocalDate.parse(c.expiryDate).format(DATE));
        String phone=c.phone.replaceAll("[^0-9]",""); if(phone.length()<8){toast("Revisá el número. Para WhatsApp conviene guardarlo con código de país.");return;}
        Uri uri=Uri.parse("https://wa.me/"+phone+"?text="+Uri.encode(msg)); Intent i=new Intent(Intent.ACTION_VIEW,uri); i.setPackage("com.whatsapp.w4b");
        try{startActivity(i);}catch(Exception e){try{i.setPackage("com.whatsapp");startActivity(i);}catch(Exception e2){i.setPackage(null);try{startActivity(i);}catch(Exception e3){toast("No encontré WhatsApp");}}}
    }

    private void messagesDialog(){
        LinearLayout box=vertical(10); EditText m3=multiline("3 días o menos",prefs.getString("msg_3","")),m1=multiline("1 día antes",prefs.getString("msg_1","")),m0=multiline("Vence hoy",prefs.getString("msg_0","")),me=multiline("Vencido",prefs.getString("msg_exp",""));
        box.addView(text("Podés usar: {nombre} {servicio} {dias} {fecha}",14,CYAN,false));box.addView(m3);box.addView(m1);box.addView(m0);box.addView(me);ScrollView sv=new ScrollView(this);sv.addView(box);
        new AlertDialog.Builder(this).setTitle("Mensajes automáticos").setView(wrapDialog(sv)).setPositiveButton("GUARDAR",(d,w)->prefs.edit().putString("msg_3",m3.getText().toString()).putString("msg_1",m1.getText().toString()).putString("msg_0",m0.getText().toString()).putString("msg_exp",me.getText().toString()).apply()).setNegativeButton("Cancelar",null).show();
    }

    private void pickTime(){int h=prefs.getInt("reminder_hour",10),m=prefs.getInt("reminder_minute",0);new TimePickerDialog(this,(v,hh,mm)->{prefs.edit().putInt("reminder_hour",hh).putInt("reminder_minute",mm).apply();ReminderReceiver.schedule(this);showPage("settings");},h,m,true).show();}

    private void changeGroupDialog(){
        LinearLayout box=vertical(10);EditText name=field("Nombre del grupo",false),code=field("Código",false);name.setText(prefs.getString("group_name",""));code.setText(group());box.addView(name);box.addView(code);box.addView(text("Cambiar de código cambia la agenda visible en este teléfono.",14,YELLOW,false));
        new AlertDialog.Builder(this).setTitle("Grupo de trabajo").setView(wrapDialog(box)).setPositiveButton("GUARDAR",(d,w)->{String c=code.getText().toString().trim().toUpperCase(Locale.ROOT);if(c.length()<5){toast("Código inválido");return;}prefs.edit().putString("group_name",clean(name.getText().toString(),"Mi grupo")).putString("group_code",c).apply();buildMain();}).setNegativeButton("Cancelar",null).show();
    }

    private void cycleFont(){int n=(prefs.getInt("font_mode",1)+1)%3;prefs.edit().putInt("font_mode",n).apply();buildMain();}
    private String fontLabel(){int x=prefs.getInt("font_mode",1);return x==0?"A":x==1?"A+":"A++";}
    private float ts(float base){int x=prefs==null?1:prefs.getInt("font_mode",1);return base*(x==0?1f:x==1?1.10f:1.22f);}
    private String group(){return prefs.getString("group_code","");}

    private View statCard(String label,String value,int color){LinearLayout c=card();c.setPadding(dp(14),dp(13),dp(14),dp(13));c.addView(text(value,31,color,true));c.addView(text(label,13,MUTED,true));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(102),1f);p.setMargins(dp(4),dp(4),dp(4),dp(4));c.setLayoutParams(p);return c;}
    private TextView empty(String s){TextView t=text(s,17,MUTED,false);t.setGravity(Gravity.CENTER);t.setPadding(dp(18),dp(28),dp(18),dp(28));t.setBackground(neon(Color.rgb(48,53,83),18,1));return t;}
    private String dayText(ClientDb.Client c){if("INACTIVE".equals(c.status))return"BAJA";long d=c.daysLeft();if(d<0)return"VENCIDO "+Math.abs(d)+"d";if(d==0)return"VENCE HOY";if(d==1)return"1 DÍA";return d+" DÍAS";}
    private int dayColor(ClientDb.Client c){if("INACTIVE".equals(c.status))return MUTED;long d=c.daysLeft();return d<0?RED:d<=1?YELLOW:d<=3?PINK:GREEN;}

    private LinearLayout card(){LinearLayout l=vertical(7);l.setPadding(dp(15),dp(15),dp(15),dp(15));l.setBackground(neon(CARD,20,1));return l;}
    private LinearLayout vertical(int gap){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);if(gap>0)l.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView text(String s,float size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(ts(size));t.setTextColor(color);t.setLineSpacing(dp(2),1f);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private Button bigButton(String s,int color){Button b=new Button(this);b.setText(s);b.setTextSize(ts(17));b.setTextColor(BG);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(neon(color,18,0));b.setAllCaps(false);return b;}
    private Button outlineButton(String s){Button b=new Button(this);b.setText(s);b.setTextSize(ts(15));b.setTextColor(WHITE);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(neon(Color.rgb(34,39,69),16,1));b.setAllCaps(false);return b;}
    private Button smallButton(String s,int color){Button b=new Button(this);b.setText(s);b.setTextSize(ts(13));b.setTextColor(color==CYAN||color==GREEN||color==YELLOW?BG:WHITE);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(neon(color,14,0));b.setAllCaps(false);b.setPadding(dp(10),0,dp(10),0);return b;}
    private EditText field(String hint,boolean number){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Color.rgb(120,130,158));e.setTextColor(WHITE);e.setTextSize(ts(17));e.setSingleLine(!hint.toLowerCase(Locale.ROOT).contains("nota"));e.setPadding(dp(13),dp(10),dp(13),dp(10));e.setBackground(neon(Color.rgb(20,24,48),14,1));if(number)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);return e;}
    private EditText multiline(String hint,String value){EditText e=field(hint,false);e.setSingleLine(false);e.setMinLines(3);e.setText(value);e.setGravity(Gravity.TOP);return e;}
    private GradientDrawable neon(int color,int radius,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));if(stroke>0)g.setStroke(dp(stroke),Color.argb(110,112,126,190));return g;}
    private View wrapDialog(View v){LinearLayout w=vertical(0);w.setPadding(dp(22),dp(8),dp(22),0);w.addView(v,new LinearLayout.LayoutParams(-1,-2));return w;}
    private void margin(ViewGroup parent,View child,int l,int t,int r,int b,int width,int height){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width==-1?-1:dp(width),height==-2?-2:dp(height));p.setMargins(dp(l),dp(t),dp(r),dp(b));parent.addView(child,p);}
    private LinearLayout.LayoutParams weight(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1f);p.setMargins(dp(4),0,dp(4),0);return p;}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(dp(w),dp(h));}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void space(LinearLayout l,int h){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    private String clean(String x,String fallback){return x==null||x.trim().isEmpty()?fallback:x.trim();}
    private int parseInt(String s,int d){try{return Math.max(1,Integer.parseInt(s.trim()));}catch(Exception e){return d;}}
    private double parseDouble(String s,double d){try{return Double.parseDouble(s.trim().replace(',','.'));}catch(Exception e){return d;}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void requestNotifications(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},5001);}
}
