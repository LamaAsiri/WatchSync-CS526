package com.cs526.watchsync.shows;

import com.cs526.watchsync.util.Lang;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
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
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

/** One show: my progress + seasons and episodes with ticks (like TV Time). */
public class ShowDetailActivity extends BaseActivity {
    private Show show;
    private boolean inLibrary;
    private String arabicOverview = "";
    private TmdbApi api;
    private EpisodeAdapter episodes;
    private Spinner spSeason;
    private TextView tvProgress, tvMeta, tvOverview;
    private LinearProgressIndicator pbProgress;
    private MaterialButton btnAddLibrary, btnMovieWatched;
    private View layoutEpisodes;
    private int currentSeason = 1;
    private RatingsSection ratings;

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

        // Use the top bar as the app bar so it can show the options menu
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayShowTitleEnabled(false);
        toolbar.setNavigationOnClickListener(v -> finish());

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
        ImageView ivPoster = findViewById(R.id.ivPoster);
        Ui.poster(ivPoster, show.posterPath);
        registerForContextMenu(ivPoster); // long press on the poster opens the context menu

        episodes = new EpisodeAdapter(this::onEpisodeTap, this::onEpisodeLongPress);
        RecyclerView rv = findViewById(R.id.rvEpisodes);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(episodes);

        ratings = new RatingsSection(this, show);

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
        ratings.load();
        if (Lang.arabic()) loadArabicOverview();
    }

    /** In Arabic, show TMDB's Arabic description when there is one (the English one stays saved). */
    private void loadArabicOverview() {
        api.loadArabicOverview(show, new TmdbApi.Callback<String>() {
            @Override public void onResult(String overview) {
                if (isFinishing() || overview.isEmpty()) return;
                arabicOverview = overview;
                refresh();
            }
            @Override public void onError(String message) { }
        });
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
        String overview = arabicOverview.isEmpty() ? show.overview : arabicOverview;
        tvOverview.setText(overview);
        tvOverview.setVisibility(overview.isEmpty() ? View.GONE : View.VISIBLE);
        tvProgress.setText(show.progressLabel());
        int max = show.movie() ? 1 : Math.max(1, show.totalEpisodes);
        pbProgress.setMax(max);
        pbProgress.setProgress(Math.min(show.progress, max));
        btnAddLibrary.setVisibility(inLibrary ? View.GONE : View.VISIBLE);
        btnMovieWatched.setVisibility(show.movie() ? View.VISIBLE : View.GONE);
        btnMovieWatched.setText(Lang.s(show.progress >= 1 ? R.string.mark_not_watched : R.string.mark_watched));
        invalidateOptionsMenu(); // "Remove" only shows once the show is in My Shows
        if (ratings != null) ratings.render(); // watching more can unlock friends' reviews
    }

    private void setupSeasons() {
        int[] counts = EpisodeMath.counts(show.seasonCounts);
        if (show.movie() || counts.length == 0) {
            layoutEpisodes.setVisibility(View.GONE);
            return;
        }
        layoutEpisodes.setVisibility(View.VISIBLE);
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) labels.add(Lang.s(R.string.season_n, i + 1, counts[i]));
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

    /** Long press: rate a watched episode. */
    private void onEpisodeLongPress(int number) {
        if (number <= show.progress) ratings.rate(number);
        else Ui.toast(this, Lang.s(R.string.watch_first));
    }

    private void saveProgress(int newProgress) {
        int before = show.progress;
        ProgressStore.save(this, show, newProgress)
                .addOnFailureListener(e -> Ui.toast(this, Lang.s(R.string.saved_phone_only, e.getMessage())));
        if (!inLibrary) Ui.toast(this, Lang.s(R.string.added_to_my_shows, show.title));
        inLibrary = true;
        refresh();
        episodes.setProgress(show.progress);
        // Watched exactly ONE more episode (or the movie)? Ask how it was.
        // Marking many at once doesn't ask, so it never turns into 10 pop-ups in a row.
        if (show.progress == before + 1) {
            RatingSheet.askIfNotRated(this, show, show.progress, ratings::changed);
        }
    }

    // ---------- Options menu (app bar) ----------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_show_detail, menu);
        return true;
    }

    /** Runs after invalidateOptionsMenu(): hide what doesn't apply to this show. */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_open_web).setVisible(show.tmdbId > 0);
        menu.findItem(R.id.action_remove).setVisible(inLibrary);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_share) {
            shareShow();
            return true;
        } else if (id == R.id.action_open_web) {
            openWebsite();
            return true;
        } else if (id == R.id.action_remove) {
            confirmRemove();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ---------- Context menu (long press on the poster) ----------

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        getMenuInflater().inflate(R.menu.menu_poster, menu);
        menu.setHeaderTitle(show.title);
        menu.findItem(R.id.poster_open_web).setVisible(show.tmdbId > 0);
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.poster_open_web) {
            openWebsite();
            return true;
        } else if (id == R.id.poster_share) {
            shareShow();
            return true;
        }
        return super.onContextItemSelected(item);
    }

    // ---------- Actions ----------

    /** Implicit intent (ACTION_VIEW): the phone picks a browser to open the show's TMDB page. */
    private void openWebsite() {
        if (show.tmdbId <= 0) return;
        Uri uri = Uri.parse("https://www.themoviedb.org/" + (show.movie() ? "movie/" : "tv/") + show.tmdbId);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Ui.toast(this, Lang.s(R.string.no_browser));
        }
    }

    /** Implicit intent (ACTION_SEND): share the show with any app (WhatsApp, Messages...). */
    private void shareShow() {
        String text = Lang.s(R.string.share_watching, show.title);
        if (!show.movie() && show.progress > 0) {
            text += " (" + EpisodeMath.label(show.progress, show.seasonCounts, false) + ")";
        }
        if (show.tmdbId > 0) {
            text += "\nhttps://www.themoviedb.org/" + (show.movie() ? "movie/" : "tv/") + show.tmdbId;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(intent, Lang.s(R.string.share_show)));
    }

    private void confirmRemove() {
        new AlertDialog.Builder(this)
                .setTitle(Lang.s(R.string.remove_x, show.title))
                .setMessage(Lang.s(R.string.remove_show_msg))
                .setPositiveButton(Lang.s(R.string.remove), (d, w) -> {
                    ProgressStore.remove(this, show.tmdbId);
                    Ui.toast(this, Lang.s(R.string.x_removed, show.title));
                    finish();
                })
                .setNegativeButton(Lang.s(R.string.cancel), null)
                .show();
    }
}
