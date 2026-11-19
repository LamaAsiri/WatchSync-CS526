package com.cs526.watchsync.parties;

import com.cs526.watchsync.util.Lang;

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
import com.cs526.watchsync.groups.GroupActivity;
import com.cs526.watchsync.model.Party;
import com.cs526.watchsync.util.Snap;
import com.cs526.watchsync.util.Ui;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** All watch parties of a group: Upcoming / Past. Anyone can schedule one for the Now Watching show. */
public class PartiesActivity extends BaseActivity {
    private String code;
    private boolean hasShow;
    private final List<Party> all = new ArrayList<>();
    private PartyAdapter adapter;
    private MaterialButtonToggleGroup toggle;
    private TextView tvEmpty, tvNowShow;
    private DatabaseReference ref;
    private ValueEventListener listener;

    public static void open(Context c, String code) {
        Intent i = new Intent(c, PartiesActivity.class);
        i.putExtra(GroupActivity.EXTRA_CODE, code);
        c.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parties);
        setupToolbar(Lang.s(R.string.title_watch_parties));
        code = getIntent().getStringExtra(GroupActivity.EXTRA_CODE);

        tvEmpty = findViewById(R.id.tvEmpty);
        tvNowShow = findViewById(R.id.tvNowShow);
        toggle = findViewById(R.id.toggle);

        adapter = new PartyAdapter(p -> PartyDetailActivity.open(this, code, p.id));
        RecyclerView rv = findViewById(R.id.rvParties);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        toggle.addOnButtonCheckedListener((group, id, checked) -> { if (checked) render(); });
        findViewById(R.id.fabSchedule).setOnClickListener(v -> {
            if (!hasShow) {
                Ui.toast(this, Lang.s(R.string.pick_group_show_first));
                return;
            }
            SchedulePartyActivity.open(this, code);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        ref = Db.group(code);
        listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot g) {
                hasShow = g.hasChild("nowWatching");
                String now = Snap.str(g.child("nowWatching/title"));
                tvNowShow.setText(hasShow ? Lang.s(R.string.now_watching_x, now) : Lang.s(R.string.no_show_vote_first));
                setToolbarTitle("Parties · " + Snap.str(g.child("name")));
                all.clear();
                for (DataSnapshot p : g.child("parties").getChildren()) all.add(Party.from(p));
                render();
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

    private void render() {
        boolean upcoming = toggle.getCheckedButtonId() != R.id.btnPast;
        List<Party> list = new ArrayList<>();
        for (Party p : all) if (!p.ended() == upcoming) list.add(p); // a party that is on right now stays in "Upcoming"
        // upcoming: soonest first, past: newest first
        Collections.sort(list, (a, b) -> upcoming ? Long.compare(a.time, b.time) : Long.compare(b.time, a.time));
        adapter.setItems(list);
        tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        tvEmpty.setText(Lang.s(upcoming ? R.string.no_parties_planned : R.string.no_past_parties));
    }
}
