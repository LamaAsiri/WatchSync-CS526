package com.cs526.watchsync.model;

import android.content.Intent;

import com.cs526.watchsync.util.EpisodeMath;
import com.cs526.watchsync.util.Snap;
import com.google.firebase.database.DataSnapshot;

import java.util.HashMap;
import java.util.Map;

/** A movie, TV show or anime. Same class is used for search results, My Shows and group lists. */
public class Show {
    public int tmdbId;              // negative = added manually (not from TMDB)
    public String title = "";
    public String posterPath = "";
    public String mediaType = "tv"; // "tv" or "movie"
    public String year = "";
    public String overview = "";
    public boolean anime;
    public int totalEpisodes;       // movies = 1
    public String seasonCounts = ""; // "24,23"
    public int progress;            // my last watched episode number (0 = not started)
    public long updatedAt;
    public String addedByName = ""; // only used in group watchlists

    public boolean movie() {
        return "movie".equals(mediaType);
    }

    public String typeLabel() {
        if (movie()) return "Movie";
        return anime ? "Anime" : "TV show";
    }

    public String summary() {
        StringBuilder sb = new StringBuilder(typeLabel());
        if (!year.isEmpty()) sb.append(" · ").append(year);
        if (!movie() && totalEpisodes > 0) sb.append(" · ").append(totalEpisodes).append(" eps");
        return sb.toString();
    }

    public String progressLabel() {
        if (movie()) return progress >= 1 ? "Watched" : "Not watched yet";
        if (progress <= 0) return "Not started";
        if (totalEpisodes > 0 && progress >= totalEpisodes) return "Finished · " + totalEpisodes + "/" + totalEpisodes;
        return EpisodeMath.label(progress, seasonCounts, false) + " · " + progress + "/" + totalEpisodes;
    }

    public boolean finished() {
        return totalEpisodes > 0 && progress >= totalEpisodes;
    }

    /** Fields saved to Firebase. */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("tmdbId", tmdbId);
        m.put("title", title);
        m.put("posterPath", posterPath);
        m.put("mediaType", mediaType);
        m.put("year", year);
        m.put("overview", overview);
        m.put("anime", anime);
        m.put("totalEpisodes", totalEpisodes);
        m.put("seasonCounts", seasonCounts);
        m.put("progress", progress);
        m.put("updatedAt", updatedAt);
        return m;
    }

    public static Show from(DataSnapshot s) {
        Show show = new Show();
        show.tmdbId = Snap.num(s.child("tmdbId"));
        show.title = Snap.str(s.child("title"));
        show.posterPath = Snap.str(s.child("posterPath"));
        show.mediaType = s.hasChild("mediaType") ? Snap.str(s.child("mediaType")) : "tv";
        show.year = Snap.str(s.child("year"));
        show.overview = Snap.str(s.child("overview"));
        show.anime = Snap.bool(s.child("anime"));
        show.totalEpisodes = Snap.num(s.child("totalEpisodes"));
        show.seasonCounts = Snap.str(s.child("seasonCounts"));
        show.progress = Snap.num(s.child("progress"));
        show.updatedAt = Snap.lng(s.child("updatedAt"));
        show.addedByName = Snap.str(s.child("addedByName"));
        return show;
    }

    public Show copy() {
        Show c = new Show();
        c.tmdbId = tmdbId; c.title = title; c.posterPath = posterPath; c.mediaType = mediaType;
        c.year = year; c.overview = overview; c.anime = anime; c.totalEpisodes = totalEpisodes;
        c.seasonCounts = seasonCounts; c.progress = progress; c.updatedAt = updatedAt;
        c.addedByName = addedByName;
        return c;
    }

    public void putInto(Intent i) {
        i.putExtra("show_id", tmdbId);
        i.putExtra("show_title", title);
        i.putExtra("show_poster", posterPath);
        i.putExtra("show_type", mediaType);
        i.putExtra("show_year", year);
        i.putExtra("show_overview", overview);
        i.putExtra("show_anime", anime);
        i.putExtra("show_total", totalEpisodes);
        i.putExtra("show_seasons", seasonCounts);
    }

    public static Show fromIntent(Intent i) {
        Show s = new Show();
        s.tmdbId = i.getIntExtra("show_id", 0);
        s.title = nn(i.getStringExtra("show_title"));
        s.posterPath = nn(i.getStringExtra("show_poster"));
        s.mediaType = i.hasExtra("show_type") ? nn(i.getStringExtra("show_type")) : "tv";
        s.year = nn(i.getStringExtra("show_year"));
        s.overview = nn(i.getStringExtra("show_overview"));
        s.anime = i.getBooleanExtra("show_anime", false);
        s.totalEpisodes = i.getIntExtra("show_total", 0);
        s.seasonCounts = nn(i.getStringExtra("show_seasons"));
        return s;
    }

    private static String nn(String s) {
        return s == null ? "" : s;
    }
}
