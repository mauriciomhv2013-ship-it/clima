package com.modoespia.app;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaRecorder;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener {
    private static final int REQ_PERMS = 41;

    private final int bg = Color.rgb(2, 8, 8);
    private final int panel = Color.rgb(7, 22, 20);
    private final int panel2 = Color.rgb(9, 30, 26);
    private final int green = Color.rgb(0, 255, 170);
    private final int softGreen = Color.rgb(102, 235, 187);
    private final int dimGreen = Color.rgb(88, 160, 139);
    private final int white = Color.rgb(226, 255, 244);
    private final Handler handler = new Handler();

    private TextView modeText, compassText, magneticText, wifiText, statusText, soundText;
    private SensorManager sensorManager;
    private Sensor accelerometer, magnetometer;
    private final float[] gravity = new float[3];
    private final float[] geomagnetic = new float[3];
    private boolean hasGravity = false, hasMagnetic = false, wallMode = false;

    private BluetoothLeScanner bleScanner;
    private boolean bleScanning = false;
    private int bleCount = 0;
    private MediaRecorder recorder;
    private boolean soundRunning = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        buildUi();
        setupSensors();
        requestNeededPermissions();
        updateWifi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(30));
        scroll.addView(root);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(16), dp(18), dp(15));
        hero.setBackground(rounded(panel, Color.rgb(16, 78, 61), 1, 22));

        TextView eyebrow = text("SISTEMA ACTIVO • V1.1", 11, softGreen, true);
        eyebrow.setLetterSpacing(.12f);
        hero.addView(eyebrow, fullWrap());

        TextView title = text("MODO ESPÍA", 30, white, true);
        title.setLetterSpacing(.05f);
        hero.addView(title, fullWrap());

        TextView sub = text("Consola experimental de sensores", 13, dimGreen, false);
        sub.setPadding(0, dp(2), 0, 0);
        hero.addView(sub, fullWrap());

        root.addView(hero, spaced(0, 0, 0, 14));

        modeText = chip("●  EXPLORACIÓN ACTIVA");
        root.addView(modeText, spaced(0, 0, 0, 12));

        RadarView radar = new RadarView(this);
        radar.setBackground(rounded(Color.rgb(3, 17, 14), Color.rgb(8, 88, 65), 1, 24));
        LinearLayout.LayoutParams radarLp = new LinearLayout.LayoutParams(-1, dp(310));
        radarLp.setMargins(0, 0, 0, dp(14));
        root.addView(radar, radarLp);

        TextView dataTitle = text("LECTURAS EN VIVO", 12, dimGreen, true);
        dataTitle.setLetterSpacing(.1f);
        root.addView(dataTitle, spaced(2, 0, 0, 7));

        LinearLayout readings = new LinearLayout(this);
        readings.setOrientation(LinearLayout.VERTICAL);
        readings.setPadding(dp(15), dp(13), dp(15), dp(13));
        readings.setBackground(rounded(panel2, Color.rgb(12, 71, 55), 1, 18));

        compassText = reading("BRÚJULA", "--°");
        magneticText = reading("CAMPO MAGNÉTICO", "-- µT");
        wifiText = reading("WI-FI", "leyendo...");
        soundText = reading("SONIDO", "detenido");
        statusText = text("ESTADO  ·  sensores en vivo", 12, softGreen, true);
        statusText.setPadding(0, dp(7), 0, 0);

        readings.addView(compassText);
        readings.addView(divider());
        readings.addView(magneticText);
        readings.addView(divider());
        readings.addView(wifiText);
        readings.addView(divider());
        readings.addView(soundText);
        readings.addView(statusText);
        root.addView(readings, spaced(0, 0, 0, 16));

        TextView toolsTitle = text("HERRAMIENTAS", 12, dimGreen, true);
        toolsTitle.setLetterSpacing(.1f);
        root.addView(toolsTitle, spaced(2, 0, 0, 6));

        root.addView(button("◉  Escáner de pared", true, v -> toggleWallMode()));
        root.addView(button("⌁  Radar Bluetooth", false, v -> startBluetoothScan()));
        root.addView(button("◌  Actualizar Wi-Fi", false, v -> updateWifi()));
        root.addView(button("⌁  Medidor de sonido", false, v -> toggleSoundMeter()));
        root.addView(button("◐  Visión nocturna / cámara", false, v -> openCamera()));
        root.addView(button("↺  Volver a exploración", false, v -> {
            wallMode = false;
            modeText.setText("●  EXPLORACIÓN ACTIVA");
            statusText.setText("ESTADO  ·  sensores en vivo");
        }));

        TextView note = text("ESCÁNER DE PARED", 11, dimGreen, true);
        note.setLetterSpacing(.08f);
        note.setPadding(dp(13), dp(13), dp(13), 0);
        root.addView(note, spaced(0, 8, 0, 0));

        TextView disclaimer = text("Muestra variaciones del magnetómetro. No ve a través de paredes ni identifica personas u objetos.", 11, Color.rgb(123, 177, 158), false);
        disclaimer.setPadding(dp(13), dp(5), dp(13), dp(13));
        disclaimer.setBackground(rounded(Color.rgb(5, 17, 15), Color.rgb(9, 51, 41), 1, 16));
        root.addView(disclaimer, spaced(0, 0, 0, 0));

        setContentView(scroll);
    }

    private void setupSensors() {
        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (accelerometer != null) sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        if (magnetometer != null) sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        if (magnetometer == null) magneticText.setText("CAMPO MAGNÉTICO   ·   sensor no disponible");
    }

    private void requestNeededPermissions() {
        if (Build.VERSION.SDK_INT < 23) return;
        List<String> list = new ArrayList<>();
        addIfMissing(list, Manifest.permission.CAMERA);
        addIfMissing(list, Manifest.permission.RECORD_AUDIO);
        addIfMissing(list, Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 31) {
            addIfMissing(list, Manifest.permission.BLUETOOTH_SCAN);
            addIfMissing(list, Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (!list.isEmpty()) requestPermissions(list.toArray(new String[0]), REQ_PERMS);
    }

    private void addIfMissing(List<String> list, String permission) {
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) list.add(permission);
    }

    private void toggleWallMode() {
        wallMode = !wallMode;
        modeText.setText(wallMode ? "●  ESCÁNER DE PARED ACTIVO" : "●  EXPLORACIÓN ACTIVA");
        statusText.setText(wallMode ? "ESTADO  ·  barré la superficie lentamente" : "ESTADO  ·  sensores en vivo");
    }

    private void updateWifi() {
        try {
            WifiManager wm = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wm.getConnectionInfo();
            int rssi = info != null ? info.getRssi() : -127;
            String quality = rssi > -55 ? "FUERTE" : rssi > -70 ? "MEDIA" : "DÉBIL";
            wifiText.setText("WI-FI   ·   " + rssi + " dBm   ·   " + quality);
            statusText.setText("ESTADO  ·  lectura Wi-Fi actualizada");
        } catch (Exception e) {
            wifiText.setText("WI-FI   ·   sin lectura");
        }
    }

    private void startBluetoothScan() {
        try {
            BluetoothManager manager = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = manager.getAdapter();
            if (adapter == null || !adapter.isEnabled()) {
                statusText.setText("ESTADO  ·  activá Bluetooth para usar el radar");
                return;
            }
            if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                requestNeededPermissions();
                statusText.setText("ESTADO  ·  permiso Bluetooth requerido");
                return;
            }
            bleScanner = adapter.getBluetoothLeScanner();
            if (bleScanner == null) {
                statusText.setText("ESTADO  ·  escáner Bluetooth no disponible");
                return;
            }
            bleCount = 0;
            bleScanning = true;
            modeText.setText("●  RADAR BLUETOOTH ACTIVO");
            statusText.setText("ESTADO  ·  buscando señales cercanas…");
            bleScanner.startScan(scanCallback);
            handler.postDelayed(this::stopBluetoothScan, 10000);
        } catch (Exception e) {
            statusText.setText("ESTADO  ·  no se pudo iniciar Bluetooth");
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            bleCount++;
            int rssi = result.getRssi();
            runOnUiThread(() -> statusText.setText("ESTADO  ·  " + bleCount + " señales   ·   última " + rssi + " dBm"));
        }

        @Override public void onScanFailed(int errorCode) {
            runOnUiThread(() -> statusText.setText("ESTADO  ·  error Bluetooth " + errorCode));
        }
    };

    private void stopBluetoothScan() {
        try {
            if (bleScanning && bleScanner != null && (Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED)) {
                bleScanner.stopScan(scanCallback);
            }
        } catch (Exception ignored) {}
        bleScanning = false;
        statusText.setText("ESTADO  ·  radar finalizado   ·   " + bleCount + " señales");
    }

    private void toggleSoundMeter() {
        if (soundRunning) {
            stopSound();
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestNeededPermissions();
            statusText.setText("ESTADO  ·  permiso de micrófono requerido");
            return;
        }
        try {
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            File f = new File(getCacheDir(), "meter.3gp");
            recorder.setOutputFile(f.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            soundRunning = true;
            modeText.setText("●  MEDIDOR DE SONIDO ACTIVO");
            statusText.setText("ESTADO  ·  micrófono midiendo ambiente");
            handler.post(soundLoop);
        } catch (Exception e) {
            soundText.setText("SONIDO   ·   no disponible");
        }
    }

    private final Runnable soundLoop = new Runnable() {
        @Override public void run() {
            if (!soundRunning || recorder == null) return;
            try {
                int amp = recorder.getMaxAmplitude();
                double db = amp > 0 ? 20.0 * Math.log10(amp / 32767.0) + 90.0 : 0.0;
                soundText.setText(String.format(Locale.US, "SONIDO   ·   %.0f dB aprox.", Math.max(0, db)));
            } catch (Exception ignored) {}
            handler.postDelayed(this, 350);
        }
    };

    private void stopSound() {
        soundRunning = false;
        handler.removeCallbacks(soundLoop);
        try { if (recorder != null) recorder.stop(); } catch (Exception ignored) {}
        try { if (recorder != null) recorder.release(); } catch (Exception ignored) {}
        recorder = null;
        soundText.setText("SONIDO   ·   detenido");
        statusText.setText("ESTADO  ·  medidor detenido");
    }

    private void openCamera() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestNeededPermissions();
            statusText.setText("ESTADO  ·  permiso de cámara requerido");
            return;
        }
        try {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            startActivity(intent);
            modeText.setText("●  CÁMARA / NOCTURNO");
        } catch (Exception e) {
            statusText.setText("ESTADO  ·  cámara no disponible");
        }
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, gravity, 0, 3);
            hasGravity = true;
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, geomagnetic, 0, 3);
            hasMagnetic = true;
            float x = event.values[0], y = event.values[1], z = event.values[2];
            float strength = (float)Math.sqrt(x*x + y*y + z*z);
            magneticText.setText(String.format(Locale.US, "CAMPO MAGNÉTICO   ·   %.1f µT", strength));
            if (wallMode) {
                String level = strength > 100 ? "VARIACIÓN ALTA" : strength > 70 ? "VARIACIÓN MEDIA" : "LECTURA NORMAL";
                statusText.setText(String.format(Locale.US, "ESTADO  ·  %s   ·   %.1f µT", level, strength));
            }
        }

        if (hasGravity && hasMagnetic) {
            float[] R = new float[9];
            float[] I = new float[9];
            if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
                float[] o = new float[3];
                SensorManager.getOrientation(R, o);
                float az = (float)Math.toDegrees(o[0]);
                if (az < 0) az += 360f;
                compassText.setText(String.format(Locale.US, "BRÚJULA   ·   %.0f°", az));
            }
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override protected void onDestroy() {
        stopBluetoothScan();
        stopSound();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        super.onDestroy();
    }

    private Button button(String label, boolean primary, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(dp(17), 0, dp(17), 0);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setTextColor(primary ? Color.BLACK : softGreen);
        b.setBackground(primary
                ? rounded(green, green, 1, 17)
                : rounded(Color.rgb(5, 20, 17), Color.rgb(9, 83, 63), 1, 17));
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(55));
        lp.setMargins(0, dp(7), 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView chip(String s) {
        TextView t = text(s, 12, green, true);
        t.setLetterSpacing(.05f);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(13), 0, dp(13), 0);
        t.setBackground(rounded(Color.rgb(4, 25, 20), Color.rgb(0, 101, 73), 1, 999));
        t.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(40)));
        return t;
    }

    private TextView reading(String label, String value) {
        TextView t = text(label + "   ·   " + value, 14, white, true);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Color.rgb(12, 53, 44));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.setMargins(0, dp(6), 0, dp(6));
        v.setLayoutParams(lp);
        return v;
    }

    private GradientDrawable rounded(int fill, int stroke, int strokeWidthDp, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(strokeWidthDp), stroke);
        return d;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setFontFeatureSettings("kern");
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout.LayoutParams fullWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private LinearLayout.LayoutParams spaced(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
