package com.cs526.watchsync.util;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cs526.watchsync.R;

import java.util.ArrayList;
import java.util.List;

/** Simple people list: avatar, name, small line, coloured badge. Used for members, RSVPs, spoiler radar. */
public class RowAdapter extends RecyclerView.Adapter<RowAdapter.VH> {

    public static class Row {
        public String name = "";
        public String color = "";
        public String title = "";
        public String sub = "";
        public String badge = "";
        public int badgeBg = Ui.LINE;
        public int badgeFg = Ui.INK;
    }

    private final List<Row> rows = new ArrayList<>();

    public void setRows(List<Row> newRows) {
        rows.clear();
        rows.addAll(newRows);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Row r = rows.get(position);
        Ui.avatar(h.avatar, r.name, r.color);
        h.title.setText(r.title.isEmpty() ? r.name : r.title);
        h.sub.setText(r.sub);
        h.sub.setVisibility(r.sub.isEmpty() ? View.GONE : View.VISIBLE);
        if (r.badge.isEmpty()) h.badge.setVisibility(View.GONE);
        else Ui.badge(h.badge, r.badge, r.badgeBg, r.badgeFg);
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView avatar;
        final TextView title;
        final TextView sub;
        final TextView badge;

        VH(View v) {
            super(v);
            avatar = v.findViewById(R.id.tvAvatar);
            title = v.findViewById(R.id.tvRowTitle);
            sub = v.findViewById(R.id.tvRowSub);
            badge = v.findViewById(R.id.tvBadge);
        }
    }
}
