package com.cs526.watchsync.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.cs526.watchsync.model.Party;
import com.cs526.watchsync.model.Show;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite database on this phone (watchsync.db), 3 tables:
 *
 * my_shows         copy of "My Shows" + my progress. Screens read from here first (fast, works offline).
 * parties          watch parties from all my groups. Used to bring reminders back after the phone restarts.
 * recent_activity  what I did lately (added a show, watched an episode, joined a group...). Shown on Home.
 */
public class LocalDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "watchsync.db";
    private static final int DB_VERSION = 2;
    public static final String TABLE = "my_shows";
    public static final String TABLE_PARTIES = "parties";
    public static final String TABLE_ACTIVITY = "recent_activity";

    private static LocalDb instance;

    public static synchronized LocalDb get(Context c) {
        if (instance == null) instance = new LocalDb(c.getApplicationContext());
        return instance;
    }

    private LocalDb(Context c) {
        super(c, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + "tmdb_id INTEGER PRIMARY KEY, "
                + "title TEXT, "
                + "poster_path TEXT, "
                + "media_type TEXT, "
                + "year TEXT, "
                + "overview TEXT, "
                + "anime INTEGER, "
                + "total_episodes INTEGER, "
                + "season_counts TEXT, "
                + "progress INTEGER, "
                + "updated_at INTEGER)");
        createV2Tables(db);
    }

    /** Tables added in version 2. */
    private void createV2Tables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_PARTIES + " ("
                + "party_id TEXT PRIMARY KEY, "
                + "group_code TEXT, "
                + "tmdb_id INTEGER, "
                + "show_title TEXT, "
                + "episode_label TEXT, "
                + "party_time INTEGER, "
                + "going INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_ACTIVITY + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "type TEXT, "
                + "message TEXT, "
                + "created_at INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // Version 1 only had my_shows: keep it and add the new tables
        if (oldV < 2) createV2Tables(db);
    }

    public void upsert(Show s) {
        getWritableDatabase().insertWithOnConflict(TABLE, null, values(s), SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void replaceAll(List<Show> shows) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE, null, null);
            for (Show s : shows) db.insert(TABLE, null, values(s));
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void delete(int tmdbId) {
        getWritableDatabase().delete(TABLE, "tmdb_id = ?", new String[]{String.valueOf(tmdbId)});
    }

    /** Log out: empty every table. */
    public void clear() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE, null, null);
        db.delete(TABLE_PARTIES, null, null);
        db.delete(TABLE_ACTIVITY, null, null);
    }

    public Show find(int tmdbId) {
        try (Cursor c = getReadableDatabase().query(TABLE, null, "tmdb_id = ?",
                new String[]{String.valueOf(tmdbId)}, null, null, null)) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    public int progressOf(int tmdbId) {
        Show s = find(tmdbId);
        return s == null ? 0 : s.progress;
    }

    public List<Show> all() {
        List<Show> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(TABLE, null, null, null, null, null, "updated_at DESC")) {
            while (c.moveToNext()) list.add(read(c));
        }
        return list;
    }

    private static ContentValues values(Show s) {
        ContentValues v = new ContentValues();
        v.put("tmdb_id", s.tmdbId);
        v.put("title", s.title);
        v.put("poster_path", s.posterPath);
        v.put("media_type", s.mediaType);
        v.put("year", s.year);
        v.put("overview", s.overview);
        v.put("anime", s.anime ? 1 : 0);
        v.put("total_episodes", s.totalEpisodes);
        v.put("season_counts", s.seasonCounts);
        v.put("progress", s.progress);
        v.put("updated_at", s.updatedAt);
        return v;
    }

    private static Show read(Cursor c) {
        Show s = new Show();
        s.tmdbId = c.getInt(c.getColumnIndexOrThrow("tmdb_id"));
        s.title = str(c, "title");
        s.posterPath = str(c, "poster_path");
        s.mediaType = str(c, "media_type");
        s.year = str(c, "year");
        s.overview = str(c, "overview");
        s.anime = c.getInt(c.getColumnIndexOrThrow("anime")) == 1;
        s.totalEpisodes = c.getInt(c.getColumnIndexOrThrow("total_episodes"));
        s.seasonCounts = str(c, "season_counts");
        s.progress = c.getInt(c.getColumnIndexOrThrow("progress"));
        s.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"));
        return s;
    }

    private static String str(Cursor c, String col) {
        String v = c.getString(c.getColumnIndexOrThrow(col));
        return v == null ? "" : v;
    }

    // ---------- parties ----------

    /** Saves (or updates) one party and whether I'm going. */
    public void saveParty(String code, Party p, boolean going) {
        ContentValues v = new ContentValues();
        v.put("party_id", p.id);
        v.put("group_code", code);
        v.put("tmdb_id", p.tmdbId);
        v.put("show_title", p.showTitle);
        v.put("episode_label", p.episodeLabel());
        v.put("party_time", p.time);
        v.put("going", going ? 1 : 0);
        getWritableDatabase().insertWithOnConflict(TABLE_PARTIES, null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void deleteParty(String partyId) {
        getWritableDatabase().delete(TABLE_PARTIES, "party_id = ?", new String[]{partyId});
    }

    /** Parties I said "I'm in" to that haven't started yet (soonest first). */
    public List<SavedParty> upcomingPartiesImGoingTo() {
        List<SavedParty> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(TABLE_PARTIES, null,
                "going = 1 AND party_time > ?", new String[]{String.valueOf(System.currentTimeMillis())},
                null, null, "party_time ASC")) {
            while (c.moveToNext()) {
                SavedParty p = new SavedParty();
                p.id = str(c, "party_id");
                p.groupCode = str(c, "group_code");
                p.title = str(c, "show_title");
                p.episodeLabel = str(c, "episode_label");
                p.time = c.getLong(c.getColumnIndexOrThrow("party_time"));
                list.add(p);
            }
        }
        return list;
    }

    /** One row of the parties table. */
    public static class SavedParty {
        public String id = "";
        public String groupCode = "";
        public String title = "";
        public String episodeLabel = "";
        public long time;
    }

    // ---------- recent_activity ----------

    public static final int ACTIVITY_LIMIT = 30;

    /** Adds one line to my recent activity, and keeps only the newest 30. */
    public void logActivity(String type, String message) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("type", type);
        v.put("message", message);
        v.put("created_at", System.currentTimeMillis());
        db.insert(TABLE_ACTIVITY, null, v);
        db.execSQL("DELETE FROM " + TABLE_ACTIVITY + " WHERE id NOT IN (SELECT id FROM "
                + TABLE_ACTIVITY + " ORDER BY id DESC LIMIT " + ACTIVITY_LIMIT + ")");
    }

    /** The newest activity first. */
    public List<ActivityItem> recentActivity(int limit) {
        List<ActivityItem> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(TABLE_ACTIVITY, null, null, null,
                null, null, "id DESC", String.valueOf(limit))) {
            while (c.moveToNext()) {
                ActivityItem a = new ActivityItem();
                a.type = str(c, "type");
                a.message = str(c, "message");
                a.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
                list.add(a);
            }
        }
        return list;
    }

    /** One row of the recent_activity table. */
    public static class ActivityItem {
        public String type = "";
        public String message = "";
        public long createdAt;
    }
}
