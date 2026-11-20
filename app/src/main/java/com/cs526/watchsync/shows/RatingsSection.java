package com.cs526.watchsync.shows;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.cs526.watchsync.R;
import com.cs526.watchsync.data.Db;
import com.cs526.watchsync.data.LocalDb;
import com.cs526.watchsync.data.Prefs;
import com.cs526.watchsync.data.RatingStore;
import com.cs526.watchsync.model.Rating;
import com.cs526.watchsync.model.Show;
import com.cs526.watchsync.util.Avatars;
import com.cs526.watchsync.util.EpisodeMath;
import com.cs526.watchsync.util.Lang;
import com.cs526.watchsync.util.Ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * "Ratings" part of the show screen: my average, my friends' average and the reviews.
 *
 * Spoiler protection: a friend's rating of episode N only shows once I've watched up to N
 * (a movie: once I've watched it). Until then it is only counted as "hidden".
 */
public class RatingsSection {
    private static final int MAX_SHOWN = 30;

    private final Activity a;
    private final Show show;   // the same object as the screen's, so show.progress is always current
    private final View root;
    private final TextView tvSummary, tvHidden;
    private final LinearLayout list;
    private final View btnRate;

    private final List<Rating> mine = new ArrayList<>();
    private final List<Rating> friends = new ArrayList<>();
    private boolean loaded;

    public RatingsSection(Activity a, Show show) {
        this.a = a;
        this.show = show;
        root = a.findViewById(R.id.layoutRatings);
        tvSummary = a.findViewById(R.id.tvRatingSummary);
        tvHidden = a.findViewById(R.id.tvHiddenReviews);
        list = a.findViewById(R.id.listReviews);
        btnRate = a.findViewById(R.id.btnRate);
        // Rate the movie, or the last episode I watched
        btnRate.setOnClickListener(v -> rate(show.movie() ? 1 : show.progress));
    }

    public void load() {
        if (show.tmdbId <= 0) return;
        mine.clear();
        mine.addAll(LocalDb.get(a).ratingsFor(show.tmdbId)); // fast copy from SQLite first
        render();
        RatingStore.load(a, show.tmdbId, (myRatings, friendRatings) -> {
            if (a.isFinishing() || a.isDestroyed()) return;
            mine.clear();
            mine.addAll(myRatings);
            friends.clear();
            friends.addAll(friendRatings);
            loaded = true;
            render();
        });
    }

    /** Opens the sheet for an episode I watched, and updates the list after. */
    public void rate(int episode) {
        if (episode <= 0) return;
        RatingSheet.show(a, show, episode, this::changed);
    }

    /** Called by RatingSheet when I save or remove a rating. */
    public void changed(int episode, @Nullable Rating r) {
        for (Iterator<Rating> it = mine.iterator(); it.hasNext(); ) {
            if (it.next().episode == episode) it.remove();
        }
        if (r != null) mine.add(r);
        render();
    }

    private boolean watched(int episode) {
        return show.movie() ? show.progress >= 1 : episode <= show.progress;
    }

    /** Redraws everything. Also called when my progress changes (newly watched episodes unlock reviews). */
    public void render() {
        if (show.tmdbId <= 0) { root.setVisibility(View.GONE); return; }
        root.setVisibility(View.VISIBLE);
        btnRate.setVisibility(show.progress >= 1 ? View.VISIBLE : View.GONE);

        List<Rating> visible = new ArrayList<>();
        int hidden = 0;
        for (Rating r : friends) {
            if (watched(r.episode)) visible.add(r);
            else hidden++;
        }

        // Summary: "You ★4 (3) · Friends ★3.5 (5)"
        List<String> parts = new ArrayList<>();
        if (!mine.isEmpty()) parts.add(Lang.s(R.string.summary_you, Rating.starsText(average(mine)), mine.size()));
        if (!visible.isEmpty()) parts.add(Lang.s(R.string.summary_friends, Rating.starsText(average(visible)), visible.size()));
        if (parts.isEmpty()) {
            tvSummary.setText(loaded || !mine.isEmpty() ? Lang.s(show.movie() ? R.string.no_ratings_movie : R.string.no_ratings_show)
                    : Lang.s(R.string.loading));
        } else {
            tvSummary.setText(android.text.TextUtils.join(" · ", parts));
        }

        tvHidden.setVisibility(hidden > 0 ? View.VISIBLE : View.GONE);
        tvHidden.setText(Lang.s(R.string.hidden_reviews, hidden));

        // Reviews: mine + friends I can see, newest first
        List<Rating> all = new ArrayList<>();
        String me = Db.uid();
        for (Rating r : mine) {
            r.uid = me;
            r.name = Prefs.name(a);
            r.color = Prefs.color(a);
            all.add(r);
        }
        all.addAll(visible);
        Collections.sort(all, (x, y) -> Long.compare(y.ratedAt, x.ratedAt));

        list.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(a);
        for (int i = 0; i < all.size() && i < MAX_SHOWN; i++) {
            Rating r = all.get(i);
            View item = inf.inflate(R.layout.item_review, list, false);
            boolean isMe = r.uid.equals(me);
            TextView avatar = item.findViewById(R.id.tvAvatar);
            Ui.avatar(avatar, r.name, r.color);
            Avatars.load(r.uid, photo -> { if (photo != null) Ui.avatar(avatar, r.name, r.color, photo); });
            ((TextView) item.findViewById(R.id.tvReviewName)).setText(isMe ? Lang.s(R.string.you_label) : r.name);
            String label = show.seasonCounts.isEmpty() ? r.label : EpisodeMath.label(r.episode, show.seasonCounts, false);
            ((TextView) item.findViewById(R.id.tvReviewLabel)).setText(show.movie() ? "" : label);
            ((RatingBar) item.findViewById(R.id.rbReview)).setRating(r.stars);
            TextView text = item.findViewById(R.id.tvReviewText);
            text.setText(r.review);
            text.setVisibility(r.review.isEmpty() ? View.GONE : View.VISIBLE);
            if (isMe) {
                int episode = r.episode;
                item.setOnClickListener(v -> rate(episode)); // tap my own rating to change it
            }
            list.addView(item);
        }
    }

    private static float average(List<Rating> ratings) {
        float sum = 0;
        for (Rating r : ratings) sum += r.stars;
        // round to the nearest half star
        return Math.round(sum / ratings.size() * 2f) / 2f;
    }
}
