package com.gearcam;

import android.content.Context;
import android.content.SharedPreferences;

/** تخزين الإعدادات. */
public class Config {
    public static final String SRC_BROADCAST = "broadcast";
    public static final String SRC_FILE = "file";
    public static final String SRC_SETTING = "setting";

    private final SharedPreferences p;

    public Config(Context c) {
        p = c.getApplicationContext().getSharedPreferences("gearcam", Context.MODE_PRIVATE);
    }

    public String get(String k, String d) {
        return p.getString(k, d);
    }

    public void put(String k, String v) {
        p.edit().putString(k, v).apply();
    }

    public boolean getBool(String k, boolean d) {
        return p.getBoolean(k, d);
    }

    public void putBool(String k, boolean v) {
        p.edit().putBoolean(k, v).apply();
    }

    public int getInt(String k, int d) {
        try {
            return Integer.parseInt(p.getString(k, String.valueOf(d)).trim());
        } catch (Exception e) {
            return d;
        }
    }
}
