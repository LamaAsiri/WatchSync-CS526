package com.cs526.watchsync.shows;

import com.cs526.watchsync.util.Lang;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cs526.watchsync.R;
import com.cs526.watchsync.util.Ui;

import java.util.ArrayList;
import java.util.List;

/** Episodes of one season with a tick for each watched one (like TV Time). */
public class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.VH> {

    public interface Listener {
        void onTap(int episodeNumber);
    }

    private final List<String> names = new ArrayList<>();
    private final Listener listener;
    private final Listener longPress;   // long press = rate this episode
    private int firstNumber; // running number of episode 1 of this season minus 1
    private int progress;

    public EpisodeAdapter(Listener listener, Listener longPress) {
        this.listener = listener;
        this.longPress = longPress;
    }

    public void setSeason(int numberBefore, int count, int progress) {
        this.firstNumber = numberBefore;
        this.progress = progress;
        names.clear();
        for (int i = 1; i <= count; i++) names.add(Lang.s(R.string.episode_n, i));
        notifyDataSetChanged();
    }

    public void setNames(List<String> newNames) {
        for (int i = 0; i < names.size() && i < newNames.size(); i++) {
            String n = newNames.get(i);
            if (n != null && !n.trim().isEmpty()) names.set(i, n);
        }
        notifyDataSetChanged();
    }

    public void setProgress(int progress) {
        this.progress = progress;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_episode, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        int inSeason = position + 1;
        int number = firstNumber + inSeason;
        boolean watched = number <= progress;
        h.number.setText(Lang.s(R.string.ep_short, inSeason));
        h.name.setText(names.get(position));
        h.check.setBackgroundTintList(ColorStateList.valueOf(watched ? Ui.RED : Ui.LINE));
        h.check.setImageTintList(ColorStateList.valueOf(watched ? 0xFFFFFFFF : Ui.MUTED));
        h.check.setContentDescription(Lang.s(watched ? R.string.watched : R.string.not_watched));
        h.itemView.setOnClickListener(v -> listener.onTap(number));
        h.itemView.setOnLongClickListener(v -> {
            if (longPress == null) return false;
            longPress.onTap(number);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return names.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView number;
        final TextView name;
        final ImageView check;

        VH(View v) {
            super(v);
            number = v.findViewById(R.id.tvEpNumber);
            name = v.findViewById(R.id.tvEpName);
            check = v.findViewById(R.id.ivCheck);
        }
    }
}
