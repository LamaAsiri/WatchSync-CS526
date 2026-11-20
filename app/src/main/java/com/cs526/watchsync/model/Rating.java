package com.cs526.watchsync.model;

import com.cs526.watchsync.util.Snap;
import com.google.firebase.database.DataSnapshot;

import java.util.HashMap;
import java.util.Map;

/**
 * My stars (0.5 to 5, half stars allowed) and an optional review (max 500 characters)
 * for one episode, or for a movie (episode = 1).
 *
 * Firebase: users/{uid}/ratings/{tmdbId}/{episode}   stars, review, showTitle, label, ratedAt
 * SQLite:   ratings table (my own ratings, on this phone)
 */
public class Rating {
    public static final int MAX_REVIEW = 500;

    public int tmdbId;
    public int episode;          // running episode number (1 for a movie)
    public float stars;
    public String review = "";
    public String showTitle = "";
    public String label = "";    // "S1 E3" or "Movie"
    public long ratedAt;

    // Filled in when it is a friend's rating (not saved)
    public String uid = "";
    public String name = "";
    public String color = "";

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("stars", (double) stars);
        m.put("review", review);
        m.put("showTitle", showTitle);
        m.put("label", label);
        m.put("ratedAt", ratedAt);
        return m;
    }

    /** s = users/{uid}/ratings/{tmdbId}/{episode} */
    public static Rating from(int tmdbId, DataSnapshot s) {
        Rating r = new Rating();
        r.tmdbId = tmdbId;
        try {
            r.episode = Integer.parseInt(s.getKey() == null ? "0" : s.getKey());
        } catch (NumberFormatException e) {
            r.episode = 0;
        }
        Object stars = s.child("stars").getValue();
        r.stars = stars instanceof Number ? ((Number) stars).floatValue() : 0f;
        r.review = Snap.str(s.child("review"));
        r.showTitle = Snap.str(s.child("showTitle"));
        r.label = Snap.str(s.child("label"));
        r.ratedAt = Snap.lng(s.child("ratedAt"));
        return r;
    }

    /** "3.5" or "4" */
    public static String starsText(float stars) {
        return stars == Math.floor(stars) ? String.valueOf((int) stars) : String.valueOf(stars);
    }
}
