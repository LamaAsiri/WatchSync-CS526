package com.cs526.watchsync.groups;

import com.cs526.watchsync.util.Lang;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cs526.watchsync.BaseActivity;
import com.cs526.watchsync.R;
import com.cs526.watchsync.data.Db;
import com.cs526.watchsync.model.Show;
import com.cs526.watchsync.shows.ShowAdapter;
import com.cs526.watchsync.util.Ui;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Admin picks 2-4 shows from the watchlist for the vote. */
public class StartVoteActivity extends BaseActivity {
    private String code;
    private final Set<Integer> picked = new LinkedHashSet<>();
    private final List<Show> shows = new ArrayList<>();
    private ShowAdapter adapter;
    private MaterialButton btnStart;

    public static void open(Context c, String code) {
        Intent i = new Intent(c, StartVoteActivity.class);
        i.putExtra(GroupActivity.EXTRA_CODE, code);
        c.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        setupToolbar(Lang.s(R.string.title_start_vote));
        code = getIntent().getStringExtra(GroupActivity.EXTRA_CODE);

        ((TextView) findViewById(R.id.tvHeader)).setText(Lang.s(R.string.start_vote_header));
        ((TextView) findViewById(R.id.tvFooter)).setText(Lang.s(R.string.start_vote_footer));
        btnStart = findViewById(R.id.btnMain);
        btnStart.setVisibility(View.VISIBLE);

        adapter = new ShowAdapter((show, h) -> h.showAction(Lang.s(picked.contains(show.tmdbId) ? R.string.picked : R.string.pick)));
        adapter.setOnAction(this::toggle);
        adapter.setOnClick(this::toggle);
        RecyclerView rv = findViewById(R.id.rvList);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        btnStart.setOnClickListener(v -> start());
        updateButton();

        Db.group(code).child("watchlist").get().addOnSuccessListener(snap -> {
            for (DataSnapshot s : snap.getChildren()) shows.add(Show.from(s));
            adapter.setItems(shows);
        });
    }

    private void toggle(Show s) {
        if (picked.contains(s.tmdbId)) picked.remove(s.tmdbId);
        else if (picked.size() < 4) picked.add(s.tmdbId);
        else Ui.toast(this, Lang.s(R.string.four_max));
        adapter.notifyDataSetChanged();
        updateButton();
    }

    private void updateButton() {
        btnStart.setText(Lang.s(R.string.start_vote_n, picked.size()));
        btnStart.setEnabled(picked.size() >= 2);
    }

    private void start() {
        Map<String, Object> options = new HashMap<>();
        for (Show s : shows) if (picked.contains(s.tmdbId)) options.put(String.valueOf(s.tmdbId), s.toMap());
        Map<String, Object> vote = new HashMap<>();
        vote.put("open", true);
        vote.put("startedBy", Db.uid());
        vote.put("startedAt", System.currentTimeMillis());
        vote.put("options", options);
        Db.group(code).child("vote").setValue(vote).addOnSuccessListener(x -> {
            VoteActivity.open(this, code);
            finish();
        });
    }
}
