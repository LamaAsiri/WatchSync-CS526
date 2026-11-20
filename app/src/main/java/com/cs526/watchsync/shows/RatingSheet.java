package com.cs526.watchsync.shows;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.cs526.watchsync.R;
import com.cs526.watchsync.data.LocalDb;
import com.cs526.watchsync.data.RatingStore;
import com.cs526.watchsync.model.Rating;
import com.cs526.watchsync.model.Show;
import com.cs526.watchsync.util.EpisodeMath;
import com.cs526.watchsync.util.Lang;
import com.cs526.watchsync.util.Ui;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;

/**
 * "How was it?" sheet that slides up from the bottom: 5 stars (half stars allowed)
 * and an optional review (max 500 characters). Skip closes it without saving.
 *
 * Opened after marking ONE episode (or a movie) as watched, after a watch party check-in,
 * or by long-pressing a watched episode / the Rate button on the show screen.
 */
public final class RatingSheet {
    public interface Listener {
        /** r = the saved rating, or null if it was removed. */
        void onChanged(int episode, @Nullable Rating r);
    }

    private RatingSheet() {}

    /** Opens the sheet only if I haven't rated this episode yet (used after marking it watched). */
    public static void askIfNotRated(Activity a, Show show, int episode, @Nullable Listener l) {
        if (LocalDb.get(a).findRating(show.tmdbId, episode) != null) return;
        show(a, show, episode, l);
    }

    public static void show(Activity a, Show show, int episode, @Nullable Listener l) {
        if (a.isFinishing() || show.tmdbId <= 0 || episode <= 0) return;
        Rating existing = LocalDb.get(a).findRating(show.tmdbId, episode);
        String label = EpisodeMath.label(episode, show.seasonCounts, show.movie());

        BottomSheetDialog dialog = new BottomSheetDialog(a);
        View v = LayoutInflater.from(a).inflate(R.layout.sheet_rating, null, false);
        dialog.setContentView(v);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);

        ((TextView) v.findViewById(R.id.tvRateTitle)).setText(show.movie()
                ? Lang.s(R.string.rate_the_movie) : Lang.s(R.string.rate_episode_x, label));
        ((TextView) v.findViewById(R.id.tvRateShow)).setText(show.title);

        RatingBar bar = v.findViewById(R.id.ratingBar);
        TextView value = v.findViewById(R.id.tvStarsValue);
        TextInputEditText review = v.findViewById(R.id.etReview);
        bar.setOnRatingBarChangeListener((rb, stars, fromUser) -> value.setText(stars <= 0
                ? Lang.s(R.string.tap_the_stars) : Lang.s(R.string.stars_value, Rating.starsText(stars))));
        bar.setRating(existing != null ? existing.stars : 0f);
        value.setText(existing != null ? Lang.s(R.string.stars_value, Rating.starsText(existing.stars))
                : Lang.s(R.string.tap_the_stars));
        if (existing != null) review.setText(existing.review);

        v.findViewById(R.id.btnSkip).setOnClickListener(x -> dialog.dismiss());

        v.findViewById(R.id.btnSaveRating).setOnClickListener(x -> {
            float stars = bar.getRating();
            if (stars < 0.5f) { Ui.toast(a, Lang.s(R.string.err_pick_stars)); return; }
            Rating r = new Rating();
            r.tmdbId = show.tmdbId;
            r.episode = episode;
            r.stars = stars;
            r.review = review.getText() == null ? "" : review.getText().toString().trim();
            if (r.review.length() > Rating.MAX_REVIEW) r.review = r.review.substring(0, Rating.MAX_REVIEW);
            r.showTitle = show.title;
            r.label = label;
            RatingStore.save(a, r).addOnFailureListener(e ->
                    Ui.toast(a, Lang.s(R.string.saved_phone_only, e.getMessage())));
            Ui.toast(a, Lang.s(R.string.rating_saved));
            if (l != null) l.onChanged(episode, r);
            dialog.dismiss();
        });

        View remove = v.findViewById(R.id.btnRemoveRating);
        remove.setVisibility(existing != null ? View.VISIBLE : View.GONE);
        remove.setOnClickListener(x -> {
            RatingStore.remove(a, show.tmdbId, episode);
            if (l != null) l.onChanged(episode, null);
            dialog.dismiss();
        });

        dialog.show();
    }
}
