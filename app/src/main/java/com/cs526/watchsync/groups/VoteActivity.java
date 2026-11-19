package com.cs526.watchsync.groups;

import com.cs526.watchsync.util.Lang;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cs526.watchsync.BaseActivity;
import com.cs526.watchsync.R;
import com.cs526.watchsync.data.Db;
import com.cs526.watchsync.data.LocalDb;
import com.cs526.watchsync.model.Show;
import com.cs526.watchsync.shows.ShowAdapter;
import com.cs526.watchsync.util.Snap;
import com.cs526.watchsync.util.Ui;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Everyone votes once (can change). Results are hidden until the admin ends the vote. */
public class VoteActivity extends BaseActivity {
    private String code;
    private ShowAdapter adapter;
    private DatabaseReference ref;
    private ValueEventListener listener;
    private DataSnapshot group;
    private boolean open;
    private String myBallot = "";
    private final Map<Integer, Integer> counts = new HashMap<>();

    public static void open(Context c, String code) {
        Intent i = new Intent(c, VoteActivity.class);
        i.putExtra(GroupActivity.EXTRA_CODE, code);
        c.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        setupToolbar(Lang.s(R.string.title_whats_next));
        code = getIntent().getStringExtra(GroupActivity.EXTRA_CODE);

        adapter = new ShowAdapter((show, h) -> {
            String id = String.valueOf(show.tmdbId);
            if (open) {
                h.showAction(Lang.s(id.equals(myBallot) ? R.string.your_vote : R.string.vote));
            } else {
                Integer n = counts.get(show.tmdbId);
                h.subtitle.setText(Lang.s(R.string.n_votes, show.summary(), n == null ? 0 : n));
            }
        });
        adapter.setOnAction(this::vote);
        RecyclerView rv = findViewById(R.id.rvList);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        MaterialButton btnEnd = findViewById(R.id.btnMain);
        btnEnd.setText(Lang.s(R.string.end_vote_admin));
        btnEnd.setOnClickListener(v -> endVote());
    }

    @Override
    protected void onStart() {
        super.onStart();
        ref = Db.group(code);
        listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot g) { bind(g); }

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

    private void bind(DataSnapshot g) {
        group = g;
        DataSnapshot vote = g.child("vote");
        open = Snap.bool(vote.child("open"));
        myBallot = Snap.str(vote.child("ballots").child(Db.uid()));
        boolean admin = Snap.str(g.child("adminUid")).equals(Db.uid());
        long members = g.child("members").getChildrenCount();
        long voted = vote.child("ballots").getChildrenCount();

        counts.clear();
        for (DataSnapshot b : vote.child("ballots").getChildren()) {
            int id = Snap.num(b);
            Integer n = counts.get(id);
            counts.put(id, n == null ? 1 : n + 1);
        }
        List<Show> options = new ArrayList<>();
        for (DataSnapshot o : vote.child("options").getChildren()) options.add(Show.from(o));
        adapter.setItems(options);

        TextView header = findViewById(R.id.tvHeader);
        TextView highlight = findViewById(R.id.tvHighlight);
        TextView footer = findViewById(R.id.tvFooter);
        View btnEnd = findViewById(R.id.btnMain);

        if (!vote.exists()) {
            header.setText(Lang.s(R.string.no_vote_now));
            highlight.setVisibility(View.GONE);
            footer.setText("");
            btnEnd.setVisibility(View.GONE);
        } else if (open) {
            header.setText(Lang.s(R.string.vote_header, voted, members));
            highlight.setVisibility(View.GONE);
            footer.setText(Lang.s(R.string.vote_footer));
            btnEnd.setVisibility(admin ? View.VISIBLE : View.GONE);
        } else {
            header.setText(Lang.s(R.string.vote_ended));
            highlight.setVisibility(View.VISIBLE);
            highlight.setText(Lang.s(R.string.winner_x, Snap.str(vote.child("winnerTitle"))));
            footer.setText("");
            btnEnd.setVisibility(View.GONE);
        }
    }

    private void vote(Show s) {
        if (!open) return;
        ref.child("vote").child("ballots").child(Db.uid()).setValue(s.tmdbId);
        LocalDb.get(this).logActivity("vote", Lang.s(R.string.log_voted_for, s.title));
    }

    private void endVote() {
        if (group == null || !open) return;
        List<Show> options = adapter.getItems();
        if (counts.isEmpty()) { Ui.toast(this, Lang.s(R.string.nobody_voted)); return; }
        int best = 0;
        for (Integer n : counts.values()) best = Math.max(best, n);
        List<Show> tied = new ArrayList<>();
        for (Show s : options) {
            Integer n = counts.get(s.tmdbId);
            if (n != null && n == best) tied.add(s);
        }
        if (tied.size() == 1) {
            applyWinner(tied.get(0));
            return;
        }
        // Tie: the admin chooses
        String[] names = new String[tied.size()];
        for (int i = 0; i < tied.size(); i++) names[i] = tied.get(i).title;
        new AlertDialog.Builder(this)
                .setTitle(Lang.s(R.string.tie_title, best))
                .setItems(names, (d, which) -> applyWinner(tied.get(which)))
                .show();
    }

    /** Winner becomes Now Watching if the group has nothing, otherwise Up next. */
    private void applyWinner(Show winner) {
        Show w = winner.copy();
        w.progress = 0;
        Map<String, Object> updates = new HashMap<>();
        updates.put("vote/open", false);
        updates.put("vote/winnerTitle", w.title);
        updates.put("vote/winnerId", w.tmdbId);
        updates.put(group.hasChild("nowWatching") ? "upNext" : "nowWatching", w.toMap());
        updates.put("watchlist/" + w.tmdbId, null);
        ref.updateChildren(updates).addOnSuccessListener(x ->
                Ui.toast(this, Lang.s(group.hasChild("nowWatching") ? R.string.x_is_up_next : R.string.x_is_group_show, w.title)));
    }
}
