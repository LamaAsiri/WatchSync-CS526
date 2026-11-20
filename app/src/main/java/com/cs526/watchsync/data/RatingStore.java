package com.cs526.watchsync.data;

import android.content.Context;

import com.cs526.watchsync.R;
import com.cs526.watchsync.model.Member;
import com.cs526.watchsync.model.Rating;
import com.cs526.watchsync.util.Lang;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Saves my ratings (Firebase + SQLite at the same time) and loads my friends' ratings.
 * Friends = everyone in my groups. Their ratings are readable because users/{uid} is readable
 * by any signed-in user (see database.rules.json), so no rule change was needed.
 */
public final class RatingStore {
    public interface Callback {
        void done(List<Rating> mine, List<Rating> friends);
    }

    private RatingStore() {}

    private static com.google.firebase.database.DatabaseReference ref(String uid, int tmdbId) {
        return Db.user(uid).child("ratings").child(String.valueOf(tmdbId));
    }

    public static Task<Void> save(Context c, Rating r) {
        r.ratedAt = System.currentTimeMillis();
        LocalDb db = LocalDb.get(c);
        db.saveRating(r);
        db.logActivity("rate", Lang.s(R.string.log_rated, r.showTitle, r.label, Rating.starsText(r.stars)));
        return ref(Db.uid(), r.tmdbId).child(String.valueOf(r.episode)).setValue(r.toMap());
    }

    public static Task<Void> remove(Context c, int tmdbId, int episode) {
        LocalDb.get(c).deleteRating(tmdbId, episode);
        return ref(Db.uid(), tmdbId).child(String.valueOf(episode)).removeValue();
    }

    /**
     * My ratings for this show + every friend's.
     * 1) users/me/groups  2) groups/{code}/members  3) users/{friend}/ratings/{tmdbId}
     */
    public static void load(Context c, int tmdbId, Callback cb) {
        Context app = c.getApplicationContext();
        String me = Db.uid();
        ref(me, tmdbId).get().addOnCompleteListener(mineTask -> {
            List<Rating> mine = new ArrayList<>();
            if (mineTask.isSuccessful() && mineTask.getResult() != null) {
                LocalDb db = LocalDb.get(app);
                for (DataSnapshot s : mineTask.getResult().getChildren()) {
                    Rating r = Rating.from(tmdbId, s);
                    if (r.episode <= 0) continue;
                    mine.add(r);
                    db.saveRating(r); // keep SQLite in step (e.g. after logging in on a new phone)
                }
            } else {
                mine.addAll(LocalDb.get(app).ratingsFor(tmdbId)); // offline: use the copy on the phone
            }
            loadFriends(me, friends -> loadFriendRatings(tmdbId, friends, list -> cb.done(mine, list)));
        });
    }

    private interface FriendsCallback { void done(Map<String, Member> friends); }
    private interface ListCallback { void done(List<Rating> list); }

    private static void loadFriends(String me, FriendsCallback cb) {
        Map<String, Member> friends = new LinkedHashMap<>();
        Db.user(me).child("groups").get().addOnCompleteListener(t -> {
            if (!t.isSuccessful() || t.getResult() == null || !t.getResult().hasChildren()) {
                cb.done(friends);
                return;
            }
            List<String> codes = new ArrayList<>();
            for (DataSnapshot g : t.getResult().getChildren()) if (g.getKey() != null) codes.add(g.getKey());
            int[] left = {codes.size()};
            for (String code : codes) {
                Db.group(code).child("members").get().addOnCompleteListener(mt -> {
                    if (mt.isSuccessful() && mt.getResult() != null) {
                        for (DataSnapshot m : mt.getResult().getChildren()) {
                            Member member = Member.from(m);
                            if (!member.uid.isEmpty() && !member.uid.equals(me)) friends.put(member.uid, member);
                        }
                    }
                    if (--left[0] == 0) cb.done(friends);
                });
            }
        });
    }

    private static void loadFriendRatings(int tmdbId, Map<String, Member> friends, ListCallback cb) {
        List<Rating> all = new ArrayList<>();
        if (friends.isEmpty()) { cb.done(all); return; }
        int[] left = {friends.size()};
        for (Member f : friends.values()) {
            ref(f.uid, tmdbId).get().addOnCompleteListener(t -> {
                if (t.isSuccessful() && t.getResult() != null) {
                    for (DataSnapshot s : t.getResult().getChildren()) {
                        Rating r = Rating.from(tmdbId, s);
                        if (r.episode <= 0) continue;
                        r.uid = f.uid;
                        r.name = f.name;
                        r.color = f.color;
                        all.add(r);
                    }
                }
                if (--left[0] == 0) cb.done(all);
            });
        }
    }
}
