package com.sol.neoncontrol;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

/**
 * NEON Control 2.1
 * Recupera el control de tamaño de interfaz para accesibilidad.
 * Chico / Mediano / Grande escala toda la app, no solamente el texto.
 */
public class MainActivityV3 extends MainActivityV2 {
    private static final String PREFS = "neon_prefs";
    private static final String FONT_MODE = "font_mode";

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences p = newBase.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int mode = normalizeMode(p.getInt(FONT_MODE, 1));
        float factor = scaleFor(mode);

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        int baseDpi = config.densityDpi;
        if (baseDpi == Configuration.DENSITY_DPI_UNDEFINED || baseDpi <= 0) {
            baseDpi = newBase.getResources().getDisplayMetrics().densityDpi;
        }
        config.densityDpi = Math.max(120, Math.round(baseDpi * factor));
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        installSizeButton();
    }

    private void installSizeButton() {
        FrameLayout host = findViewById(android.R.id.content);
        if (host == null) return;

        Button size = new Button(this);
        size.setAllCaps(false);
        size.setText("＋");
        size.setTextSize(25);
        size.setTextColor(Color.WHITE);
        size.setTypeface(Typeface.DEFAULT_BOLD);
        size.setGravity(Gravity.CENTER);
        size.setPadding(0, 0, 0, dpLocal(2));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(86, 34, 105));
        bg.setCornerRadius(dpLocal(17));
        bg.setStroke(dpLocal(2), Color.rgb(245, 59, 255));
        size.setBackground(bg);

        int mode = normalizeMode(getSharedPreferences(PREFS, MODE_PRIVATE).getInt(FONT_MODE, 1));
        size.setContentDescription("Agrandar interfaz. Tamaño actual: " + sizeName(mode));
        size.setOnClickListener(v -> cycleSize());
        size.setOnLongClickListener(v -> {
            int current = normalizeMode(getSharedPreferences(PREFS, MODE_PRIVATE).getInt(FONT_MODE, 1));
            Toast.makeText(this, "Tamaño actual: " + sizeName(current), Toast.LENGTH_SHORT).show();
            return true;
        });

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dpLocal(54), dpLocal(50), Gravity.TOP | Gravity.END);
        lp.topMargin = dpLocal(8);
        lp.rightMargin = dpLocal(10);
        host.addView(size, lp);
    }

    private void cycleSize() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        int current = normalizeMode(p.getInt(FONT_MODE, 1));
        int next = current == 0 ? 1 : current == 1 ? 2 : 0;
        p.edit().putInt(FONT_MODE, next).apply();
        Toast.makeText(this, "Tamaño: " + sizeName(next), Toast.LENGTH_SHORT).show();
        recreate();
    }

    private static int normalizeMode(int mode) {
        return mode < 0 || mode > 2 ? 1 : mode;
    }

    private static float scaleFor(int mode) {
        if (mode == 0) return 0.92f;
        if (mode == 2) return 1.18f;
        return 1.00f;
    }

    private static String sizeName(int mode) {
        if (mode == 0) return "Chico";
        if (mode == 2) return "Grande";
        return "Mediano";
    }

    private int dpLocal(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
