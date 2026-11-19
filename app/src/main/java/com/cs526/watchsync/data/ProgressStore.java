package com.cs526.watchsync.data;

import android.content.Context;

import com.cs526.watchsync.model.Show;
import com.cs526.watchsync.util.EpisodeMath;
import com.google.android.gms.tasks.Task;

/** Saves a show in My Shows with my progress (Firebase + SQLite at the same time). */
public final class ProgressStore {
    private ProgressStore() {}

    public static Task<Void> save(Context c, Show show, int progress) {
        LocalDb db = LocalDb.get(c);
        Show before = db.find(show.tmdbId);
        int max = show.movie() ? 1 : (show.totalEpisodes > 0 ? show.totalEpisodes : Math.max(progress, 0));
        show.progress = Math.max(0, Math.min(progress, max));
        show.updatedAt = System.currentTimeMillis();
        db.upsert(show);
        logChange(db, show, before);
        return Db.me().child("shows").child(String.valueOf(show.tmdbId)).setValue(show.toMap());
    }

    public static Task<Void> remove(Context c, int tmdbId) {
        LocalDb db = LocalDb.get(c);
        Show before = db.find(tmdbId);
        if (before != null) db.logActivity("remove", "Removed " + before.title + " from My Shows");
        db.delete(tmdbId);
        return Db.me().child("shows").child(String.valueOf(tmdbId)).removeValue();
    }

    /** Writes one line in recent_activity (SQLite) about what changed. */
    private static void logChange(LocalDb db, Show show, Show before) {
        if (before == null) {
            db.logActivity("add", "Added " + show.title + " to My Shows");
        } else if (show.progress > before.progress) {
            db.logActivity("watch", show.movie() ? "Watched " + show.title
                    : "Watched " + show.title + " " + EpisodeMath.label(show.progress, show.seasonCounts, false));
        }
    }
}
