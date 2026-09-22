package com.nexo.enso;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.webkit.*;
import android.widget.Toast;
import java.util.*;

public class MainActivity extends Activity {
    private static final int REQ_PERMS = 501;
    private WebView webView;
    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(android.graphics.Color.rgb(8,17,34));
        getWindow().setNavigationBarColor(android.graphics.Color.rgb(5,9,20));
        buildWebView();
        requestNeededPermissions();
        startSentinelIfEnabled();
        maybeAskBatteryExemption();
    }

    private void buildWebView() {
        webView = new WebView(this);
        setContentView(webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);

        webView.addJavascriptInterface(new AndroidBridge(), "NexoAndroid");
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("file".equalsIgnoreCase(u.getScheme())) return false;
                openExternal(u.toString());
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                String js = "try{if(window.NexoAndroid&&window.state){NexoAndroid.updateLocation(+state.lat,+state.lon,String(state.name||''),String(state.country||''));NexoAndroid.setSentinelEnabled(!!state.settings.sentinel);}}catch(e){}";
                view.evaluateJavascript(js, null);
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    callback.invoke(origin, true, false);
                } else {
                    pendingGeoOrigin = origin; pendingGeoCallback = callback;
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_PERMS);
                }
            }
        });
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void requestNeededPermissions() {
        ArrayList<String> p = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            p.add(Manifest.permission.POST_NOTIFICATIONS);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            p.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            p.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (!p.isEmpty()) requestPermissions(p.toArray(new String[0]), REQ_PERMS);
        else captureLastLocation();
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        if (req == REQ_PERMS) {
            boolean loc = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                          checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            if (pendingGeoCallback != null) {
                pendingGeoCallback.invoke(pendingGeoOrigin, loc, false);
                pendingGeoCallback = null; pendingGeoOrigin = null;
            }
            if (loc) captureLastLocation();
            startSentinelIfEnabled();
        }
    }

    @SuppressWarnings("MissingPermission")
    private void captureLastLocation() {
        try {
            LocationManager lm = (LocationManager)getSystemService(LOCATION_SERVICE);
            Location best = null;
            for (String provider : lm.getProviders(true)) {
                Location x = lm.getLastKnownLocation(provider);
                if (x != null && (best == null || x.getAccuracy() < best.getAccuracy())) best = x;
            }
            if (best != null) {
                getSharedPreferences("nexo_native", MODE_PRIVATE).edit()
                    .putString("lat", Double.toString(best.getLatitude()))
                    .putString("lon", Double.toString(best.getLongitude())).apply();
            }
        } catch (Exception ignored) {}
    }

    private void startSentinelIfEnabled() {
        boolean enabled = getSharedPreferences("nexo_native", MODE_PRIVATE).getBoolean("sentinel_enabled", true);
        if (!enabled) return;
        try {
            Intent i = new Intent(this, SentinelService.class).setAction(SentinelService.ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        } catch (Exception e) {
            Toast.makeText(this, "No pude iniciar Centinela: "+e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void maybeAskBatteryExemption() {
        if (Build.VERSION.SDK_INT < 23) return;
        SharedPreferences sp = getSharedPreferences("nexo_native", MODE_PRIVATE);
        if (sp.getBoolean("battery_prompted", false)) return;
        sp.edit().putBoolean("battery_prompted", true).apply();
        new Handler(Looper.getMainLooper()).postDelayed(() -> new AlertDialog.Builder(this)
            .setTitle("Mantener NEXO activo")
            .setMessage("Para recibir alertas con el celular bloqueado, permití que NEXO funcione sin optimización de batería. Android mostrará una pantalla del sistema para que vos decidas.")
            .setPositiveButton("Configurar", (d,w) -> requestBatteryExemption())
            .setNegativeButton("Ahora no", null).show(), 1200);
    }

    private void requestBatteryExemption() {
        if (Build.VERSION.SDK_INT < 23) return;
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); } catch (Exception ignored) {}
        }
    }

    private void openExternal(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {}
    }

    public class AndroidBridge {
        @JavascriptInterface public void updateLocation(double lat, double lon, String name, String country) {
            getSharedPreferences("nexo_native", MODE_PRIVATE).edit()
                .putString("lat", Double.toString(lat)).putString("lon", Double.toString(lon))
                .putString("name", name == null ? "" : name).putString("country", country == null ? "" : country).apply();
        }
        @JavascriptInterface public void setSentinelEnabled(boolean enabled) {
            getSharedPreferences("nexo_native", MODE_PRIVATE).edit().putBoolean("sentinel_enabled", enabled).apply();
            runOnUiThread(() -> {
                Intent i = new Intent(MainActivity.this, SentinelService.class)
                    .setAction(enabled ? SentinelService.ACTION_START : SentinelService.ACTION_STOP);
                try {
                    if (enabled && Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
                } catch (Exception ignored) {}
            });
        }
        @JavascriptInterface public void openExternal(String url) { runOnUiThread(() -> MainActivity.this.openExternal(url)); }
        @JavascriptInterface public void requestBackgroundSettings() { runOnUiThread(() -> requestBatteryExemption()); }
        @JavascriptInterface public String platform() { return "android-apk"; }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
}
