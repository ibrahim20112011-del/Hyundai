package com.gearcam;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/**
 * منطق التشغيل:
 *  R  -> الكاميرا الخلفية فوراً.
 *  D  -> الكاميرا الأمامية، وتبقى المدة المحددة (10 ثوانٍ) ما لم يعد القير إلى R.
 *  بعد R إذا أصبح القير N/P (غير D) لأكثر من 3 ثوانٍ -> العودة للرئيسية.
 */
public class GearController {
    public static final int UNKNOWN = 0, R = 1, D = 2, OTHER = 3;

    private static GearController inst;

    public static synchronized GearController get(Context c) {
        if (inst == null) {
            inst = new GearController(c.getApplicationContext());
        }
        return inst;
    }

    private Context ctx;
    private final Handler h = new Handler(Looper.getMainLooper());
    private int state = UNKNOWN;

    private final Runnable endFront = () -> {
        Logx.add("انتهت مدة الكاميرا الأمامية");
        Util.home(ctx);
    };

    private final Runnable leaveRear = () -> {
        Logx.add("خرج القير من R، العودة للرئيسية");
        Util.home(ctx);
    };

    private GearController(Context c) {
        ctx = c;
    }

    /** تُستدعى من أي خيط بالقيمة الخام التي قرأتها الخدمة. */
    public void onValue(final String raw) {
        h.post(() -> handle(raw));
    }

    /** للاختبار من الواجهة. */
    public void simulate(final int ns) {
        h.post(() -> apply(ns, new Config(ctx)));
    }

    private void handle(String raw) {
        Config c = new Config(ctx);
        boolean revOnly = c.getBool("rev_only", false);
        int ns;
        if (matches(raw, c.get("val_r", "R"))) {
            ns = R;
        } else if (revOnly) {
            ns = D;
        } else if (matches(raw, c.get("val_d", "D"))) {
            ns = D;
        } else {
            ns = OTHER;
        }
        Logx.add("القيمة [" + raw + "] ← " + name(ns));
        apply(ns, c);
    }

    private void apply(int ns, Config c) {
        if (ns == state) {
            return;
        }
        int prev = state;
        state = ns;
        boolean revOnly = c.getBool("rev_only", false);

        if (ns == R) {
            h.removeCallbacks(endFront);
            h.removeCallbacks(leaveRear);
            Logx.add("فتح الكاميرا الخلفية");
            Util.launch(ctx, c.get("rear_pkg", ""));
        } else if (ns == D) {
            if (revOnly && prev != R) {
                return;
            }
            h.removeCallbacks(endFront);
            h.removeCallbacks(leaveRear);
            Logx.add("فتح الكاميرا الأمامية");
            Util.launch(ctx, c.get("front_pkg", ""));
            if (c.getBool("go_home", true)) {
                int secs = Math.max(1, c.getInt("front_secs", 10));
                h.postDelayed(endFront, secs * 1000L);
            }
        } else if (ns == OTHER && prev == R) {
            h.removeCallbacks(leaveRear);
            if (c.getBool("go_home", true)) {
                h.postDelayed(leaveRear, 3000);
            }
        }
    }

    private static boolean matches(String raw, String list) {
        if (raw == null || list == null) {
            return false;
        }
        String v = raw.trim();
        for (String t : list.split(",")) {
            if (!t.trim().isEmpty() && v.equalsIgnoreCase(t.trim())) {
                return true;
            }
        }
        return false;
    }

    private static String name(int s) {
        switch (s) {
            case R:
                return "R";
            case D:
                return "D";
            case OTHER:
                return "أخرى (N/P)";
            default:
                return "?";
        }
    }
}
