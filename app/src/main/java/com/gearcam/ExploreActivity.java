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

/**
 * مستكشف شاشات تطبيق: يعرض كل Activity داخل حزمة (مثل com.autochips.android.backcar)
 * ويسمح بتجربة كل واحدة واعتمادها للكاميرا الخلفية أو الأمامية.
 */
public class ExploreActivity extends Activity {
    private LinearLayout root;
    private LinearLayout list;
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
        help.setText("اكتب اسم الحزمة واضغط \"عرض\". لكل شاشة اضغط \"جرّب\" لترى ماذا تفتح. "
                + "عندما تجد الشاشة التي تعرض الكاميرا الخلفية اضغط \"اعتمد للخلفية\" "
                + "(وكذلك \"اعتمد للأمامية\" للأمامية). ثم ارجع واضغط \"حفظ وتشغيل الخدمة\".\n"
                + "ملاحظة: الشاشات التي لا تفتح غالباً محمية من التطبيقات الأخرى.");
        root.addView(help);

        pkgEdit = new EditText(this);
        pkgEdit.setSingleLine(true);
        pkgEdit.setText("com.autochips.android.backcar");
        root.addView(pkgEdit);

        Button show = new Button(this);
        show.setText("عرض");
        show.setOnClickListener(v -> load());
        root.addView(show);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        load();
    }

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
        String shortName = ai.name.startsWith(pkg) ? ai.name.substring(pkg.length()) : ai.name;
        name.setText(shortName + (ai.exported ? "   [مفتوحة]" : "   [غير مفتوحة]"));
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
