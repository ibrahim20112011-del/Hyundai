package com.gearcam;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * أداة اكتشاف إشارة القير.
 * 1) "ابدأ الاستماع": تسجل أي تغيّر في إعدادات النظام وأي Broadcast تكتب اسمه.
 * 2) لقطتان A و B: خذ A والقير على P، ثم B والقير على R، ثم "قارن" لترى ما تغيّر.
 */
public class DiagActivity extends Activity {
    private LinearLayout root;
    private TextView logView;
    private EditText actions;
    private BroadcastReceiver rec;
    private ContentObserver obs;
    private volatile Map<String, String> snapA, snapB;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(24));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sv.addView(root);
        setContentView(sv);

        TextView help = new TextView(this);
        help.setText("طريقة 1: اضغط \"ابدأ الاستماع\" ثم حرّك القير (P ← R ← D) وراقب السجل.\n"
                + "طريقة 2: القير على P اضغط \"لقطة A\"، ثم حرّكه إلى R واضغط \"لقطة B\"، ثم \"قارن\". "
                + "القيمة التي تغيّرت هي إشارة القير (ويفضّل تكرار التجربة للتأكد).");
        root.addView(help);

        TextView l = new TextView(this);
        l.setText("Broadcast إضافية للاستماع (اختياري، مفصولة بفاصلة)");
        l.setPadding(0, dp(12), 0, 0);
        root.addView(l);
        actions = new EditText(this);
        actions.setSingleLine(true);
        root.addView(actions);

        button("ابدأ الاستماع", v -> startListen());
        button("إيقاف الاستماع", v -> stopListen());
        button("لقطة A (القير على P)", v -> takeSnap(true));
        button("لقطة B (القير على R)", v -> takeSnap(false));
        button("قارن", v -> compare());

        logView = new TextView(this);
        logView.setTextIsSelectable(true);
        logView.setPadding(0, dp(16), 0, 0);
        root.addView(logView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Logx.listener = () -> ui.post(() -> logView.setText(Logx.dump()));
        logView.setText(Logx.dump());
    }

    @Override
    protected void onPause() {
        Logx.listener = null;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopListen();
        super.onDestroy();
    }

    private void startListen() {
        stopListen();
        final ContentResolver cr = getContentResolver();
        obs = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                String v = (uri == null) ? "" : Util.readSetting(cr, uri.getLastPathSegment());
                Logx.add("Setting تغيّر: " + uri + " = " + v);
            }
        };
        cr.registerContentObserver(Settings.System.CONTENT_URI, true, obs);
        cr.registerContentObserver(Settings.Global.CONTENT_URI, true, obs);
        cr.registerContentObserver(Settings.Secure.CONTENT_URI, true, obs);

        IntentFilter f = new IntentFilter();
        int n = 0;
        for (String a : actions.getText().toString().split(",")) {
            a = a.trim();
            if (!a.isEmpty()) {
                f.addAction(a);
                n++;
            }
        }
        if (n > 0) {
            rec = new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent in) {
                    StringBuilder sb = new StringBuilder("Broadcast: " + in.getAction());
                    Bundle e = in.getExtras();
                    if (e != null) {
                        for (String k : e.keySet()) {
                            sb.append("\n    ").append(k).append(" = ").append(String.valueOf(e.get(k)));
                        }
                    }
                    Logx.add(sb.toString());
                }
            };
            registerReceiver(rec, f);
        }
        Logx.add("بدأ الاستماع (إعدادات النظام" + (n > 0 ? " + " + n + " Broadcast" : "") + ")");
    }

    private void stopListen() {
        if (obs != null) {
            try {
                getContentResolver().unregisterContentObserver(obs);
            } catch (Exception ignored) {
            }
            obs = null;
        }
        if (rec != null) {
            try {
                unregisterReceiver(rec);
            } catch (Exception ignored) {
            }
            rec = null;
        }
    }

    private void takeSnap(final boolean isA) {
        final String n = isA ? "A" : "B";
        Logx.add("جارٍ أخذ اللقطة " + n + " ...");
        new Thread(() -> {
            Map<String, String> m = snapshot();
            if (isA) {
                snapA = m;
            } else {
                snapB = m;
            }
            Logx.add("اللقطة " + n + " جاهزة: " + m.size() + " قيمة");
        }).start();
    }

    private void compare() {
        Map<String, String> a = snapA, b = snapB;
        if (a == null || b == null) {
            Logx.add("خذ اللقطتين A و B أولاً");
            return;
        }
        Set<String> keys = new TreeSet<>(a.keySet());
        keys.addAll(b.keySet());
        StringBuilder sb = new StringBuilder("الفروقات بين A و B:\n");
        int n = 0;
        for (String k : keys) {
            String x = a.get(k), y = b.get(k);
            if (x == null ? y != null : !x.equals(y)) {
                if (n < 120) {
                    sb.append(k).append(": ").append(x).append(" → ").append(y).append("\n");
                }
                n++;
            }
        }
        sb.append("المجموع: ").append(n);
        Logx.add(sb.toString());
    }

    private Map<String, String> snapshot() {
        Map<String, String> m = new HashMap<>();
        ContentResolver cr = getContentResolver();
        addTable(m, cr, Settings.System.CONTENT_URI, "system/");
        addTable(m, cr, Settings.Global.CONTENT_URI, "global/");
        addTable(m, cr, Settings.Secure.CONTENT_URI, "secure/");
        int[] count = {0};
        String[] roots = {"/sys/class/gpio", "/sys/class/switch", "/sys/class/extcon",
                "/sys/devices/virtual/switch", "/sys/devices/platform"};
        for (String r : roots) {
            walk(new File(r), 0, 4, m, count);
        }
        return m;
    }

    private void addTable(Map<String, String> m, ContentResolver cr, Uri uri, String prefix) {
        Cursor c = null;
        try {
            c = cr.query(uri, new String[]{"name", "value"}, null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    m.put(prefix + c.getString(0), String.valueOf(c.getString(1)));
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    private void walk(File f, int depth, int max, Map<String, String> m, int[] count) {
        if (count[0] > 6000 || depth > max) {
            return;
        }
        try {
            if (f.isDirectory()) {
                File[] ch = f.listFiles();
                if (ch == null) {
                    return;
                }
                for (File x : ch) {
                    walk(x, depth + 1, max, m, count);
                }
            } else if (f.canRead()) {
                count[0]++;
                String v = Util.readFile(f.getPath());
                if (v != null && v.length() <= 40 && v.indexOf('\n') < 0) {
                    m.put(f.getPath(), v);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void button(String text, View.OnClickListener l) {
        Button bt = new Button(this);
        bt.setText(text);
        bt.setOnClickListener(l);
        root.addView(bt);
    }
}
