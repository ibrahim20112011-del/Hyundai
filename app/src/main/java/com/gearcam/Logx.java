package com.gearcam;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/** سجل بسيط يظهر في الشاشة وفي logcat بوسم GearCam. */
public class Logx {
    private static final ArrayList<String> lines = new ArrayList<>();
    private static final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss", Locale.US);
    public static volatile Runnable listener;

    public static void add(String s) {
        Runnable r;
        synchronized (Logx.class) {
            Log.d("GearCam", s);
            lines.add(0, fmt.format(new Date()) + "  " + s);
            while (lines.size() > 80) {
                lines.remove(lines.size() - 1);
            }
            r = listener;
        }
        if (r != null) {
            r.run();
        }
    }

    public static synchronized String dump() {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            sb.append(l).append("\n");
        }
        return sb.toString();
    }
}
