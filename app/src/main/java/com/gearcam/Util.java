package com.gearcam;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import java.io.FileInputStream;

public class Util {

    /** يفتح تطبيقاً حسب اسم الحزمة. */
    public static boolean launch(Context c, String pkg) {
        if (pkg == null || pkg.trim().isEmpty()) {
            return false;
        }
        try {
            Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg.trim());
            if (i == null) {
                Logx.add("التطبيق غير موجود: " + pkg);
                return false;
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            Logx.add("تعذر فتح " + pkg + ": " + e);
            return false;
        }
    }

    /** يعود إلى الشاشة الرئيسية. */
    public static void home(Context c) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception e) {
            Logx.add("تعذر الرجوع للرئيسية: " + e);
        }
    }

    /** يقرأ أول 64 بايت من ملف نصي، أو null عند الفشل. */
    public static String readFile(String path) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(path);
            byte[] buf = new byte[64];
            int n = in.read(buf);
            if (n <= 0) {
                return "";
            }
            return new String(buf, 0, n, "UTF-8").trim();
        } catch (Exception e) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** يقرأ قيمة من إعدادات النظام (system ثم global ثم secure). */
    public static String readSetting(ContentResolver cr, String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String v = null;
        try {
            v = Settings.System.getString(cr, name);
            if (v == null) {
                v = Settings.Global.getString(cr, name);
            }
            if (v == null) {
                v = Settings.Secure.getString(cr, name);
            }
        } catch (Exception ignored) {
        }
        return v;
    }
}
