package com.cs526.watchsync.reminders;

import com.cs526.watchsync.util.Lang;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.cs526.watchsync.R;
import com.cs526.watchsync.auth.SplashActivity;

/** Fired by AlarmManager: shows "Your watch party starts in 1 hour". */
public class PartyReminderReceiver extends BroadcastReceiver {
    public static final String EXTRA_TEXT = "text";
    private static final String CHANNEL = "party_reminders";

    @Override
    public void onReceive(Context c, Intent intent) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, Lang.s(R.string.channel_party_reminders),
                    NotificationManager.IMPORTANCE_DEFAULT));
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        String text = intent.getStringExtra(EXTRA_TEXT);
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, SplashActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        nm.notify((int) (System.currentTimeMillis() % Integer.MAX_VALUE),
                new NotificationCompat.Builder(c, CHANNEL)
                        .setSmallIcon(R.drawable.ic_tv)
                        .setContentTitle(Lang.s(R.string.notif_party_title))
                        .setContentText(text == null || text.isEmpty() ? Lang.s(R.string.notif_party_default) : text)
                        .setContentIntent(open)
                        .setAutoCancel(true)
                        .build());
    }
}
