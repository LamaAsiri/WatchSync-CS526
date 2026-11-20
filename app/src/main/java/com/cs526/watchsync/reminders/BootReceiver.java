package com.cs526.watchsync.reminders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.cs526.watchsync.data.LocalDb;

/**
 * System broadcast: ACTION_BOOT_COMPLETED (sent once the phone finishes restarting).
 * Android deletes every AlarmManager alarm when the phone turns off, so this receiver
 * reads my upcoming parties from SQLite and sets their reminders (before and after) again.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        LocalDb db = LocalDb.get(context);
        for (LocalDb.SavedParty p : db.upcomingPartiesImGoingTo()) {
            ReminderScheduler.schedule(context, p.id, p.title + " · " + p.episodeLabel, p.time);
        }
        // and the "How was the watch party?" alarms for parties that haven't ended (+20 min) yet
        for (LocalDb.SavedParty p : db.partiesImGoingToNotOverYet(ReminderScheduler.AFTER_PARTY_DELAY)) {
            ReminderScheduler.scheduleAfterParty(context, p.groupCode, p.id, p.title + " · " + p.episodeLabel, p.endTime);
        }
    }
}
