package com.cs526.watchsync.data;

import android.content.Context;

import com.cs526.watchsync.model.Show;
import com.google.android.gms.tasks.Task;

/** Saves a show in My Shows with my progress (Firebase + SQLite at the same time). */
public final class ProgressStore {
    private ProgressStore() {}

    public static Task<Void> save(Context c, Show show, int progress) {
        int max = show.movie() ? 1 : (show.totalEpisodes > 0 ? show.totalEpisodes : Math.max(progress, 0));
        show.progress = Math.max(0, Math.min(progress, max));
        show.updatedAt = System.currentTimeMillis();
        LocalDb.get(c).upsert(show);
        return Db.me().child("shows").child(String.valueOf(show.tmdbId)).setValue(show.toMap());
    }

    public static Task<Void> remove(Context c, int tmdbId) {
        LocalDb.get(c).delete(tmdbId);
        return Db.me().child("shows").child(String.valueOf(tmdbId)).removeValue();
    }
}
