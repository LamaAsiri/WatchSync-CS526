package com.cs526.watchsync.model;

import com.cs526.watchsync.util.EpisodeMath;
import com.cs526.watchsync.util.Snap;
import com.google.firebase.database.DataSnapshot;

import java.util.HashMap;
import java.util.Map;

/** A watch party: the group watches one episode of the Now Watching show together. */
public class Party {
    public String id = "";
    public int tmdbId;
    public String showTitle = "";
    public String posterPath = "";
    public String mediaType = "tv";
    public String seasonCounts = "";
    public int totalEpisodes;
    public int episode;          // running episode number they will watch together
    public long time;            // start time in ms
    public String note = "";
    public String createdBy = "";
    public String createdByName = "";
    public Map<String, String> rsvp = new HashMap<>(); // uid -> "going" / "no"

    public boolean movie() {
        return "movie".equals(mediaType);
    }

    public String episodeLabel() {
        return EpisodeMath.label(episode, seasonCounts, movie());
    }

    public int goingCount() {
        int n = 0;
        for (String v : rsvp.values()) if ("going".equals(v)) n++;
        return n;
    }

    /** Episodes I still have to watch before the party (0 = ready). */
    public int episodesToCatchUp(int myProgress) {
        int needed = movie() ? 0 : episode - 1;
        return Math.max(0, needed - myProgress);
    }

    public Show toShow() {
        Show s = new Show();
        s.tmdbId = tmdbId; s.title = showTitle; s.posterPath = posterPath; s.mediaType = mediaType;
        s.seasonCounts = seasonCounts; s.totalEpisodes = totalEpisodes;
        return s;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("tmdbId", tmdbId);
        m.put("showTitle", showTitle);
        m.put("posterPath", posterPath);
        m.put("mediaType", mediaType);
        m.put("seasonCounts", seasonCounts);
        m.put("totalEpisodes", totalEpisodes);
        m.put("episode", episode);
        m.put("time", time);
        m.put("note", note);
        m.put("createdBy", createdBy);
        m.put("createdByName", createdByName);
        m.put("rsvp", new HashMap<>(rsvp));
        return m;
    }

    public static Party from(DataSnapshot s) {
        Party p = new Party();
        p.id = s.getKey() == null ? "" : s.getKey();
        p.tmdbId = Snap.num(s.child("tmdbId"));
        p.showTitle = Snap.str(s.child("showTitle"));
        p.posterPath = Snap.str(s.child("posterPath"));
        p.mediaType = s.hasChild("mediaType") ? Snap.str(s.child("mediaType")) : "tv";
        p.seasonCounts = Snap.str(s.child("seasonCounts"));
        p.totalEpisodes = Snap.num(s.child("totalEpisodes"));
        p.episode = Snap.num(s.child("episode"));
        p.time = Snap.lng(s.child("time"));
        p.note = Snap.str(s.child("note"));
        p.createdBy = Snap.str(s.child("createdBy"));
        p.createdByName = Snap.str(s.child("createdByName"));
        for (DataSnapshot r : s.child("rsvp").getChildren()) {
            if (r.getKey() != null) p.rsvp.put(r.getKey(), Snap.str(r));
        }
        return p;
    }
}
