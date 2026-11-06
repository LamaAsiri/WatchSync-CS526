package com.cs526.watchsync.shows;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cs526.watchsync.BaseActivity;
import com.cs526.watchsync.R;
import com.cs526.watchsync.data.Db;
import com.cs526.watchsync.data.LocalDb;
import com.cs526.watchsync.data.ProgressStore;
import com.cs526.watchsync.data.TmdbApi;
import com.cs526.watchsync.model.Show;
import com.cs526.watchsync.util.EpisodeMath;
import com.cs526.watchsync.util.Ui;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

/** One show: my progress + seasons and episodes with ticks (like TV Time). */
public class ShowDetailActivity extends BaseActivity {
    private Show show;
    private boolean inLibrary;
    private TmdbApi api;
    private EpisodeAdapter episodes;
    private Spinner spSeason;
    private TextView tvProgress, tvMeta, tvOverview;
    private LinearProgressIndicator pbProgress;
    private MaterialButton btnAddLibrary, btnMovieWatched;
    private View layoutEpisodes;
    private int currentSeason = 1;

    public static void open(Context c, Show s) {
        Intent i = new Intent(c, ShowDetailActivity.class);
        s.putInto(i);
        c.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_detail);
        setupToolbar("");
        api = new TmdbApi(this);
        show = Show.fromIntent(getIntent());

        tvMeta = findViewById(R.id.tvMeta);
        tvOverview = findViewById(R.id.tvOverview);
        tvProgress = findViewById(R.id.tvProgress);
        pbProgress = findViewById(R.id.pbProgress);
        btnAddLibrary = findViewById(R.id.btnAddLibrary);
        btnMovieWatched = findViewById(R.id.btnMovieWatched);
        layoutEpisodes = findViewById(R.id.layoutEpisodes);
        spSeason = findViewById(R.id.spSeason);

        ((TextView) findViewById(R.id.tvTitle)).setText(show.title);
        ((TextView) findViewById(R.id.tvType)).setText(show.typeLabel().toUpperCase());
        Ui.poster((ImageView) findViewById(R.id.ivPoster), show.posterPath);

        episodes = new EpisodeAdapter(this::onEpisodeTap);
        RecyclerView rv = findViewById(R.id.rvEpisodes);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(episodes);

        btnAddLibrary.setOnClickListener(v -> saveProgress(show.progress));
        btnMovieWatched.setOnClickListener(v -> saveProgress(show.progress >= 1 ? 0 : 1));

        // 1) my saved copy (SQLite), 2) Firebase, 3) TMDB for seasons if we don't have them
        Show local = LocalDb.get(this).find(show.tmdbId);
        if (local != null) useSaved(local);
        Db.me().child("shows").child(String.valueOf(show.tmdbId)).get().addOnSuccessListener(snap -> {
            if (snap.exists()) useSaved(Show.from(snap));
        });
        if (!show.movie() && show.tmdbId > 0 && (show.seasonCounts.isEmpty() || show.overview.isEmpty())) {
            api.loadDetails(show, new TmdbApi.Callback<Show>() {
                @Override public void onResult(Show full) { refresh(); setupSeasons(); }
                @Override public void onError(String message) { refresh(); setupSeasons(); }
            });
        }
        refresh();
        setupSeasons();
    }

    private void useSaved(Show saved) {
        inLibrary = true;
        show.progress = saved.progress;
        if (show.seasonCounts.isEmpty()) show.seasonCounts = saved.seasonCounts;
        if (show.totalEpisodes == 0) show.totalEpisodes = saved.totalEpisodes;
        if (show.posterPath.isEmpty()) show.posterPath = saved.posterPath;
        if (show.year.isEmpty()) show.year = saved.year;
        if (show.overview.isEmpty()) show.overview = saved.overview;
        show.anime = show.anime || saved.anime;
        refresh();
        episodes.setProgress(show.progress);
    }

    private void refresh() {
        tvMeta.setText(show.summary());
        tvOverview.setText(show.overview);
        tvOverview.setVisibility(show.overview.isEmpty() ? View.GONE : View.VISIBLE);
        tvProgress.setText(show.progressLabel());
        int max = show.movie() ? 1 : Math.max(1, show.totalEpisodes);
        pbProgress.setMax(max);
        pbProgress.setProgress(Math.min(show.progress, max));
        btnAddLibrary.setVisibility(inLibrary ? View.GONE : View.VISIBLE);
        btnMovieWatched.setVisibility(show.movie() ? View.VISIBLE : View.GONE);
        btnMovieWatched.setText(show.progress >= 1 ? "Mark as not watched" : "✓ Mark as watched");
    }

    private void setupSeasons() {
        int[] counts = EpisodeMath.counts(show.seasonCounts);
        if (show.movie() || counts.length == 0) {
            layoutEpisodes.setVisibility(View.GONE);
            return;
        }
        layoutEpisodes.setVisibility(View.VISIBLE);
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) labels.add("Season " + (i + 1) + " (" + counts[i] + ")");
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spSeason.setAdapter(a);
        spSeason.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                loadSeason(position + 1);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        // open the season that has my next episode
        int next = Math.min(show.progress + 1, Math.max(1, show.totalEpisodes));
        int season = EpisodeMath.seasonOf(next, show.seasonCounts);
        spSeason.setSelection(season - 1);
        loadSeason(season);
    }

    private void loadSeason(int season) {
        currentSeason = season;
        int[] counts = EpisodeMath.counts(show.seasonCounts);
        if (season < 1 || season > counts.length) return;
        int before = EpisodeMath.toNumber(season, 0, show.seasonCounts);
        episodes.setSeason(before, counts[season - 1], show.progress);
        if (show.tmdbId > 0) {
            api.loadEpisodeNames(show.tmdbId, season, new TmdbApi.Callback<List<String>>() {
                @Override
                public void onResult(List<String> names) {
                    if (season == currentSeason) episodes.setNames(names);
                }

                @Override
                public void onError(String message) { }
            });
        }
    }

    /** Tap = watched up to this episode. Tap my last watched one again = un-watch it. */
    private void onEpisodeTap(int number) {
        int newProgress = number == show.progress ? number - 1 : number;
        saveProgress(newProgress);
    }

    private void saveProgress(int newProgress) {
        ProgressStore.save(this, show, newProgress)
                .addOnFailureListener(e -> Ui.toast(this, "Saved on this phone only: " + e.getMessage()));
        if (!inLibrary) Ui.toast(this, show.title + " added to My Shows");
        inLibrary = true;
        refresh();
        episodes.setProgress(show.progress);
    }
}
