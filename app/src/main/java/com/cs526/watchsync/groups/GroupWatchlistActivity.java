package com.cs526.watchsync.groups;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cs526.watchsync.BaseActivity;
import com.cs526.watchsync.R;
import com.cs526.watchsync.data.Db;
import com.cs526.watchsync.model.Show;
import com.cs526.watchsync.shows.SearchActivity;
import com.cs526.watchsync.shows.ShowAdapter;
import com.cs526.watchsync.shows.ShowDetailActivity;
import com.cs526.watchsync.util.Snap;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/** Shows the group wants to watch. Admin starts a vote from here. */
public class GroupWatchlistActivity extends BaseActivity {
    private String code;
    private ShowAdapter adapter;
    private DatabaseReference ref;
    private ValueEventListener listener;
    private boolean admin;
    private final java.util.Set<Integer> inVote = new java.util.HashSet<>();

    public static void open(Context c, String code) {
        Intent i = new Intent(c, GroupWatchlistActivity.class);
        i.putExtra(GroupActivity.EXTRA_CODE, code);
        c.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_watchlist);
        setupToolbar("Watchlist");
        code = getIntent().getStringExtra(GroupActivity.EXTRA_CODE);

        adapter = new ShowAdapter((show, h) -> {
            String sub = show.summary();
            if (!show.addedByName.isEmpty()) sub += " · by " + show.addedByName;
            h.subtitle.setText(sub);
            if (inVote.contains(show.tmdbId)) h.showAction("In vote");
        });
        adapter.setOnClick(show -> ShowDetailActivity.open(this, show));
        adapter.setOnAction(show -> VoteActivity.open(this, code));
        adapter.setOnLongClick(show -> {
            if (!admin) return;
            new AlertDialog.Builder(this)
                    .setTitle("Remove " + show.title + "?")
                    .setPositiveButton("Remove", (d, w) -> ref.child("watchlist").child(String.valueOf(show.tmdbId)).removeValue())
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        RecyclerView rv = findViewById(R.id.rvWatchlist);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        findViewById(R.id.fabAdd).setOnClickListener(v -> SearchActivity.openForGroup(this, code));
        findViewById(R.id.btnStartVote).setOnClickListener(v -> StartVoteActivity.open(this, code));
        findViewById(R.id.tvVoteOpen).setOnClickListener(v -> VoteActivity.open(this, code));
    }

    @Override
    protected void onStart() {
        super.onStart();
        ref = Db.group(code);
        listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot g) {
                admin = Snap.str(g.child("adminUid")).equals(Db.uid());
                boolean voteOpen = Snap.bool(g.child("vote/open"));
                inVote.clear();
                if (voteOpen) {
                    for (DataSnapshot o : g.child("vote/options").getChildren()) inVote.add(Snap.num(o.child("tmdbId")));
                }
                List<Show> list = new ArrayList<>();
                for (DataSnapshot s : g.child("watchlist").getChildren()) list.add(Show.from(s));
                adapter.setItems(list);

                findViewById(R.id.tvEmpty).setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                findViewById(R.id.tvVoteOpen).setVisibility(voteOpen ? View.VISIBLE : View.GONE);
                boolean canStart = admin && !voteOpen && !g.hasChild("upNext") && list.size() >= 2;
                findViewById(R.id.btnStartVote).setVisibility(canStart ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };
        ref.addValueEventListener(listener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (ref != null && listener != null) ref.removeEventListener(listener);
    }
}
