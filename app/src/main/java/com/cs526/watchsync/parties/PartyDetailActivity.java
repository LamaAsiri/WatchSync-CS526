package com.cs526.watchsync.parties;

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
import com.cs526.watchsync.data.ProgressStore;
import com.cs526.watchsync.groups.GroupActivity;
import com.cs526.watchsync.model.Member;
import com.cs526.watchsync.model.Party;
import com.cs526.watchsync.model.Show;
import com.cs526.watchsync.reminders.ReminderScheduler;
import com.cs526.watchsync.shows.ShowDetailActivity;
import com.cs526.watchsync.util.EpisodeMath;
import com.cs526.watchsync.util.RowAdapter;
import com.cs526.watchsync.util.Snap;
import com.cs526.watchsync.util.Ui;
import com.google.android.material.button.MaterialButton;
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
 * One watch party: countdown, spoiler warning, who's coming (and who's ready), RSVP.
 * After the party ends, asks "Did you watch it?" and moves my progress forward.
 */
public class PartyDetailActivity extends BaseActivity {
    public static final String EXTRA_PARTY = "party_id";

    private String code, partyId;
    private Party party;
    private boolean admin;
    private boolean askedCheckIn;
    private final List<Member> members = new ArrayList<>();
    private final Map<String, Integer> progress = new HashMap<>();
    private final RowAdapter people = new RowAdapter();

    private DatabaseReference ref;
    private ValueEventListener listener;

    public static void open(Context c, String code, String partyId) {
        Intent i = new Intent(c, PartyDetailActivity.class);
        i.putExtra(GroupActivity.EXTRA_CODE, code);
        i.putExtra(EXTRA_PARTY, partyId);
        c.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_party_detail);
        setupToolbar(Lang.s(R.string.title_watch_party));
        code = getIntent().getStringExtra(GroupActivity.EXTRA_CODE);
        partyId = getIntent().getStringExtra(EXTRA_PARTY);

        RecyclerView rv = findViewById(R.id.rvPeople);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(people);

        findViewById(R.id.btnGoing).setOnClickListener(v -> rsvp("going"));
        findViewById(R.id.btnNotGoing).setOnClickListener(v -> rsvp("no"));
        findViewById(R.id.btnDelete).setOnClickListener(v -> confirmDelete());
        findViewById(R.id.btnCatchUp).setOnClickListener(v -> {
            if (party != null) ShowDetailActivity.open(this, myShow());
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
        DataSnapshot ps = g.child("parties").child(partyId);
        if (!ps.exists()) {
            LocalDb.get(this).deleteParty(partyId);
            ReminderScheduler.cancel(this, partyId);
            Ui.toast(this, Lang.s(R.string.party_deleted));
            finish();
            return;
        }
        party = Party.from(ps);
        admin = Snap.str(g.child("adminUid")).equals(Db.uid());
        members.clear();
        for (DataSnapshot m : g.child("members").getChildren()) members.add(Member.from(m));
        Collections.sort(members, (a, b) -> Long.compare(a.joinedAt, b.joinedAt));

        boolean ended = party.ended();
        String mine = party.rsvp.get(Db.uid());
        LocalDb.get(this).saveParty(code, party, "going".equals(mine)); // keep the SQLite copy up to date

        setToolbarTitle(Snap.str(g.child("name")));
        ((TextView) findViewById(R.id.tvShowTitle)).setText(party.showTitle);
        ((TextView) findViewById(R.id.tvEpisode)).setText(party.movie() ? Lang.s(R.string.movie_night)
                : Lang.s(R.string.episode_x_of_y, party.episodeLabel(), party.episode, party.totalEpisodes));
        ((TextView) findViewById(R.id.tvWhen)).setText(Lang.s(R.string.planned_by, Ui.when(party.time), party.createdByName));
        ((TextView) findViewById(R.id.tvCountdown)).setText(ended ? Lang.s(R.string.party_has_ended)
                : party.live() ? Lang.s(R.string.happening_now) : Ui.countdown(party.time));

        TextView note = findViewById(R.id.tvNote);
        note.setVisibility(party.note.isEmpty() ? View.GONE : View.VISIBLE);
        note.setText(Lang.s(R.string.note_x, party.note));

        // RSVP buttons
        findViewById(R.id.layoutRsvp).setVisibility(party.upcoming() ? View.VISIBLE : View.GONE);
        ((MaterialButton) findViewById(R.id.btnGoing)).setText("going".equals(mine) ? Lang.s(R.string.btn_youre_in) : Lang.s(R.string.im_in));
        ((MaterialButton) findViewById(R.id.btnNotGoing)).setText("no".equals(mine) ? Lang.s(R.string.btn_not_going) : Lang.s(R.string.cant_go));

        boolean canDelete = admin || party.createdBy.equals(Db.uid());
        findViewById(R.id.btnDelete).setVisibility(canDelete ? View.VISIBLE : View.GONE);

        ((TextView) findViewById(R.id.tvPeopleTitle)).setText(Lang.s(ended ? R.string.who_went_n : R.string.whos_coming_n, party.goingCount()));

        loadProgress();

        // After the party: ask once if I watched it
        boolean checked = ps.child("checkedIn").hasChild(Db.uid());
        if (ended && "going".equals(mine) && !checked && !askedCheckIn) {
            askedCheckIn = true;
            askCheckIn();
        }
    }

    /** Every member's progress on this show, then refresh the warning and the list. */
    private void loadProgress() {
        Party p = party;
        for (Member m : members) {
            Db.user(m.uid).child("shows").child(String.valueOf(p.tmdbId)).child("progress").get()
                    .addOnSuccessListener(s -> {
                        if (isFinishing() || party == null || party.tmdbId != p.tmdbId) return;
                        progress.put(m.uid, s.exists() ? Snap.num(s) : 0);
                        render();
                    });
        }
        render();
    }

    private void render() {
        if (party == null) return;
        boolean ended = party.ended();

        // My spoiler warning
        Integer mineObj = progress.get(Db.uid());
        int mine = mineObj != null ? mineObj : LocalDb.get(this).progressOf(party.tmdbId);
        int catchUp = party.episodesToCatchUp(mine);
        View card = findViewById(R.id.cardSpoiler);
        if (!ended && catchUp > 0 && !"no".equals(party.rsvp.get(Db.uid()))) {
            card.setVisibility(View.VISIBLE);
            String from = EpisodeMath.label(mine + 1, party.seasonCounts, false);
            String to = EpisodeMath.label(party.episode - 1, party.seasonCounts, false);
            ((TextView) findViewById(R.id.tvSpoiler)).setText(Lang.s(R.string.spoiler_zone,
                    mine == 0 ? Lang.s(R.string.not_started_yet) : Lang.s(R.string.on_x, EpisodeMath.label(mine, party.seasonCounts, false)),
                    EpisodeMath.count(catchUp),
                    catchUp == 1 ? to : Lang.s(R.string.from_to, from, to)));
        } else {
            card.setVisibility(View.GONE);
        }

        // People: RSVP + ready / behind
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (Member m : members) {
            RowAdapter.Row r = new RowAdapter.Row();
            r.uid = m.uid;
            r.name = m.name;
            r.color = m.color;
            r.title = m.uid.equals(Db.uid()) ? Lang.s(R.string.name_you, m.name) : m.name;
            String answer = party.rsvp.get(m.uid);
            r.sub = Lang.s("going".equals(answer) ? R.string.going : "no".equals(answer) ? R.string.not_going : R.string.no_answer_yet);

            Integer p = progress.get(m.uid);
            if (p != null && !party.movie()) {
                int behind = party.episodesToCatchUp(p);
                if (behind > 0) {
                    r.badge = Lang.s(R.string.behind_catch_up, behind);
                    r.badgeBg = Ui.RED_TINT;
                    r.badgeFg = Ui.LAVA;
                } else {
                    r.badge = Lang.s(R.string.ready);
                    r.badgeBg = Ui.STEEL_TINT;
                    r.badgeFg = Ui.INK;
                }
            }
            rows.add(r);
        }
        people.setRows(rows);
    }

    private void rsvp(String answer) {
        if (party == null) return;
        ref.child("parties").child(partyId).child("rsvp").child(Db.uid()).setValue(answer);
        boolean going = "going".equals(answer);
        LocalDb.get(this).saveParty(code, party, going);
        LocalDb.get(this).logActivity("party", Lang.s(going ? R.string.log_going_to : R.string.log_cant_go_to,
                party.showTitle + " · " + party.episodeLabel()));
        if (going) {
            ReminderScheduler.schedule(this, partyId, party.showTitle + " · " + party.episodeLabel(), party.time);
        } else {
            ReminderScheduler.cancel(this, partyId);
        }
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle(Lang.s(R.string.delete_party_q))
                .setMessage(Lang.s(R.string.delete_party_msg))
                .setPositiveButton(Lang.s(R.string.delete), (d, w) -> {
                    ReminderScheduler.cancel(this, partyId);
                    LocalDb.get(this).deleteParty(partyId);
                    ref.child("parties").child(partyId).removeValue();
                    finish();
                })
                .setNegativeButton(Lang.s(R.string.cancel), null)
                .show();
    }

    private void askCheckIn() {
        String what = party.movie() ? Lang.s(R.string.the_movie) : party.episodeLabel();
        new AlertDialog.Builder(this)
                .setTitle(Lang.s(R.string.did_you_watch, what))
                .setMessage(party.movie() ? Lang.s(R.string.checkin_movie_msg)
                        : Lang.s(R.string.checkin_msg, party.episodeLabel()))
                .setCancelable(false)
                .setPositiveButton(Lang.s(R.string.yes_watched_it), (d, w) -> checkIn(true))
                .setNegativeButton(Lang.s(R.string.no_missed_it), (d, w) -> checkIn(false))
                .show();
    }

    private void checkIn(boolean watched) {
        ref.child("parties").child(partyId).child("checkedIn").child(Db.uid()).setValue(watched);
        if (!watched) return;
        Show s = myShow();
        int newProgress = Math.max(s.progress, party.movie() ? 1 : party.episode);
        ProgressStore.save(this, s, newProgress);
        Ui.toast(this, Lang.s(R.string.progress_updated, s.progressLabel()));
    }

    /** My copy of the show (keeps my progress), or the party's info if I never added it. */
    private Show myShow() {
        Show local = LocalDb.get(this).find(party.tmdbId);
        Show s = local != null ? local : party.toShow();
        Integer fromFirebase = progress.get(Db.uid());
        if (fromFirebase != null) s.progress = Math.max(s.progress, fromFirebase);
        if (s.seasonCounts.isEmpty()) s.seasonCounts = party.seasonCounts;
        if (s.totalEpisodes == 0) s.totalEpisodes = party.totalEpisodes;
        return s;
    }
}
