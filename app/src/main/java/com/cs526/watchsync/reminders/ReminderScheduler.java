package com.cs526.watchsync.reminders;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.cs526.watchsync.data.Prefs;

/** Sets an alarm 1 hour before a watch party (AlarmManager + BroadcastReceiver). */
public final class ReminderScheduler {
    private static final long ONE_HOUR = 60L * 60L * 1000L;

    private ReminderScheduler() {}

    public static void schedule(Context c, String partyId, String text, long partyTime) {
        if (!Prefs.reminders(c)) return;
        long at = partyTime - ONE_HOUR;
        if (at <= System.currentTimeMillis()) return;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(c, partyId, text));
    }

    public static void cancel(Context c, String partyId) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pending(c, partyId, ""));
    }

    private static PendingIntent pending(Context c, String partyId, String text) {
        Intent i = new Intent(c, PartyReminderReceiver.class);
        i.putExtra(PartyReminderReceiver.EXTRA_TEXT, text);
        return PendingIntent.getBroadcast(c, partyId.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
