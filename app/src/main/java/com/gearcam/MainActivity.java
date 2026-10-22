package com.gearcam;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private Config cfg;
    private final Map<String, EditText> fields = new LinkedHashMap<>();
    private final Map<String, CheckBox> checks = new LinkedHashMap<>();
    private final List<String> pkgs = new ArrayList<>();
    private final String[] sources = {Config.SRC_BROADCAST, Config.SRC_FILE, Config.SRC_SETTING};
    private Spinner rearSp, frontSp, srcSp;
    private TextView logView;
    private LinearLayout root;
    private final Handler ui = new Handler(Looper.getMainLooper());

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

        // تحميل قائمة التطبيقات
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(q, 0);
        Collections.sort(apps, new ResolveInfo.DisplayNameComparator(pm));
        List<String> labels = new ArrayList<>();
        labels.add("— غير محدد —");
        pkgs.add("");
        for (ResolveInfo r : apps) {
            labels.add(r.loadLabel(pm) + "  [" + r.activityInfo.packageName + "]");
            pkgs.add(r.activityInfo.packageName);
        }

        title("الكاميرات");
        label("تطبيق الكاميرا الخلفية (عند R)");
        rearSp = appSpinner(labels, cfg.get("rear_pkg", ""), null);
        label("تطبيق الكاميرا الأمامية (عند D) — مثل AVIN");
        frontSp = appSpinner(labels, cfg.get("front_pkg", ""), "avin");
        field("front_secs", "مدة بقاء الأمامية بالثواني", "10");
        check("go_home", "العودة للشاشة الرئيسية بعد انتهاء المدة", true);

        title("مصدر إشارة القير");
        label("المصدر");
        srcSp = new Spinner(this);
        ArrayAdapter<String> sa = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"Broadcast من النظام", "ملف نظام (sysfs)", "إعداد في Settings"});
        sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        srcSp.setAdapter(sa);
        String cur = cfg.get("source", Config.SRC_BROADCAST);
        for (int i = 0; i < sources.length; i++) {
            if (sources[i].equals(cur)) {
                srcSp.setSelection(i);
            }
        }
        root.addView(srcSp);

        field("bc_actions", "Broadcast: أسماء الـ Action مفصولة بفاصلة", "");
        field("key", "Broadcast: اسم الـ extra (فارغ = يُستخدم اسم الـ Action نفسه كقيمة)", "");
        field("file_path", "ملف: المسار الكامل", "");
        field("setting_name", "Settings: اسم الإعداد", "");
        field("val_r", "قيم تعني R (مفصولة بفاصلة)", "R");
        field("val_d", "قيم تعني D (مفصولة بفاصلة)", "D");
        check("rev_only", "الإشارة تخص الرجوع فقط: أي قيمة غير R بعد R تعني D", false);

        title("التحكم");
        button("حفظ وتشغيل الخدمة", v -> {
            save();
            restartService();
        });
        button("إيقاف الخدمة", v -> {
            cfg.putBool("enabled", false);
            stopService(new Intent(this, GearService.class));
            Logx.add("تم إيقاف الخدمة");
        });

        title("اختبار");
        button("جرّب فتح الخلفية", v -> {
            save();
            Util.launch(this, cfg.get("rear_pkg", ""));
        });
        button("جرّب فتح الأمامية", v -> {
            save();
            Util.launch(this, cfg.get("front_pkg", ""));
        });
        button("محاكاة R", v -> {
            save();
            GearController.get(this).simulate(GearController.R);
        });
        button("محاكاة D", v -> {
            save();
            GearController.get(this).simulate(GearController.D);
        });
        button("أداة اكتشاف إشارة القير", v ->
                startActivity(new Intent(this, DiagActivity.class)));

        title("السجل");
        logView = new TextView(this);
        logView.setTextIsSelectable(true);
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

    private void save() {
        cfg.put("rear_pkg", pkgs.get(rearSp.getSelectedItemPosition()));
        cfg.put("front_pkg", pkgs.get(frontSp.getSelectedItemPosition()));
        cfg.put("source", sources[srcSp.getSelectedItemPosition()]);
        for (Map.Entry<String, EditText> e : fields.entrySet()) {
            cfg.put(e.getKey(), e.getValue().getText().toString().trim());
        }
        for (Map.Entry<String, CheckBox> e : checks.entrySet()) {
            cfg.putBool(e.getKey(), e.getValue().isChecked());
        }
    }

    private void restartService() {
        cfg.putBool("enabled", true);
        Intent s = new Intent(this, GearService.class);
        stopService(s);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(s);
        } else {
            startService(s);
        }
        Logx.add("تم حفظ الإعدادات وتشغيل الخدمة");
    }

    // ---------- أدوات بناء الواجهة ----------

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void title(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(20);
        tv.setPadding(0, dp(18), 0, dp(4));
        root.addView(tv);
    }

    private void label(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setPadding(0, dp(8), 0, 0);
        root.addView(tv);
    }

    private void field(String key, String hint, String def) {
        label(hint);
        EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setText(cfg.get(key, def));
        fields.put(key, et);
        root.addView(et);
    }

    private void check(String key, String text, boolean def) {
        CheckBox cb = new CheckBox(this);
        cb.setText(text);
        cb.setChecked(cfg.getBool(key, def));
        checks.put(key, cb);
        root.addView(cb);
    }

    private void button(String text, View.OnClickListener l) {
        Button bt = new Button(this);
        bt.setText(text);
        bt.setOnClickListener(l);
        root.addView(bt);
    }

    private Spinner appSpinner(List<String> labels, String savedPkg, String guess) {
        Spinner sp = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(a);
        int sel = pkgs.indexOf(savedPkg);
        if (sel < 0 && guess != null) {
            for (int i = 0; i < labels.size(); i++) {
                if (labels.get(i).toLowerCase().contains(guess)) {
                    sel = i;
                    break;
                }
            }
        }
        if (sel > 0) {
            sp.setSelection(sel);
        }
        root.addView(sp);
        return sp;
    }
}
