package com.gearcam;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;

/** خدمة تراقب إشارة القير حسب المصدر المختار في الإعدادات. */
public class GearService extends Service {
    private BroadcastReceiver receiver;
    private ContentObserver observer;
    private volatile boolean polling;
    private Thread pollThread;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, buildNotification());
        teardown();
        setup();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        teardown();
        super.onDestroy();
    }

    private Notification buildNotification() {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(
                    new NotificationChannel("gear", "GearCam", NotificationManager.IMPORTANCE_LOW));
            b = new Notification.Builder(this, "gear");
        } else {
            b = new Notification.Builder(this);
        }
        b.setContentTitle("Gear Cam");
        b.setContentText("مراقبة القير تعمل");
        b.setSmallIcon(android.R.drawable.ic_menu_camera);
        return b.build();
    }

    private void setup() {
        Config c = new Config(this);
        final GearController gc = GearController.get(this);
        String src = c.get("source", Config.SRC_BROADCAST);

        if (Config.SRC_BROADCAST.equals(src)) {
            IntentFilter f = new IntentFilter();
            int n = 0;
            for (String a : c.get("bc_actions", "").split(",")) {
                a = a.trim();
                if (!a.isEmpty()) {
                    f.addAction(a);
                    n++;
                }
            }
            if (n == 0) {
                Logx.add("لم تُحدد أسماء Broadcast");
                return;
            }
            final String key = c.get("key", "").trim();
            receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent in) {
                    String v;
                    if (key.isEmpty()) {
                        v = in.getAction();
                    } else {
                        Bundle e = in.getExtras();
                        Object o = (e == null) ? null : e.get(key);
                        if (o == null) {
                            Logx.add("وصل " + in.getAction() + " بدون المفتاح " + key);
                            return;
                        }
                        v = String.valueOf(o);
                    }
                    gc.onValue(v);
                }
            };
            registerReceiver(receiver, f);
            Logx.add("استماع Broadcast: " + c.get("bc_actions", ""));

        } else if (Config.SRC_FILE.equals(src)) {
            final String path = c.get("file_path", "").trim();
            if (path.isEmpty()) {
                Logx.add("لم يُحدد مسار الملف");
                return;
            }
            polling = true;
            pollThread = new Thread(() -> {
                String last = null;
                boolean errLogged = false;
                while (polling) {
                    String v = Util.readFile(path);
                    if (v == null) {
                        if (!errLogged) {
                            Logx.add("تعذر قراءة الملف: " + path);
                            errLogged = true;
                        }
                    } else {
                        errLogged = false;
                        if (!v.equals(last)) {
                            last = v;
                            gc.onValue(v);
                        }
                    }
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            });
            pollThread.start();
            Logx.add("مراقبة الملف: " + path);

        } else if (Config.SRC_SETTING.equals(src)) {
            final String name = c.get("setting_name", "").trim();
            if (name.isEmpty()) {
                Logx.add("لم يُحدد اسم الإعداد");
                return;
            }
            observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    String v = Util.readSetting(getContentResolver(), name);
                    if (v != null) {
                        gc.onValue(v);
                    }
                }
            };
            getContentResolver().registerContentObserver(Settings.System.CONTENT_URI, true, observer);
            getContentResolver().registerContentObserver(Settings.Global.CONTENT_URI, true, observer);
            getContentResolver().registerContentObserver(Settings.Secure.CONTENT_URI, true, observer);
            Logx.add("مراقبة إعداد: " + name);
            String v0 = Util.readSetting(getContentResolver(), name);
            if (v0 != null) {
                gc.onValue(v0);
            }
        }
    }

    private void teardown() {
        polling = false;
        if (pollThread != null) {
            pollThread.interrupt();
            pollThread = null;
        }
        if (receiver != null) {
            try {
                unregisterReceiver(receiver);
            } catch (Exception ignored) {
            }
            receiver = null;
        }
        if (observer != null) {
            try {
                getContentResolver().unregisterContentObserver(observer);
            } catch (Exception ignored) {
            }
            observer = null;
        }
    }
}
