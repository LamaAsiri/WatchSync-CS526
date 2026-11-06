package com.cs526.watchsync.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cs526.watchsync.R;
import com.cs526.watchsync.data.Db;
import com.cs526.watchsync.groups.CreateGroupActivity;
import com.cs526.watchsync.groups.GroupActivity;
import com.cs526.watchsync.groups.JoinGroupActivity;
import com.cs526.watchsync.util.Snap;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class GroupsFragment extends Fragment {
    private final List<String[]> rows = new ArrayList<>(); // {code, name, sub}
    private RecyclerView.Adapter<RecyclerView.ViewHolder> adapter;
    private TextView tvEmpty;
    private DatabaseReference ref;
    private ValueEventListener listener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_groups, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        tvEmpty = v.findViewById(R.id.tvEmpty);
        v.findViewById(R.id.btnCreate).setOnClickListener(x -> startActivity(new Intent(requireContext(), CreateGroupActivity.class)));
        v.findViewById(R.id.btnJoin).setOnClickListener(x -> startActivity(new Intent(requireContext(), JoinGroupActivity.class)));

        adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View item = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group, parent, false);
                return new RecyclerView.ViewHolder(item) { };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
                String[] row = rows.get(position);
                ((TextView) h.itemView.findViewById(R.id.tvGroupName)).setText(row[1]);
                ((TextView) h.itemView.findViewById(R.id.tvGroupSub)).setText(row[2]);
                h.itemView.setOnClickListener(x -> GroupActivity.open(requireContext(), row[0]));
            }

            @Override
            public int getItemCount() {
                return rows.size();
            }
        };
        RecyclerView rv = v.findViewById(R.id.rvGroups);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);
    }

    @Override
    public void onStart() {
        super.onStart();
        ref = Db.me().child("groups");
        listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> codes = new ArrayList<>();
                for (DataSnapshot c : snapshot.getChildren()) codes.add(c.getKey());
                loadGroups(codes);
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

    private void loadGroups(List<String> codes) {
        if (!isAdded()) return;
        rows.clear();
        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(codes.isEmpty() ? View.VISIBLE : View.GONE);
        for (String code : codes) {
            Db.group(code).get().addOnSuccessListener(g -> {
                if (!isAdded() || !g.exists()) return;
                String now = Snap.str(g.child("nowWatching/title"));
                long members = g.child("members").getChildrenCount();
                String sub = (now.isEmpty() ? "No show yet" : "Watching " + now) + " · " + members + " members";
                if (Snap.bool(g.child("vote/open"))) sub += " · voting open";
                rows.add(new String[]{code, Snap.str(g.child("name")), sub});
                adapter.notifyDataSetChanged();
            });
        }
    }
}
