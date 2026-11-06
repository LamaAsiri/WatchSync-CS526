package com.cs526.watchsync.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.cs526.watchsync.model.Show;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite copy of "My Shows" on this phone.
 * Screens read from here first (fast, works offline), then Firebase updates it.
 */
public class LocalDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "watchsync.db";
    private static final int DB_VERSION = 1;
    public static final String TABLE = "my_shows";

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
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
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

    public void clear() {
        getWritableDatabase().delete(TABLE, null, null);
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
}
