package com.cs526.watchsync.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cs526.watchsync.R;
import com.cs526.watchsync.data.Db;
import com.cs526.watchsync.data.LocalDb;
import com.cs526.watchsync.data.ProgressStore;
import com.cs526.watchsync.model.Show;
import com.cs526.watchsync.shows.SearchActivity;
import com.cs526.watchsync.shows.ShowAdapter;
import com.cs526.watchsync.shows.ShowDetailActivity;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** My library (like TV Time): Watching / Not started / Finished. SQLite first, then Firebase. */
public class MyShowsFragment extends Fragment {
    private ShowAdapter adapter;
    private TextView tvEmpty;
    private ChipGroup chips;
    private DatabaseReference ref;
    private ValueEventListener listener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_my_shows, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        tvEmpty = v.findViewById(R.id.tvEmpty);
        chips = v.findViewById(R.id.chipGroup);

        adapter = new ShowAdapter((show, h) -> {
            h.subtitle.setText(show.typeLabel() + " · " + show.progressLabel());
            h.showProgress(show);
        });
        adapter.setOnClick(show -> ShowDetailActivity.open(requireContext(), show));
        adapter.setOnLongClick(show -> new AlertDialog.Builder(requireContext())
                .setTitle("Remove " + show.title + "?")
                .setMessage("It will be removed from My Shows with your progress.")
                .setPositiveButton("Remove", (d, w) -> { ProgressStore.remove(requireContext(), show.tmdbId); render(); })
                .setNegativeButton("Cancel", null)
                .show());

        RecyclerView rv = v.findViewById(R.id.rvShows);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        chips.setOnCheckedStateChangeListener((group, ids) -> render());
        v.findViewById(R.id.fabSearch).setOnClickListener(x -> startActivity(new Intent(requireContext(), SearchActivity.class)));
    }

    @Override
    public void onStart() {
        super.onStart();
        render(); // SQLite copy, instantly
        ref = Db.me().child("shows");
        listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Show> shows = new ArrayList<>();
                for (DataSnapshot s : snapshot.getChildren()) shows.add(Show.from(s));
                if (!isAdded()) return;
                LocalDb.get(requireContext()).replaceAll(shows);
                render();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };
        ref.addValueEventListener(listener);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (ref != null && listener != null) ref.removeEventListener(listener);
    }

    private void render() {
        if (!isAdded()) return;
        int checked = chips.getCheckedChipId();
        List<Show> list = new ArrayList<>();
        for (Show s : LocalDb.get(requireContext()).all()) {
            boolean finished = s.movie() ? s.progress >= 1 : s.finished();
            boolean notStarted = s.progress == 0 && !finished;
            if (checked == R.id.chipFinished && finished) list.add(s);
            else if (checked == R.id.chipNotStarted && notStarted) list.add(s);
            else if (checked == R.id.chipWatching && !finished && !notStarted) list.add(s);
        }
        Collections.sort(list, (a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        adapter.setItems(list);
        tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
