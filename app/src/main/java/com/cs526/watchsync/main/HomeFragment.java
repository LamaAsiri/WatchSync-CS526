package com.cs526.watchsync.main;

import com.cs526.watchsync.util.Lang;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cs526.watchsync.R;
import com.cs526.watchsync.data.Db;
import com.cs526.watchsync.data.LocalDb;
import com.cs526.watchsync.data.Prefs;
import com.cs526.watchsync.data.ProgressStore;
import com.cs526.watchsync.model.Party;
import com.cs526.watchsync.model.Show;
import com.cs526.watchsync.parties.PartyDetailActivity;
import com.cs526.watchsync.shows.SearchActivity;
import com.cs526.watchsync.shows.ShowAdapter;
import com.cs526.watchsync.shows.ShowDetailActivity;
import com.cs526.watchsync.util.EpisodeMath;
import com.cs526.watchsync.util.Snap;
import com.cs526.watchsync.util.Avatars;
import com.cs526.watchsync.util.Ui;
import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Home: my next watch party (from all my groups) + "Up next" episode of every show I'm watching. */
public class HomeFragment extends Fragment {
    private TextView tvHello, tvName, tvAvatar, tvPartyLabel, tvPartyTitle, tvPartySub, tvSpoiler;
    private View cardParty, layoutEmpty;
    private LinearLayout layoutActivity;
    private ShowAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        tvHello = v.findViewById(R.id.tvHello);
        tvName = v.findViewById(R.id.tvName);
        tvAvatar = v.findViewById(R.id.tvAvatar);
        cardParty = v.findViewById(R.id.cardParty);
        tvPartyLabel = v.findViewById(R.id.tvPartyLabel);
        tvPartyTitle = v.findViewById(R.id.tvPartyTitle);
        tvPartySub = v.findViewById(R.id.tvPartySub);
        tvSpoiler = v.findViewById(R.id.tvSpoiler);
        layoutEmpty = v.findViewById(R.id.layoutEmpty);
        layoutActivity = v.findViewById(R.id.layoutActivity);

        adapter = new ShowAdapter((show, h) -> {
            int next = show.progress + 1;
            h.subtitle.setText(Lang.s(R.string.next_ep_left, EpisodeMath.label(next, show.seasonCounts, false),
                    show.totalEpisodes - show.progress));
            h.showProgress(show);
            h.showAction(Lang.s(R.string.action_watched));
        });
        adapter.setOnClick(show -> ShowDetailActivity.open(requireContext(), show));
        adapter.setOnAction(show -> {
            ProgressStore.save(requireContext(), show, show.progress + 1);
            Ui.toast(requireContext(), Lang.s(R.string.toast_ep_watched, show.title, EpisodeMath.label(show.progress, show.seasonCounts, false)));
            loadUpNext();
        });

        RecyclerView rv = v.findViewById(R.id.rvUpNext);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        v.findViewById(R.id.btnFindShows).setOnClickListener(x -> startActivity(new Intent(requireContext(), SearchActivity.class)));
        tvAvatar.setOnClickListener(x -> ((com.google.android.material.bottomnavigation.BottomNavigationView)
                requireActivity().findViewById(R.id.bottomNav)).setSelectedItemId(R.id.nav_profile));
    }

    @Override
    public void onResume() {
        super.onResume();
        String name = Prefs.name(requireContext());
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        tvHello.setText(Lang.s(hour < 12 ? R.string.good_morning : hour < 18 ? R.string.good_afternoon : R.string.good_evening));
        tvName.setText(Ui.firstName(name));
        Ui.avatar(tvAvatar, name, Prefs.color(requireContext()), Avatars.mine(requireContext()));
        Avatars.refreshMine(requireContext(), photo -> {
            if (isAdded()) Ui.avatar(tvAvatar, Prefs.name(requireContext()), Prefs.color(requireContext()), photo);
        });
        loadUpNext();
        loadNextParty();
        loadActivity();
    }

    /** Last 5 things I did, from the recent_activity table. */
    private void loadActivity() {
        layoutActivity.removeAllViews();
        List<LocalDb.ActivityItem> items = LocalDb.get(requireContext()).recentActivity(5);
        if (items.isEmpty()) {
            layoutActivity.addView(activityLine(Lang.s(R.string.activity_empty), ""));
            return;
        }
        for (LocalDb.ActivityItem a : items) {
            layoutActivity.addView(activityLine(a.message, Ui.when(a.createdAt)));
        }
    }

    private View activityLine(String message, String when) {
        TextView tv = new TextView(requireContext());
        tv.setText(when.isEmpty() ? message : message + "\n" + when);
        tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.ink));
        tv.setTextSize(14);
        int pad = (int) (8 * getResources().getDisplayMetrics().density);
        tv.setPadding(0, pad, 0, pad);
        return tv;
    }

    private void loadUpNext() {
        List<Show> upNext = new ArrayList<>();
        for (Show s : LocalDb.get(requireContext()).all()) {
            if (!s.movie() && s.totalEpisodes > 0 && s.progress < s.totalEpisodes) upNext.add(s);
        }
        adapter.setItems(upNext);
        layoutEmpty.setVisibility(upNext.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** Looks at every group I'm in and finds the soonest party. */
    private void loadNextParty() {
        Db.me().child("groups").get().addOnSuccessListener(groups -> {
            List<String> codes = new ArrayList<>();
            for (DataSnapshot g : groups.getChildren()) codes.add(g.getKey());
            if (codes.isEmpty()) { showNoParty(); return; }

            final Party[] best = {null};
            final String[] bestGroup = {""};
            final String[] bestCode = {""};
            final int[] left = {codes.size()};
            long now = System.currentTimeMillis();

            for (String code : codes) {
                Db.group(code).get().addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        DataSnapshot group = task.getResult();
                        for (DataSnapshot ps : group.child("parties").getChildren()) {
                            Party p = Party.from(ps);
                            if (isAdded()) LocalDb.get(requireContext()).saveParty(code, p, "going".equals(p.rsvp.get(Db.uid())));
                            if (p.endTime() > now && !"no".equals(p.rsvp.get(Db.uid()))
                                    && (best[0] == null || p.time < best[0].time)) {
                                best[0] = p;
                                bestGroup[0] = Snap.str(group.child("name"));
                                bestCode[0] = code;
                            }
                        }
                    }
                    left[0]--;
                    if (left[0] == 0 && isAdded()) {
                        if (best[0] == null) showNoParty();
                        else showParty(best[0], bestGroup[0], bestCode[0]);
                    }
                });
            }
        });
    }

    private void showNoParty() {
        if (!isAdded()) return;
        cardParty.setVisibility(View.GONE);
        tvSpoiler.setVisibility(View.GONE);
    }

    private void showParty(Party p, String groupName, String code) {
        cardParty.setVisibility(View.VISIBLE);
        tvPartyLabel.setText(Lang.s(R.string.next_party_label, groupName.toUpperCase(Lang.locale())));
        tvPartyTitle.setText(p.showTitle + " · " + p.episodeLabel());
        tvPartySub.setText(Lang.s(R.string.party_sub_when_going, Ui.when(p.time), p.goingCount()));
        cardParty.setOnClickListener(v -> PartyDetailActivity.open(requireContext(), code, p.id));

        // Show the phone's copy first, then check Firebase (progress may have been saved
        // from another phone, or this phone's SQLite copy isn't synced yet)
        int local = LocalDb.get(requireContext()).progressOf(p.tmdbId);
        showSpoiler(p, local);
        Db.me().child("shows").child(String.valueOf(p.tmdbId)).child("progress").get()
                .addOnSuccessListener(snap -> {
                    if (isAdded() && snap.exists()) showSpoiler(p, Math.max(local, Snap.num(snap)));
                });
    }

    private void showSpoiler(Party p, int mine) {
        int catchUp = p.ended() ? 0 : p.episodesToCatchUp(mine);
        if (catchUp > 0) {
            tvSpoiler.setVisibility(View.VISIBLE);
            tvSpoiler.setText(Lang.s(R.string.spoiler_alert_home, EpisodeMath.count(catchUp), p.showTitle,
                    EpisodeMath.label(p.episode - 1, p.seasonCounts, false)));
            tvSpoiler.setOnClickListener(v -> ShowDetailActivity.open(requireContext(), mineOrParty(p)));
        } else {
            tvSpoiler.setVisibility(View.GONE);
        }
    }

    private Show mineOrParty(Party p) {
        Show s = LocalDb.get(requireContext()).find(p.tmdbId);
        return s != null ? s : p.toShow();
    }
}
