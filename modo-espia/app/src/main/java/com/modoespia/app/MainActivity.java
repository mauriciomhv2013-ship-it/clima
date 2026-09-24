package com.modoespia.app;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
    private final int green = Color.rgb(0, 255, 170);
    private final int dimGreen = Color.rgb(70, 180, 140);
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
        buildUi();
        setupSensors();
        requestNeededPermissions();
        updateWifi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(1, 7, 6));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(28));
        scroll.addView(root);

        TextView title = text("MODO ESPÍA", 28, green, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, fullWrap());
        TextView sub = text("CONSOLA DE SENSORES • V1", 12, dimGreen, false);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub, fullWrap());

        modeText = text("MODO: EXPLORACIÓN", 14, green, true);
        modeText.setPadding(0, dp(14), 0, dp(8));
        root.addView(modeText, fullWrap());

        RadarView radar = new RadarView(this);
        LinearLayout.LayoutParams radarLp = new LinearLayout.LayoutParams(-1, dp(300));
        radarLp.setMargins(0, 0, 0, dp(12));
        root.addView(radar, radarLp);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        panel.setBackgroundColor(Color.rgb(4, 20, 16));
        compassText = text("BRÚJULA: --°", 15, green, true);
        magneticText = text("CAMPO MAGNÉTICO: -- µT", 15, green, true);
        wifiText = text("WI-FI: leyendo...", 15, green, true);
        soundText = text("SONIDO: detenido", 15, green, true);
        statusText = text("ESTADO: listo", 13, dimGreen, false);
        panel.addView(compassText); panel.addView(magneticText); panel.addView(wifiText); panel.addView(soundText); panel.addView(statusText);
        root.addView(panel, fullWrap());

        root.addView(button("ESCÁNER DE PARED", v -> toggleWallMode()));
        root.addView(button("RADAR BLUETOOTH", v -> startBluetoothScan()));
        root.addView(button("ACTUALIZAR WI-FI", v -> updateWifi()));
        root.addView(button("MEDIDOR DE SONIDO", v -> toggleSoundMeter()));
        root.addView(button("VISIÓN NOCTURNA / CÁMARA", v -> openCamera()));
        root.addView(button("VOLVER A EXPLORACIÓN", v -> { wallMode = false; modeText.setText("MODO: EXPLORACIÓN"); statusText.setText("ESTADO: sensores en vivo"); }));

        TextView note = text("El escáner de pared muestra variaciones del magnetómetro. No ve a través de paredes ni identifica personas u objetos.", 11, Color.rgb(120, 170, 150), false);
        note.setPadding(0, dp(14), 0, 0);
        root.addView(note, fullWrap());
        setContentView(scroll);
    }

    private void setupSensors() {
        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (accelerometer != null) sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        if (magnetometer != null) sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        if (magnetometer == null) magneticText.setText("CAMPO MAGNÉTICO: sensor no disponible");
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
        modeText.setText(wallMode ? "MODO: ESCÁNER DE PARED" : "MODO: EXPLORACIÓN");
        statusText.setText(wallMode ? "ESTADO: mové el teléfono lentamente frente a la superficie" : "ESTADO: sensores en vivo");
    }

    private void updateWifi() {
        try {
            WifiManager wm = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wm.getConnectionInfo();
            int rssi = info != null ? info.getRssi() : -127;
            String quality = rssi > -55 ? "FUERTE" : rssi > -70 ? "MEDIA" : "DÉBIL";
            wifiText.setText("WI-FI: " + rssi + " dBm • " + quality);
        } catch (Exception e) {
            wifiText.setText("WI-FI: sin lectura");
        }
    }

    private void startBluetoothScan() {
        try {
            BluetoothManager manager = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = manager.getAdapter();
            if (adapter == null || !adapter.isEnabled()) {
                statusText.setText("ESTADO: activá Bluetooth para usar el radar");
                return;
            }
            if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                requestNeededPermissions();
                statusText.setText("ESTADO: permiso Bluetooth requerido");
                return;
            }
            bleScanner = adapter.getBluetoothLeScanner();
            if (bleScanner == null) { statusText.setText("ESTADO: escáner Bluetooth no disponible"); return; }
            bleCount = 0;
            bleScanning = true;
            modeText.setText("MODO: RADAR BLUETOOTH");
            statusText.setText("ESTADO: buscando dispositivos cercanos...");
            bleScanner.startScan(scanCallback);
            handler.postDelayed(this::stopBluetoothScan, 10000);
        } catch (Exception e) {
            statusText.setText("ESTADO: no se pudo iniciar Bluetooth");
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            bleCount++;
            int rssi = result.getRssi();
            runOnUiThread(() -> statusText.setText("ESTADO: " + bleCount + " señales • última " + rssi + " dBm"));
        }
        @Override public void onScanFailed(int errorCode) {
            runOnUiThread(() -> statusText.setText("ESTADO: error Bluetooth " + errorCode));
        }
    };

    private void stopBluetoothScan() {
        try {
            if (bleScanning && bleScanner != null && (Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED)) {
                bleScanner.stopScan(scanCallback);
            }
        } catch (Exception ignored) {}
        bleScanning = false;
        statusText.setText("ESTADO: radar finalizado • " + bleCount + " señales detectadas");
    }

    private void toggleSoundMeter() {
        if (soundRunning) { stopSound(); return; }
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestNeededPermissions();
            statusText.setText("ESTADO: permiso de micrófono requerido");
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
            modeText.setText("MODO: MEDIDOR DE SONIDO");
            handler.post(soundLoop);
        } catch (Exception e) {
            soundText.setText("SONIDO: no disponible");
        }
    }

    private final Runnable soundLoop = new Runnable() {
        @Override public void run() {
            if (!soundRunning || recorder == null) return;
            try {
                int amp = recorder.getMaxAmplitude();
                double db = amp > 0 ? 20.0 * Math.log10(amp / 32767.0) + 90.0 : 0.0;
                soundText.setText(String.format(Locale.US, "SONIDO: %.0f dB aprox.", Math.max(0, db)));
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
        soundText.setText("SONIDO: detenido");
        statusText.setText("ESTADO: medidor detenido");
    }

    private void openCamera() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestNeededPermissions();
            statusText.setText("ESTADO: permiso de cámara requerido");
            return;
        }
        try {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            startActivity(intent);
            modeText.setText("MODO: CÁMARA / NOCTURNO");
        } catch (Exception e) {
            statusText.setText("ESTADO: cámara no disponible");
        }
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, gravity, 0, 3); hasGravity = true;
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, geomagnetic, 0, 3); hasMagnetic = true;
            float x = event.values[0], y = event.values[1], z = event.values[2];
            float strength = (float)Math.sqrt(x*x + y*y + z*z);
            magneticText.setText(String.format(Locale.US, "CAMPO MAGNÉTICO: %.1f µT", strength));
            if (wallMode) {
                String level = strength > 100 ? "VARIACIÓN ALTA" : strength > 70 ? "VARIACIÓN MEDIA" : "LECTURA NORMAL";
                statusText.setText(String.format(Locale.US, "ESTADO: %s • %.1f µT", level, strength));
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
                compassText.setText(String.format(Locale.US, "BRÚJULA: %.0f°", az));
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

    private Button button(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.BLACK);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setBackgroundColor(green);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.setMargins(0, dp(9), 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private LinearLayout.LayoutParams fullWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
