package com.cs526.watchsync.parties;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.cs526.watchsync.BaseActivity;
import com.cs526.watchsync.R;
import com.cs526.watchsync.data.Db;
import com.cs526.watchsync.data.LocalDb;
import com.cs526.watchsync.data.Prefs;
import com.cs526.watchsync.groups.GroupActivity;
import com.cs526.watchsync.model.Member;
import com.cs526.watchsync.model.Party;
import com.cs526.watchsync.model.Show;
import com.cs526.watchsync.reminders.ReminderScheduler;
import com.cs526.watchsync.util.EpisodeMath;
import com.cs526.watchsync.util.Snap;
import com.cs526.watchsync.util.Ui;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plan a watch party for the group's Now Watching show:
 * pick the episode, date and time. Warns who hasn't reached the episode before it.
 */
public class SchedulePartyActivity extends BaseActivity {
    private String code;
    private Show show;
    private int episode = 1;
    private final Calendar when = Calendar.getInstance();
    private final List<Member> members = new ArrayList<>();
    private final Map<String, Integer> progress = new HashMap<>(); // uid -> last watched episode

    private TextView tvEpisode, tvDate, tvTime, tvWarning;
    private EditText etNote;

    public static void open(Context c, String code) {
        Intent i = new Intent(c, SchedulePartyActivity.class);
        i.putExtra(GroupActivity.EXTRA_CODE, code);
        c.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule_party);
        setupToolbar("Schedule a party");
        code = getIntent().getStringExtra(GroupActivity.EXTRA_CODE);

        tvEpisode = findViewById(R.id.tvEpisode);
        tvDate = findViewById(R.id.tvDate);
        tvTime = findViewById(R.id.tvTime);
        tvWarning = findViewById(R.id.tvWarning);
        etNote = findViewById(R.id.etNote);

        // Default: tomorrow at 9:00 PM
        when.add(Calendar.DAY_OF_MONTH, 1);
        when.set(Calendar.HOUR_OF_DAY, 21);
        when.set(Calendar.MINUTE, 0);
        when.set(Calendar.SECOND, 0);
        when.set(Calendar.MILLISECOND, 0);
        showDateTime();

        findViewById(R.id.btnMinus).setOnClickListener(v -> setEpisode(episode - 1));
        findViewById(R.id.btnPlus).setOnClickListener(v -> setEpisode(episode + 1));
        tvDate.setOnClickListener(v -> pickDate());
        tvTime.setOnClickListener(v -> pickTime());
        findViewById(R.id.btnSchedule).setOnClickListener(v -> save());
        findViewById(R.id.btnSchedule).setEnabled(false);

        loadGroup();
    }

    private void loadGroup() {
        Db.group(code).get().addOnSuccessListener(g -> {
            if (!g.hasChild("nowWatching")) {
                Ui.toast(this, "The group has no show yet");
                finish();
                return;
            }
            show = Show.from(g.child("nowWatching"));
            ((TextView) findViewById(R.id.tvShowTitle)).setText(show.title);
            findViewById(R.id.layoutEpisode).setVisibility(show.movie() ? View.GONE : View.VISIBLE);

            for (DataSnapshot m : g.child("members").getChildren()) members.add(Member.from(m));
            int mine = LocalDb.get(this).progressOf(show.tmdbId);
            setEpisode(show.movie() ? 1 : mine + 1); // default: my next episode
            findViewById(R.id.btnSchedule).setEnabled(true);
            loadProgress();
        }).addOnFailureListener(e -> Ui.toast(this, "No connection: " + e.getMessage()));
    }

    /** Reads every member's progress on this show (users/{uid}/shows/{tmdbId}/progress). */
    private void loadProgress() {
        for (Member m : members) {
            Db.user(m.uid).child("shows").child(String.valueOf(show.tmdbId)).child("progress").get()
                    .addOnSuccessListener(s -> {
                        progress.put(m.uid, s.exists() ? Snap.num(s) : 0);
                        updateWarning();
                    });
        }
    }

    private void setEpisode(int value) {
        if (show == null) return;
        int max = show.movie() ? 1 : Math.max(1, show.totalEpisodes);
        episode = Math.max(1, Math.min(value, max));
        tvEpisode.setText(show.movie() ? "Movie"
                : EpisodeMath.label(episode, show.seasonCounts, false) + "  (" + episode + " of " + max + ")");
        updateWarning();
    }

    /** "2 members haven't reached S1 E11 yet: you (Ep 9), Lama (Ep 7)." */
    private void updateWarning() {
        if (show == null || show.movie() || episode <= 1) {
            tvWarning.setVisibility(View.GONE);
            return;
        }
        List<String> behind = new ArrayList<>();
        for (Member m : members) {
            Integer p = progress.get(m.uid);
            if (p == null) continue; // not loaded yet
            if (p < episode - 1) {
                String who = m.uid.equals(Db.uid()) ? "you" : m.name;
                behind.add(who + " (" + (p == 0 ? "not started" : "on " + EpisodeMath.label(p, show.seasonCounts, false)) + ")");
            }
        }
        if (behind.isEmpty()) {
            tvWarning.setVisibility(View.GONE);
            return;
        }
        tvWarning.setVisibility(View.VISIBLE);
        tvWarning.setText(behind.size() + (behind.size() == 1 ? " member hasn't" : " members haven't")
                + " reached " + EpisodeMath.label(episode - 1, show.seasonCounts, false) + " yet: "
                + android.text.TextUtils.join(", ", behind) + ". They'll see a catch-up warning.");
    }

    private void pickDate() {
        new DatePickerDialog(this, (view, y, m, d) -> {
            when.set(Calendar.YEAR, y);
            when.set(Calendar.MONTH, m);
            when.set(Calendar.DAY_OF_MONTH, d);
            showDateTime();
        }, when.get(Calendar.YEAR), when.get(Calendar.MONTH), when.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void pickTime() {
        new TimePickerDialog(this, (view, h, min) -> {
            when.set(Calendar.HOUR_OF_DAY, h);
            when.set(Calendar.MINUTE, min);
            showDateTime();
        }, when.get(Calendar.HOUR_OF_DAY), when.get(Calendar.MINUTE), false).show();
    }

    private void showDateTime() {
        tvDate.setText(Ui.format("EEE, d MMM", when.getTimeInMillis()));
        tvTime.setText(Ui.format("h:mm a", when.getTimeInMillis()));
    }

    private void save() {
        if (show == null) return;
        if (when.getTimeInMillis() <= System.currentTimeMillis()) {
            Ui.toast(this, "Pick a time in the future");
            return;
        }
        Party p = new Party();
        p.tmdbId = show.tmdbId;
        p.showTitle = show.title;
        p.posterPath = show.posterPath;
        p.mediaType = show.mediaType;
        p.seasonCounts = show.seasonCounts;
        p.totalEpisodes = show.totalEpisodes;
        p.episode = episode;
        p.time = when.getTimeInMillis();
        p.note = etNote.getText().toString().trim();
        p.createdBy = Db.uid();
        p.createdByName = Prefs.name(this);
        p.rsvp.put(Db.uid(), "going"); // the person who plans it is going

        DatabaseReference ref = Db.group(code).child("parties").push();
        String id = ref.getKey();
        if (id == null) return;
        findViewById(R.id.btnSchedule).setEnabled(false);
        ref.setValue(p.toMap()).addOnSuccessListener(x -> {
            ReminderScheduler.schedule(this, id, p.showTitle + " · " + p.episodeLabel(), p.time);
            Ui.toast(this, "Party scheduled!");
            PartyDetailActivity.open(this, code, id);
            finish();
        }).addOnFailureListener(e -> {
            findViewById(R.id.btnSchedule).setEnabled(true);
            Ui.toast(this, "Couldn't save: " + e.getMessage());
        });
    }
}
