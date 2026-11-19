package com.cs526.watchsync.parties;

import com.cs526.watchsync.util.Lang;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cs526.watchsync.R;
import com.cs526.watchsync.data.Db;
import com.cs526.watchsync.data.LocalDb;
import com.cs526.watchsync.model.Party;
import com.cs526.watchsync.util.EpisodeMath;
import com.cs526.watchsync.util.Ui;

import java.util.ArrayList;
import java.util.List;

public class PartyAdapter extends RecyclerView.Adapter<PartyAdapter.VH> {

    public interface Listener {
        void onClick(Party p);
    }

    private final List<Party> items = new ArrayList<>();
    private final Listener listener;

    public PartyAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<Party> parties) {
        items.clear();
        items.addAll(parties);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_party, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Party p = items.get(position);
        boolean upcoming = !p.ended();

        h.day.setText(Ui.format("EEE", p.time).toUpperCase(Lang.locale()));
        h.date.setText(Ui.format("d", p.time));
        h.month.setText(Ui.format("MMM", p.time).toUpperCase(Lang.locale()));
        int blockBg = upcoming ? Ui.RED : Ui.LINE;
        int blockFg = upcoming ? 0xFFFFFFFF : Ui.INK;
        h.dateBlock.setBackgroundTintList(ColorStateList.valueOf(blockBg));
        h.day.setTextColor(blockFg);
        h.date.setTextColor(blockFg);
        h.month.setTextColor(blockFg);

        h.title.setText(p.showTitle + " · " + p.episodeLabel());
        h.sub.setText(Lang.s(R.string.party_row_sub, Ui.time(p.time), p.goingCount(), p.createdByName));

        String mine = p.rsvp.get(Db.uid());
        int myProgress = LocalDb.get(h.itemView.getContext()).progressOf(p.tmdbId);
        int catchUp = p.episodesToCatchUp(myProgress);

        if (upcoming && catchUp > 0 && !"no".equals(mine)) {
            Ui.badge(h.badge, Lang.s(R.string.badge_catch_up, EpisodeMath.count(catchUp)), Ui.RED_TINT, Ui.LAVA);
        } else if ("going".equals(mine)) {
            Ui.badge(h.badge, Lang.s(upcoming ? R.string.badge_youre_in : R.string.badge_you_went), Ui.INK, Ui.PAPAYA);
        } else if ("no".equals(mine)) {
            Ui.badge(h.badge, Lang.s(R.string.badge_not_going), Ui.LINE, Ui.INK);
        } else if (upcoming) {
            Ui.badge(h.badge, Lang.s(R.string.badge_answer_needed), Ui.STEEL_TINT, Ui.INK);
        } else {
            Ui.badge(h.badge, Lang.s(R.string.badge_ended), Ui.LINE, Ui.INK);
        }

        h.itemView.setOnClickListener(v -> listener.onClick(p));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final View dateBlock;
        final TextView day, date, month, title, sub, badge;

        VH(View v) {
            super(v);
            dateBlock = v.findViewById(R.id.dateBlock);
            day = v.findViewById(R.id.tvDay);
            date = v.findViewById(R.id.tvDate);
            month = v.findViewById(R.id.tvMonth);
            title = v.findViewById(R.id.tvPartyTitle);
            sub = v.findViewById(R.id.tvPartySub);
            badge = v.findViewById(R.id.tvBadge);
        }
    }
}
