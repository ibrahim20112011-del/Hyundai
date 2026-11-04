package com.gearcam;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * مستكشف تطبيق:
 *  - يعرض شاشات (Activities) حزمة ويسمح بتجربتها واعتمادها.
 *  - يعرض الـ Actions التي تستمع لها الحزمة.
 *  - بحث شامل في كل التطبيقات عن Actions تخص القير والكاميرا.
 */
public class ExploreActivity extends Activity {
    private LinearLayout root;
    private LinearLayout list;
    private TextView scanOut;
    private EditText pkgEdit;
    private Config cfg;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        cfg = new Config(this);

        ScrollView sv = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(24));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sv.addView(root);
        setContentView(sv);

        TextView help = new TextView(this);
        help.setText("1) \"بحث شامل\": يفحص كل التطبيقات ويعرض أسماء الإشارات (Actions) التي لها علاقة "
                + "بالقير أو الكاميرا. بعده افتح \"أداة اكتشاف إشارة القير\" واضغط "
                + "\"استمع لكل الـ Actions المكتشفة\" ثم حرّك القير P ← R ← P.\n"
                + "2) أو اكتب اسم حزمة واضغط \"عرض\" لرؤية شاشاتها، و\"عرض Actions\" لرؤية ما تستمع له.");
        root.addView(help);

        Button scan = new Button(this);
        scan.setText("بحث شامل في كل التطبيقات (قد يستغرق نصف دقيقة)");
        scan.setOnClickListener(v -> runScan());
        root.addView(scan);

        pkgEdit = new EditText(this);
        pkgEdit.setSingleLine(true);
        pkgEdit.setText("com.autochips.android.backcar");
        root.addView(pkgEdit);

        Button show = new Button(this);
        show.setText("عرض شاشات الحزمة");
        show.setOnClickListener(v -> load());
        root.addView(show);

        Button acts = new Button(this);
        acts.setText("عرض Actions الحزمة");
        acts.setOnClickListener(v -> showActions());
        root.addView(acts);

        scanOut = new TextView(this);
        scanOut.setTextIsSelectable(true);
        scanOut.setPadding(0, dp(12), 0, 0);
        root.addView(scanOut);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
    }

    // ---------- البحث الشامل ----------

    private void runScan() {
        scanOut.setText("جارٍ البحث...");
        new Thread(() -> {
            List<String[]> rows = ManifestScan.scanAll(this);
            StringBuilder sb = new StringBuilder();
            sb.append("وُجد ").append(rows.size()).append(" إدخال، ")
                    .append(ManifestScan.found.size()).append(" Action مختلف:\n\n");
            int n = 0;
            for (String[] r : rows) {
                if (n++ >= 250) {
                    sb.append("... (اقتُصر العرض على 250 سطراً)\n");
                    break;
                }
                sb.append(r[0]).append("\n    ← ").append(r[1]).append("  [").append(r[2])
                        .append("]  ").append(shorten(r[3], r[1])).append("\n");
            }
            final String text = sb.toString();
            runOnUiThread(() -> scanOut.setText(text));
        }).start();
    }

    private void showActions() {
        final String pkg = pkgEdit.getText().toString().trim();
        List<String[]> rows = ManifestScan.manifestActions(this, pkg);
        StringBuilder sb = new StringBuilder("Actions في " + pkg + " (" + rows.size() + "):\n\n");
        for (String[] r : rows) {
            sb.append("[").append(r[1]).append("] ").append(shorten(r[2], pkg))
                    .append("\n    ").append(r[0]).append("\n");
        }
        scanOut.setText(sb.toString());
    }

    private static String shorten(String name, String pkg) {
        if (name != null && name.startsWith(pkg)) {
            return name.substring(pkg.length());
        }
        return name == null ? "" : name;
    }

    // ---------- شاشات الحزمة ----------

    private void load() {
        list.removeAllViews();
        final String pkg = pkgEdit.getText().toString().trim();
        PackageInfo pi;
        try {
            pi = getPackageManager().getPackageInfo(pkg,
                    PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES);
        } catch (Exception e) {
            addText("الحزمة غير موجودة: " + pkg);
            return;
        }

        if (pi.activities == null || pi.activities.length == 0) {
            addText("لا توجد شاشات (Activities) في هذه الحزمة.");
        } else {
            addText("الشاشات (" + pi.activities.length + "):");
            for (ActivityInfo ai : pi.activities) {
                addActivityRow(pkg, ai);
            }
        }

        if (pi.services != null && pi.services.length > 0) {
            StringBuilder sb = new StringBuilder("\nالخدمات (للمعلومة):\n");
            for (ServiceInfo si : pi.services) {
                sb.append(si.name).append("\n");
            }
            addText(sb.toString());
        }
    }

    private void addActivityRow(final String pkg, ActivityInfo ai) {
        final String target = pkg + "/" + ai.name;

        TextView name = new TextView(this);
        name.setText(shorten(ai.name, pkg) + (ai.exported ? "   [مفتوحة]" : "   [غير مفتوحة]"));
        name.setTextIsSelectable(true);
        name.setPadding(0, dp(14), 0, 0);
        list.addView(name);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(smallButton("جرّب", v -> {
            boolean ok = Util.launch(this, target);
            toast(ok ? "تم الفتح" : "تعذر الفتح (انظر السجل)");
        }));
        row.addView(smallButton("اعتمد للخلفية", v -> {
            cfg.put("rear_comp", target);
            toast("اعتُمدت للخلفية");
        }));
        row.addView(smallButton("اعتمد للأمامية", v -> {
            cfg.put("front_comp", target);
            toast("اعتُمدت للأمامية");
        }));
        list.addView(row);
    }

    private Button smallButton(String text, View.OnClickListener l) {
        Button bt = new Button(this);
        bt.setText(text);
        bt.setOnClickListener(l);
        return bt;
    }

    private void addText(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextIsSelectable(true);
        tv.setPadding(0, dp(8), 0, 0);
        list.addView(tv);
    }

    private void toast(String t) {
        Toast.makeText(this, t, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
