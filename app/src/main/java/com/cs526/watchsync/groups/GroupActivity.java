package com.cs526.watchsync.groups;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.cs526.watchsync.BaseActivity;
import com.cs526.watchsync.R;
import com.cs526.watchsync.data.Db;
import com.cs526.watchsync.data.LocalDb;
import com.cs526.watchsync.model.Party;
import com.cs526.watchsync.model.Show;
import com.cs526.watchsync.parties.PartiesActivity;
import com.cs526.watchsync.parties.SpoilerRadarActivity;
import com.cs526.watchsync.shows.ShowDetailActivity;
import com.cs526.watchsync.util.EpisodeMath;
import com.cs526.watchsync.util.Snap;
import com.cs526.watchsync.util.Ui;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Group hub: Now Watching (one show at a time), Up next, vote, and 4 tiles.
 * Show states: Watchlist -> In vote -> Up next -> Now watching -> Finished.
 */
public class GroupActivity extends BaseActivity {
    public static final String EXTRA_CODE = "code";

    private String code;
    private String groupName = "";
    private boolean admin;
    private Show now;
    private DatabaseReference ref;
    private ValueEventListener listener;

    public static void open(Context c, String code) {
        Intent i = new Intent(c, GroupActivity.class);
        i.putExtra(EXTRA_CODE, code);
        c.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group);
        setupToolbar("");
        code = getIntent().getStringExtra(EXTRA_CODE);

        findViewById(R.id.tvCode).setOnClickListener(v -> Ui.shareCode(this, groupName, code));
        findViewById(R.id.tileParties).setOnClickListener(v -> PartiesActivity.open(this, code));
        findViewById(R.id.tileRadar).setOnClickListener(v -> SpoilerRadarActivity.open(this, code));
        findViewById(R.id.tileWatchlist).setOnClickListener(v -> GroupWatchlistActivity.open(this, code));
        findViewById(R.id.tileMembers).setOnClickListener(v -> MembersActivity.open(this, code));
        findViewById(R.id.cardVote).setOnClickListener(v -> VoteActivity.open(this, code));
        findViewById(R.id.btnStartVote).setOnClickListener(v -> StartVoteActivity.open(this, code));
        findViewById(R.id.cardNow).setOnClickListener(v -> { if (now != null) ShowDetailActivity.open(this, now); });
        findViewById(R.id.btnFinish).setOnClickListener(v -> confirmFinish());
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
        if (!g.exists() || !g.child("members").hasChild(Db.uid())) {
            Ui.toast(this, "You're not in this group anymore");
            finish();
            return;
        }
        groupName = Snap.str(g.child("name"));
        setToolbarTitle(groupName);
        String adminUid = Snap.str(g.child("adminUid"));
        admin = adminUid.equals(Db.uid());
        String adminName = Snap.str(g.child("members").child(adminUid).child("name"));
        ((TextView) findViewById(R.id.tvAdmin)).setText(admin ? "You're the admin" : "Admin: " + adminName);
        ((TextView) findViewById(R.id.tvCode)).setText(code);

        long members = g.child("members").getChildrenCount();
        long watchlist = g.child("watchlist").getChildrenCount();
        boolean voteOpen = Snap.bool(g.child("vote/open"));

        // Now watching / empty state
        now = g.hasChild("nowWatching") ? Show.from(g.child("nowWatching")) : null;
        View cardNow = findViewById(R.id.cardNow);
        View noShow = findViewById(R.id.layoutNoShow);
        Party nextParty = nextParty(g);
        if (now != null) {
            cardNow.setVisibility(View.VISIBLE);
            noShow.setVisibility(View.GONE);
            ((TextView) findViewById(R.id.tvNowTitle)).setText(now.title);
            Ui.poster((ImageView) findViewById(R.id.ivNowPoster), now.posterPath);
            TextView party = findViewById(R.id.tvNowParty);
            party.setText(nextParty == null ? "No party scheduled"
                    : "Next party: " + Ui.format("EEE h:mm a", nextParty.time) + " · " + nextParty.episodeLabel());
            findViewById(R.id.btnFinish).setVisibility(admin ? View.VISIBLE : View.GONE);
            loadMyProgress(nextParty);
        } else {
            cardNow.setVisibility(View.GONE);
            noShow.setVisibility(View.VISIBLE);
            String hint;
            if (voteOpen) hint = "A vote is running. The winner becomes the group's show.";
            else if (watchlist < 2) hint = "Add at least 2 shows to the Watchlist, then the admin starts a vote.";
            else hint = admin ? "Start a vote to pick your first show." : "Waiting for the admin to start a vote.";
            ((TextView) findViewById(R.id.tvNoShow)).setText(hint);
            ((TextView) findViewById(R.id.tvRadarSub)).setText("Pick a show first");
        }

        // Up next
        TextView upNext = findViewById(R.id.tvUpNext);
        if (g.hasChild("upNext")) {
            upNext.setVisibility(View.VISIBLE);
            upNext.setText("Up next: " + Snap.str(g.child("upNext/title")) + " (starts when you finish this one)");
        } else {
            upNext.setVisibility(View.GONE);
        }

        // Vote
        findViewById(R.id.cardVote).setVisibility(voteOpen ? View.VISIBLE : View.GONE);
        if (voteOpen) {
            long voted = g.child("vote/ballots").getChildrenCount();
            boolean iVoted = g.child("vote/ballots").hasChild(Db.uid());
            ((TextView) findViewById(R.id.tvVoteSub)).setText(voted + " of " + members + " voted"
                    + (iVoted ? " · you voted" : " · tap to vote"));
        }
        findViewById(R.id.btnStartVote).setVisibility(admin && !voteOpen && !g.hasChild("upNext") ? View.VISIBLE : View.GONE);

        // Tiles
        int upcoming = 0;
        long t = System.currentTimeMillis();
        for (DataSnapshot p : g.child("parties").getChildren()) if (Snap.lng(p.child("time")) > t) upcoming++;
        ((TextView) findViewById(R.id.tvPartiesSub)).setText(upcoming + " upcoming");
        ((TextView) findViewById(R.id.tvWatchlistSub)).setText(watchlist + " shows");
        ((TextView) findViewById(R.id.tvMembersSub)).setText(members + " · invite");
    }

    private Party nextParty(DataSnapshot g) {
        Party best = null;
        long t = System.currentTimeMillis();
        for (DataSnapshot ps : g.child("parties").getChildren()) {
            Party p = Party.from(ps);
            if (now != null && p.tmdbId == now.tmdbId && p.time > t && (best == null || p.time < best.time)) best = p;
        }
        return best;
    }

    private void loadMyProgress(Party nextParty) {
        Show showNow = now;
        Db.me().child("shows").child(String.valueOf(showNow.tmdbId)).child("progress").get()
                .addOnSuccessListener(s -> {
                    if (isFinishing() || now == null || now.tmdbId != showNow.tmdbId) return;
                    int mine = s.exists() ? Snap.num(s) : LocalDb.get(this).progressOf(showNow.tmdbId);
                    now.progress = mine;
                    TextView tv = findViewById(R.id.tvNowProgress);
                    LinearProgressIndicator pb = findViewById(R.id.pbNow);
                    tv.setText(mine == 0 ? "You haven't started" : "You're on " + now.progressLabel());
                    int max = now.movie() ? 1 : Math.max(1, now.totalEpisodes);
                    pb.setMax(max);
                    pb.setProgress(Math.min(mine, max));
                    TextView radar = findViewById(R.id.tvRadarSub);
                    int catchUp = nextParty == null ? 0 : nextParty.episodesToCatchUp(mine);
                    radar.setText(catchUp > 0 ? EpisodeMath.count(catchUp) + " to catch up" : "Who's ahead of you");
                });
    }

    /** Admin: current show -> Finished, Up next -> Now watching. */
    private void confirmFinish() {
        if (now == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Finished " + now.title + "?")
                .setMessage("It moves to Finished. If a show is Up next, it becomes the new Now Watching.")
                .setPositiveButton("Yes, finished", (d, w) -> ref.get().addOnSuccessListener(g -> {
                    Map<String, Object> updates = new HashMap<>();
                    Show done = Show.from(g.child("nowWatching"));
                    done.progress = 0;
                    updates.put("finished/" + done.tmdbId, done.toMap());
                    if (g.hasChild("upNext")) {
                        Show next = Show.from(g.child("upNext"));
                        next.progress = 0;
                        updates.put("nowWatching", next.toMap());
                    } else {
                        updates.put("nowWatching", null);
                    }
                    updates.put("upNext", null);
                    ref.updateChildren(updates);
                }))
                .setNegativeButton("Cancel", null)
                .show();
    }
}
