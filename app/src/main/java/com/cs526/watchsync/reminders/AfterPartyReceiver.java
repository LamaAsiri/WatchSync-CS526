package com.cs526.watchsync.reminders;

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
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;

import com.cs526.watchsync.R;
import com.cs526.watchsync.groups.GroupActivity;
import com.cs526.watchsync.main.MainActivity;
import com.cs526.watchsync.parties.PartyDetailActivity;
import com.cs526.watchsync.util.Lang;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Fired by AlarmManager 20 minutes after a watch party ends: "How was the watch party?".
 * Tapping it opens the party, which asks "Did you watch it?" and then lets me rate it (optional).
 */
public class AfterPartyReceiver extends BroadcastReceiver {
    public static final String EXTRA_CODE = "code";
    public static final String EXTRA_PARTY = "party";
    public static final String EXTRA_TEXT = "text";
    private static final String CHANNEL = "party_reminders";

    @Override
    public void onReceive(Context c, Intent intent) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return; // logged out since
        String code = intent.getStringExtra(EXTRA_CODE);
        String partyId = intent.getStringExtra(EXTRA_PARTY);
        if (code == null || code.isEmpty() || partyId == null || partyId.isEmpty()) return;

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

        // Back from the party screen goes to the app's Home, not out of the app
        Intent party = new Intent(c, PartyDetailActivity.class);
        party.putExtra(GroupActivity.EXTRA_CODE, code);
        party.putExtra(PartyDetailActivity.EXTRA_PARTY, partyId);
        PendingIntent open = TaskStackBuilder.create(c)
                .addNextIntent(new Intent(c, MainActivity.class))
                .addNextIntent(party)
                .getPendingIntent((partyId + "#after").hashCode(),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String text = intent.getStringExtra(EXTRA_TEXT);
        nm.notify((partyId + "#after").hashCode(),
                new NotificationCompat.Builder(c, CHANNEL)
                        .setSmallIcon(R.drawable.ic_tv)
                        .setContentTitle(Lang.s(R.string.notif_after_title))
                        .setContentText(Lang.s(R.string.notif_after_text, text == null ? "" : text))
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(Lang.s(R.string.notif_after_text, text == null ? "" : text)))
                        .setContentIntent(open)
                        .setAutoCancel(true)
                        .build());
    }
}
