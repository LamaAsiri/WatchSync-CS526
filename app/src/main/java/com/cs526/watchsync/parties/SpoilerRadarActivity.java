package com.cs526.watchsync.parties;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cs526.watchsync.BaseActivity;
import com.cs526.watchsync.R;
import com.cs526.watchsync.data.Db;
import com.cs526.watchsync.data.LocalDb;
import com.cs526.watchsync.data.ProgressStore;
import com.cs526.watchsync.groups.GroupActivity;
import com.cs526.watchsync.model.Member;
import com.cs526.watchsync.model.Party;
import com.cs526.watchsync.model.Show;
import com.cs526.watchsync.shows.ShowDetailActivity;
import com.cs526.watchsync.util.EpisodeMath;
import com.cs526.watchsync.util.RowAdapter;
import com.cs526.watchsync.util.Snap;
import com.cs526.watchsync.util.Ui;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * My progress on the group's Now Watching show + spoiler radar:
 * who is ahead of me (can spoil me) and who is behind (I must not spoil them).
 */
public class SpoilerRadarActivity extends BaseActivity {
    private String code;
    private Show show;
    private Party nextParty;
    private int mine;
    private final List<Member> members = new ArrayList<>();
    private final Map<String, Integer> progress = new HashMap<>();
    private final RowAdapter radar = new RowAdapter();

    private DatabaseReference ref;
    private ValueEventListener listener;

    public static void open(Context c, String code) {
        Intent i = new Intent(c, SpoilerRadarActivity.class);
        i.putExtra(GroupActivity.EXTRA_CODE, code);
        c.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spoiler_radar);
        setupToolbar("My progress");
        code = getIntent().getStringExtra(GroupActivity.EXTRA_CODE);

        RecyclerView rv = findViewById(R.id.rvMembers);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(radar);

        findViewById(R.id.btnMinus).setOnClickListener(v -> changeMine(mine - 1));
        findViewById(R.id.btnPlus).setOnClickListener(v -> changeMine(mine + 1));
        findViewById(R.id.btnEpisodes).setOnClickListener(v -> {
            if (show != null) ShowDetailActivity.open(this, myShow());
        });
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
        boolean hasShow = g.hasChild("nowWatching");
        findViewById(R.id.tvEmpty).setVisibility(hasShow ? View.GONE : View.VISIBLE);
        findViewById(R.id.layoutContent).setVisibility(hasShow ? View.VISIBLE : View.GONE);
        if (!hasShow) return;

        show = Show.from(g.child("nowWatching"));
        ((TextView) findViewById(R.id.tvShowTitle)).setText(show.title.toUpperCase());

        members.clear();
        for (DataSnapshot m : g.child("members").getChildren()) members.add(Member.from(m));
        Collections.sort(members, (a, b) -> Long.compare(a.joinedAt, b.joinedAt));

        // The soonest upcoming party for this show
        nextParty = null;
        long now = System.currentTimeMillis();
        for (DataSnapshot ps : g.child("parties").getChildren()) {
            Party p = Party.from(ps);
            if (p.tmdbId == show.tmdbId && p.time > now && (nextParty == null || p.time < nextParty.time)) nextParty = p;
        }

        mine = LocalDb.get(this).progressOf(show.tmdbId);
        loadProgress();
    }

    private void loadProgress() {
        Show s = show;
        for (Member m : members) {
            Db.user(m.uid).child("shows").child(String.valueOf(s.tmdbId)).child("progress").get()
                    .addOnSuccessListener(snap -> {
                        if (isFinishing() || show == null || show.tmdbId != s.tmdbId) return;
                        int p = snap.exists() ? Snap.num(snap) : 0;
                        progress.put(m.uid, p);
                        if (m.uid.equals(Db.uid())) mine = p;
                        render();
                    });
        }
        render();
    }

    private void render() {
        if (show == null) return;
        int total = show.movie() ? 1 : Math.max(1, show.totalEpisodes);
        ((TextView) findViewById(R.id.tvMyEpisode)).setText(show.movie()
                ? (mine >= 1 ? "Watched" : "Not watched")
                : (mine == 0 ? "—" : EpisodeMath.label(mine, show.seasonCounts, false)));
        ((TextView) findViewById(R.id.tvMyTotal)).setText(show.movie() ? "movie" : mine + " of " + total + " episodes");

        TextView hint = findViewById(R.id.tvPartyHint);
        if (nextParty == null) {
            hint.setText("No party planned for this show yet.");
        } else {
            int catchUp = nextParty.episodesToCatchUp(mine);
            String when = Ui.format("EEE h:mm a", nextParty.time);
            hint.setText(catchUp > 0
                    ? "Next party " + when + " (" + nextParty.episodeLabel() + "): watch " + EpisodeMath.count(catchUp) + " before it."
                    : "Next party " + when + " (" + nextParty.episodeLabel() + "): you're ready!");
        }

        List<RowAdapter.Row> rows = new ArrayList<>();
        for (Member m : members) {
            if (m.uid.equals(Db.uid())) continue;
            Integer p = progress.get(m.uid);
            RowAdapter.Row r = new RowAdapter.Row();
            r.name = m.name;
            r.color = m.color;
            if (p == null) {
                r.sub = "Loading…";
            } else {
                r.sub = p == 0 ? "Not started" : "On " + EpisodeMath.label(p, show.seasonCounts, show.movie());
                int diff = p - mine;
                if (diff > 0) {
                    r.badge = diff + " ahead · can spoil you";
                    r.badgeBg = Ui.RED_TINT;
                    r.badgeFg = Ui.LAVA;
                } else if (diff < 0) {
                    r.badge = (-diff) + " behind · don't spoil";
                    r.badgeBg = Ui.STEEL_TINT;
                    r.badgeFg = Ui.INK;
                } else {
                    r.badge = "Same as you";
                    r.badgeBg = Ui.LINE;
                    r.badgeFg = Ui.INK;
                }
            }
            rows.add(r);
        }
        radar.setRows(rows);
    }

    private void changeMine(int value) {
        if (show == null) return;
        int max = show.movie() ? 1 : Math.max(1, show.totalEpisodes);
        int newValue = Math.max(0, Math.min(value, max));
        if (newValue == mine) return;
        Show s = myShow();
        ProgressStore.save(this, s, newValue);
        mine = newValue;
        progress.put(Db.uid(), newValue);
        render();
    }

    private Show myShow() {
        Show local = LocalDb.get(this).find(show.tmdbId);
        Show s = local != null ? local : show.copy();
        s.progress = mine;
        if (s.seasonCounts.isEmpty()) s.seasonCounts = show.seasonCounts;
        if (s.totalEpisodes == 0) s.totalEpisodes = show.totalEpisodes;
        return s;
    }
}
