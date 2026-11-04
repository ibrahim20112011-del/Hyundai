package com.gearcam;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** يقرأ ملف AndroidManifest لأي تطبيق مثبّت ويستخرج الـ Actions التي يستمع لها. */
public class ManifestScan {

    /** كل الـ Actions التي وجدها آخر بحث شامل (تستخدمها أداة الاكتشاف للاستماع). */
    public static final Set<String> found = Collections.synchronizedSet(new TreeSet<String>());

    private static final String[] KEYS = {
            "backcar", "back_car", "reverse", "reversing", "gear", "avin", "brake",
            "parking", "autochips", "camera", "rear", "canbus"
    };

    /** صفوف {action, نوع المكوّن, اسم المكوّن} لحزمة واحدة. */
    public static List<String[]> manifestActions(Context c, String pkg) {
        List<String[]> out = new ArrayList<>();
        XmlResourceParser p = null;
        try {
            Resources r = c.getPackageManager().getResourcesForApplication(pkg);
            p = r.getAssets().openXmlResourceParser("AndroidManifest.xml");
            String compType = null;
            String compName = null;
            int ev = p.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    String tag = p.getName();
                    if (tag.equals("activity") || tag.equals("activity-alias")
                            || tag.equals("service") || tag.equals("receiver")
                            || tag.equals("provider")) {
                        compType = tag;
                        compName = attr(p, "name");
                    } else if (tag.equals("action")) {
                        String a = attr(p, "name");
                        if (a != null && compType != null) {
                            out.add(new String[]{a, compType, compName == null ? "" : compName});
                        }
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    String tag = p.getName();
                    if (compType != null && tag.equals(compType)) {
                        compType = null;
                        compName = null;
                    }
                }
                ev = p.next();
            }
        } catch (Throwable t) {
            // حزمة لا يمكن قراءتها: نتجاهلها
        } finally {
            if (p != null) {
                p.close();
            }
        }
        return out;
    }

    /** يبحث في كل التطبيقات عن Actions تخص القير أو الكاميرا. صفوفها {action, حزمة, نوع, اسم}. */
    public static List<String[]> scanAll(Context c) {
        PackageManager pm = c.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        List<String[]> res = new ArrayList<>();
        found.clear();
        for (ApplicationInfo ai : apps) {
            for (String[] r : manifestActions(c, ai.packageName)) {
                String low = r[0].toLowerCase(Locale.US);
                for (String k : KEYS) {
                    if (low.contains(k)) {
                        res.add(new String[]{r[0], ai.packageName, r[1], r[2]});
                        found.add(r[0]);
                        break;
                    }
                }
            }
        }
        return res;
    }

    private static String attr(XmlResourceParser p, String name) {
        for (int i = 0; i < p.getAttributeCount(); i++) {
            if (name.equals(p.getAttributeName(i))) {
                return p.getAttributeValue(i);
            }
        }
        return null;
    }
}
