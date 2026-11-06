package com.cs526.watchsync.shows;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cs526.watchsync.BaseActivity;
import com.cs526.watchsync.R;
import com.cs526.watchsync.data.Db;
import com.cs526.watchsync.data.Prefs;
import com.cs526.watchsync.data.ProgressStore;
import com.cs526.watchsync.data.TmdbApi;
import com.cs526.watchsync.model.Show;
import com.cs526.watchsync.util.Ui;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Search TMDB.
 * Without EXTRA_GROUP: "Add" puts the show in My Shows.
 * With EXTRA_GROUP: "Add" puts the show in that group's watchlist.
 */
public class SearchActivity extends BaseActivity {
    public static final String EXTRA_GROUP = "group_code";

    private TmdbApi api;
    private ShowAdapter adapter;
    private ChipGroup chips;
    private ProgressBar progress;
    private TextView tvInfo;
    private String groupCode;
    private final List<Show> results = new ArrayList<>();
    private final Set<Integer> added = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    public static void openForGroup(Context c, String code) {
        Intent i = new Intent(c, SearchActivity.class);
        i.putExtra(EXTRA_GROUP, code);
        c.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        groupCode = getIntent().getStringExtra(EXTRA_GROUP);
        setupToolbar(groupCode == null ? "Find a show" : "Add to group watchlist");

        api = new TmdbApi(this);
        chips = findViewById(R.id.chipGroup);
        progress = findViewById(R.id.progress);
        tvInfo = findViewById(R.id.tvInfo);

        adapter = new ShowAdapter((show, h) -> {
            if (added.contains(show.tmdbId)) {
                h.showAction("✓ Added");
                h.action.setEnabled(false);
            } else {
                h.showAction("+ Add");
            }
        });
        adapter.setOnAction(this::add);
        adapter.setOnClick(show -> ShowDetailActivity.open(this, show));
        RecyclerView rv = findViewById(R.id.rvResults);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        loadAlreadyAdded();

        EditText etSearch = findViewById(R.id.etSearch);
        etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) {
                if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
                String q = s.toString().trim();
                pendingSearch = () -> search(q);
                handler.postDelayed(pendingSearch, 450); // wait until the user stops typing
            }
        });
        chips.setOnCheckedStateChangeListener((g, ids) -> showFiltered());
        findViewById(R.id.btnManual).setOnClickListener(v -> manualDialog());
    }

    private DatabaseReference listRef() {
        return groupCode == null ? Db.me().child("shows") : Db.group(groupCode).child("watchlist");
    }

    private void loadAlreadyAdded() {
        listRef().get().addOnSuccessListener(snap -> {
            for (DataSnapshot s : snap.getChildren()) {
                try { added.add(Integer.parseInt(s.getKey())); } catch (Exception ignored) { }
            }
            adapter.notifyDataSetChanged();
        });
    }

    private void search(String q) {
        if (q.length() < 2) {
            results.clear();
            showFiltered();
            tvInfo.setText("Type at least 2 letters to search.");
            tvInfo.setVisibility(View.VISIBLE);
            return;
        }
        progress.setVisibility(View.VISIBLE);
        api.search(q, new TmdbApi.Callback<List<Show>>() {
            @Override
            public void onResult(List<Show> list) {
                progress.setVisibility(View.GONE);
                results.clear();
                results.addAll(list);
                showFiltered();
            }

            @Override
            public void onError(String message) {
                progress.setVisibility(View.GONE);
                tvInfo.setText(message);
                tvInfo.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showFiltered() {
        int checked = chips.getCheckedChipId();
        List<Show> list = new ArrayList<>();
        for (Show s : results) {
            if (checked == R.id.chipAnime && !s.anime) continue;
            if (checked == R.id.chipTv && (s.movie() || s.anime)) continue;
            if (checked == R.id.chipMovie && !s.movie()) continue;
            list.add(s);
        }
        adapter.setItems(list);
        if (!results.isEmpty()) {
            tvInfo.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            tvInfo.setText("No results in this filter.");
        }
    }

    private void add(Show show) {
        if (added.contains(show.tmdbId)) return;
        added.add(show.tmdbId);
        adapter.notifyDataSetChanged();
        // Get the episode count first, then save
        api.loadDetails(show, new TmdbApi.Callback<Show>() {
            @Override public void onResult(Show full) { save(full); }
            @Override public void onError(String message) { save(show); }
        });
    }

    private void save(Show show) {
        if (groupCode == null) {
            ProgressStore.save(this, show, 0);
            Ui.toast(this, show.title + " added to My Shows");
        } else {
            Map<String, Object> m = show.toMap();
            m.put("progress", 0);
            m.put("addedBy", Db.uid());
            m.put("addedByName", Prefs.name(this));
            Db.group(groupCode).child("watchlist").child(String.valueOf(show.tmdbId)).setValue(m);
            Ui.toast(this, show.title + " added to the group watchlist");
        }
    }

    private void manualDialog() {
        View form = LayoutInflater.from(this).inflate(R.layout.dialog_manual_show, null);
        EditText etTitle = form.findViewById(R.id.etTitle);
        EditText etEpisodes = form.findViewById(R.id.etEpisodes);
        new AlertDialog.Builder(this)
                .setTitle("Add manually")
                .setView(form)
                .setPositiveButton("Add", (d, w) -> {
                    String title = etTitle.getText().toString().trim();
                    if (title.isEmpty()) { Ui.toast(this, "Enter a title"); return; }
                    int eps = 0;
                    try { eps = Integer.parseInt(etEpisodes.getText().toString().trim()); } catch (Exception ignored) { }
                    Show s = new Show();
                    s.tmdbId = -(int) (System.currentTimeMillis() % 1_000_000_000L); // negative = manual
                    s.title = title;
                    s.mediaType = eps > 0 ? "tv" : "movie";
                    s.totalEpisodes = eps > 0 ? eps : 1;
                    s.seasonCounts = eps > 0 ? String.valueOf(eps) : "";
                    added.add(s.tmdbId);
                    save(s);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
