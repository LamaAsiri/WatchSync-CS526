package com.cs526.watchsync.reminders;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.cs526.watchsync.data.Prefs;

/**
 * Two alarms per watch party I'm going to (AlarmManager + BroadcastReceiver):
 * 1) 1 hour before it starts: "Your watch party starts in 1 hour"  (PartyReminderReceiver)
 * 2) 20 minutes after it ends: "How was the watch party?"          (AfterPartyReceiver)
 */
public final class ReminderScheduler {
    private static final long ONE_HOUR = 60L * 60L * 1000L;
    /** The "How was it?" notification comes 20 minutes after the party ends. */
    public static final long AFTER_PARTY_DELAY = 20L * 60L * 1000L;

    private ReminderScheduler() {}

    public static void schedule(Context c, String partyId, String text, long partyTime) {
        if (!Prefs.reminders(c)) return;
        long at = partyTime - ONE_HOUR;
        if (at <= System.currentTimeMillis()) return;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(c, partyId, text));
    }

    /** "How was the watch party?" a little after it ends. Opens the party to check in and rate. */
    public static void scheduleAfterParty(Context c, String groupCode, String partyId, String text, long endTime) {
        if (!Prefs.reminders(c) || endTime <= 0) return;
        long at = endTime + AFTER_PARTY_DELAY;
        if (at <= System.currentTimeMillis()) return;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, afterPending(c, groupCode, partyId, text));
    }

    /** Cancels both alarms (I can't go any more, or the party was deleted). */
    public static void cancel(Context c, String partyId) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(pending(c, partyId, ""));
        am.cancel(afterPending(c, "", partyId, ""));
    }

    private static PendingIntent pending(Context c, String partyId, String text) {
        Intent i = new Intent(c, PartyReminderReceiver.class);
        i.putExtra(PartyReminderReceiver.EXTRA_TEXT, text);
        return PendingIntent.getBroadcast(c, partyId.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent afterPending(Context c, String groupCode, String partyId, String text) {
        Intent i = new Intent(c, AfterPartyReceiver.class);
        i.putExtra(AfterPartyReceiver.EXTRA_CODE, groupCode);
        i.putExtra(AfterPartyReceiver.EXTRA_PARTY, partyId);
        i.putExtra(AfterPartyReceiver.EXTRA_TEXT, text);
        return PendingIntent.getBroadcast(c, (partyId + "#after").hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
